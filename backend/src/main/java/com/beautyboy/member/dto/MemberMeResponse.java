package com.beautyboy.member.dto;

import com.beautyboy.member.Member;
import com.beautyboy.member.MemberProfile;

import java.util.List;

public record MemberMeResponse(
        Long id,
        String email,
        String nickname,
        String grade,
        String skinType,
        List<String> concerns,
        String ageBand
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
                profile != null ? profile.getAgeBand() : null
        );
    }
}
