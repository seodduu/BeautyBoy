package com.beautyboy.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Redis 조회수 버퍼를 주기적으로 DB로 흘려보내는 스케줄러. {@code beautyboy.view-count.redis=true}일 때만 뜬다.
 *
 * <p>플러시 주기 {@code 60_000ms}(1분): 랭킹 집계가 일 단위라 1분 지연은 순위 정확도에 영향이 없고,
 * 1분이면 서버가 죽어도 잃는 조회수가 한 상품당 한 자릿수다. 더 짧으면 Redis를 쓴 의미가 줄고,
 * 더 길면 재기동 시 손실이 눈에 띈다.
 *
 * <p><b>순서가 핵심: HGETALL → 상품별 addViewCount → DEL.</b> DEL을 HGETALL 뒤에 두어야 한다 —
 * 먼저 지우면 읽기 전에 버퍼를 날려 그 사이 조회를 통째로 잃는다.
 *
 * <p>그래도 HGETALL~DEL 사이에 들어온 증가분은 DEL이 통째로 지우므로 잃는다(손실 창). 조회수는
 * 근사값이어도 되는 데이터라 감수한다 — 돈·재고였다면 HDEL/DECRBY로 읽은 만큼만 지우거나 이 설계를
 * 아예 쓰지 않는다.
 */
@Component
@ConditionalOnProperty(name = "beautyboy.view-count.redis", havingValue = "true")
public class ViewCountFlushScheduler {

    /** 조회수 버퍼 해시 키. 해시 하나로 모아 플러시가 HGETALL+DEL 한 쌍으로 끝난다(키를 상품마다 나누면 SCAN이 필요). */
    static final String KEY = "bb:viewcount";

    private static final Logger log = LoggerFactory.getLogger(ViewCountFlushScheduler.class);

    private final RedisTemplate<String, Long> redisTemplate;
    private final GoodsRepository goodsRepository;

    public ViewCountFlushScheduler(@Qualifier("viewCountRedisTemplate") RedisTemplate<String, Long> redisTemplate,
                                   GoodsRepository goodsRepository) {
        this.redisTemplate = redisTemplate;
        this.goodsRepository = goodsRepository;
    }

    /**
     * <b>{@code @Transactional}이 반드시 있어야 한다.</b> {@code addViewCount}는 {@code @Modifying} 벌크
     * UPDATE라 활성 트랜잭션이 없으면 "Executing an update/delete query"로 매 주기 실패한다
     * (mock 리포지토리를 쓰는 유닛테스트는 이걸 못 잡는다 — 실제로 Redis를 띄운 수동 확인에서 발견했다).
     *
     * <p>DB 갱신과 DEL을 같은 트랜잭션 안에 두는 덕에 얻는 성질: 중간에 DB가 실패하면 예외가 밖으로
     * 나가 <b>롤백되고 DEL도 실행되지 않으므로</b> 버퍼가 살아남아 다음 주기에 그대로 재시도된다.
     * 즉 실패가 조회수 손실이 아니라 지연이 된다. (DEL 성공 후 커밋이 실패하는 좁은 창은 남는다 —
     * 2PC가 아니므로 완전히 없앨 수 없고, 조회수는 근사값이어도 되는 데이터라 감수한다.)
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void flush() {
        Map<String, Long> buffer;
        try {
            HashOperations<String, String, Long> hashOps = redisTemplate.opsForHash();
            buffer = hashOps.entries(KEY);
        } catch (Exception e) {
            // Redis가 죽어 있어도 스케줄러를 멈추지 않는다 — 다음 주기에 다시 시도한다.
            log.warn("조회수 버퍼 읽기 실패 — 다음 주기에 재시도한다", e);
            return;
        }
        if (buffer.isEmpty()) {
            return; // 쌓인 게 없으면 DB도 DEL도 건드리지 않는다.
        }
        buffer.forEach((goodsNo, delta) ->
                goodsRepository.addViewCount(Long.valueOf(goodsNo), delta.intValue()));
        redisTemplate.delete(KEY); // 반드시 읽은 뒤에.
    }
}
