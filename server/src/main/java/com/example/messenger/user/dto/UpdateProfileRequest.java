package com.example.messenger.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 내 프로필 부분 수정 요청.
 *  - nickname 은 항상 보낸다(NOT NULL).
 *  - statusMessage / profileImageUrl 은 빈 문자열이면 ""로 저장(=비우기).
 *    null 로 보내면 서버는 빈 문자열로 정규화.
 */
public record UpdateProfileRequest(
        @NotBlank(message = "닉네임을 입력하세요.")
        @Size(min = 2, max = 20, message = "닉네임은 2~20자입니다.")
        @Pattern(regexp = "^[\\p{L}0-9_가-힣]+$", message = "닉네임은 한글/영문/숫자/언더스코어만 가능합니다.")
        String nickname,

        @Size(max = 200, message = "상태메시지는 200자 이내입니다.")
        String statusMessage,

        @Size(max = 500, message = "프로필 이미지 URL이 너무 깁니다.")
        String profileImageUrl
) {}
