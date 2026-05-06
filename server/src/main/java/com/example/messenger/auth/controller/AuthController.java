package com.example.messenger.auth.controller;

import com.example.messenger.auth.dto.LoginRequest;
import com.example.messenger.auth.dto.RefreshTokenRequest;
import com.example.messenger.auth.dto.SignupRequest;
import com.example.messenger.auth.dto.TokenResponse;
import com.example.messenger.auth.service.AuthService;
import com.example.messenger.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    public static final String ACCESS_COOKIE  = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;

    @PostMapping("/signup")
    public ApiResponse<Map<String, Object>> signup(@Valid @RequestBody SignupRequest req) {
        Long id = authService.signup(req);
        return ApiResponse.ok(Map.of("id", id, "email", req.email()));
    }

    /**
     * 로그인. 응답 본문에 토큰을 담아주면서 동시에 HttpOnly·SameSite=Lax 쿠키도 설정한다.
     * 클라이언트는 두 가지 중 편한 방식을 선택 가능 — 양쪽 모두 JwtAuthFilter 가 인식.
     * 운영 HTTPS 환경에서는 Secure 가 활성화되도록 X-Forwarded-Proto 인식 필요.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest httpReq) {
        TokenResponse tokens = authService.login(req);
        boolean secure = isHttps(httpReq);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE,
                buildCookie(ACCESS_COOKIE,  tokens.accessToken(),
                            Duration.ofSeconds(tokens.expiresIn()), secure).toString());
        headers.add(HttpHeaders.SET_COOKIE,
                buildCookie(REFRESH_COOKIE, tokens.refreshToken(),
                            Duration.ofDays(14), secure).toString());

        return ResponseEntity.ok().headers(headers).body(ApiResponse.ok(tokens));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        return ApiResponse.ok(authService.refresh(req.refreshToken()));
    }

    /** 명시적 로그아웃 — access/refresh 쿠키를 즉시 무효화한다. (헤더 토큰은 클라가 알아서 폐기) */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpReq) {
        boolean secure = isHttps(httpReq);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, expireCookie(ACCESS_COOKIE,  secure).toString());
        headers.add(HttpHeaders.SET_COOKIE, expireCookie(REFRESH_COOKIE, secure).toString());
        return ResponseEntity.ok().headers(headers).body(ApiResponse.ok());
    }

    // ─────────── 내부 유틸 ───────────

    private static ResponseCookie buildCookie(String name, String value, Duration maxAge, boolean secure) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private static ResponseCookie expireCookie(String name, boolean secure) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    /** 프록시(Fly.io / Render) 뒷단에서도 X-Forwarded-Proto 를 살펴 Secure 플래그를 정확히 박는다. */
    private static boolean isHttps(HttpServletRequest req) {
        if (req.isSecure()) return true;
        String proto = req.getHeader("X-Forwarded-Proto");
        return proto != null && proto.equalsIgnoreCase("https");
    }
}
