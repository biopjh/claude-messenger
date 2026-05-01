package com.example.messenger.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 발급/검증 컴포넌트.
 * - subject  : userId (Long)
 * - claims   : email, type ("ACCESS" | "REFRESH")
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenValiditySec;
    private final long refreshTokenValiditySec;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity-seconds}") long accessTokenValiditySec,
            @Value("${jwt.refresh-token-validity-seconds}") long refreshTokenValiditySec
    ) {
        // HS256은 256bit (32바이트) 이상의 키가 필요. 일반 문자열도 그대로 사용.
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // 짧으면 base64로 한번 더 늘려본다 (개발 편의)
            keyBytes = Decoders.BASE64.decode(java.util.Base64.getEncoder().encodeToString(keyBytes));
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenValiditySec = accessTokenValiditySec;
        this.refreshTokenValiditySec = refreshTokenValiditySec;
    }

    public String createAccessToken(long userId, String email) {
        return build(userId, email, "ACCESS", accessTokenValiditySec);
    }

    public String createRefreshToken(long userId, String email) {
        return build(userId, email, "REFRESH", refreshTokenValiditySec);
    }

    private String build(long userId, String email, String type, long validitySec) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("type", type)
                .issuedAt(new Date(now))
                .expiration(new Date(now + validitySec * 1000))
                .signWith(key)
                .compact();
    }

    /** 파싱하면서 서명/만료 검증까지 함께 수행. 실패하면 JwtException 발생. */
    public Claims parse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }
}
