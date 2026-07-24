package com.beautyboy.qna;

import com.beautyboy.auth.TokenProvider;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.config.MethodSecurityConfig;
import com.beautyboy.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인가 슬라이스 테스트 — /admin/qna/{qnaId}/answer는 ROLE_ADMIN만 통과해야 한다.
 */
@WebMvcTest(AdminQnaController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class})
@ActiveProfiles("test")
class AdminQnaControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    QnaService qnaService;
    @MockitoBean
    TokenProvider tokenProvider;

    private String 답변_바디() throws Exception {
        return objectMapper.writeValueAsString(Map.of("answer", "네, 재고 있습니다."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void 일반_회원이_답변_등록을_부르면_403이다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/qna/1/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(답변_바디()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자는_문의에_답변을_등록할_수_있다() throws Exception {
        willDoNothing().given(qnaService).answer(1L, "네, 재고 있습니다.");

        mockMvc.perform(post("/api/v1/admin/qna/1/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(답변_바디()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 이미_답변된_문의면_409다() throws Exception {
        willThrow(new BusinessException(ErrorCode.QNA_ALREADY_ANSWERED))
                .given(qnaService).answer(1L, "네, 재고 있습니다.");

        mockMvc.perform(post("/api/v1/admin/qna/1/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(답변_바디()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QNA_ALREADY_ANSWERED"));
    }
}
