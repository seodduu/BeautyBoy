package com.beautyboy.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 리뷰 작성 요청. 금액처럼 조작 위험 값이 없다.
 *
 * <p>rating에는 붙이지 않는다 — ReviewService의 MIN_RATING/MAX_RATING이 판정 주체다(§2 결정 3).
 */
public record ReviewCreateRequest(
        @NotNull Long goodsNo,
        int rating,
        @NotBlank @Size(max = 2000) String content) {   // review.content VARCHAR(2000)
}
