package com.example.messenger.user.dto;

import com.example.messenger.user.domain.User;

/**
 * 외부로 노출되는 사용자 정보. 절대 passwordHash 를 포함하지 않는다.
 */
public record UserResponse(
        Long id,
        String email,
        String nickname,
        String profileImageUrl,
        String statusMessage
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(),
                u.getEmail(),
                u.getNickname(),
                u.getProfileImageUrl(),
                u.getStatusMessage()
        );
    }
}
