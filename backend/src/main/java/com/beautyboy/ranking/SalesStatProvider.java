package com.beautyboy.ranking;

import java.time.LocalDate;
import java.util.Map;

/**
 * 랭킹 집계가 필요로 하는 "그 날의 판매 수량" 공급자.
 *
 * <p>왜 ranking 패키지에 있는가: 랭킹 점수는 {@code 판매×3 + 찜×2 + 조회×1}인데
 * 판매 수량은 {@code order} 도메인 소유다. CLAUDE.md "패키지 = 서비스 경계"에 따라
 * ranking이 {@code order_item} 테이블을 직접 읽을 수 없다.
 *
 * <p>그래서 <b>필요한 쪽(ranking)이 인터페이스를 정의하고, 가진 쪽(order)이 구현한다</b>(의존성 역전).
 * ranking은 order를 컴파일 의존하지 않고, order는 ranking의 이 타입 하나만 알면 된다.
 *
 * <p>Wave 2는 T1(ranking)과 T2(order)가 다른 터미널에서 병렬로 도므로 실 구현이 아직 없다.
 * 그동안 T1은 테스트에서 fake를 주입하고, 앱 구동은
 * {@link RankingStatFallbackConfig}의 빈 구현으로 버틴다. T2가 머지되면 그쪽 구현이 자동으로 이긴다.
 *
 * <p><b>이 시그니처는 Wave 2 착수 전에 확정된 계약이다.</b> 바꿔야 하면 양쪽 터미널이
 * 동시에 깨지므로, 수정하지 말고 보고한다.
 */
public interface SalesStatProvider {

    /**
     * 지정한 날짜에 <b>결제 완료</b>된 주문의 상품별 판매 수량.
     *
     * <p>결제대기·취소 주문은 세지 않는다 — 장바구니에 담아두기만 해도 랭킹이 오르면 조작이 쉬워진다.
     *
     * @param date 집계 대상 날짜
     * @return {@code goods.id → 판매 수량}. 그 날 팔린 것이 없으면 빈 맵(널을 반환하지 않는다).
     */
    Map<Long, Integer> salesQuantityByGoods(LocalDate date);
}
