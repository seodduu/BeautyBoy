package com.beautyboy.review.dto;

/** 리뷰 작성 요청. 금액처럼 조작 위험 값이 없다. */
public record ReviewCreateRequest(Long goodsNo, int rating, String content) {
}
