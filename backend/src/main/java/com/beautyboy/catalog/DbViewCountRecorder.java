package com.beautyboy.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기본(폴백) 조회수 기록 구현: 상세 조회마다 goods.view_count를 DB에서 즉시 1 증가시킨다.
 *
 * <p>Redis 토글({@code beautyboy.view-count.redis})이 꺼져 있을 때 쓰는 경로다. 컨테이너 없이
 * 백엔드만 띄워도 조회수가 동작해야 하므로 이 구현이 기본이다. 등록은
 * {@link ViewCountRecorderAutoConfiguration}에서 {@code @ConditionalOnMissingBean}으로 한다 —
 * Redis 구현이 있으면 자동으로 물러난다. 새 증가 쿼리를 만들지 않고
 * {@link GoodsRepository#addViewCount(Long, int)} 하나만 쓴다.
 *
 * <p><b>왜 새 트랜잭션(REQUIRES_NEW)인가:</b> 호출자인 {@code GoodsService.detail}은
 * {@code @Transactional(readOnly = true)}다. 거기에 그냥 합류하면 MySQL 드라이버가
 * {@code Connection.setReadOnly(true)}를 실제로 강제해 벌크 UPDATE가
 * <i>"Connection is read-only. Queries leading to data modification are not allowed"</i>로 거부되고
 * 상세가 500이 된다. H2는 이 제약을 강제하지 않아 유닛테스트가 이걸 못 잡는다 — 실 MySQL 수동 확인에서
 * 드러났다. 그래서 쓰기 가능한 별도 트랜잭션을 연다({@code GoodsViewCountInterceptor}와 같은 이유).
 *
 * <p><b>왜 {@code @Transactional}이 아니라 {@link TransactionTemplate}인가:</b> 인터페이스 계약이
 * "record는 절대 예외를 던지지 않는다"인데, 애노테이션 방식은 try-catch가 프록시 <i>안쪽</i>에 놓인다.
 * 그러면 예외를 삼켜도 프록시가 커밋을 시도하다 rollback-only 때문에 다시 던져 상세가 또 500이 된다.
 * 템플릿을 직접 쓰면 커밋 실패까지 이 try-catch 안에서 잡힌다.
 */
public class DbViewCountRecorder implements ViewCountRecorder {

    private static final Logger log = LoggerFactory.getLogger(DbViewCountRecorder.class);

    private final GoodsRepository goodsRepository;
    private final TransactionTemplate transactionTemplate;

    public DbViewCountRecorder(GoodsRepository goodsRepository, PlatformTransactionManager transactionManager) {
        this.goodsRepository = goodsRepository;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate = template;
    }

    @Override
    public void record(Long goodsNo) {
        try {
            // 없는 상품이면 0행 갱신으로 조용히 지나간다.
            transactionTemplate.executeWithoutResult(status -> goodsRepository.addViewCount(goodsNo, 1));
        } catch (Exception e) {
            log.warn("조회수 DB 기록 실패 goodsNo={} — 이 조회는 집계에서 누락된다", goodsNo, e);
        }
    }
}
