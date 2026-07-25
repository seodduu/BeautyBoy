package com.beautyboy.member.dto;

import com.beautyboy.member.Member;
import com.beautyboy.member.MemberProfile;

import java.util.List;

/**
 * GET /members/me 응답. {@code role}은 Task 4-14에서 append했다 — admin 화면 라우팅 가드가
 * 이 값을 프론트 authStore에 보관해 판정한다(JWT를 프론트에서 디코드하지 않는다는 project law
 * 때문에 role의 유일한 출처는 이 응답이다). 기존 필드 순서는 그대로 두고 끝에만 추가한다.
 */
public record MemberMeResponse(
        Long id,
        String email,
        String nickname,
        String grade,
        String skinType,
        List<String> concerns,
        String ageBand,
        String role
) {

    public static MemberMeResponse from(Member member) {
        MemberProfile profile = member.getProfile();
        return new MemberMeResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getGrade().name(),
                profile != null ? profile.getSkinType() : null,
                profile != null ? profile.getConcerns() : List.of(),
                profile != null ? profile.getAgeBand() : null,
                member.getRole().name()
        );
    }
}
