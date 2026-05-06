package com.example.messenger.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 요청마다 한 번 실행. 다음 두 출처에서 토큰을 찾는다 (앞쪽이 우선).
 *   1. Authorization: Bearer &lt;token&gt;
 *   2. access_token 쿠키 (HttpOnly, /api/auth/login 이 발급)
 *
 * 어느 것도 없거나 토큰이 만료/위조되었으면 익명 상태로 흘려보내고
 * 보호된 엔드포인트는 SecurityConfig 의 authenticationEntryPoint 가 401을 반환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String ACCESS_COOKIE = "access_token";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractFromHeader(request);
        if (token == null) token = extractFromCookie(request);

        if (token != null) {
            try {
                Claims claims = jwtTokenProvider.parse(token);
                Long userId  = jwtTokenProvider.getUserId(claims);
                String email = claims.get("email", String.class);

                var authentication = new UsernamePasswordAuthenticationToken(
                        new AuthPrincipal(userId, email),
                        null,
                        List.of(() -> "ROLE_USER")
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException e) {
                log.debug("JWT parse failed: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private static String extractFromHeader(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private static String extractFromCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (ACCESS_COOKIE.equals(c.getName()) && StringUtils.hasText(c.getValue())) {
                return c.getValue();
            }
        }
        return null;
    }

    /** 컨트롤러에서 @AuthenticationPrincipal AuthPrincipal me 형태로 받아쓸 수 있게 단순한 record. */
    public record AuthPrincipal(Long userId, String email) {}
}
