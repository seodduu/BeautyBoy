package com.beautyboy.auth;

import com.beautyboy.auth.dto.LoginRequest;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.member.MemberService;
import com.beautyboy.member.dto.SignupRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * 같은 리프레시 토큰으로 두 요청이 <b>동시에</b> 들어왔을 때의 사양.
 *
 * <p>왜 이 시나리오가 별도 클래스이고, H2/실 MySQL 두 벌로 도는가:
 * Task 4-16의 E2E가 실제 결함을 드러냈다 — {@code AuthService.refresh()}가 조회한 엔티티를
 * 곧바로 {@code delete()}하는 구조라, 두 트랜잭션이 같은 행을 잡으면 늦은 쪽이
 * {@code ObjectOptimisticLockingFailureException}으로 죽고 전역 핸들러가 그것을 <b>500</b>으로
 * 흘려보냈다. 500은 프론트가 "세션 없음"과 구분할 수 없어서, 승자가 방금 정상 발급한 세션까지
 * 지워지는 결과로 이어졌다. 동시성 동작은 엔진마다 다르므로(H2의 잠금 타임아웃 vs InnoDB의
 * 행 잠금 대기) 같은 단언을 <b>H2와 실 MySQL 양쪽에서</b> 돌린다
 * ({@link AuthRefreshConcurrencyTest}, {@link AuthRefreshConcurrencyMysqlIntegrationTest}).
 *
 * <p>레이스를 어떻게 <b>진짜로</b> 재현하는가: 타이밍 운에 맡기지 않는다.
 * {@link RefreshTokenRepository}를 spy로 감싸 {@code findByTokenHash}가 <b>실제 조회를 끝낸 직후</b>
 * {@link CyclicBarrier}에서 상대를 기다리게 한다. 그래서 두 스레드는 반드시
 * "둘 다 토큰을 조회한 뒤 아직 아무도 지우지 않은" 상태에서 출발한다 — 결함이 재현되는 정확한
 * 인터리빙이다(수정 전에는 이 테스트가 실패한다).
 */
abstract class AuthRefreshConcurrencyScenario {

    /** 조회 직후 두 스레드를 만나게 하려면 리포지토리를 감싸야 한다 — 프로덕션 코드에 훅을 심지 않기 위한 선택. */
    @MockitoSpyBean
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    AuthService authService;

    @Autowired
    MemberService memberService;

    private final AtomicInteger 조회_횟수 = new AtomicInteger();
    private CyclicBarrier 조회_직후_만남;
    private String 이메일;

