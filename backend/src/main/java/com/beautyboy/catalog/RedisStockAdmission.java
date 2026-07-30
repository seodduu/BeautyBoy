package com.beautyboy.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis 카운터로 선착순을 판정하는 선점 필터(설계 §3). 토스 호출 <b>전</b>, DB 트랜잭션
 * 밖에서 돈다 — 폭주의 패자를 돈이 움직이기 전에 돌려보내는 것이 존재 이유다.
 *
 * <p>모든 Redis 실패는 통과로 강등된다. 필터가 죽어도 시스템은 1단계(차감을 승인 뒤로)와
 * 똑같이 동작할 뿐이고, 초과 판매는 여전히 DB 조건부 UPDATE가 막는다.
 */
@Component
@ConditionalOnProperty(name = "beautyboy.stock.admission", havingValue = "true")
public class RedisStockAdmission implements StockAdmission {

    private static final Logger log = LoggerFactory.getLogger(RedisStockAdmission.class);

    /** TTL 5분 — 드리프트의 유일한 교정 수단(설계 §3-2). 값 변경 시 설계 §3-2 근거도 갱신할 것. */
    static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "stock:adm:";

    // 설계 §3-3 Lua. -2=MISS, -1=REJECT, >=0=성공(남은 수량)
    private static final RedisScript<Long> ACQUIRE = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return -2 end
            local remain = tonumber(redis.call('GET', KEYS[1]))
            if remain < tonumber(ARGV[1]) then return -1 end
            return redis.call('DECRBY', KEYS[1], ARGV[1])
            """, Long.class);

    // 존재할 때만 반환 — 만료 키에 INCRBY하면 TTL 없는 유령 카운터가 생긴다(설계 §3-4)
    private static final RedisScript<Long> RELEASE = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            return redis.call('INCRBY', KEYS[1], ARGV[1])
            """, Long.class);

    private final StringRedisTemplate redis;
    private final GoodsOptionRepository goodsOptionRepository;

    public RedisStockAdmission(StringRedisTemplate redis,
                               GoodsOptionRepository goodsOptionRepository) {
        this.redis = redis;
        this.goodsOptionRepository = goodsOptionRepository;
    }

    @Override
    public boolean tryAcquire(List<Line> lines) {
        List<Line> acquired = new ArrayList<>();
        try {
            for (Line line : lines) {
                if (!acquireOne(line)) {
                    releaseAll(acquired);          // all-or-nothing — 부분 선점을 남기지 않는다
                    return false;
                }
                acquired.add(line);
            }
            return true;
        } catch (RuntimeException e) {
            // Redis 장애 = 필터 없이 통과(설계 §3-5). 이미 선점한 것은 반환을 시도만 한다.
            log.warn("선점 필터 장애 — 통과로 강등: {}", e.toString());
            releaseAll(acquired);
            return true;
        }
    }

    private boolean acquireOne(Line line) {
        String key = KEY_PREFIX + line.optionId();
        Long r = redis.execute(ACQUIRE, List.of(key), String.valueOf(line.quantity()));
        if (r != null && r == -2L) {               // MISS — DB 적재 후 1회 재시도(설계 §3-3)
            int stock = goodsOptionRepository.findStockById(line.optionId()).orElse(0);
            redis.opsForValue().setIfAbsent(key, String.valueOf(stock), TTL);
            r = redis.execute(ACQUIRE, List.of(key), String.valueOf(line.quantity()));
            if (r != null && r == -2L) {
                return true;                        // 재적재 직후 만료 — 통과(막지 못한 것은 DB가 막는다)
            }
        }
        return r == null || r >= 0;                // null(파이프라인 특수)도 통과 방향
    }

    @Override
    public void release(List<Line> lines) {
        releaseAll(lines);
    }

    private void releaseAll(List<Line> lines) {
        for (Line line : lines) {
            try {
                redis.execute(RELEASE, List.of(KEY_PREFIX + line.optionId()),
                        String.valueOf(line.quantity()));
            } catch (RuntimeException e) {
                log.warn("선점 반환 실패(안전 방향 — TTL이 교정): optionId={}", line.optionId(), e);
            }
        }
    }
}
