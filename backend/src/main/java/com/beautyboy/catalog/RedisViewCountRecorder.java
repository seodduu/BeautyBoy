package com.beautyboy.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis INCR 버퍼 조회수 기록 구현. {@code beautyboy.view-count.redis=true}일 때만 뜬다.
 *
 * <p>조회 1회마다 DB를 때리는 대신 Redis 해시 {@code bb:viewcount}의 field(goodsNo)를 HINCRBY로
 * 1 올린다. 실제 DB 반영은 {@link ViewCountFlushScheduler}가 1분마다 모아서 한다.
 *
 * <p><b>전체를 try-catch로 감싼다</b>: 조회수 기록이 실패해도 상세 페이지가 500이 되면 안 된다
 * (인터페이스 계약). Redis가 죽어 있으면 그 조회는 세지 못하고 로그만 남는다 — 조회수는 근사값이어도
 * 되는 데이터라 감수한다.
 */
@Component
@ConditionalOnProperty(name = "beautyboy.view-count.redis", havingValue = "true")
public class RedisViewCountRecorder implements ViewCountRecorder {

    private static final Logger log = LoggerFactory.getLogger(RedisViewCountRecorder.class);

    private final RedisTemplate<String, Long> redisTemplate;

    public RedisViewCountRecorder(@Qualifier("viewCountRedisTemplate") RedisTemplate<String, Long> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void record(Long goodsNo) {
        try {
            redisTemplate.opsForHash().increment(ViewCountFlushScheduler.KEY, goodsNo.toString(), 1);
        } catch (Exception e) {
            log.warn("조회수 Redis 기록 실패 goodsNo={} — 이 조회는 집계에서 누락된다", goodsNo, e);
        }
    }
}
