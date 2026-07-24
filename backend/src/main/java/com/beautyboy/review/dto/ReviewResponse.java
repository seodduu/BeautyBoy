package com.beautyboy.review.dto;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long reviewId,
        Long memberId,
        int rating,
        String content,
        String skinType,
        int helpfulCount,
        LocalDateTime createdAt) {
}
