package com.beautyboy.qna.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminQnaAnswerRequest(@NotBlank @Size(max = 2000) String answer) {
}
