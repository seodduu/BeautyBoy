package com.beautyboy.auth;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenProviderTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=";

    private final JwtProperties jwtProperties = jwtProperties(SECRET, 30, 14);
    private final TokenProvider tokenProvider = new TokenProvider(jwtProperties);

    @Test
    void 발급한_토큰을_파싱하면_memberId와_role이_복원된다() {
        String token = tokenProvider.createAccessToken(1L, "USER");

        TokenProvider.TokenClaims claims = tokenProvider.parse(token);

        assertThat(claims.memberId()).isEqualTo(1L);
        assertThat(claims.role()).isEqualTo("USER");
    }

    @Test
    void 만료된_토큰을_파싱하면_UNAUTHORIZED_예외() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        Instant now = Instant.now();
        String expiredToken = Jwts.builder()
                .subject("1")
                .claim("role", "USER")
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> tokenProvider.parse(expiredToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void 위조된_토큰을_파싱하면_UNAUTHORIZED_예외() {
        String tampered = tokenProvider.createAccessToken(1L, "USER") + "tampered";

        assertThatThrownBy(() -> tokenProvider.parse(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    private JwtProperties jwtProperties(String secret, int accessExpMinutes, int refreshExpDays) {
        JwtProperties props = new JwtProperties();
        props.setSecret(secret);
        props.setAccessExpMinutes(accessExpMinutes);
        props.setRefreshExpDays(refreshExpDays);
        return props;
    }
}
