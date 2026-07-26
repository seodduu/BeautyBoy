package com.beautyboy.qna.dto;

import java.time.LocalDateTime;

/**
 * admin 문의 목록 표시용. 공개 {@link QnaResponse}와의 차이 둘:
 * (1) goodsNo를 포함한다 — admin 목록은 goodsNo 필터 없이 전체를 훑으므로 어느 상품의
 * 문의인지 알아야 한다(공개 목록은 goodsNo가 이미 쿼리 파라미터라 필요 없었다).
 * (2) question이 마스킹되지 않는다 — admin에게는 비밀글도 본문을 그대로 낸다
 * (QnaService.visibleQuestion의 admin 예외, 근거는 QnaService 참고).
 */
public record AdminQnaResponse(
        Long qnaId,
        Long goodsNo,
        String question,
        boolean isSecret,
        String status,
        LocalDateTime createdAt
) {
}
