package com.beautyboy.member;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 90일 보관 기간을 넘긴 행동 로그를 매일 지우는 배치. {@code ViewCountFlushScheduler}와 같은
 * 관례로 주기를 프로퍼티로 뺀다. {@code @EnableScheduling}이 전역이라 테스트 컨텍스트에서도
 * 실제로 틱하기 때문이다. 테스트 프로필은 {@code application-test.yml}에서 이 값을 절대 안 도는
 * 크론({@code 0 0 4 1 1 *})으로 덮는다.
 *
 * <p>한 번에 {@code batchSize}행씩 지운다(기본 1000). 90일치를 한 트랜잭션에 지우면 락이
 * 길어지므로 {@link BehaviorEventRepository#deleteOlderThan}에 LIMIT을 걸어 끊는다. 반복
 * 하나가 독립 트랜잭션이 되도록, {@code @Transactional}은 {@code deleteBatch}가 아니라
 * {@link BehaviorEventRepository#deleteOlderThan} 쪽에 둔다. {@code deleteBatch}는 같은
 * 인스턴스 안에서 {@code purge()}가 직접 부르는 자기 호출이라 이 클래스 자신에 어노테이션을
 * 둬도 프록시 어드바이스를 타지 않는다. 리포지토리 인터페이스 메서드는 자기 호출이 아니라
 * 스프링이 만든 프록시를 거쳐 호출되므로 거기에 붙여야 실제로 트랜잭션 경계가 잡힌다.
 * 한 번 실행에서 최대 {@link #MAX_ITERATIONS}회까지만 돌고, 그래도 남으면 다음 실행에
 * 넘긴다(무한 루프 방지). 예외는 삼키고 로그만 남긴다. 스케줄러가 죽으면 다음 날도 안 돈다.
 */
@Component
public class BehaviorEventRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(BehaviorEventRetentionJob.class);

    private static final int MAX_ITERATIONS = 100;

    private final BehaviorEventRepository behaviorEventRepository;
    private final int retentionDays;
    private final int batchSize;

    public BehaviorEventRetentionJob(
            BehaviorEventRepository behaviorEventRepository,
            @Value("${beautyboy.behavior-event.retention-days:90}") int retentionDays,
            @Value("${beautyboy.behavior-event.retention-batch-size:1000}") int batchSize) {
        this.behaviorEventRepository = behaviorEventRepository;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${beautyboy.behavior-event.retention-cron:0 0 4 * * *}")
    public void purge() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
            int iterations = 0;
            int deleted;
            do {
                deleted = deleteBatch(threshold);
                iterations++;
            } while (deleted > 0 && iterations < MAX_ITERATIONS);
        } catch (Exception e) {
            log.warn("행동 로그 보관 삭제 배치 실패. 다음 실행에 재시도한다", e);
        }
    }

    int deleteBatch(LocalDateTime threshold) {
        return behaviorEventRepository.deleteOlderThan(threshold, batchSize);
    }
}
