package com.beautyboy.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 주문 생성 요청.
 *
 * <p><b>금액 필드가 없는 것이 의도다.</b> 클라이언트가 보낸 금액을 담을 자리를 만들지 않으면
 * 그 값을 쓰는 실수 자체가 불가능해진다. 요청 본문에 금액이 끼어 있어도 Jackson이 무시한다.
 *
 * <p>배송지를 addressId 참조가 아니라 값으로 받는 이유: 주문서에 스냅샷으로 남길 것이고,
 * 프론트가 새 주소를 즉석에서 입력하는 흐름도 지원해야 한다.
 */
public record OrderCreateRequest(
        // @Valid만 붙인다(@NotEmpty 아님) — null·빈 목록은 OrderService가 CART_EMPTY로 판정한다.
        // 여기서 400으로 가로채면 그 코드가 사라진다.
        @Valid List<OrderItemRequest> items,
        @NotBlank @Size(max = 50) String receiverName,     // order.receiver_name VARCHAR(50)
        @NotBlank @Size(max = 20) String receiverPhone,    // order.receiver_phone VARCHAR(20)
        @NotBlank @Size(max = 10) String zipcode,          // order.zipcode VARCHAR(10)
        @NotBlank @Size(max = 200) String address1,        // order.address1 VARCHAR(200)
        @Size(max = 200) String address2,                  // NULL 허용 컬럼이라 @NotBlank 없음
        // 값 집합(NORMAL만) 검증은 하지 않는다 — 오늘드림을 도입하면 여기부터 고쳐야 하는데,
        // 허용 값 목록이 DTO에 박히면 도메인 결정이 DTO로 새어 나간다.
        @NotBlank @Size(max = 20) String deliveryType) {

    /** 무엇을 몇 개. 가격은 서버가 정한다. quantity는 OrderService가 CART_QUANTITY_INVALID로 판정한다. */
    public record OrderItemRequest(@NotNull Long goodsNo, Long optionNo, int quantity) {
    }
}
