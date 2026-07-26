package com.beautyboy.auth;

import com.beautyboy.auth.dto.LoginRequest;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.config.JwtProperties;
import com.beautyboy.member.MemberService;
import com.beautyboy.member.dto.MemberCredentials;
import com.beautyboy.member.dto.MemberMeResponse;
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

    /**
     * 새로고침 부트스트랩(App.tsx)이 accessToken과 함께 회원 정보까지 한 번에 받아야
     * RequireAdmin 같은 role 기반 가드가 새로고침 직후에도 즉시 판정할 수 있다 —
     * accessToken만 내리면 프론트 authStore.member가 새로고침 후 계속 null로 남는다
     * (Task 4-14a에서 발견된 admin 패널 새로고침 시 튕김 버그). MemberMeResponse는
     * GET /members/me(MemberService.getMe)와 같은 조립을 그대로 재사용한다.
     */
    @Transactional
    public RefreshResult refresh(String rawRefreshToken) {
        RefreshToken saved = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        // 만료 검사를 소유권 주장보다 먼저 한다.
        // 근거: 만료 토큰은 동시 호출이어도 양쪽 다 401이어야 한다(계약 (iv)). 삭제를 먼저 하면
        // 늦은 쪽이 409를 받아 "만료됐다"가 "경쟁에서 졌다"로 둔갑한다.
        // 만료 토큰을 지우지 않고 두는 것은 이전과 같은 동작이다 — 이전 코드도 delete 직후 예외를
        // 던져 트랜잭션이 롤백되며 그 delete가 되돌려졌다. 만료 토큰은 항상 여기서 걸리므로
        // 남아 있어도 재사용될 수 없다(정리는 별도 관심사).
        if (saved.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 재사용 방지(회전)와 동시성 승패 판정을 한 문장에 합친다.
        // 지운 행이 1이면 이 요청이 토큰의 소유권을 얻었고, 0이면 같은 토큰을 쓰는 다른 요청이
        // 한 발 먼저 가져갔다는 뜻이다. 후자는 인증 실패가 아니므로 401이 아니라 409로 내린다
        // (401로 내리면 client.ts 인터셉터가 승자의 세션까지 지운다 — ErrorCode 주석 참고).
        //
        // 왜 낙관적 락 예외를 잡아 번역하지 않는가: 어느 예외가 오는지가 엔진에 따라 달라진다
        // (H2의 잠금 타임아웃 vs InnoDB의 행 잠금 대기). 영향 행 수는 어느 엔진에서도 같은 뜻이라
        // 실 MySQL에서 조용히 깨질 여지가 없다.
        if (refreshTokenRepository.deleteRowById(saved.getId()) == 0) {
            throw new BusinessException(ErrorCode.AUTH_REFRESH_CONFLICT);
        }

        MemberCredentials credentials = memberService.getCredentials(saved.getMemberId());
        LoginResult tokens = issueTokens(credentials);
        MemberMeResponse member = memberService.getMe(credentials.memberId());
        return new RefreshResult(tokens.accessToken(), tokens.refreshToken(), member);
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

    /** refresh() 전용 반환 타입 — LoginResult에 member를 더한다. */
    public record RefreshResult(String accessToken, String refreshToken, MemberMeResponse member) {
    }
}
