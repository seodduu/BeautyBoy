package com.beautyboy.compat.dto;

import java.util.List;

/**
 * 하나의 분류쌍 판정. goodsNos는 이 판정에 기여한 상품들(A 또는 B 분류를 가진 것), 오름차순 정렬.
 */
public record CompatFinding(
        String verdict,               // CONFLICT|CAUTION|SYNERGY
        String categoryA, String categoryB,
        String reason,
        List<Long> goodsNos
) {
}
