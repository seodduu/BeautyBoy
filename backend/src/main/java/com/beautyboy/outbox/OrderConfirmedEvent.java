package com.beautyboy.outbox;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 확정 이벤트 페이로드 (설계 §4.3 — 공유 계약, 전량 그대로).
 *
 * <p>{@code eventId}는 outbox_event.id로, INSERT 후 채번되므로 {@link OutboxAppender}
 * 호출 시점엔 null로 넘기고 직렬화 직전에 채운다. 컨슈머 멱등성 키로 쓰인다.
 */
public record OrderConfirmedEvent(
        int version,
        Long eventId,
        String eventType,
        Long orderId,
        Long memberId,
        String orderNo,
        LocalDateTime confirmedAt,
        List<Line> lines) {

    public record Line(Long goodsId, Long optionId, int quantity) {
    }
}
