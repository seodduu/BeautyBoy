package com.beautyboy.outbox;

import java.time.LocalDateTime;
import java.util.List;

/** ORDER_CANCELED v1. OrderConfirmedEvent와 같은 토픽(order-events)에 실린다(설계 §8). */
public record OrderCanceledEvent(
        int version,
        Long eventId,           // 아웃박스 INSERT로 채번(행 PK) — 발행 시점엔 null
        String eventType,       // "ORDER_CANCELED"
        Long orderId,
        Long memberId,
        String orderNo,
        LocalDateTime canceledAt,
        int refundAmount,
        List<Line> lines) {

    public record Line(Long goodsId, Long optionId, int quantity) {
    }
}
