package com.example.messenger.user.controller;

import com.example.messenger.auth.jwt.JwtAuthFilter.AuthPrincipal;
import com.example.messenger.common.exception.ApiException;
import com.example.messenger.common.exception.ErrorCode;
import com.example.messenger.common.response.ApiResponse;
import com.example.messenger.user.dto.UpdateProfileRequest;
import com.example.messenger.user.dto.UserResponse;
import com.example.messenger.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 내 정보 조회. JwtAuthFilter가 SecurityContext에 심어둔 AuthPrincipal을 그대로 받는다. */
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthPrincipal me) {
        if (me == null) throw new ApiException(ErrorCode.UNAUTHENTICATED);
        return ApiResponse.ok(UserResponse.from(userService.getById(me.userId())));
    }

    /** 이메일/닉네임으로 사용자 검색 (자기 자신 제외, 최대 limit 명). */
    @GetMapping("/search")
    public ApiResponse<List<UserResponse>> search(
            @AuthenticationPrincipal AuthPrincipal me,
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        if (me == null) throw new ApiException(ErrorCode.UNAUTHENTICATED);
        List<UserResponse> users = userService.search(query, me.userId(), limit)
                .stream()
                .map(UserResponse::from)
                .toList();
        return ApiResponse.ok(users);
    }

    /** 내 프로필 수정 (닉네임 / 상태메시지 / 프로필 이미지 URL). 비어 있으면 NULL 로 저장. */
    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateMe(@AuthenticationPrincipal AuthPrincipal me,
                                              @Valid @RequestBody UpdateProfileRequest req) {
        if (me == null) throw new ApiException(ErrorCode.UNAUTHENTICATED);
        userService.updateProfile(
                me.userId(),
                req.nickname().trim(),
                blankToNull(req.statusMessage()),
                blankToNull(req.profileImageUrl())
        );
        return ApiResponse.ok(UserResponse.from(userService.getById(me.userId())));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
