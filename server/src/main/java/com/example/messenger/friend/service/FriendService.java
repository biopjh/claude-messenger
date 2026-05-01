package com.example.messenger.friend.service;

import com.example.messenger.common.exception.ApiException;
import com.example.messenger.common.exception.ErrorCode;
import com.example.messenger.friend.domain.Friendship;
import com.example.messenger.friend.domain.FriendshipStatus;
import com.example.messenger.friend.dto.FriendListItem;
import com.example.messenger.friend.mapper.FriendMapper;
import com.example.messenger.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendMapper friendMapper;
    private final UserService userService;

    /** 친구 요청. 이미 관계가 있으면 상태별로 처리한다. */
    @Transactional
    public Long request(Long me, Long targetUserId) {
        if (me.equals(targetUserId)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "본인에게는 친구 요청할 수 없습니다.");
        }
        // 상대 존재 확인 (없으면 USER_NOT_FOUND)
        userService.getById(targetUserId);

        // 이미 내가 → 상대로 가지고 있는 행이 있나
        var existing = friendMapper.findByUserAndFriend(me, targetUserId);
        if (existing.isPresent()) {
            return switch (existing.get().getStatus()) {
                case PENDING   -> existing.get().getId();          // 멱등
                case ACCEPTED  -> throw new ApiException(ErrorCode.BAD_REQUEST, "이미 친구입니다.");
                case BLOCKED   -> throw new ApiException(ErrorCode.BAD_REQUEST, "차단된 사용자입니다.");
            };
        }

        // 상대가 → 나에게 PENDING 으로 이미 보낸 상태라면 곧바로 ACCEPTED 로 끌어올린다.
        var reverse = friendMapper.findByUserAndFriend(targetUserId, me);
        if (reverse.isPresent() && reverse.get().getStatus() == FriendshipStatus.PENDING) {
            // 이쪽도 ACCEPTED 로 만들고
            Friendship mine = Friendship.builder()
                    .userId(me).friendId(targetUserId).status(FriendshipStatus.ACCEPTED)
                    .build();
            friendMapper.insert(mine);
            // 반대쪽도 ACCEPTED
            friendMapper.updateStatus(reverse.get().getId(), FriendshipStatus.ACCEPTED);
            return mine.getId();
        }

        Friendship f = Friendship.builder()
                .userId(me).friendId(targetUserId)
                .status(FriendshipStatus.PENDING)
                .build();
        friendMapper.insert(f);
        return f.getId();
    }

    /** 받은 요청을 수락. friendshipId 는 ‘상대→나’ 방향 행의 id. */
    @Transactional
    public void accept(Long me, Long friendshipId) {
        Friendship incoming = friendMapper.findById(friendshipId)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "존재하지 않는 요청입니다."));
        if (!incoming.getFriendId().equals(me)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "내가 받은 요청이 아닙니다.");
        }
        if (incoming.getStatus() != FriendshipStatus.PENDING) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "이미 처리된 요청입니다.");
        }
        // 상대→나 행을 ACCEPTED 로
        friendMapper.updateStatus(incoming.getId(), FriendshipStatus.ACCEPTED);
        // 나→상대 행도 만들어서 양방향 ACCEPTED 가 되게 한다 (없으면 새로 insert)
        var mineSide = friendMapper.findByUserAndFriend(me, incoming.getUserId());
        if (mineSide.isEmpty()) {
            friendMapper.insert(Friendship.builder()
                    .userId(me).friendId(incoming.getUserId())
                    .status(FriendshipStatus.ACCEPTED)
                    .build());
        } else {
            friendMapper.updateStatus(mineSide.get().getId(), FriendshipStatus.ACCEPTED);
        }
    }

    /** 친구 삭제 또는 받은 요청 거절. 내 행을 지운다. (반대편 행은 정책상 그대로 두는 단순화) */
    @Transactional
    public void remove(Long me, Long friendshipId) {
        Friendship f = friendMapper.findById(friendshipId)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "존재하지 않는 친구 관계입니다."));
        if (!f.getUserId().equals(me) && !f.getFriendId().equals(me)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "내 친구 관계가 아닙니다.");
        }
        friendMapper.deleteById(friendshipId);
        // 양방향 정리: 반대 방향 행이 있으면 함께 삭제
        Long other = f.getUserId().equals(me) ? f.getFriendId() : f.getUserId();
        friendMapper.findByUserAndFriend(other, me)
                .ifPresent(rev -> friendMapper.deleteById(rev.getId()));
    }

    @Transactional(readOnly = true)
    public List<FriendListItem> listAccepted(Long me) {
        return friendMapper.listAccepted(me);
    }

    @Transactional(readOnly = true)
    public List<FriendListItem> listIncoming(Long me) {
        return friendMapper.listIncomingPending(me);
    }

    /** 두 사용자가 친구인지(ACCEPTED) — 채팅 시작 가능 여부 검사용. */
    @Transactional(readOnly = true)
    public boolean areFriends(Long a, Long b) {
        return friendMapper.findByUserAndFriend(a, b)
                .map(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                .orElse(false);
    }
}
