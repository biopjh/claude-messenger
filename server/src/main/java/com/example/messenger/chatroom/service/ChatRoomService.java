package com.example.messenger.chatroom.service;

import com.example.messenger.chatroom.domain.ChatRoom;
import com.example.messenger.chatroom.domain.ChatRoomMember;
import com.example.messenger.chatroom.domain.ChatRoomType;
import com.example.messenger.chatroom.dto.ChatRoomDetail;
import com.example.messenger.chatroom.dto.ChatRoomSummary;
import com.example.messenger.chatroom.mapper.ChatRoomMapper;
import com.example.messenger.common.exception.ApiException;
import com.example.messenger.common.exception.ErrorCode;
import com.example.messenger.friend.service.FriendService;
import com.example.messenger.message.domain.Message;
import com.example.messenger.message.domain.MessageType;
import com.example.messenger.message.dto.MessageResponse;
import com.example.messenger.message.mapper.MessageMapper;
import com.example.messenger.messaging.MessageBroker;
import com.example.messenger.user.domain.User;
import com.example.messenger.user.dto.UserResponse;
import com.example.messenger.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomMapper chatRoomMapper;
    private final UserService userService;
    private final FriendService friendService;
    private final MessageMapper messageMapper;
    private final MessageBroker broker;

    /**
     * 두 사용자의 1:1 방을 찾거나 만든다(idempotent).
     */
    @Transactional
    public Long getOrCreateDirectRoom(Long me, Long otherUserId) {
        if (me.equals(otherUserId)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "본인과는 채팅방을 만들 수 없습니다.");
        }
        var existing = chatRoomMapper.findDirectRoomId(me, otherUserId);
        if (existing.isPresent()) return existing.get();

        ChatRoom room = ChatRoom.builder()
                .type(ChatRoomType.DIRECT)
                .title(null)
                .createdBy(me)
                .build();
        chatRoomMapper.insertRoom(room);

        chatRoomMapper.insertMember(ChatRoomMember.builder().roomId(room.getId()).userId(me).build());
        chatRoomMapper.insertMember(ChatRoomMember.builder().roomId(room.getId()).userId(otherUserId).build());
        return room.getId();
    }

    /**
     * 그룹채팅 만들기.
     *  - title 필수
     *  - memberIds 는 모두 ‘나(creator)’의 ACCEPTED 친구여야 함
     *  - 자기 자신은 자동으로 멤버에 포함
     */
    @Transactional
    public Long createGroupRoom(Long creator, String title, List<Long> memberIds) {
        if (title == null || title.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "방 제목을 입력하세요.");
        }
        Set<Long> targets = new HashSet<>(memberIds);
        targets.remove(creator);
        if (targets.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "초대할 멤버를 1명 이상 선택하세요.");
        }
        // 모두 친구인지 확인 (단순화: 1건씩)
        for (Long uid : targets) {
            if (!friendService.areFriends(creator, uid)) {
                User u = safeFind(uid);
                String name = u != null ? u.getNickname() : ("id=" + uid);
                throw new ApiException(ErrorCode.BAD_REQUEST, name + " 님은 친구가 아닙니다.");
            }
        }

        ChatRoom room = ChatRoom.builder()
                .type(ChatRoomType.GROUP)
                .title(title.trim())
                .createdBy(creator)
                .build();
        chatRoomMapper.insertRoom(room);

        chatRoomMapper.insertMember(ChatRoomMember.builder().roomId(room.getId()).userId(creator).build());
        for (Long uid : targets) {
            chatRoomMapper.insertMember(ChatRoomMember.builder().roomId(room.getId()).userId(uid).build());
        }
        return room.getId();
    }

    /**
     * 그룹채팅 멤버 초대. inviter 는 방의 멤버여야 하고, 초대 대상은 inviter 의 친구여야 한다.
     * 이미 멤버인 사용자는 조용히 건너뛴다.
     */
    @Transactional
    public List<User> invite(Long roomId, Long inviter, List<Long> userIds) {
        ChatRoom room = chatRoomMapper.findRoomById(roomId)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "존재하지 않는 채팅방입니다."));
        if (room.getType() != ChatRoomType.GROUP) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "그룹채팅이 아닙니다.");
        }
        if (!chatRoomMapper.isMember(roomId, inviter)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "참여 중인 방이 아닙니다.");
        }

        Set<Long> existingMemberIds = new HashSet<>(chatRoomMapper.findMemberUserIds(roomId));
        List<User> added = new ArrayList<>();
        for (Long uid : new HashSet<>(userIds)) {
            if (uid.equals(inviter)) continue;
            if (existingMemberIds.contains(uid)) continue;
            if (!friendService.areFriends(inviter, uid)) {
                User u = safeFind(uid);
                String name = u != null ? u.getNickname() : ("id=" + uid);
                throw new ApiException(ErrorCode.BAD_REQUEST, name + " 님은 친구가 아닙니다.");
            }
            chatRoomMapper.insertMember(ChatRoomMember.builder().roomId(roomId).userId(uid).build());
            added.add(userService.getById(uid));
        }

        if (!added.isEmpty()) {
            String names = added.stream().map(User::getNickname).collect(Collectors.joining(", "));
            broadcastSystem(roomId, inviter, names + " 님이 초대되었습니다.");
        }
        return added;
    }

    /**
     * 그룹채팅 나가기. DIRECT 방은 ‘나가기’를 허용하지 않는다(카카오톡과 동일하게 단순화).
     * 마지막 멤버가 나가면 빈 방으로 남겨둔다(데이터 보존). 메시지 검색 등은 그대로 가능.
     */
    @Transactional
    public void leave(Long roomId, Long userId) {
        ChatRoom room = chatRoomMapper.findRoomById(roomId)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "존재하지 않는 채팅방입니다."));
        if (room.getType() != ChatRoomType.GROUP) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "1:1 채팅방은 나갈 수 없습니다.");
        }
        if (!chatRoomMapper.isMember(roomId, userId)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "참여 중인 방이 아닙니다.");
        }
        // 시스템 메시지 먼저 (나간 사람이 화면에 마지막으로 보고 갈 메시지)
        User u = userService.getById(userId);
        broadcastSystem(roomId, userId, u.getNickname() + " 님이 나갔습니다.");
        chatRoomMapper.removeMember(roomId, userId);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomSummary> listMyRooms(Long me) {
        return chatRoomMapper.listMyRoomSummaries(me);
    }

    @Transactional(readOnly = true)
    public ChatRoomDetail getDetail(Long me, Long roomId) {
        ChatRoom room = chatRoomMapper.findRoomById(roomId)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "존재하지 않는 채팅방입니다."));
        if (!chatRoomMapper.isMember(roomId, me)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "참여 중인 방이 아닙니다.");
        }
        var members = chatRoomMapper.findMembers(roomId).stream()
                .map(UserResponse::from)
                .toList();

        String displayTitle = room.getType() == ChatRoomType.GROUP
                ? (room.getTitle() != null ? room.getTitle() : "그룹채팅")
                : members.stream()
                    .filter(u -> !u.id().equals(me))
                    .map(UserResponse::nickname)
                    .findFirst().orElse("(상대방 없음)");

        return new ChatRoomDetail(room.getId(), room.getType(), displayTitle, members);
    }

    public void requireMember(Long roomId, Long userId) {
        if (!chatRoomMapper.isMember(roomId, userId)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "참여 중인 방이 아닙니다.");
        }
    }

    public List<Long> findMemberUserIds(Long roomId) {
        return chatRoomMapper.findMemberUserIds(roomId);
    }

    @Transactional
    public void markRead(Long roomId, Long userId, Long messageId) {
        chatRoomMapper.updateLastRead(roomId, userId, messageId);
    }

    /**
     * 시스템 메시지를 DB에 저장하고 /topic/rooms/{id} 와 멤버들의 개인 큐로 브로드캐스트한다.
     * sender_id 는 (NOT NULL 제약 때문에) 트리거한 사용자로 기록한다.
     */
    private void broadcastSystem(Long roomId, Long actorId, String content) {
        Message m = Message.builder()
                .roomId(roomId)
                .senderId(actorId)
                .type(MessageType.SYSTEM)
                .content(content)
                .build();
        messageMapper.insert(m);

        MessageResponse dto = new MessageResponse(
                m.getId(), m.getRoomId(), m.getSenderId(),
                null, null,                          // senderNickname / senderProfileImageUrl
                m.getType(), m.getContent(), m.getCreatedAt(),
                null, null, null,                    // attachment*
                null, null,                          // editedAt / deletedAt
                null, null, null, null               // replyTo*
        );
        broker.send("/topic/rooms/" + roomId, dto);

        for (Long uid : chatRoomMapper.findMemberUserIds(roomId)) {
            broker.sendToUser(
                    uid.toString(),
                    "/queue/notifications",
                    Map.of("kind", "NEW_MESSAGE", "roomId", roomId, "message", dto)
            );
        }
    }

    private User safeFind(Long uid) {
        try { return userService.getById(uid); } catch (Exception e) { return null; }
    }
}
