package com.beautyboy.cart;

import com.beautyboy.cart.dto.CartAddRequest;
import com.beautyboy.cart.dto.CartBulkAddRequest;
import com.beautyboy.cart.dto.CartItemResponse;
import com.beautyboy.cart.dto.CartQuantityRequest;
import com.beautyboy.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 전부 인증 대상이다 — SecurityConfig의 anyRequest().authenticated()에 걸리므로 설정 추가가 필요 없다. */
@RestController
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/api/v1/cart/items")
    public ResponseEntity<ApiResponse<List<CartItemResponse>>> items(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.itemsOf(memberId)));
    }

    @PostMapping("/api/v1/cart/items")
    public ResponseEntity<ApiResponse<Void>> add(@AuthenticationPrincipal Long memberId,
                                                 @Valid @RequestBody CartAddRequest request) {
        cartService.add(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null));
    }

    /** 루틴 전체 담기. 한 건이라도 실패하면 전부 되돌린다(CartService.addAll의 트랜잭션). */
    @PostMapping("/api/v1/cart/items/bulk")
    public ResponseEntity<ApiResponse<Void>> addAll(@AuthenticationPrincipal Long memberId,
                                                    @Valid @RequestBody CartBulkAddRequest request) {
        cartService.addAll(memberId, request.items());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null));
    }

    @PatchMapping("/api/v1/cart/items/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> changeQuantity(@AuthenticationPrincipal Long memberId,
                                                            @PathVariable Long cartItemId,
                                                            @RequestBody CartQuantityRequest request) {
        cartService.changeQuantity(memberId, cartItemId, request.quantity());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/api/v1/cart/items/{cartItemId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Long memberId, @PathVariable Long cartItemId) {
        cartService.remove(memberId, cartItemId);
        return ResponseEntity.noContent().build();
    }
}
