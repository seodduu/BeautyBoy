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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
@Import({SecurityErrorHandlingTest.BoomController.class, SecurityErrorHandlingTest.MethodSecurityTestConfig.class})
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

    @Test
    void 깨진_JSON_바디는_500이_아니라_400_INVALID_INPUT을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content("{\"email\": \"a@b.com\", "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 지원하지_않는_HTTP_메서드는_500이_아니라_405를_반환한다() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me").header("Authorization", "Bearer " + validToken))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void 경로변수_타입이_맞지_않으면_500이_아니라_400_INVALID_INPUT을_반환한다() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me/addresses/abc")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 공개_조회_경로는_무토큰이어도_401이_아니다() throws Exception {
        // 설계 7장 "공개" 목록을 선반영했으므로 무토큰 GET이 인증에서 막히면 안 된다.
        // 아직 컨트롤러가 없어 404가 나오는데, 그것이 곧 "인증은 통과했다"는 증거다.
        for (String path : new String[]{
                "/api/v1/goods/1",
                "/api/v1/search",
                "/api/v1/rankings",
                "/api/v1/routines"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void 카테고리_트리는_구현됐으므로_무토큰이어도_401이_아니라_200이다() throws Exception {
        // Task 1-3에서 컨트롤러가 생기면서 위 플레이스홀더 목록에서 분리됐다.
        // 시드가 없는 테스트 환경에서는 빈 배열을 낸다 — 여기서 검증할 것은 인증 통과(200) 여부뿐이다.
        mockMvc.perform(get("/api/v1/categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void 상품_목록은_구현됐으므로_무토큰이어도_401이_아니라_200이다() throws Exception {
        // Task 1-4에서 컨트롤러가 생기면서 위 플레이스홀더 목록에서 분리됐다.
        // 시드가 없는 테스트 환경에서는 빈 목록을 낸다 — 여기서 검증할 것은 인증 통과(200) 여부뿐이다.
        mockMvc.perform(get("/api/v1/goods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void 공개된_것은_조회뿐이고_쓰기는_여전히_인증이_필요하다() throws Exception {
        // GET /api/v1/reviews는 공개지만 POST는 아니다.
        mockMvc.perform(post("/api/v1/reviews").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/v1/qna").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 공개_목록에_없는_회원_경로는_무토큰이면_401이다() throws Exception {
        // 공개 경로를 선반영하면서 실수로 회원 경로까지 열리지 않았는지 확인한다.
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/members/me/addresses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인가_거부는_캐치올에_먹히지_않고_403을_반환한다() throws Exception {
        // 가장 중요한 회귀 방지선: @PreAuthorize가 던지는 AccessDeniedException을
        // GlobalExceptionHandler의 Exception 캐치올이 가로채면 인가 거부(403)가 500으로 둔갑한다.
        mockMvc.perform(get("/api/v1/test/admin-only").header("Authorization", "Bearer " + validToken))
                .andExpect(status().isForbidden());
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

    /**
     * 프로덕션 SecurityConfig에는 아직 @EnableMethodSecurity가 없다(admin API가 없으므로).
     * 이후 웨이브가 그것을 켰을 때 인가 거부가 403으로 나가는지를 지금 고정해두기 위해
     * 테스트 컨텍스트에서만 메서드 보안을 활성화한다. 프로덕션 설정은 건드리지 않는다.
     */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @RestController
    static class BoomController {

        @GetMapping("/api/v1/test/boom")
        public void boom() {
            throw new IllegalStateException("의도적으로 발생시킨 테스트용 예외");
        }

        /**
         * 일반 USER 토큰으로는 통과할 수 없는 엔드포인트.
         * 이후 웨이브의 admin API가 쓸 @PreAuthorize 경로를 미리 재현한다.
         */
        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/api/v1/test/admin-only")
        public void adminOnly() {
        }
    }
}
