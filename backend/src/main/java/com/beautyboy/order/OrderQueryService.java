package com.beautyboy.order;

/**
 * order가 타 도메인에 내주는 조회 통로.
 *
 * <p>review는 "구매한 사람만 리뷰를 쓴다"를 판정해야 하는데 order 테이블을 직접 볼 수 없다
 * (패키지 = 서비스 경계). 그래서 order가 이 인터페이스를 내주고 review가 호출한다.
 * order 엔티티/리포지토리를 review가 import하지 않게 하는 유일한 통로다.
 */
public interface OrderQueryService {

    /**
     * 이 회원이 이 상품을 <b>결제 완료</b> 상태로 구매한 적이 있는가.
     *
     * <p>결제대기(PENDING)는 세지 않는다 — 담아두기만 한 것으로 리뷰를 쓰면 인증이 무의미하다.
     * 배송완료(DONE) 조건을 쓰지 않는 이유: 배송 상태 전이는 Wave 3 스케줄러 몫이라 아직 PAID가 최종 상태다.
     * 배송 개념이 생기면 이 판정 기준을 DONE 이상으로 좁힌다.
     *
     * @return 결제완료 주문에 그 상품이 하나라도 있으면 true
     */
    boolean hasPurchased(Long memberId, Long goodsNo);
}
