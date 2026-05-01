package com.example.messenger.friend.controller;

import com.example.messenger.auth.jwt.JwtAuthFilter.AuthPrincipal;
import com.example.messenger.common.exception.ApiException;
import com.example.messenger.common.exception.ErrorCode;
import com.example.messenger.common.response.ApiResponse;
import com.example.messenger.friend.dto.FriendListItem;
import com.example.messenger.friend.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    /** 친구 요청 보내기. (멱등) */
    @PostMapping("/request/{userId}")
    public ApiResponse<Map<String, Object>> request(@AuthenticationPrincipal AuthPrincipal me,
                                                    @PathVariable Long userId) {
        requireMe(me);
        Long id = friendService.request(me.userId(), userId);
        return ApiResponse.ok(Map.of("friendshipId", id));
    }

    /** 받은 요청 수락. {id}는 friendships.id. */
    @PostMapping("/{id}/accept")
    public ApiResponse<Void> accept(@AuthenticationPrincipal AuthPrincipal me,
                                    @PathVariable Long id) {
        requireMe(me);
        friendService.accept(me.userId(), id);
        return ApiResponse.ok();
    }

    /** 친구 삭제 또는 받은 요청 거절. {id}는 friendships.id. */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal AuthPrincipal me,
                                    @PathVariable Long id) {
        requireMe(me);
        friendService.remove(me.userId(), id);
        return ApiResponse.ok();
    }

    /** 내 친구 목록 (ACCEPTED). */
    @GetMapping
    public ApiResponse<List<FriendListItem>> list(@AuthenticationPrincipal AuthPrincipal me) {
        requireMe(me);
        return ApiResponse.ok(friendService.listAccepted(me.userId()));
    }

    /** 내가 받은 PENDING 요청. */
    @GetMapping("/requests")
    public ApiResponse<List<FriendListItem>> incoming(@AuthenticationPrincipal AuthPrincipal me) {
        requireMe(me);
        return ApiResponse.ok(friendService.listIncoming(me.userId()));
    }

    private static void requireMe(AuthPrincipal me) {
        if (me == null) throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
}
