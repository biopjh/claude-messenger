package com.example.messenger.message.controller;

import com.example.messenger.auth.jwt.JwtAuthFilter.AuthPrincipal;
import com.example.messenger.common.exception.ApiException;
import com.example.messenger.common.exception.ErrorCode;
import com.example.messenger.common.response.ApiResponse;
import com.example.messenger.message.dto.AddReactionRequest;
import com.example.messenger.message.dto.MessageResponse;
import com.example.messenger.message.service.ReactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages/{messageId}/reactions")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    /**
     * 한 메시지에 이모지 반응 토글. 같은 이모지를 또 누르면 제거, 새 이모지면 추가.
     * 결과는 갱신된 MessageResponse — 같은 방 모든 멤버에게도 STOMP 로 broadcast 됨.
     */
    @PostMapping
    public ApiResponse<MessageResponse> toggle(@AuthenticationPrincipal AuthPrincipal me,
                                               @PathVariable Long messageId,
                                               @Valid @RequestBody AddReactionRequest req) {
        if (me == null) throw new ApiException(ErrorCode.UNAUTHENTICATED);
        return ApiResponse.ok(reactionService.toggle(messageId, me.userId(), req.emoji()));
    }
}
