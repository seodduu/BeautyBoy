package com.beautyboy.auth;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class TokenProvider {

    private final SecretKey key;
    private final long accessExpMinutes;

    public TokenProvider(JwtProperties jwtProperties) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
        this.accessExpMinutes = jwtProperties.getAccessExpMinutes();
    }

    public String createAccessToken(Long memberId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessExpMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public TokenClaims parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long memberId = Long.valueOf(claims.getSubject());
            String role = claims.get("role", String.class);
            return new TokenClaims(memberId, role);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    public record TokenClaims(Long memberId, String role) {
    }
}
