package com.example.messenger.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
 * 모든 요청 진입 직후 한 번 실행되어
 * Authorization: Bearer <token> 이 있으면 SecurityContext에 인증 정보를 채운다.
 * 토큰이 없거나 유효하지 않으면 그냥 익명 상태로 흘려보내고,
 * 보호된 엔드포인트는 SecurityConfig 의 authorizeHttpRequests 가 401을 만든다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtTokenProvider.parse(token);
                Long userId = jwtTokenProvider.getUserId(claims);
                String email = claims.get("email", String.class);

                // 권한은 단순화: ROLE_USER 만 부여
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

    /** 컨트롤러에서 @AuthenticationPrincipal AuthPrincipal me 형태로 받아쓸 수 있게 단순한 record. */
    public record AuthPrincipal(Long userId, String email) {}
}
