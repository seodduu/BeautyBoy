package com.beautyboy.order.dto;

import java.time.LocalDateTime;

/**
 * 취소 응답(설계 §9-1). {@code refundAmount}는 서버가 확정한 값이다 —
 * 화면이 미리 보여주는 예상 환불액은 표시용이고, 진실은 이 응답이다.
 */
public record OrderCancelResponse(
        String orderNo,
        String status,
        int refundAmount,
        LocalDateTime canceledAt) {
}
