package com.beautyboy.qna.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QnaCreateRequest(
        @NotNull Long goodsNo,
        @NotBlank @Size(max = 1000) String question,    // qna.question VARCHAR(1000)
        boolean isSecret) {
}
