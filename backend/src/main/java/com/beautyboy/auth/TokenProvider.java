package com.beautyboy.auth;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class TokenProvider {

    private final SecretKey key;
    private final long accessExpMinutes;

    /**
     * jwt.secret(환경변수 JWT_SECRET)이 비어있거나 Base64가 아니면 그냥 NPE/DecodingException으로
     * 죽어서 원인을 알기 어렵다. 여기서 명확한 설정 오류 메시지로 기동을 실패시킨다.
     */
    public TokenProvider(JwtProperties jwtProperties) {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new BeanCreationException(
                    "jwt.secret(환경변수 JWT_SECRET)이 설정되지 않았습니다. "
                            + "Base64로 인코딩된 값을 넣어야 합니다. 예: export JWT_SECRET=\"$(openssl rand -base64 48)\"");
        }
        try {
            this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } catch (DecodingException e) {
            throw new BeanCreationException(
                    "jwt.secret(환경변수 JWT_SECRET)이 Base64로 인코딩된 값이 아닙니다. "
                            + "예: export JWT_SECRET=\"$(openssl rand -base64 48)\"", e);
        }
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
