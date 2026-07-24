package com.beautyboy.wishlist;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.wishlist.dto.WishlistItemResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 전부 인증 대상이다 — SecurityConfig의 anyRequest().authenticated()에 걸리므로 설정 추가가 필요 없다. */
@RestController
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @PostMapping("/api/v1/wishlist/{goodsNo}")
    public ResponseEntity<ApiResponse<Void>> add(@AuthenticationPrincipal Long memberId,
                                                 @PathVariable Long goodsNo) {
        wishlistService.add(memberId, goodsNo);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null));
    }

    @DeleteMapping("/api/v1/wishlist/{goodsNo}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Long memberId, @PathVariable Long goodsNo) {
        wishlistService.remove(memberId, goodsNo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/wishlist")
    public ResponseEntity<ApiResponse<List<WishlistItemResponse>>> items(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(wishlistService.itemsOf(memberId)));
    }
}
