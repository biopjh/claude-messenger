package com.example.messenger.auth.controller;

import com.example.messenger.auth.dto.LoginRequest;
import com.example.messenger.auth.dto.RefreshTokenRequest;
import com.example.messenger.auth.dto.SignupRequest;
import com.example.messenger.auth.dto.TokenResponse;
import com.example.messenger.auth.service.AuthService;
import com.example.messenger.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ApiResponse<Map<String, Object>> signup(@Valid @RequestBody SignupRequest req) {
        Long id = authService.signup(req);
        return ApiResponse.ok(Map.of("id", id, "email", req.email()));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        return ApiResponse.ok(authService.refresh(req.refreshToken()));
    }
}
