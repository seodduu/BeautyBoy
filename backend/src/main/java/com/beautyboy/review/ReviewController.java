package com.beautyboy.review;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.common.PageResponse;
import com.beautyboy.review.dto.MyReviewItem;
import com.beautyboy.review.dto.ReviewCreateRequest;
import com.beautyboy.review.dto.ReviewResponse;
import com.beautyboy.review.dto.ReviewStatResponse;
import com.beautyboy.review.dto.ReviewUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리뷰 작성·조회. 작성은 인증 필요(SecurityConfig의 anyRequest().authenticated()),
 * 목록·통계 조회는 SecurityConfig에서 공개 처리돼 있다. 전부 서비스에 위임한다.
 */
@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/api/v1/reviews")
    public ResponseEntity<ApiResponse<Void>> create(@AuthenticationPrincipal Long memberId,
                                                     @Valid @RequestBody ReviewCreateRequest request) {
        reviewService.create(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null));
    }

    @GetMapping("/api/v1/reviews")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> list(
            @RequestParam Long goodsNo,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.list(goodsNo, page)));
    }

    @GetMapping("/api/v1/reviews/me")
    public ResponseEntity<ApiResponse<PageResponse<MyReviewItem>>> myReviews(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.myReviews(memberId, page, size)));
    }

    @GetMapping("/api/v1/reviews/stats")
    public ResponseEntity<ApiResponse<ReviewStatResponse>> stats(@RequestParam Long goodsNo) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.stat(goodsNo)));
    }

    @PutMapping("/api/v1/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> update(@AuthenticationPrincipal Long memberId,
                                                      @PathVariable Long reviewId,
                                                      @Valid @RequestBody ReviewUpdateRequest request) {
        reviewService.update(memberId, reviewId, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/api/v1/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal Long memberId,
                                                      @PathVariable Long reviewId) {
        reviewService.delete(memberId, reviewId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/api/v1/reviews/{reviewId}/helpful")
    public ResponseEntity<ApiResponse<Void>> markHelpful(@AuthenticationPrincipal Long memberId,
                                                          @PathVariable Long reviewId) {
        reviewService.markHelpful(reviewId, memberId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
