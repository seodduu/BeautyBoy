package com.beautyboy.payment;

import com.beautyboy.order.dto.OrderCancelRequest;
import com.beautyboy.order.dto.OrderCancelResponse;

/**
 * 취소 오케스트레이션 경계. 컨트롤러는 이 인터페이스만 주입받는다 —
 * 구현({@code PaymentCancelServiceImpl})을 order 쪽에서 import하면 도메인 경계가 깨진다.
 */
public interface PaymentCancelService {

    OrderCancelResponse cancel(Long memberId, String orderNo, OrderCancelRequest request);
}
