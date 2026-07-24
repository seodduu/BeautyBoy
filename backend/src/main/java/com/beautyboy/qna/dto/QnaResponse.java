package com.beautyboy.qna.dto;

import java.time.LocalDateTime;

/**
 * Q&A 목록 표시용. {@code question}은 마스킹된 값이 들어갈 수 있다
 * — 비밀글이고 조회자가 작성자 본인이 아니면 "비밀글입니다."로 대체된다(QnaService.visibleQuestion).
 */
public record QnaResponse(
        Long qnaId,
        String question,
        boolean isSecret,
        String status,
        LocalDateTime createdAt
) {
}
