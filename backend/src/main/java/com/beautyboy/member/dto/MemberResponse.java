package com.beautyboy.member.dto;

import com.beautyboy.member.Member;

public record MemberResponse(Long id, String email, String nickname, String grade) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getNickname(), member.getGrade().name());
    }
}
