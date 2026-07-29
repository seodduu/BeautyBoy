package com.beautyboy.order;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.common.PageResponse;
import com.beautyboy.order.dto.OrderCancelRequest;
import com.beautyboy.order.dto.OrderCancelResponse;
import com.beautyboy.order.dto.OrderCreateRequest;
import com.beautyboy.order.dto.OrderCreateResponse;
import com.beautyboy.order.dto.OrderDetailResponse;
import com.beautyboy.order.dto.OrderSummaryResponse;
import com.beautyboy.payment.PaymentCancelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;
    private final PaymentCancelService paymentCancelService;

    /**
     * 취소는 {@code PaymentCancelService} <b>인터페이스</b>로만 받는다. 구현
     * ({@code PaymentCancelServiceImpl})을 여기서 import하면 order가 payment 내부를 아는 셈이라
     * 도메인 경계가 깨진다 — 확정 경로가 {@code OrderConfirmPort}를 쓰는 것과 같은 방향이다.
     */
    public OrderController(OrderService orderService,
                           PaymentCancelService paymentCancelService) {
        this.orderService = orderService;
        this.paymentCancelService = paymentCancelService;
    }

    @PostMapping("/api/v1/orders")
    public ResponseEntity<ApiResponse<OrderCreateResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody OrderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(orderService.create(memberId, request)));
    }

    @GetMapping("/api/v1/orders")
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> orders(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.ordersOf(memberId, page, size)));
    }

    @GetMapping("/api/v1/orders/{orderNo}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> orderDetail(
            @AuthenticationPrincipal Long memberId, @PathVariable String orderNo) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.orderDetail(memberId, orderNo)));
    }

    /** 수량 단위 부분 취소. 환불액은 요청에 없고 서버가 스냅샷 단가로 계산한다(설계 §9-1). */
    @PostMapping("/api/v1/orders/{orderNo}/cancel")
    public ResponseEntity<ApiResponse<OrderCancelResponse>> cancel(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String orderNo,
            @Valid @RequestBody OrderCancelRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(paymentCancelService.cancel(memberId, orderNo, request)));
    }
}
