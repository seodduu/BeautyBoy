package com.beautyboy.catalog;

import java.util.List;

/**
 * 재고 차감 커맨드 경계. 재고의 소유자는 catalog이고, 소비자는 결제 승인(payment)뿐이다.
 *
 * <p>호출 계약:
 * <ul>
 *   <li><b>호출자의 트랜잭션 안에서만 부른다</b>(구현이 {@code MANDATORY}로 강제한다).
 *       이후 단계가 실패해 트랜잭션이 롤백되면 차감도 함께 되돌아간다 — 그것이 복원의 전부다.</li>
 *   <li>{@code optionId}는 null이면 안 된다. 옵션 없는 상품(재고 비관리)은 호출자가 거른다.</li>
 *   <li>전부 성공하거나(반환), 하나라도 부족하면 {@code ORDER_OUT_OF_STOCK}을 던진다(all-or-nothing).</li>
 * </ul>
 */
public interface StockCommandService {

    /** 한 옵션에서 깎을 수량. quantity는 양수여야 한다(주문 생성이 이미 검증했다). */
    record DeductionLine(Long optionId, int quantity) {
    }

    void deductAll(List<DeductionLine> lines);

    /** 한 옵션에 되돌릴 수량. quantity는 양수여야 한다(취소 검증이 이미 보장했다). */
    record RestoreLine(Long optionId, int quantity) {
    }

    /**
     * 재고 복원 — deductAll의 거울상. 호출자의 트랜잭션 안에서만 부른다(MANDATORY).
     * 조건 없는 원자 UPDATE라 실패 경로가 없다. optionId null은 호출자가 거른다.
     */
    void restoreAll(List<RestoreLine> lines);
}
