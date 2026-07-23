package com.beautyboy.order.dto;

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
        List<OrderItemRequest> items,
        String receiverName,
        String receiverPhone,
        String zipcode,
        String address1,
        String address2,
        String deliveryType) {

    /** 무엇을 몇 개. 가격은 서버가 정한다. */
    public record OrderItemRequest(Long goodsNo, Long optionNo, int quantity) {
    }
}
