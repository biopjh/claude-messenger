package com.example.messenger.message.controller;

import com.example.messenger.auth.jwt.JwtAuthFilter.AuthPrincipal;
import com.example.messenger.common.exception.ApiException;
import com.example.messenger.common.exception.ErrorCode;
import com.example.messenger.common.response.ApiResponse;
import com.example.messenger.message.dto.EditMessageRequest;
import com.example.messenger.message.dto.MessageResponse;
import com.example.messenger.message.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    // ===== 방 단위 페이지네이션 =====

    @GetMapping("/api/chat-rooms/{roomId}/messages")
    public ApiResponse<List<MessageResponse>> page(@AuthenticationPrincipal AuthPrincipal me,
                                                   @PathVariable Long roomId,
                                                   @RequestParam(value = "cursorId", required = false) Long cursorId,
                                                   @RequestParam(value = "size", defaultValue = "30") int size) {
        requireMe(me);
        return ApiResponse.ok(messageService.getPage(roomId, me.userId(), cursorId, size));
    }

    /** 방 내부 본문 부분일치 검색. q 가 비어 있으면 빈 리스트. */
    @GetMapping("/api/chat-rooms/{roomId}/messages/search")
    public ApiResponse<List<MessageResponse>> search(@AuthenticationPrincipal AuthPrincipal me,
                                                     @PathVariable Long roomId,
                                                     @RequestParam("q") String query,
                                                     @RequestParam(value = "size", defaultValue = "30") int size) {
        requireMe(me);
        return ApiResponse.ok(messageService.searchInRoom(roomId, me.userId(), query, size));
    }

    // ===== 단일 메시지 수정 / 삭제 =====

    /** 본인 메시지 본문 수정. TEXT, 5분 이내, 비삭제 상태일 때만. */
    @PatchMapping("/api/messages/{messageId}")
    public ApiResponse<MessageResponse> edit(@AuthenticationPrincipal AuthPrincipal me,
                                             @PathVariable Long messageId,
                                             @Valid @RequestBody EditMessageRequest req) {
        requireMe(me);
        return ApiResponse.ok(messageService.editMessage(messageId, me.userId(), req.content()));
    }

    /** 본인 메시지 소프트 삭제. 행은 남고 deleted_at 만 채워짐. */
    @DeleteMapping("/api/messages/{messageId}")
    public ApiResponse<MessageResponse> remove(@AuthenticationPrincipal AuthPrincipal me,
                                               @PathVariable Long messageId) {
        requireMe(me);
        return ApiResponse.ok(messageService.deleteMessage(messageId, me.userId()));
    }

    private static void requireMe(AuthPrincipal me) {
        if (me == null) throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
}