    @BeforeEach
    void 회원가입하고_동시_조회_인터리빙을_강제한다() throws Exception {
        // 이 테스트는 @Transactional이 아니다(스레드마다 진짜 커밋이 필요하다) — 데이터가 남으므로
        // 매 테스트가 자기 회원을 쓴다. 실 MySQL 실행에서는 Flyway 시드 회원과도 겹치지 않아야 한다.
        이메일 = "race-" + UUID.randomUUID().toString().substring(0, 8) + "@b.com";
        memberService.signup(new SignupRequest(이메일, "pw123456", "레이스맨", null, null, null));

        조회_횟수.set(0);
        조회_직후_만남 = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Object 조회_결과 = invocation.callRealMethod();
            // 레이스에 참가하는 처음 두 번의 조회만 서로를 기다린다.
            // (레이스 이후의 순차 검증 호출까지 기다리면 영원히 멈춘다.)
            if (조회_횟수.incrementAndGet() <= 2) {
                조회_직후_만남.await(10, TimeUnit.SECONDS);
            }
            return 조회_결과;
        }).when(refreshTokenRepository).findByTokenHash(anyString());
    }

    @Test
    void 같은_토큰으로_동시에_리프레시하면_한쪽만_성공하고_다른_쪽은_500이_아닌_409를_받는다() throws Exception {
        String 발급된_리프레시_토큰 = 로그인해서_리프레시_토큰을_받는다();

        List<Object> 결과 = 동시에_두_번_리프레시한다(발급된_리프레시_토큰);

        List<AuthService.RefreshResult> 성공 = 성공만(결과);
        List<Throwable> 실패 = 실패만(결과);

        // (b) 정확히 한쪽만 새 토큰을 받는다.
        assertThat(성공).hasSize(1);
        assertThat(성공.get(0).accessToken()).isNotBlank();
        assertThat(성공.get(0).refreshToken()).isNotBlank();
        assertThat(성공.get(0).member().email()).isEqualTo(이메일);

        // (a) 패배자는 500(미분류 예외)이 아니라 의미 있는 도메인 응답을 받는다.
        assertThat(실패).hasSize(1);
        assertThat(실패.get(0))
                .as("패배자는 미분류 예외(→500)가 아니라 BusinessException이어야 한다. 실제: %s", 실패.get(0))
                .isInstanceOf(BusinessException.class);
        BusinessException 패배 = (BusinessException) 실패.get(0);
        assertThat(패배.getErrorCode()).isEqualTo(ErrorCode.AUTH_REFRESH_CONFLICT);
        // 401이면 client.ts(동결)의 인터셉터가 세션을 지워버린다 — 그래서 409여야 한다.
        assertThat(패배.getErrorCode().getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void 동시_리프레시가_있어도_재사용_방지는_그대로_성립한다() throws Exception {
        String 발급된_리프레시_토큰 = 로그인해서_리프레시_토큰을_받는다();

        List<Object> 결과 = 동시에_두_번_리프레시한다(발급된_리프레시_토큰);
        String 승자가_받은_새_토큰 = 성공만(결과).get(0).refreshToken();

        // (c-1) 레이스에 쓰인 원래 토큰은 소진됐다 — 이후 어떤 요청도 새 토큰을 받지 못한다.
        assertThatThrownBy(() -> authService.refresh(발급된_리프레시_토큰))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        // (c-2) 승자의 새 토큰도 딱 한 번만 쓰인다(회전).
        String 다음_토큰 = authService.refresh(승자가_받은_새_토큰).refreshToken();
        assertThat(다음_토큰).isNotEqualTo(승자가_받은_새_토큰);

        assertThatThrownBy(() -> authService.refresh(승자가_받은_새_토큰))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    private String 로그인해서_리프레시_토큰을_받는다() {
        return authService.login(new LoginRequest(이메일, "pw123456")).refreshToken();
    }

    /** 두 스레드가 같은 raw 토큰으로 refresh를 호출한다. 예외는 던지지 않고 결과로 수집한다. */
    private List<Object> 동시에_두_번_리프레시한다(String rawRefreshToken) throws Exception {
        Callable<Object> 한_번_시도 = () -> {
            try {
                return authService.refresh(rawRefreshToken);
            } catch (Throwable t) {
                return t;
            }
        };

        ExecutorService 풀 = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> 미래 = 풀.invokeAll(List.of(한_번_시도, 한_번_시도));
            List<Object> 결과 = new java.util.ArrayList<>();
            for (Future<Object> f : 미래) {
                결과.add(f.get(20, TimeUnit.SECONDS));
            }
            return 결과;
        } finally {
            풀.shutdownNow();
        }
    }

    private List<AuthService.RefreshResult> 성공만(List<Object> 결과) {
        return 결과.stream()
                .filter(AuthService.RefreshResult.class::isInstance)
                .map(AuthService.RefreshResult.class::cast)
                .toList();
    }

    private List<Throwable> 실패만(List<Object> 결과) {
        return 결과.stream()
                .filter(Throwable.class::isInstance)
                .map(Throwable.class::cast)
                .toList();
    }

    /** spy 스텁이 Optional을 그대로 돌려주는지 컴파일 타임에 못 박아두기 위한 참조(사용처 없음). */
    @SuppressWarnings("unused")
    private Optional<RefreshToken> 반환형_문서화() {
        return Optional.empty();
    }
}
