package com.example.messenger.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 리액션 토글 요청 페이로드.
 * 같은 이모지를 다시 보내면 제거되고, 새 이모지면 추가된다.
 */
public record AddReactionRequest(
        @NotBlank @Size(max = 16) String emoji
) {}
