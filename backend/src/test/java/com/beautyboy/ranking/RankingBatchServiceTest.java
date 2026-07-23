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
 * <p>판매·찜은 타 도메인(order/wishlist)이 아직 구현하지 않았으므로 가짜 Provider를 주입한다.
 * 이것이 인터페이스로 가른 이유 그 자체다 — T2·T3를 기다리지 않고 랭킹을 완성할 수 있다.
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
     * 판매찜_대상_상품_id가 가리키는 상품은 오늘 3개 팔리고 5명이 찜했다.
     * 실 구현이 주입되면 폴백/이 가짜가 밀려나므로, 테스트는 항상 이 값을 본다.
     */
    @TestConfiguration
    static class 가짜_통계_공급자 {
        @Bean
        SalesStatProvider fakeSalesStatProvider() {
            return date -> {
                Long goodsId = 판매찜_대상_상품_id.get();
                return (goodsId != null && date.equals(오늘)) ? Map.of(goodsId, 3) : Map.of();
            };
        }

        @Bean
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
    void 배치가_Provider_수치를_일별통계에_반영한다() {
        Long goodsId = 상품_저장("C001001001");

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
