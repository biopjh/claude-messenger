package com.example.messenger.security;

import com.example.messenger.common.exception.ErrorCode;
import com.example.messenger.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 단순 IP 기반 rate limiter (bucket4j 토큰버킷).
 *
 *  - login   : 5  / 분 / IP   (브루트포스 방어)
 *  - signup  : 3  / 분 / IP   (스팸 가입 방어)
 *  - 검색    : 60 / 분 / IP   (검색 남용 방어)
 *  - 그 외는 통과
 *
 *  메모리 내 ConcurrentHashMap 으로 보관 — 단일 인스턴스 한도. 다중 인스턴스 환경에서는
 *  실제 한도가 N배가 되지만 학습용으로는 충분.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    private record Rule(String name, int permits, Duration period) {
        Bucket newBucket() {
            return Bucket.builder()
                    .addLimit(Bandwidth.classic(permits, Refill.intervally(permits, period)))
                    .build();
        }
    }

    private static final Rule LOGIN  = new Rule("login",  5,  Duration.ofMinutes(1));
    private static final Rule SIGNUP = new Rule("signup", 3,  Duration.ofMinutes(1));
    private static final Rule SEARCH = new Rule("search", 60, Duration.ofMinutes(1));

    /** key = "ip:rule" */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Rule rule = ruleFor(request);
        if (rule != null) {
            String ip = clientIp(request);
            Bucket bucket = buckets.computeIfAbsent(ip + ":" + rule.name(), k -> rule.newBucket());
            if (!bucket.tryConsume(1)) {
                log.warn("[ratelimit] {} {} blocked (rule={})", ip, request.getRequestURI(), rule.name());
                writeRateLimited(response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private Rule ruleFor(HttpServletRequest req) {
        String path = req.getRequestURI();
        String method = req.getMethod();
        // POST /api/auth/login
        if ("POST".equals(method) && "/api/auth/login".equals(path)) return LOGIN;
        // POST /api/auth/signup
        if ("POST".equals(method) && "/api/auth/signup".equals(path)) return SIGNUP;
        // GET /api/users/search
        if ("GET".equals(method) && "/api/users/search".equals(path)) return SEARCH;
        // GET /api/chat-rooms/{id}/messages/search
        if ("GET".equals(method) && path.startsWith("/api/chat-rooms/") && path.endsWith("/messages/search")) return SEARCH;
        return null;
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // 프록시 뒷단이면 첫 번째 IP 가 원본
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }

    private void writeRateLimited(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.RATE_LIMITED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", "60");
        ApiResponse<Void> body = ApiResponse.fail(
                ErrorCode.RATE_LIMITED.code(),
                ErrorCode.RATE_LIMITED.defaultMessage()
        );
        objectMapper.writeValue(response.getWriter(), body);
    }

    /** 외부에서 키 저장소 통계 노출이 필요할 때 사용. */
    public Function<String, Long> snapshot() {
        return key -> {
            Bucket b = buckets.get(key);
            return b == null ? null : b.getAvailableTokens();
        };
    }
}
