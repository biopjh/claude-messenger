package com.example.messenger.chatroom.mapper;

import com.example.messenger.chatroom.domain.ChatRoom;
import com.example.messenger.chatroom.domain.ChatRoomMember;
import com.example.messenger.chatroom.dto.ChatRoomSummary;
import com.example.messenger.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ChatRoomMapper {

    int insertRoom(ChatRoom room);

    int insertMember(ChatRoomMember member);

    Optional<ChatRoom> findRoomById(@Param("id") Long id);

    /** 두 사용자의 1:1 방을 찾는다 (DIRECT 타입, 멤버가 정확히 두 사람). */
    Optional<Long> findDirectRoomId(@Param("userA") Long userA,
                                    @Param("userB") Long userB);

    /** 한 사용자가 멤버인지 확인. */
    boolean isMember(@Param("roomId") Long roomId,
                     @Param("userId") Long userId);

    /** 한 방의 멤버 user 정보들. */
    List<User> findMembers(@Param("roomId") Long roomId);

    /** 한 방의 멤버 userId들 (브로드캐스트 대상 계산용). */
    List<Long> findMemberUserIds(@Param("roomId") Long roomId);

    /** 내가 멤버인 방들의 요약 목록 (마지막 메시지/안읽음 수 포함). */
    List<ChatRoomSummary> listMyRoomSummaries(@Param("userId") Long userId);

    /** last_read_message_id 갱신 (읽음 처리). */
    int updateLastRead(@Param("roomId") Long roomId,
                       @Param("userId") Long userId,
                       @Param("messageId") Long messageId);

    /** 한 멤버 제거 (그룹채팅 나가기). */
    int removeMember(@Param("roomId") Long roomId,
                     @Param("userId") Long userId);
}
