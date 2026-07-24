package com.beautyboy.review.dto;

import java.time.LocalDateTime;

/** 마이페이지용. 상품 카드가 아니라 "내가 쓴 글" 관점이므로 상품명·썸네일만 곁들인다. */
public record MyReviewItem(
        Long reviewId, Long goodsNo, String goodsName, String thumbnailUrl,
        int rating, String content, int helpfulCount, LocalDateTime createdAt) {
}
