package com.beautyboy.auth;

import com.beautyboy.auth.dto.LoginRequest;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.config.JwtProperties;
import com.beautyboy.member.MemberService;
import com.beautyboy.member.dto.MemberCredentials;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private final MemberService memberService;
    private final TokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    public AuthService(MemberService memberService,
                        TokenProvider tokenProvider,
                        RefreshTokenRepository refreshTokenRepository,
                        JwtProperties jwtProperties) {
        this.memberService = memberService;
        this.tokenProvider = tokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        MemberCredentials credentials = memberService.authenticate(request.email(), request.password());
        return issueTokens(credentials);
    }

    @Transactional
    public LoginResult refresh(String rawRefreshToken) {
        RefreshToken saved = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        // 재사용 방지를 위해 조회된 토큰은 유효성과 무관하게 즉시 폐기한다.
        refreshTokenRepository.delete(saved);

        if (saved.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        MemberCredentials credentials = memberService.getCredentials(saved.getMemberId());
        return issueTokens(credentials);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .ifPresent(refreshTokenRepository::delete);
    }

    private LoginResult issueTokens(MemberCredentials credentials) {
        String accessToken = tokenProvider.createAccessToken(credentials.memberId(), credentials.role());

        String rawRefreshToken = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(jwtProperties.getRefreshExpDays());
        refreshTokenRepository.save(new RefreshToken(credentials.memberId(), hash(rawRefreshToken), expiresAt));

        return new LoginResult(accessToken, rawRefreshToken);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
    }

    public record LoginResult(String accessToken, String refreshToken) {
    }
}
