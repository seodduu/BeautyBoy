package com.beautyboy.catalog;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.beautyboy.support.TestPersistence.DB_왕복_강제;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 리뷰수 동기화 커맨드(catalog 소유)의 계약 검증.
 *
 * <p>클래스 {@code @Transactional}이 곧 호출자의 트랜잭션 역할을 한다 — 구현이 요구하는
 * {@code Propagation.MANDATORY}를 충족시킨다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GoodsReviewCountServiceTest {

    @Autowired
    GoodsReviewCountCommand goodsReviewCountCommand;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    EntityManager entityManager;

    @Test
    void 재계산_값을_그대로_set한다_멱등_같은_값을_두_번_보내도_결과_동일() {
        Long goodsId = 상품_저장("토너");

        goodsReviewCountCommand.syncReviewCount(goodsId, 7);
        assertThat(리뷰수(goodsId)).isEqualTo(7);

        goodsReviewCountCommand.syncReviewCount(goodsId, 7);
        assertThat(리뷰수(goodsId)).isEqualTo(7);

        goodsReviewCountCommand.syncReviewCount(goodsId, 3); // 삭제로 줄어든 재계산 값
        assertThat(리뷰수(goodsId)).isEqualTo(3);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 트랜잭션_밖_호출은_예외다() {
        assertThatThrownBy(() -> goodsReviewCountCommand.syncReviewCount(1L, 1))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private int 리뷰수(Long goodsId) {
        DB_왕복_강제(entityManager);
        return goodsRepository.findById(goodsId).orElseThrow().getReviewCount();
    }

    private Long 상품_저장(String name) {
        Brand brand = brandRepository.save(new Brand(name + "브랜드" + System.nanoTime(), null));
        Goods goods = goodsRepository.save(
                new Goods(brand, "C001001001", name, null, "https://img/x.jpg", 20000, 16000));
        return goods.getId();
    }
}
