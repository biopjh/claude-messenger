package com.example.messenger.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.List;

/**
 * STOMP CONNECT 시 Authorization 헤더의 JWT 를 검증하고
 * 세션에 userId 를 심어두며, Principal 도 설정한다.
 *
 * 이후 @MessageMapping 핸들러에서 SimpMessageHeaderAccessor.getSessionAttributes() 로 userId 를 꺼낼 수 있고,
 * convertAndSendToUser(userId.toString(), ...) 로 특정 사용자 큐에 메시지를 보낼 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    public static final String SESSION_USER_ID = "userId";
    public static final String SESSION_EMAIL   = "email";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractBearer(accessor);
            if (!StringUtils.hasText(token)) {
                throw new IllegalArgumentException("STOMP CONNECT 에 Authorization 헤더가 없습니다.");
            }
            try {
                Claims claims = jwtTokenProvider.parse(token);
                Long userId = jwtTokenProvider.getUserId(claims);
                String email = claims.get("email", String.class);

                accessor.getSessionAttributes().put(SESSION_USER_ID, userId);
                accessor.getSessionAttributes().put(SESSION_EMAIL, email);

                Principal principal = (Principal) () -> String.valueOf(userId); // user destination key
                accessor.setUser(principal);

                var auth = new UsernamePasswordAuthenticationToken(
                        new JwtAuthFilter.AuthPrincipal(userId, email),
                        null,
                        List.of(() -> "ROLE_USER"));
                accessor.setHeader("simpUser", principal);
                log.debug("STOMP CONNECT 인증 성공: userId={}", userId);
            } catch (JwtException e) {
                log.debug("STOMP CONNECT 토큰 검증 실패: {}", e.getMessage());
                throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
            }
        }
        return message;
    }

    private String extractBearer(StompHeaderAccessor accessor) {
        // 우선 Authorization 헤더, 없으면 native 헤더에서 access_token 도 허용
        String auth = accessor.getFirstNativeHeader("Authorization");
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) return auth.substring(7);
        String alt = accessor.getFirstNativeHeader("access_token");
        if (StringUtils.hasText(alt)) return alt;
        return null;
    }
}
