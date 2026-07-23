package com.beautyboy.ranking;

import java.time.LocalDate;
import java.util.Map;

/**
 * 랭킹 집계가 필요로 하는 "그 날의 찜 수" 공급자.
 *
 * <p>{@link SalesStatProvider}와 같은 이유로 ranking이 정의하고 {@code wishlist}(Wave 2 T3)가 구현한다.
 * ranking은 {@code wishlist} 테이블을 직접 읽지 않는다.
 */
public interface WishStatProvider {

    /**
     * 지정한 날짜에 <b>새로 추가된</b> 찜의 상품별 개수.
     *
     * <p>누적 찜 수가 아니라 그 날의 증가분이다 — 누적을 쓰면 한 번 오른 상품이 영원히 상위에 남아
     * "최근 3일 가중"이라는 설계 의도가 무의미해진다. 찜 취소는 차감하지 않는다(집계 단순화).
     *
     * @param date 집계 대상 날짜
     * @return {@code goods.id → 찜 추가 수}. 없으면 빈 맵(널을 반환하지 않는다).
     */
    Map<Long, Integer> wishCountByGoods(LocalDate date);
}
