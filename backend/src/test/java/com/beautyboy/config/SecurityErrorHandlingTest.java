package com.beautyboy.config;

import com.beautyboy.member.MemberService;
import com.beautyboy.member.dto.SignupRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결함2 재현/회귀 테스트: 미처리 예외가 컨테이너의 /error 포워딩을 거쳐
 * SecurityConfig의 anyRequest().authenticated()에 걸리면서 500이 401로 둔갑하던 문제.
 * 실제 SecurityFilterChain을 그대로 태워서(addFilters 기본값=true) 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityErrorHandlingTest.BoomController.class)
@Transactional
class SecurityErrorHandlingTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MemberService memberService;

    String validToken;

    @BeforeEach
    void 회원가입_후_로그인() throws Exception {
        memberService.signup(new SignupRequest("error-test@b.com", "pw123456", "에러테스트", null, null, null));
        validToken = login("error-test@b.com", "pw123456");
    }

    @Test
    void 유효한_토큰으로_보낸_요청에서_서버_내부_오류가_나면_500과_ErrorResponse를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/test/boom").header("Authorization", "Bearer " + validToken))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void 유효한_토큰으로_존재하지_않는_경로에_접근하면_401이_아니라_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/no-such-path").header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 무토큰으로_보호된_경로에_접근하면_여전히_401과_UNAUTHORIZED를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/test/boom"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 무토큰으로_존재하지_않는_경로에_접근해도_401을_반환한다() throws Exception {
        // 무토큰 상태에서는 컨트롤러에 도달하기도 전에 인증 진입점이 걸리므로
        // 경로 존재 여부와 무관하게 401이 나오는 것이 기존 보안 자세(deny-by-default)다.
        mockMvc.perform(get("/api/v1/no-such-path"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
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

    @RestController
    static class BoomController {

        @GetMapping("/api/v1/test/boom")
        public void boom() {
            throw new IllegalStateException("의도적으로 발생시킨 테스트용 예외");
        }
    }
}
