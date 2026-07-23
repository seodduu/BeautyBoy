package com.beautyboy.member;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.member.dto.SignupRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberServiceTest {

    @Autowired
    MemberService memberService;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void 가입하면_비밀번호가_해시로_저장된다() {
        var res = memberService.signup(new SignupRequest("a@b.com", "pw123456", "민수", "OILY", List.of("TROUBLE"), "20s"));

        var saved = memberRepository.findByEmail("a@b.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("pw123456");
        assertThat(passwordEncoder.matches("pw123456", saved.getPasswordHash())).isTrue();
        assertThat(res.email()).isEqualTo("a@b.com");
        assertThat(res.nickname()).isEqualTo("민수");
        assertThat(res.grade()).isEqualTo("BABY");
    }

    @Test
    void 중복_이메일이면_MEMBER_EMAIL_DUPLICATED() {
        memberService.signup(req("a@b.com"));

        assertThatThrownBy(() -> memberService.signup(req("a@b.com")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_EMAIL_DUPLICATED);
    }

    private SignupRequest req(String email) {
        return new SignupRequest(email, "pw123456", "민수", "OILY", List.of("TROUBLE"), "20s");
    }
}
