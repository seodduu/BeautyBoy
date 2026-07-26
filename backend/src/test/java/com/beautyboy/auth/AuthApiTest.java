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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

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

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    private Long 회원_id;

    @BeforeEach
    void 회원가입() {
        회원_id = memberService.signup(new SignupRequest("auth@b.com", "pw123456", "인증맨", null, null, null)).id();
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
    void 리프레시_응답에_회원_정보가_포함된다() throws Exception {
        // GET /members/me 새로고침 시 admin 등 role 기반 라우팅 가드가 즉시 판정할 수 있도록
        // 리프레시 응답이 accessToken뿐 아니라 member(GET /members/me와 같은 형태)도 함께 실어야 한다.
        MvcResult loginResult = login("auth@b.com", "pw123456");
        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.member.email").value("auth@b.com"))
                .andExpect(jsonPath("$.data.member.nickname").value("인증맨"))
                .andExpect(jsonPath("$.data.member.role").value("USER"));
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

    @Test
    void 만료된_리프레시_토큰으로_리프레시하면_401이다() throws Exception {
        // 왜 이 테스트가 필요한가 (Task 4-16a): 다른 401 케이스(재사용·로그아웃 후)는 행이
        // '없어서' 401이라 만료 분기를 한 번도 지나지 않는다. 행이 '있는데' 만료인 경우만
        // AuthService.refresh()의 만료 검사를 실제로 밟는다 — 브리프 불변식 (iv)의 회귀 방어.
        //
        // 이 테스트가 방어하지 '못하는' 것도 적어둔다: 만료 검사가 소유권 주장(조건부 삭제)보다
        // 앞에 있어야 한다는 '순서'는 여기서 검증되지 않는다. 단일 요청에서는 삭제가 항상 1행을
        // 지워 409가 나지 않으므로 순서를 뒤집어도 이 테스트는 통과한다(변이 테스트로 확인함).
        // 순서가 틀렸을 때 실제로 깨지는 것은 '동시 + 만료' 조합뿐인데, 이 클래스는 @Transactional
        // 단일 스레드라 그 조합을 만들 수 없다. 그 위험은 리포트 우려 항목으로 남긴다.
        String 만료된_raw_토큰 = UUID.randomUUID().toString();
        refreshTokenRepository.save(new RefreshToken(
                회원_id, sha256Hex(만료된_raw_토큰), LocalDateTime.now().minusDays(1)));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", 만료된_raw_토큰)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    /** AuthService가 토큰을 저장하는 방식과 같은 해시(SHA-256 hex) — 만료 행을 직접 심기 위해 필요하다. */
    private String sha256Hex(String rawToken) throws Exception {
        byte[] hashed = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashed);
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
