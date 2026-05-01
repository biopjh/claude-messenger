package com.example.messenger.friend.mapper;

import com.example.messenger.friend.domain.Friendship;
import com.example.messenger.friend.domain.FriendshipStatus;
import com.example.messenger.friend.dto.FriendListItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface FriendMapper {

    Optional<Friendship> findByUserAndFriend(@Param("userId") Long userId,
                                             @Param("friendId") Long friendId);

    Optional<Friendship> findById(@Param("id") Long id);

    int insert(Friendship f);

    int updateStatus(@Param("id") Long id,
                     @Param("status") FriendshipStatus status);

    int deleteById(@Param("id") Long id);

    /** 내가 ACCEPTED 상태로 가지고 있는 친구 목록 (사용자 정보 join). */
    List<FriendListItem> listAccepted(@Param("userId") Long userId);

    /** 내가 받은 PENDING 요청 목록 (보낸 사람 정보 join). user_id가 ‘보낸 사람’임에 주의. */
    List<FriendListItem> listIncomingPending(@Param("userId") Long userId);
}
