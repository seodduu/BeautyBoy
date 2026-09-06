package com.beautyboy.member.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 행동 로그 수집 요청 본문. {@code kind}는 문자열로 받아 서비스에서
 * {@link com.beautyboy.member.BehaviorKind}로 변환한다. 잘못된 값이면 400이어야 하는데,
 * 컨트롤러 파라미터를 곧바로 enum으로 받으면 Jackson 역직렬화 단계에서
 * {@code HttpMessageNotReadableException}으로 죽어 400은 맞지만 별도 처리 없이도 이미
 * {@code GlobalExceptionHandler}가 잡는다. 여기서는 문자열로 받아 서비스가 명시적으로
 * 검증하게 해 "이상한 kind는 조용히 버리지 않고 400을 낸다"는 계약을 코드로 드러낸다.
 */
public record BehaviorEventRequest(@NotNull Long goodsNo, @NotNull String kind) {
}
