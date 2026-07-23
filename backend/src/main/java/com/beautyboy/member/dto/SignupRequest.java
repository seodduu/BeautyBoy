package com.beautyboy.member.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 인증 없이 누구나 호출 가능한 회원가입 엔드포인트의 요청 DTO.
 * email/nickname/skinType/concerns/ageBand는 ProfileRequest와 같은 컬럼(member, member_profile)에
 * 저장되므로, 스키마 컬럼 길이(email VARCHAR(100), nickname VARCHAR(30), skin_type VARCHAR(20),
 * concerns 합산 VARCHAR(200), age_band VARCHAR(10))를 넘으면 DB 예외(500)로 새기 전에
 * 여기서 400 INVALID_INPUT으로 막는다.
 * (요청 JSON은 평면 구조 계약이라 ProfileRequest를 컴포지션하지 않고 검증 규칙만 중복시켰다.)
 */
public record SignupRequest(
        @NotBlank @Email @Size(max = 100, message = "이메일은 100자 이내여야 합니다") String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank @Size(max = 30, message = "닉네임은 30자 이내여야 합니다") String nickname,
        @Size(max = 20, message = "피부타입은 20자 이내여야 합니다")
        String skinType,
        List<String> concerns,
        @Size(max = 10, message = "연령대는 10자 이내여야 합니다")
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
