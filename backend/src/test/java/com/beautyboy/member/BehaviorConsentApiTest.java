package com.beautyboy.member;

import com.beautyboy.member.dto.SignupRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BehaviorConsentApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MemberService memberService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    EntityManager entityManager;

    String tokenA;
    Long memberIdA;

    @BeforeEach
    void 회원_가입_후_로그인() throws Exception {
        memberService.signup(new SignupRequest("consent-a@b.com", "pw123456", "동의맨", null, null, null));

        tokenA = login("consent-a@b.com", "pw123456");
        memberIdA = memberRepository.findByEmail("consent-a@b.com").orElseThrow().getId();
    }

    @Test
    void 동의를_켜면_true와_시각이_남는다() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me/behavior-consent")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content(consentBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.behaviorConsent").value(true));

        MemberProfile 프로필 = 재조회();
        assertThat(프로필.getBehaviorConsentAt()).isNotNull();
    }

    @Test
    void 동의를_끄면_false지만_시각은_갱신된다() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me/behavior-consent")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content(consentBody(true)))
                .andExpect(status().isOk());
        LocalDateTime 첫_시각 = 재조회().getBehaviorConsentAt();

        mockMvc.perform(patch("/api/v1/members/me/behavior-consent")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content(consentBody(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.behaviorConsent").value(false));

        LocalDateTime 둘째_시각 = 재조회().getBehaviorConsentAt();
        assertThat(둘째_시각).isAfterOrEqualTo(첫_시각);
    }

    @Test
    void 프로필이_없는_회원도_동의를_켤_수_있다() throws Exception {
        memberService.signup(new SignupRequest("noprofile@b.com", "pw123456", "노프로필", null, null, null));
        String token = login("noprofile@b.com", "pw123456");

        mockMvc.perform(patch("/api/v1/members/me/behavior-consent")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(consentBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.behaviorConsent").value(true));
    }

    @Test
    void me응답에_동의상태가_실린다() throws Exception {
        mockMvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.behaviorConsent").exists());
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me/behavior-consent")
                        .contentType("application/json")
                        .content(consentBody(true)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consent가_없으면_400이다() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me/behavior-consent")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private MemberProfile 재조회() {
        entityManager.flush();
        entityManager.clear();
        return memberRepository.findById(memberIdA).orElseThrow().getProfile();
    }

    private String consentBody(boolean consent) throws Exception {
        return objectMapper.writeValueAsString(new ConsentRequestFixture(consent));
    }

    private String login(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequestFixture(email, password));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("accessToken").asText();
    }

    private record LoginRequestFixture(String email, String password) {
    }

    private record ConsentRequestFixture(Boolean consent) {
    }
}
