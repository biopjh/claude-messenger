package com.example.messenger.auth.service;

import com.example.messenger.auth.dto.LoginRequest;
import com.example.messenger.auth.dto.SignupRequest;
import com.example.messenger.auth.dto.TokenResponse;
import com.example.messenger.auth.jwt.JwtTokenProvider;
import com.example.messenger.common.exception.ApiException;
import com.example.messenger.common.exception.ErrorCode;
import com.example.messenger.user.domain.User;
import com.example.messenger.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.access-token-validity-seconds}")
    private long accessTokenValiditySec;

    @Transactional
    public Long signup(SignupRequest req) {
        if (userService.existsByEmail(req.email())) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_USED);
        }
        String hash = passwordEncoder.encode(req.password());
        return userService.create(req.email(), hash, req.nickname());
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        User user;
        try {
            user = userService.getByEmail(req.email());
        } catch (ApiException e) {
            // 사용자 없음을 그대로 노출하지 않고 동일한 401로 통일 (계정 존재 여부 leak 방지)
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        return issueTokens(user);
    }

    /** Refresh 토큰을 받아 새 Access(+Refresh)를 발급. 단순화를 위해 Refresh도 새로 발급한다. */
    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtTokenProvider.parse(refreshToken);
        } catch (JwtException e) {
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
        String type = claims.get("type", String.class);
        if (!"REFRESH".equals(type)) {
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
        Long userId = jwtTokenProvider.getUserId(claims);
        User user = userService.getById(userId);
        return issueTokens(user);
    }

    private TokenResponse issueTokens(User user) {
        String access = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail());
        String refresh = jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail());
        return TokenResponse.of(access, refresh, accessTokenValiditySec);
    }
}
