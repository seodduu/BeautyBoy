package com.beautyboy.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 취소 요청(설계 §9-1).
 *
 * <p><b>금액 필드가 없는 것이 이 record의 핵심이다.</b> 환불액은 서버가 스냅샷 단가로 계산한다 —
 * 클라이언트가 보낸 금액을 쓰면 그 자리가 곧 조작 지점이 된다(주문 생성과 같은 원칙).
 */
public record OrderCancelRequest(
        @NotEmpty(message = "취소할 항목을 선택해주세요")
        @Valid
        List<Item> items,

        @Size(max = 200)
        String reason) {

    public record Item(
            @NotNull Long orderItemId,
            @Positive int quantity) {
    }
}
