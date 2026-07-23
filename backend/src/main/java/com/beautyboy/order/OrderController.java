package com.beautyboy.order;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.order.dto.OrderCreateRequest;
import com.beautyboy.order.dto.OrderCreateResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
}
