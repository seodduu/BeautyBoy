package com.beautyboy.ranking;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRatingProvider;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.catalog.WishedGoodsProvider;
import com.beautyboy.ranking.dto.RankingItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

/**
 * catalog의 두 공급자를 통해 랭킹 카드에 별점·찜이 채워지는지 검증한다(GoodsServiceTest와 같은 계약).
 * 스냅샷 정렬·카테고리 필터는 {@code RankingApiTest}가 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RankingServiceTest {

    @Autowired
    RankingService rankingService;
    @Autowired
    RankingSnapshotRepository rankingSnapshotRepository;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @MockBean
    GoodsRatingProvider goodsRatingProvider;
    @MockBean
    WishedGoodsProvider wishedGoodsProvider;

    @Test
    void 랭킹_카드에_별점과_리뷰수가_공급자_값으로_채워진다() {
        Long 상품A = 상품_저장("상품A");
        Long 상품B = 상품_저장("상품B");
        스냅샷_저장(상품A, 1, 100.0);
        스냅샷_저장(상품B, 2, 50.0);

        given(goodsRatingProvider.ratingsByGoods(any()))
                .willReturn(Map.of(상품A, new GoodsRatingProvider.RatingStat(4.5, 12)));
        given(wishedGoodsProvider.wishedGoodsIds(any(), any())).willReturn(Set.of());

        List<RankingItem> items = rankingService.rankings(null, null);

        assertThat(items).filteredOn(i -> i.goodsNo().equals(상품A)).singleElement()
                .satisfies(i -> {
                    assertThat(i.rating()).isEqualTo(4.5);
                    assertThat(i.reviewCount()).isEqualTo(12);
                });
        // 리뷰가 없는 상품은 0.0/0 — 공급자가 키를 안 주면 기본값이다
        assertThat(items).filteredOn(i -> i.goodsNo().equals(상품B)).singleElement()
                .satisfies(i -> {
                    assertThat(i.rating()).isEqualTo(0.0);
                    assertThat(i.reviewCount()).isEqualTo(0);
                });
    }

    @Test
    void 로그인한_회원이_찜한_상품만_wished가_true다() {
        Long 상품A = 상품_저장("상품A");
        Long 상품B = 상품_저장("상품B");
        스냅샷_저장(상품A, 1, 100.0);
        스냅샷_저장(상품B, 2, 50.0);

        given(goodsRatingProvider.ratingsByGoods(any())).willReturn(Map.of());
        given(wishedGoodsProvider.wishedGoodsIds(1L, List.of(상품A, 상품B))).willReturn(Set.of(상품A));

        List<RankingItem> items = rankingService.rankings(null, 1L);

        assertThat(items).filteredOn(RankingItem::wished)
                .extracting(RankingItem::goodsNo).containsExactly(상품A);
    }

    @Test
    void 비로그인이면_wished는_전부_false이고_공급자에_null이_전달된다() {
        Long 상품A = 상품_저장("상품A");
        스냅샷_저장(상품A, 1, 100.0);

        given(goodsRatingProvider.ratingsByGoods(any())).willReturn(Map.of());
        given(wishedGoodsProvider.wishedGoodsIds(isNull(), any())).willReturn(Set.of());

        List<RankingItem> items = rankingService.rankings(null, null);

        assertThat(items).allMatch(i -> !i.wished());
    }

    private Long 상품_저장(String name) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        return goodsRepository.save(
                new Goods(brand, "C001001001", name, null, "https://img/x.jpg", 20000, 16000)).getId();
    }

    private void 스냅샷_저장(Long goodsId, int rankNo, double score) {
        rankingSnapshotRepository.save(
                new RankingSnapshot(RankingSnapshot.CATEGORY_ALL, goodsId, rankNo, score, LocalDateTime.now()));
    }
}
