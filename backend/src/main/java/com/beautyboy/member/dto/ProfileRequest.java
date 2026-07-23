package com.beautyboy.member.dto;

import jakarta.validation.constraints.AssertTrue;

import java.util.List;

public record ProfileRequest(
        String skinType,
        List<String> concerns,
        String ageBand
) {

    /**
     * MemberProfile.concerns 컬럼은 VARCHAR(200) — 콤마로 이어붙인 길이가 넘으면
     * DB 예외(500)로 새는 대신 여기서 400 INVALID_INPUT으로 막는다.
     */
    @AssertTrue(message = "관심사항은 합산 200자 이내여야 합니다")
    public boolean isConcernsWithinLimit() {
        if (concerns == null || concerns.isEmpty()) {
            return true;
        }
        return String.join(",", concerns).length() <= 200;
    }
}
