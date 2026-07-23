package com.beautyboy.member;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.member.dto.MemberResponse;
import com.beautyboy.member.dto.SignupRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        memberRepository.findByEmail(request.email()).ifPresent(m -> {
            throw new BusinessException(ErrorCode.MEMBER_EMAIL_DUPLICATED);
        });

        Member member = new Member(request.email(), passwordEncoder.encode(request.password()), request.nickname());

        if (hasProfileData(request)) {
            MemberProfile profile = new MemberProfile(request.skinType(), request.concerns(), request.ageBand());
            member.assignProfile(profile);
        }

        Member saved = memberRepository.save(member);
        return MemberResponse.from(saved);
    }

    private boolean hasProfileData(SignupRequest request) {
        return request.skinType() != null
                || (request.concerns() != null && !request.concerns().isEmpty())
                || request.ageBand() != null;
    }
}
