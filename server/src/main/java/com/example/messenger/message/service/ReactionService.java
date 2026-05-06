package com.example.messenger.message.service;

import com.example.messenger.chatroom.service.ChatRoomService;
import com.example.messenger.common.exception.ApiException;
import com.example.messenger.common.exception.ErrorCode;
import com.example.messenger.message.domain.Message;
import com.example.messenger.message.dto.MessageResponse;
import com.example.messenger.message.mapper.MessageMapper;
import com.example.messenger.message.mapper.ReactionMapper;
import com.example.messenger.messaging.MessageBroker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 메시지 이모지 리액션 토글 + STOMP 브로드캐스트.
 * 같은 사용자가 같은 메시지에 같은 이모지를 다시 클릭하면 제거되고, 새 이모지면 추가된다.
 */
@Service
@RequiredArgsConstructor
public class ReactionService {

    /** 클라이언트 picker 와 동일한 화이트리스트. 이외 이모지는 거부. */
    private static final Set<String> ALLOWED_EMOJIS = Set.of(
            "👍", "❤️", "😂", "😮", "😢", "🔥"
    );

    private final ReactionMapper reactionMapper;
    private final MessageMapper messageMapper;
    private final MessageService messageService;
    private final ChatRoomService chatRoomService;
    private final MessageBroker broker;

    @Transactional
    public MessageResponse toggle(Long messageId, Long userId, String emoji) {
        if (emoji == null || !ALLOWED_EMOJIS.contains(emoji)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "지원하지 않는 이모지입니다.");
        }
        Message m = messageMapper.findById(messageId)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "메시지를 찾을 수 없습니다."));

        // 권한: 같은 방 멤버만 반응 가능
        chatRoomService.requireMember(m.getRoomId(), userId);

        if (m.getDeletedAt() != null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "삭제된 메시지에는 반응할 수 없습니다.");
        }

        // 토글: 기존 반응이 있으면 삭제, 없으면 새로 추가
        int deleted = reactionMapper.delete(messageId, userId, emoji);
        if (deleted == 0) {
            reactionMapper.insert(messageId, userId, emoji);
        }

        MessageResponse updated = messageService.getResponseById(messageId);
        broker.send("/topic/rooms/" + m.getRoomId(), updated);
        return updated;
    }
}
