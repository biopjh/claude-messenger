package com.example.messenger.message.service;

import com.example.messenger.chatroom.service.ChatRoomService;
import com.example.messenger.common.exception.ApiException;
import com.example.messenger.common.exception.ErrorCode;
import com.example.messenger.message.domain.Message;
import com.example.messenger.message.domain.MessageType;
import com.example.messenger.message.dto.MessageResponse;
import com.example.messenger.message.dto.ReactionRow;
import com.example.messenger.message.dto.ReactionSummary;
import com.example.messenger.message.mapper.MessageMapper;
import com.example.messenger.message.mapper.ReactionMapper;
import com.example.messenger.messaging.MessageBroker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageService {

    /** 본인 메시지 수정 가능 시간 — 카카오톡과 비슷하게 5분. */
    private static final Duration EDIT_WINDOW = Duration.ofMinutes(5);

    private final MessageMapper messageMapper;
    private final ReactionMapper reactionMapper;
    private final ChatRoomService chatRoomService;
    private final MessageBroker broker;

    /**
     * 메시지 영구 저장. 방의 멤버만 보낼 수 있다.
     * type 이 IMAGE/FILE 이면 attachmentUrl 이 필수이며 attachments 테이블에도 1건 insert.
     * replyToMessageId 가 있으면 그 메시지가 같은 방에 존재해야 한다.
     */
    @Transactional
    public Message save(Long roomId, Long senderId,
                        MessageType type, String content,
                        String attachmentUrl, String attachmentMimeType, Long attachmentSizeBytes,
                        Long replyToMessageId) {
        chatRoomService.requireMember(roomId, senderId);

        MessageType safeType = type == null ? MessageType.TEXT : type;
        boolean needsAttachment = (safeType == MessageType.IMAGE || safeType == MessageType.FILE);
        if (needsAttachment && (attachmentUrl == null || attachmentUrl.isBlank())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "첨부 URL 이 필요합니다.");
        }
        if (safeType == MessageType.SYSTEM) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "잘못된 메시지 타입입니다.");
        }

        // 답장 대상 검증 — 같은 방의 메시지여야 함
        if (replyToMessageId != null) {
            Message target = messageMapper.findById(replyToMessageId)
                    .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "답장 대상 메시지를 찾을 수 없습니다."));
            if (!target.getRoomId().equals(roomId)) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "다른 방의 메시지에는 답장할 수 없습니다.");
            }
        }

        Message m = Message.builder()
                .roomId(roomId)
                .senderId(senderId)
                .type(safeType)
                .content(content)
                .replyToMessageId(replyToMessageId)
                .build();
        messageMapper.insert(m);

        if (needsAttachment) {
            messageMapper.insertAttachment(m.getId(), attachmentUrl, attachmentMimeType, attachmentSizeBytes);
        }

        // 보낸 사람은 자동 read 처리
        chatRoomService.markRead(roomId, senderId, m.getId());
        return m;
    }

    /**
     * 본인 메시지 본문 수정. TEXT 만, 5 분 이내, 삭제되지 않은 것만.
     * 성공 시 같은 방의 모든 멤버에게 STOMP 로 갱신된 메시지 broadcast.
     */
    @Transactional
    public MessageResponse editMessage(Long messageId, Long userId, String newContent) {
        Message m = messageMapper.findById(messageId)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "메시지를 찾을 수 없습니다."));
        if (!m.getSenderId().equals(userId)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "본인 메시지만 수정할 수 있습니다.");
        }
        if (m.getType() != MessageType.TEXT) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "텍스트 메시지만 수정할 수 있습니다.");
        }
        if (m.getDeletedAt() != null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "삭제된 메시지는 수정할 수 없습니다.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (Duration.between(m.getCreatedAt(), now).compareTo(EDIT_WINDOW) > 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "5분이 지난 메시지는 수정할 수 없습니다.");
        }

        messageMapper.updateContent(messageId, newContent, now);

        MessageResponse updated = getResponseById(messageId);
        broker.send("/topic/rooms/" + m.getRoomId(), updated);
        return updated;
    }

    /**
     * 본인 메시지 소프트 삭제. 행은 남기고 deleted_at 만 채운다.
     * 성공 시 같은 방의 모든 멤버에게 STOMP 로 갱신된 메시지 broadcast.
     */
    @Transactional
    public MessageResponse deleteMessage(Long messageId, Long userId) {
        Message m = messageMapper.findById(messageId)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "메시지를 찾을 수 없습니다."));
        if (!m.getSenderId().equals(userId)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "본인 메시지만 삭제할 수 있습니다.");
        }
        if (m.getDeletedAt() != null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "이미 삭제된 메시지입니다.");
        }
        if (m.getType() == MessageType.SYSTEM) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "시스템 메시지는 삭제할 수 없습니다.");
        }

        messageMapper.softDelete(messageId, OffsetDateTime.now());

        MessageResponse updated = getResponseById(messageId);
        broker.send("/topic/rooms/" + m.getRoomId(), updated);
        return updated;
    }

    /** 한 방의 과거 메시지 페이지. 멤버 검사 + 리액션 결합. */
    @Transactional(readOnly = true)
    public List<MessageResponse> getPage(Long roomId, Long me, Long cursorId, int size) {
        chatRoomService.requireMember(roomId, me);
        int safeSize = Math.max(1, Math.min(size, 100));
        List<MessageResponse> raw = messageMapper.findPage(roomId, cursorId, safeSize);
        return attachReactions(raw);
    }

    /** 단일 메시지의 화면용 DTO (수정/삭제/리액션 후 broadcast 용). 리액션 자동 결합. */
    @Transactional(readOnly = true)
    public MessageResponse getResponseById(Long messageId) {
        MessageResponse raw = messageMapper.findResponseById(messageId)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "메시지 조회 실패"));
        return attachReactions(List.of(raw)).get(0);
    }

    /** 한 방 안에서 본문 부분일치 검색. */
    @Transactional(readOnly = true)
    public List<MessageResponse> searchInRoom(Long roomId, Long me, String query, int size) {
        chatRoomService.requireMember(roomId, me);
        if (query == null || query.isBlank()) return List.of();
        String trimmed = query.trim();
        if (trimmed.length() > 100) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "검색어는 100자 이내로 입력하세요.");
        }
        int safeSize = Math.max(1, Math.min(size, 100));
        return attachReactions(messageMapper.searchInRoom(roomId, trimmed, safeSize));
    }

    /**
     * 메시지 리스트에 reactions 를 한 번의 추가 쿼리로 결합한다 (N+1 회피).
     * MyBatis 가 17-arg 생성자로 만들어준 MessageResponse 의 reactions 필드는 빈 리스트 →
     * 이 메서드가 채운 새 record 로 교체.
     */
    private List<MessageResponse> attachReactions(List<MessageResponse> messages) {
        if (messages == null || messages.isEmpty()) return messages;
        List<Long> ids = messages.stream().map(MessageResponse::id).toList();
        List<ReactionRow> rows = reactionMapper.findRowsByMessageIds(ids);

        // messageId → (emoji → ReactionSummary)
        Map<Long, Map<String, ReactionSummary>> grouped = new HashMap<>();
        for (ReactionRow r : rows) {
            grouped
                .computeIfAbsent(r.messageId(), k -> new LinkedHashMap<>())
                .compute(r.emoji(), (emoji, prev) -> {
                    if (prev == null) {
                        return new ReactionSummary(emoji, new ArrayList<>(List.of(r.userId())),
                                                          new ArrayList<>(List.of(r.userNickname())));
                    }
                    prev.userIds().add(r.userId());
                    prev.userNicknames().add(r.userNickname());
                    return prev;
                });
        }

        List<MessageResponse> out = new ArrayList<>(messages.size());
        for (MessageResponse m : messages) {
            Map<String, ReactionSummary> em = grouped.get(m.id());
            out.add(em == null ? m : m.withReactions(new ArrayList<>(em.values())));
        }
        return out;
    }
}
