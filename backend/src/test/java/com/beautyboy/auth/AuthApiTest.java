package com.beautyboy.auth;

import com.beautyboy.member.MemberService;
import com.beautyboy.member.dto.SignupRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MemberService memberService;

    @BeforeEach
    void 회원가입() {
        memberService.signup(new SignupRequest("auth@b.com", "pw123456", "인증맨", null, null, null));
    }

    @Test
    void 인증없이_보호된_경로에_접근하면_401을_반환한다() throws Exception {
        // members/me는 Task 6 소유 — 아직 존재하지 않는 보호 경로지만,
        // 필터체인이 deny-by-default로 동작한다면 404가 아니라 401이어야 한다.
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증없이_보호된_경로에_접근하면_공통_에러_계약_형태의_바디를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다"));
    }

    @Test
    void 로그인하면_액세스토큰과_리프레시_쿠키를_내려준다() throws Exception {
        MvcResult result = login("auth@b.com", "pw123456");

        assertThat(result.getResponse().getContentAsString()).contains("accessToken");
        Cookie refreshCookie = result.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.getPath()).isEqualTo("/api/v1/auth");
    }

    @Test
    void 로그인_비밀번호가_틀리면_401을_반환한다() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequestFixture("auth@b.com", "wrongpw12"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 리프레시하면_새_액세스토큰과_새_리프레시_쿠키를_받고_기존_쿠키는_재사용시_401이다() throws Exception {
        MvcResult loginResult = login("auth@b.com", "pw123456");
        Cookie oldRefreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(oldRefreshCookie).isNotNull();

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh").cookie(oldRefreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andReturn();

        Cookie newRefreshCookie = refreshResult.getResponse().getCookie("refresh_token");
        assertThat(newRefreshCookie).isNotNull();
        assertThat(newRefreshCookie.getValue()).isNotEqualTo(oldRefreshCookie.getValue());

        // 재사용된 구 리프레시 토큰은 이미 폐기되었으므로 401
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(oldRefreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그아웃하면_리프레시_쿠키가_만료되고_재사용시_401이다() throws Exception {
        MvcResult loginResult = login("auth@b.com", "pw123456");
        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();

        mockMvc.perform(post("/api/v1/auth/logout").cookie(refreshCookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    private MvcResult login(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequestFixture(email, password));
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
    }

    private record LoginRequestFixture(String email, String password) {
    }
}
