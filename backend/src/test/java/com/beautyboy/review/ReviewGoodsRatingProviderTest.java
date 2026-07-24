package com.beautyboy.review;

import com.beautyboy.catalog.GoodsRatingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReviewGoodsRatingProviderTest {

    @Autowired
    GoodsRatingProvider provider;
    @Autowired
    GoodsReviewStatRepository goodsReviewStatRepository;

    @Test
    void 폴백이_아니라_review_도메인_구현이_주입된다() {
        // 이 구현이 있으면 catalog의 빈 맵 폴백이 물러나야 한다.
        // 여기가 깨지면 카드 별점이 영원히 0으로 남는다.
        assertThat(provider).isInstanceOf(ReviewGoodsRatingProvider.class);
    }

    @Test
    void 별점은_ratingSum을_reviewCount로_나눈_값이다() {
        Long 상품A = 1L;
        GoodsReviewStat stat = new GoodsReviewStat(상품A);
        stat.update(4, 18, LocalDateTime.now());
        goodsReviewStatRepository.save(stat);

        Map<Long, GoodsRatingProvider.RatingStat> stats = provider.ratingsByGoods(List.of(상품A));

        assertThat(stats.get(상품A).rating()).isEqualTo(4.5);   // 18/4
        assertThat(stats.get(상품A).reviewCount()).isEqualTo(4);
    }

    @Test
    void 리뷰수가_0인_행은_0으로_나누지_않고_키를_넣지_않는다() {
        Long 상품B = 2L;
        GoodsReviewStat stat = new GoodsReviewStat(상품B);
        stat.update(0, 0, LocalDateTime.now());
        goodsReviewStatRepository.save(stat);

        assertThat(provider.ratingsByGoods(List.of(상품B))).doesNotContainKey(상품B);
    }

    @Test
    void 빈_입력은_리포지토리를_부르지_않고_빈_맵이다() {
        GoodsReviewStatRepository mockRepository = mock(GoodsReviewStatRepository.class);
        ReviewGoodsRatingProvider isolatedProvider = new ReviewGoodsRatingProvider(mockRepository);

        assertThat(isolatedProvider.ratingsByGoods(List.of())).isEmpty();
        verifyNoInteractions(mockRepository);
    }
}
