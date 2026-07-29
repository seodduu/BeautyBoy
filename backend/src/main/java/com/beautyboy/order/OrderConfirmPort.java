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
 *   <li>{@code eventLines}는 <b>전 주문 줄</b>을 담는다(optionId가 null인 줄도 포함).
 *       확정 이벤트의 소비자(장바구니 비우기·판매 집계·알림)는 재고 비관리 상품도 다뤄야 하므로
 *       재고용 필터를 그대로 재사용할 수 없다. 두 목록이 갈라지는 이유가 이것이다.</li>
 *   <li>{@code markPaid}: PENDING→PAID 전이 후 전이된 상태 문자열을 반환한다.</li>
 * </ul>
 */
public interface OrderConfirmPort {

    ConfirmTarget lockPendingOrder(String orderNo, Long memberId);

    String markPaid(Long orderId, LocalDateTime paidAt);

    /** 재고 차감 한 줄 — StockCommandService.DeductionLine과 같은 모양(변환은 payment가 한다). */
    record StockLine(Long optionId, int quantity) {}

    /**
     * 확정 이벤트 페이로드용 한 줄 — OrderConfirmedEvent.Line과 같은 모양(변환은 payment가 한다).
     * outbox의 record를 여기서 그대로 쓰지 않는 이유: 포트는 order의 소유물이고, 어떤 이벤트
     * 스키마로 나가는지는 발행 지점(payment)의 관심사다.
     */
    record EventLine(Long goodsId, Long optionId, int quantity) {}

    /** 승인 검증과 확정 이벤트 발행에 필요한 주문 스냅샷. 엔티티는 경계를 넘지 않는다. */
    record ConfirmTarget(Long orderId, String orderNo, Long memberId, int payableAmount,
                         List<StockLine> stockLines, List<EventLine> eventLines) {}
}
