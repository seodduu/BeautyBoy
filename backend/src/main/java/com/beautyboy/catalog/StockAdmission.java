package com.beautyboy.catalog;

import java.util.List;

/**
 * 재고 선점 필터. 통과는 판매 보장이 아니다 — 최종 판정은 DB 조건부 UPDATE다.
 *
 * <p>Redis 카운터는 재고가 아니라 <b>입장권</b>이다(설계 §3). 카운터가 DB와 어긋나도 어느
 * 방향으로든 초과 판매는 나지 않는다 — 재고의 진실은 DB 한 곳이고 이 필터는 순서만 정한다.
 * 그래서 이 인터페이스의 모든 실패는 "통과" 쪽으로 강등된다.
 */
public interface StockAdmission {

    /** 선점 대상 한 줄. optionId는 null이 아니다(옵션 없는 상품은 호출자가 이미 걸렀다). */
    record Line(Long optionId, int quantity) {
    }

    /** all-or-nothing 선점. 전 라인 성공 시에만 true. Redis 장애 시 true(통과)로 강등. */
    boolean tryAcquire(List<Line> lines);

    /** 선점 반환. 존재하는 키에만 더한다. 실패는 삼킨다(카운터가 작아지는 안전 방향). */
    void release(List<Line> lines);
}
