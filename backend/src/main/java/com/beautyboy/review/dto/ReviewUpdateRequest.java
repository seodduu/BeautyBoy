package com.beautyboy.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 리뷰 수정 요청. goodsNo가 없는 것이 사양이다 — 리뷰가 어느 상품의 것인지는
 * 저장된 사실이고, 요청이 바꿀 수 있는 값이 아니다(넘겨받으면 "다른 상품으로 옮기기"가
 * 열린다). skinTypeSnapshot도 없다 — 작성 시점의 사실이므로 수정 대상이 아니다(§2.4).
 */
public record ReviewUpdateRequest(
        int rating,
        @NotBlank @Size(max = 2000) String content) {   // review.content VARCHAR(2000)
}
