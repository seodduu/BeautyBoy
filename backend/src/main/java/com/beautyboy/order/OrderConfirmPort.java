package com.beautyboy.order;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 승인이 주문에 요구하는 것 전부. 주문의 소유자는 order이고, 소비자는 결제 승인(payment)뿐이다.
 *
 * <p>호출 계약:
 * <ul>
 *   <li><b>호출자의 트랜잭션 안에서만 부른다</b>(구현이 {@code MANDATORY}로 강제한다).
 *       {@code lockPendingOrder}의 행 락은 그 트랜잭션이 끝날 때까지 유지된다 —
 *       동시 승인(더블클릭) 직렬화가 이 락에 걸려 있다.</li>
 *   <li>{@code lockPendingOrder}: 미존재·타인 소유면 {@code ORDER_NOT_FOUND}(존재를 숨겨 404),
 *       PENDING이 아니면 {@code PAYMENT_ALREADY_CONFIRMED}를 던진다.</li>
 *   <li>{@code stockLines}는 재고 관리 대상(optionId 비-null)만 담는다 — 필터링은 order의 책임.</li>
 *   <li>{@code markPaid}: PENDING→PAID 전이 후 전이된 상태 문자열을 반환한다.</li>
 * </ul>
 */
public interface OrderConfirmPort {

    ConfirmTarget lockPendingOrder(String orderNo, Long memberId);

    String markPaid(Long orderId, LocalDateTime paidAt);

    /** 재고 차감 한 줄 — StockCommandService.DeductionLine과 같은 모양(변환은 payment가 한다). */
    record StockLine(Long optionId, int quantity) {}

    /** 승인 검증에 필요한 주문 스냅샷. 엔티티는 경계를 넘지 않는다. */
    record ConfirmTarget(Long orderId, String orderNo, int payableAmount, List<StockLine> stockLines) {}
}
