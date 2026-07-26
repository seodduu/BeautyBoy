package com.beautyboy.review;

import com.beautyboy.catalog.GoodsRatingProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 카탈로그가 요구하는 별점 통계를 review가 공급한다(의존성 역전).
 *
 * <p>catalog는 goods_review_stat 테이블을 직접 읽을 수 없다(패키지 = 서비스 경계). 그래서 catalog가
 * 인터페이스를 정의하고 데이터를 가진 review가 구현한다. 이 {@code @Component}가 존재하면
 * catalog의 빈 맵 폴백({@code CatalogStatFallbackAutoConfiguration})이 자동으로 물러난다.
 *
 * <p>{@code review_count == 0}인 행은 결과에 넣지 않는다 — 삭제로 0이 된 행이 실제로 존재하며,
 * 0으로 나누는 것도 피한다.
 */
@Component
public class ReviewGoodsRatingProvider implements GoodsRatingProvider {

    private final GoodsReviewStatRepository goodsReviewStatRepository;

    public ReviewGoodsRatingProvider(GoodsReviewStatRepository goodsReviewStatRepository) {
        this.goodsReviewStatRepository = goodsReviewStatRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, RatingStat> ratingsByGoods(Collection<Long> goodsIds) {
        if (goodsIds.isEmpty()) {
            return Map.of();
        }

        List<GoodsReviewStat> stats = goodsReviewStatRepository.findAllById(goodsIds);

        Map<Long, RatingStat> result = new HashMap<>();
        for (GoodsReviewStat stat : stats) {
            if (stat.getReviewCount() > 0) {
                double rating = stat.getRatingSum() / (double) stat.getReviewCount();
                result.put(stat.getGoodsId(), new RatingStat(rating, stat.getReviewCount()));
            }
        }
        return result;
    }
}
