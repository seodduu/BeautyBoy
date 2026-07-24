package com.beautyboy.catalog;

import java.util.Collection;
import java.util.Map;

/**
 * 카탈로그 카드가 필요로 하는 "상품별 별점 집계" 공급자.
 *
 * <p>왜 catalog 패키지에 있는가: GoodsListItem은 rating·reviewCount를 담는데 그 값은
 * review 도메인 소유(goods_review_stat)다. 필요한 쪽(catalog)이 정의하고 가진 쪽(review)이
 * 구현한다(의존성 역전) — ranking.SalesStatProvider와 같은 패턴이다.
 */
public interface GoodsRatingProvider {

    /** 평균 별점과 리뷰 수. 평균은 rating_sum/review_count로 계산하며, 리뷰가 없으면 맵에 키가 없다. */
    record RatingStat(double rating, int reviewCount) {
    }

    /**
     * @param goodsIds 조회 대상. 비어 있으면 빈 맵.
     * @return {@code goods.id → RatingStat}. 리뷰가 없는 상품은 키를 넣지 않는다(널 반환 금지).
     */
    Map<Long, RatingStat> ratingsByGoods(Collection<Long> goodsIds);
}
