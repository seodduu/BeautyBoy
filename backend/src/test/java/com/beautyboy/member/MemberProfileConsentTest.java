package com.beautyboy.member;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberProfileConsentTest {

    @Autowired
    MemberRepository memberRepository;

    @PersistenceContext
    EntityManager entityManager;

    private Member 프로필_붙인_회원() {
        Member 회원 = new Member("consent-" + System.nanoTime() + "@beautyboy.test", "hash", "동의맨");
        회원.assignProfile(new MemberProfile("DRY", List.of("moisture"), "20s"));
        return memberRepository.saveAndFlush(회원);
    }

    private MemberProfile 재조회(Long memberId) {
        entityManager.flush();
        entityManager.clear();
        return memberRepository.findById(memberId).orElseThrow().getProfile();
    }

    @Test
    void 새_프로필은_동의_false_시각_null이다() {
        Member 회원 = 프로필_붙인_회원();

        MemberProfile 프로필 = 재조회(회원.getId());

        assertThat(프로필.isBehaviorConsent()).isFalse();
        assertThat(프로필.getBehaviorConsentAt()).isNull();
    }

    @Test
    void 동의를_바꾸면_값과_시각이_함께_바뀐다() {
        Member 회원 = 프로필_붙인_회원();
        LocalDateTime 시각 = LocalDateTime.of(2026, 9, 4, 14, 22);

        회원.getProfile().updateBehaviorConsent(true, 시각);

        MemberProfile 프로필 = 재조회(회원.getId());
        assertThat(프로필.isBehaviorConsent()).isTrue();
        assertThat(프로필.getBehaviorConsentAt()).isEqualTo(시각);
    }

    @Test
    void 같은_값으로_다시_바꿔도_시각은_갱신된다() {
        Member 회원 = 프로필_붙인_회원();
        LocalDateTime 시각1 = LocalDateTime.of(2026, 9, 4, 14, 22);
        LocalDateTime 시각2 = LocalDateTime.of(2026, 9, 5, 9, 0);

        회원.getProfile().updateBehaviorConsent(true, 시각1);
        회원.getProfile().updateBehaviorConsent(true, 시각2);

        MemberProfile 프로필 = 재조회(회원.getId());
        assertThat(프로필.isBehaviorConsent()).isTrue();
        assertThat(프로필.getBehaviorConsentAt()).isEqualTo(시각2);
    }
}
