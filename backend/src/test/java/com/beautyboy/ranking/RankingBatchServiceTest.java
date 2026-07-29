package com.beautyboy.ranking;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.support.TestPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 랭킹 배치 테스트.
 *
 * <p>찜은 타 도메인(wishlist) 소유라 가짜 Provider를 주입한다 — 여기서 검증하려는 것은 실 집계
 * 쿼리가 아니라 "받은 수치로 점수를 옳게 계산하는가"이기 때문이다.
 *
 * <p><b>판매는 더 이상 Provider로 들어오지 않는다(A4b).</b> 주문 확정 후처리가
 * {@code upsertSalesIncrement}로 직접 증분하므로, 이 테스트도 배치를 돌리기 전에 같은 방식으로
 * 판매량을 쌓아 둔다. 배치가 그 값을 덮어쓰지 않는다는 사실 자체가
 * {@code 배치는_판매량을_덮어쓰지_않는다}로 명시돼 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RankingBatchServiceTest {

    private static final LocalDate 오늘 = LocalDate.now();

    /**
     * 판매·찜 수치를 부여할 상품 id. {@code @Transactional} 롤백은 auto_increment를 되돌리지 않아
     * "첫 상품 = id 1"이 클래스 안에서 성립하지 않으므로, 각 테스트가 실제로 저장한 id를
     * 이 가변 홀더에 넣어 가짜 Provider가 그 id를 가리키게 한다(브리프 Step 1 주의).
     */
    private static final AtomicReference<Long> 판매찜_대상_상품_id = new AtomicReference<>();

    /**
     * 판매찜_대상_상품_id가 가리키는 상품은 오늘 5명이 찜했다(판매 3은 각 테스트가 증분으로 쌓는다).
     *
     * <p>{@code @Primary}가 필요한 이유: wishlist 도메인의 실 구현
     * {@code WishlistWishStatProvider}(@Component)와 공존하므로, 가짜를 우선시켜 배치가
     * 결정적인 값(찜 5)을 보게 한다.
     */
    @TestConfiguration
    static class 가짜_통계_공급자 {
        @Bean
        @Primary
        WishStatProvider fakeWishStatProvider() {
            return date -> {
                Long goodsId = 판매찜_대상_상품_id.get();
                return (goodsId != null && date.equals(오늘)) ? Map.of(goodsId, 5) : Map.of();
            };
        }
    }

    @Autowired
    RankingBatchService rankingBatchService;
    @Autowired
    RankingSnapshotRepository rankingSnapshotRepository;
    @Autowired
    GoodsDailyStatRepository goodsDailyStatRepository;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    void 배치가_찜_수치를_일별통계에_반영한다() {
        Long goodsId = 상품_저장("C001001001");

        rankingBatchService.rebuild();

        TestPersistence.DB_왕복_강제(entityManager);

        GoodsDailyStat stat = goodsDailyStatRepository
                .findById(new GoodsDailyStat.Key(goodsId, 오늘)).orElseThrow();
        assertThat(stat.getWishCount()).isEqualTo(5);
    }

    /**
     * 판매 수집을 배치에서 뺀 뒤의 회귀(A4b). 찜 수집이 판매 컬럼을 함께 대입하면
     * 확정 후처리가 쌓아 둔 증분이 매시 사라진다 — 설계 §2-3의 "한 시점에 한 경로만".
     */
    @Test
    void 배치는_판매량을_덮어쓰지_않는다() {
        Long goodsId = 상품_저장("C001001001");
        판매_증분(goodsId, 3);

        rankingBatchService.rebuild();

        TestPersistence.DB_왕복_강제(entityManager);

        GoodsDailyStat stat = goodsDailyStatRepository
                .findById(new GoodsDailyStat.Key(goodsId, 오늘)).orElseThrow();
        assertThat(stat.getSalesCount()).isEqualTo(3);
        assertThat(stat.getWishCount()).isEqualTo(5);
    }

    @Test
    void 점수는_판매3_찜2_조회1_가중합이다() {
        Long goodsId = 상품_저장("C001001001");
        판매_증분(goodsId, 3);
        goodsDailyStatRepository.upsertViewCount(goodsId, 오늘, 10);

        TestPersistence.DB_왕복_강제(entityManager);

        rankingBatchService.rebuild();

        TestPersistence.DB_왕복_강제(entityManager);

        // 오늘 가중치 1.0 × (판매3×3 + 찜5×2 + 조회10×1) = 29.0
        RankingSnapshot snapshot = rankingSnapshotRepository
                .findByCategoryCodeOrderByRankNoAsc("ALL").get(0);
        assertThat(snapshot.getGoodsId()).isEqualTo(goodsId);
        assertThat(snapshot.getScore()).isEqualTo(29.0);
    }

    @Test
    void 오래된_날짜일수록_가중치가_낮다() {
        Long goodsId = 상품_저장("C001001001");
        판매_증분(goodsId, 3);
        // 어제 조회 10 → 가중치 0.6 → 6.0. 오늘 것(판매·찜)과 합산된다.
        goodsDailyStatRepository.upsertViewCount(goodsId, 오늘.minusDays(1), 10);

        TestPersistence.DB_왕복_강제(entityManager);

        rankingBatchService.rebuild();

        TestPersistence.DB_왕복_강제(entityManager);

        // 오늘 1.0×(3×3 + 5×2 + 0) = 19.0, 어제 0.6×(0 + 0 + 10) = 6.0 → 25.0
        assertThat(rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc("ALL").get(0).getScore())
                .isEqualTo(25.0);
    }

    @Test
    void 삼일보다_오래된_통계는_점수에_들어가지_않는다() {
        Long goodsId = 상품_저장("C001001001");
        판매_증분(goodsId, 3);
        goodsDailyStatRepository.upsertViewCount(goodsId, 오늘.minusDays(5), 1000);

        TestPersistence.DB_왕복_강제(entityManager);

        rankingBatchService.rebuild();

        TestPersistence.DB_왕복_강제(entityManager);

        // 1000이 새어 들어오면 19.0이 아니다. "한 번 뜬 상품이 영원히 1위"를 막는 창이다.
        assertThat(rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc("ALL").get(0).getScore())
                .isEqualTo(19.0);
    }

    @Test
    void 대분류별_랭킹이_따로_만들어진다() {
        상품_저장("C001001001");   // 스킨케어(C001)
        상품_저장("C002001001");   // 클렌징(C002)

        rankingBatchService.rebuild();

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc("C001")).hasSize(1);
        assertThat(rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc("C002")).hasSize(1);
        assertThat(rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc("ALL")).hasSize(2);
    }

    @Test
    void 순위는_1부터_빈틈없이_매겨진다() {
        상품_저장("C001001001");
        상품_저장("C001001001");

        rankingBatchService.rebuild();

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc("ALL"))
                .extracting(RankingSnapshot::getRankNo)
                .containsExactly(1, 2);
    }

    @Test
    void 다시_돌리면_이전_스냅샷이_남지_않고_통째로_교체된다() {
        상품_저장("C001001001");

        rankingBatchService.rebuild();
        rankingBatchService.rebuild();

        TestPersistence.DB_왕복_강제(entityManager);

        // 누적되면 (category_code, rank_no) 유니크 제약에 걸리거나 순위가 중복된다.
        assertThat(rankingSnapshotRepository.findAll()).hasSize(2);   // ALL 1건 + C001 1건
    }

    @Test
    void 숨김_상품은_랭킹에_오르지_않는다() {
        Brand brand = brandRepository.save(new Brand("브랜드", null));
        Goods hidden = new Goods(brand, "C001001001", "숨김", null, "https://img/x.jpg", 10000, 10000);
        hidden.hide();
        goodsRepository.save(hidden);

        rankingBatchService.rebuild();

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(rankingSnapshotRepository.findAll()).isEmpty();
    }

    /** 주문 확정 후처리가 하는 것과 같은 증분. 판매량이 배치가 아니라 이 경로로 들어온다(A4b). */
    private void 판매_증분(Long goodsId, int 수량) {
        goodsDailyStatRepository.upsertSalesIncrement(goodsId, 오늘, 수량);
    }

    /**
     * 상품을 저장하고 실제 발급된 id를 반환한다. 판매·찜 가짜 Provider가 참조할 대상도
     * 이 id로 갱신한다 — 그래야 "goods_id 1"이 아니라 "방금 저장한 상품"이 대상이 된다.
     */
    private Long 상품_저장(String categoryCode) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        Goods goods = goodsRepository.save(
                new Goods(brand, categoryCode, "상품", null, "https://img/x.jpg", 10000, 10000));
        판매찜_대상_상품_id.set(goods.getId());
        return goods.getId();
    }
}
