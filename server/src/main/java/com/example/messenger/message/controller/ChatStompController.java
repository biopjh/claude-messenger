package com.example.messenger.message.controller;

import com.example.messenger.auth.jwt.JwtChannelInterceptor;
import com.example.messenger.chatroom.service.ChatRoomService;
import com.example.messenger.message.domain.Message;
import com.example.messenger.message.dto.MessageResponse;
import com.example.messenger.message.dto.SendMessageRequest;
import com.example.messenger.message.service.MessageService;
import com.example.messenger.user.domain.User;
import com.example.messenger.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * 실시간 채팅 STOMP 컨트롤러.
 *  - 클라 → /app/chat.send : 메시지 송신
 *  - 클라 → /app/chat.read : 읽음 처리
 *  서버 → /topic/rooms/{roomId} : 방 멤버 모두에게 브로드캐스트
 *  서버 → /user/queue/notifications : 특정 사용자 개인 큐 (목록 갱신용)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final MessageService messageService;
    private final ChatRoomService chatRoomService;
    private final UserService userService;
    private final SimpMessagingTemplate broker;

    @MessageMapping("/chat.send")
    public void send(@Payload SendMessageRequest req, SimpMessageHeaderAccessor headers) {
        Long senderId = (Long) headers.getSessionAttributes().get(JwtChannelInterceptor.SESSION_USER_ID);
        if (senderId == null) {
            log.warn("STOMP send: senderId missing in session");
            return;
        }

        // 1) DB 저장 (서비스가 멤버 검사 + 첨부 insert도 수행)
        Message saved = messageService.save(
                req.roomId(), senderId,
                req.type(), req.content(),
                req.attachmentUrl(), req.attachmentMimeType(), req.attachmentSizeBytes());

        // 2) 보낸 사람 정보 채워서 응답 DTO 구성
        User sender = userService.getById(senderId);
        MessageResponse dto = new MessageResponse(
                saved.getId(), saved.getRoomId(), saved.getSenderId(),
                sender.getNickname(), sender.getProfileImageUrl(),
                saved.getType(), saved.getContent(), saved.getCreatedAt(),
                req.attachmentUrl(), req.attachmentMimeType(), req.attachmentSizeBytes());

        // 3) 같은 방 토픽으로 브로드캐스트
        broker.convertAndSend("/topic/rooms/" + req.roomId(), dto);

        // 4) 방의 다른 멤버들에게 개인 큐 알림 (목록 갱신용)
        for (Long uid : chatRoomService.findMemberUserIds(req.roomId())) {
            if (uid.equals(senderId)) continue;
            broker.convertAndSendToUser(
                    uid.toString(),
                    "/queue/notifications",
                    Map.of(
                            "kind", "NEW_MESSAGE",
                            "roomId", req.roomId(),
                            "message", dto));
        }
    }

    /**
     * 클라이언트가 메시지를 읽었음을 알릴 때.
     * payload: { "roomId": 1, "lastReadMessageId": 42 }
     */
    @MessageMapping("/chat.read")
    public void read(@Payload Map<String, Object> body, SimpMessageHeaderAccessor headers) {
        Long userId = (Long) headers.getSessionAttributes().get(JwtChannelInterceptor.SESSION_USER_ID);
        if (userId == null) return;
        Long roomId = toLong(body.get("roomId"));
        Long lastId = toLong(body.get("lastReadMessageId"));
        if (roomId == null || lastId == null) return;
        chatRoomService.markRead(roomId, userId, lastId);

        // 같은 방 다른 멤버에게 ‘읽음 위치’ 갱신을 알림 (선택적 — 화면에 1 표시 사라짐 등에 활용)
        for (Long uid : chatRoomService.findMemberUserIds(roomId)) {
            if (uid.equals(userId)) continue;
            broker.convertAndSendToUser(
                    uid.toString(),
                    "/queue/notifications",
                    Map.of(
                            "kind", "READ",
                            "roomId", roomId,
                            "userId", userId,
                            "lastReadMessageId", lastId));
        }
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
