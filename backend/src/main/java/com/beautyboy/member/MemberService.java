package com.beautyboy.member;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.member.dto.MemberCredentials;
import com.beautyboy.member.dto.MemberMeResponse;
import com.beautyboy.member.dto.MemberResponse;
import com.beautyboy.member.dto.ProfileRequest;
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

    /**
     * auth 도메인이 로그인 시 자격증명을 검증하기 위한 진입점.
     * 이메일 미존재/비밀번호 불일치를 구분하지 않고 동일한 예외를 던진다(정보 노출 방지).
     */
    public MemberCredentials authenticate(String email, String rawPassword) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_LOGIN_FAILED));

        if (!passwordEncoder.matches(rawPassword, member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.MEMBER_LOGIN_FAILED);
        }

        return new MemberCredentials(member.getId(), member.getRole().name());
    }

    /**
     * auth 도메인이 리프레시 로테이션 시 최신 role을 조회하기 위한 진입점.
     */
    public MemberCredentials getCredentials(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return new MemberCredentials(member.getId(), member.getRole().name());
    }

    @Transactional(readOnly = true)
    public MemberMeResponse getMe(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return MemberMeResponse.from(member);
    }

    /**
     * PUT 시맨틱에 맞춰 프로필을 통째로 교체(upsert)한다. 기존 값과의 병합(partial update)은 하지 않는다.
     * MemberProfile은 값객체 성격(세터 없음)이라 Member의 cascade(ALL)+orphanRemoval을 이용해
     * 기존 프로필을 고아 제거하고 새 프로필을 그 자리에 대입한다.
     */
    @Transactional
    public MemberMeResponse updateProfile(Long memberId, ProfileRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        MemberProfile profile = new MemberProfile(request.skinType(), request.concerns(), request.ageBand());
        member.assignProfile(profile);

        return MemberMeResponse.from(member);
    }

    private boolean hasProfileData(SignupRequest request) {
        return request.skinType() != null
                || (request.concerns() != null && !request.concerns().isEmpty())
                || request.ageBand() != null;
    }
}
