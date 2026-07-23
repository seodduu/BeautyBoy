package com.beautyboy.order;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.order.dto.OrderCreateRequest;
import com.beautyboy.order.dto.OrderCreateResponse;
import com.beautyboy.order.dto.OrderDetailResponse;
import com.beautyboy.order.dto.OrderSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/v1/orders")
    public ResponseEntity<ApiResponse<OrderCreateResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @RequestBody OrderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(orderService.create(memberId, request)));
    }

    @GetMapping("/api/v1/orders")
    public ResponseEntity<ApiResponse<List<OrderSummaryResponse>>> orders(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.ordersOf(memberId)));
    }

    @GetMapping("/api/v1/orders/{orderNo}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> orderDetail(
            @AuthenticationPrincipal Long memberId, @PathVariable String orderNo) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.orderDetail(memberId, orderNo)));
    }
}
