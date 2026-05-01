package com.example.messenger.message.service;

import com.example.messenger.chatroom.service.ChatRoomService;
import com.example.messenger.common.exception.ApiException;
import com.example.messenger.common.exception.ErrorCode;
import com.example.messenger.message.domain.Message;
import com.example.messenger.message.domain.MessageType;
import com.example.messenger.message.dto.MessageResponse;
import com.example.messenger.message.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;
    private final ChatRoomService chatRoomService;

    /**
     * 메시지 영구 저장. 방의 멤버만 보낼 수 있다.
     * type 이 IMAGE/FILE 이면 attachmentUrl 이 필수이며 attachments 테이블에도 1건 insert.
     */
    @Transactional
    public Message save(Long roomId, Long senderId,
                        MessageType type, String content,
                        String attachmentUrl, String attachmentMimeType, Long attachmentSizeBytes) {
        chatRoomService.requireMember(roomId, senderId);

        MessageType safeType = type == null ? MessageType.TEXT : type;
        boolean needsAttachment = (safeType == MessageType.IMAGE || safeType == MessageType.FILE);
        if (needsAttachment && (attachmentUrl == null || attachmentUrl.isBlank())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "첨부 URL 이 필요합니다.");
        }
        // SYSTEM 은 클라이언트가 직접 보낼 수 없게 차단 (시스템 메시지는 서버가 직접 만든다)
        if (safeType == MessageType.SYSTEM) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "잘못된 메시지 타입입니다.");
        }

        Message m = Message.builder()
                .roomId(roomId)
                .senderId(senderId)
                .type(safeType)
                .content(content)
                .build();
        messageMapper.insert(m);

        if (needsAttachment) {
            messageMapper.insertAttachment(m.getId(), attachmentUrl, attachmentMimeType, attachmentSizeBytes);
        }

        // 보낸 사람은 자동 read 처리
        chatRoomService.markRead(roomId, senderId, m.getId());
        return m;
    }

    /** 한 방의 과거 메시지 페이지. 멤버 검사 포함. */
    @Transactional(readOnly = true)
    public List<MessageResponse> getPage(Long roomId, Long me, Long cursorId, int size) {
        chatRoomService.requireMember(roomId, me);
        int safeSize = Math.max(1, Math.min(size, 100));
        return messageMapper.findPage(roomId, cursorId, safeSize);
    }
}
