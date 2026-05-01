package com.example.messenger.chatroom.dto;

import com.example.messenger.chatroom.domain.ChatRoomType;
import com.example.messenger.user.dto.UserResponse;

import java.util.List;

/**
 * 한 채팅방의 상세 정보 (참여자 포함).
 */
public record ChatRoomDetail(
        Long roomId,
        ChatRoomType roomType,
        String title,
        List<UserResponse> members
) {}
