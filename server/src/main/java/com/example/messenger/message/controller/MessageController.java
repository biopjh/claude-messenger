package com.example.messenger.message.controller;

import com.example.messenger.auth.jwt.JwtAuthFilter.AuthPrincipal;
import com.example.messenger.common.exception.ApiException;
import com.example.messenger.common.exception.ErrorCode;
import com.example.messenger.common.response.ApiResponse;
import com.example.messenger.message.dto.MessageResponse;
import com.example.messenger.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat-rooms/{roomId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /**
     * 한 방의 과거 메시지를 cursor 페이지네이션으로 조회.
     * 첫 호출: cursorId 생략 → 가장 최신 size 개.
     * 위로 더 보기: 화면에서 가장 오래된 id 를 cursorId 로 보냄.
     */
    @GetMapping
    public ApiResponse<List<MessageResponse>> page(@AuthenticationPrincipal AuthPrincipal me,
                                                   @PathVariable Long roomId,
                                                   @RequestParam(value = "cursorId", required = false) Long cursorId,
                                                   @RequestParam(value = "size", defaultValue = "30") int size) {
        if (me == null) throw new ApiException(ErrorCode.UNAUTHENTICATED);
        return ApiResponse.ok(messageService.getPage(roomId, me.userId(), cursorId, size));
    }
}
