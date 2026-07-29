package com.beautyboy.order;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 취소의 주문 쪽 절반. payment의 취소 오케스트레이션(PaymentCancelServiceImpl)이
 * 이 포트를 경유해 order 도메인을 조작한다 — OrderConfirmPort와 같은 방향의 경계.
 */
public interface OrderCancelPort {

    record CancelLine(Long orderItemId, int quantity) {
    }

    /** 재고 복원 입력. optionId가 null인 항목(재고 비관리)은 stockLines에서 이미 걸러져 있다. */
    record StockLine(Long optionId, int quantity) {
    }

    record CancelApplication(
            Long orderId,
            String orderNo,
            int refundAmount,        // 서버 계산: Σ(스냅샷 unit_price × 취소 수량)
            String statusAfter,      // PARTIALLY_CANCELED | CANCELED (파생 판정 결과)
            LocalDateTime canceledAt,
            List<StockLine> stockLines) {
    }

    /**
     * 호출자의 트랜잭션 안에서(MANDATORY): 주문을 락과 함께 읽고, 검증하고(소유→404·
     * 상태→ORDER_INVALID_STATUS·수량→ORDER_CANCEL_QUANTITY_EXCEEDED·빈 목록→ORDER_CANCEL_EMPTY),
     * canceled_quantity 반영·상태 전이·취소 이력 저장·ORDER_CANCELED 아웃박스 INSERT까지 마친다.
     * 토스 호출과 보상 기록은 호출자(payment)의 몫이다.
     */
    CancelApplication applyCancel(String orderNo, Long memberId,
                                  List<CancelLine> lines, String reason);
}
