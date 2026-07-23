package com.beautyboy.catalog;

/**
 * 타 도메인이 catalog를 경유할 때 쓰는 진입점. 도메인 패키지는 서로의 엔티티/리포지토리를
 * 직접 import하지 않으므로(패키지 = 서비스 경계), 상품 존재 여부가 필요한 타 도메인(예:
 * Wave 1 ingredient의 `/goods/{goodsNo}/ingredients`)은 이 인터페이스만 본다.
 */
public interface GoodsQueryService {

    /**
     * 상품이 존재하고 노출 상태(HIDDEN이 아님)인지 여부.
     * 상세/설명/추천과 동일한 기준을 쓴다 — 목록에서 숨긴 상품을 다른 경로로도 보면 안 되므로,
     * HIDDEN 상품은 "행이 존재해도" false를 반환한다.
     */
    boolean exists(Long goodsNo);
}
