package com.beautyboy.member;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** 스프링 컨텍스트 없이 순수 단위 테스트. {@code Clock.fixed}를 갈아 끼운다. */
class BehaviorEventRateLimiterTest {

    private static final Long MEMBER_A = 1L;
    private static final Long MEMBER_B = 2L;

    @Test
    void 한도까지는_통과한다() {
        BehaviorEventRateLimiter limiter = new BehaviorEventRateLimiter(3, fixedClock(0));

        assertThat(limiter.tryAcquire(MEMBER_A)).isTrue();
        assertThat(limiter.tryAcquire(MEMBER_A)).isTrue();
        assertThat(limiter.tryAcquire(MEMBER_A)).isTrue();
    }

    @Test
    void 한도를_넘으면_막힌다() {
        BehaviorEventRateLimiter limiter = new BehaviorEventRateLimiter(3, fixedClock(0));

        limiter.tryAcquire(MEMBER_A);
        limiter.tryAcquire(MEMBER_A);
        limiter.tryAcquire(MEMBER_A);

        assertThat(limiter.tryAcquire(MEMBER_A)).isFalse();
    }

    @Test
    void 창이_바뀌면_다시_열린다() {
        AtomicReference<Clock> clock = new AtomicReference<>(fixedClock(0));
        BehaviorEventRateLimiter limiter = new BehaviorEventRateLimiter(3, delegatingClock(clock));

        limiter.tryAcquire(MEMBER_A);
        limiter.tryAcquire(MEMBER_A);
        limiter.tryAcquire(MEMBER_A);
        assertThat(limiter.tryAcquire(MEMBER_A)).isFalse();

        clock.set(fixedClock(60_000L));

        assertThat(limiter.tryAcquire(MEMBER_A)).isTrue();
    }

    @Test
    void 회원이_다르면_따로_센다() {
        BehaviorEventRateLimiter limiter = new BehaviorEventRateLimiter(3, fixedClock(0));

        limiter.tryAcquire(MEMBER_A);
        limiter.tryAcquire(MEMBER_A);
        limiter.tryAcquire(MEMBER_A);
        assertThat(limiter.tryAcquire(MEMBER_A)).isFalse();

        assertThat(limiter.tryAcquire(MEMBER_B)).isTrue();
    }

    private static Clock fixedClock(long epochMilli) {
        return Clock.fixed(Instant.ofEpochMilli(epochMilli), ZoneOffset.UTC);
    }

    private static Clock delegatingClock(AtomicReference<Clock> ref) {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ref.get().getZone();
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return ref.get().withZone(zone);
            }

            @Override
            public Instant instant() {
                return ref.get().instant();
            }
        };
    }
}
