package com.beautyboy.qna.dto;

public record QnaCreateRequest(Long goodsNo, String question, boolean isSecret) {
}
