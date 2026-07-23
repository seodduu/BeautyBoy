package com.beautyboy.common;

import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SecurityConfig는 전역 인가 경계를 가진다(모든 요청 인증 필요).
// 이 테스트는 GlobalExceptionHandler의 예외 변환 로직만 검증하는 것이 목적이므로,
// 실제 보안 정책과 무관한 임시 TestController 경로(/test/dup, /test/validate)에 대해
// 서블릿 필터체인을 비활성화한다. 프로덕션 SecurityConfig는 변경하지 않는다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 비즈니스_예외는_에러코드와_상태로_변환된다() throws Exception {
        mockMvc.perform(get("/test/dup"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_EMAIL_DUPLICATED"));
    }

    @Test
    void 유효성_검증_실패는_400과_INVALID_INPUT을_반환한다() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/dup")
        public void dup() {
            throw new BusinessException(ErrorCode.MEMBER_EMAIL_DUPLICATED);
        }

        @PostMapping("/test/validate")
        public void validate(@jakarta.validation.Valid @RequestBody TestRequest request) {
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
