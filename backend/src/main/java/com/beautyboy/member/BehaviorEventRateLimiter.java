package com.beautyboy.member;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 회원당 분당 요청 수 제한(고정창).
 *
 * <p>왜 인메모리인가: 이 앱은 단일 인스턴스이고, 이 제한의 목적은 분산 방어가 아니라 클라이언트
 * 버그가 초당 수백 건을 밀어 넣는 것을 막는 것이다. Redis는 {@code beautyboy.cache.redis=true}일
 * 때만 뜨는 선택 사항이라 거기 기대면 Redis 없이 띄운 환경에서 제한이 통째로 사라진다.
 *
 * <p>고정창을 쓴다. 창 경계에서 최대 2배까지 통과할 수 있지만(59초에 120건, 61초에 120건),
 * 슬라이딩 윈도는 회원마다 타임스탬프 목록을 들고 있어야 해서 메모리와 잠금이 늘어난다.
 * 120이라는 값 자체가 여유 있게 잡은 상한이라 2배를 견딜 수 있다.
 *
 * <p>맵은 창이 바뀔 때 통째로 갈아 끼운다. 회원별로 지우면 삭제와 증가가 경쟁해 방금 만든
 * 카운터가 지워질 수 있다.
 *
 * <p>이 저장소에는 아직 {@code Clock} 빈이 없다(생성 시점에 {@code grep -rn "Clock"
 * backend/src/main/java} 결과 0건). 그래서 이 컴포넌트가 새 설정 클래스를 만들어 전역
 * {@code Clock} 빈을 두는 대신, 스프링이 쓰는 생성자는 {@link Clock#systemDefaultZone()}을
 * 기본값으로 받는다. 테스트는 패키지 전용 생성자로 {@code Clock.fixed(...)}를 직접 넣는다.
 */
@Component
public class BehaviorEventRateLimiter {

    private final int limitPerMinute;
    private final Clock clock;

    /** 현재 창의 분 단위 시각. 창이 바뀌면 counters와 함께 교체된다. */
    private volatile long windowMinute;
    private volatile Map<Long, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Autowired
    public BehaviorEventRateLimiter(
            @Value("${beautyboy.behavior-event.limit-per-minute:120}") int limitPerMinute) {
        this(limitPerMinute, Clock.systemDefaultZone());
    }

    /** 테스트가 {@code Clock.fixed(...)}를 갈아 끼우기 위한 생성자. */
    BehaviorEventRateLimiter(int limitPerMinute, Clock clock) {
        this.limitPerMinute = limitPerMinute;
        this.clock = clock;
        this.windowMinute = clock.millis() / 60_000L;
    }

    /** 이번 요청을 허용하면 true. 호출 자체가 1건을 소비한다. */
    public boolean tryAcquire(Long memberId) {
        long now = clock.millis() / 60_000L;
        if (now != windowMinute) {
            synchronized (this) {
                if (now != windowMinute) {
                    counters = new ConcurrentHashMap<>();
                    windowMinute = now;
                }
            }
        }
        Map<Long, AtomicInteger> current = counters;
        int used = current.computeIfAbsent(memberId, key -> new AtomicInteger()).incrementAndGet();
        return used <= limitPerMinute;
    }
}
