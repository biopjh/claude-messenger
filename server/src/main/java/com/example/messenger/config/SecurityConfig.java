package com.example.messenger.config;

import com.example.messenger.auth.jwt.JwtAuthFilter;
import com.example.messenger.common.response.ApiResponse;
import com.example.messenger.security.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // 현재 단계: CSRF 비활성. 토큰 기반 인증이라 CSRF 영향 적음. 다음 세션에서
                // 쿠키-only 전환 시 CookieCsrfTokenRepository 로 전환 예정.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())

                // ─── 보안 헤더 ───────────────────────────────────────────────
                .headers(h -> h
                        .contentTypeOptions(c -> {})                              // X-Content-Type-Options: nosniff
                        .referrerPolicy(rp -> rp.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .frameOptions(fo -> fo.sameOrigin())                       // 동일출처 iframe 만 허용
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                // 'unsafe-inline' 은 학습용 인라인 스크립트(template) 호환을 위해 둠.
                                // 운영에서는 nonce 또는 hash 기반으로 강화 가능.
                                "default-src 'self'; " +
                                "img-src 'self' data: blob: https:; " +
                                "media-src 'self' blob:; " +
                                "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                                "style-src 'self' 'unsafe-inline'; " +
                                "connect-src 'self' ws: wss: https:; " +
                                "font-src 'self' data:; " +
                                "frame-ancestors 'self'; " +
                                "base-uri 'self'; " +
                                "form-action 'self'"))
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/login", "/signup", "/home", "/me", "/rooms/**",
                                "/css/**", "/js/**", "/images/**", "/favicon.ico",
                                "/api/auth/**",
                                "/ws-chat/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(401);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.setCharacterEncoding("UTF-8");
                            res.getWriter().write(objectMapper.writeValueAsString(
                                    ApiResponse.fail("AUTH_002", "로그인이 필요합니다.")));
                        })
                )
                // RateLimit 가 가장 먼저 — JWT 검증 비용이 들기 전에 차단
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter,   UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        var cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);   // 쿠키/credentials 동반 요청 허용
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
