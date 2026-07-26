package com.beautyboy.qna;

import com.beautyboy.auth.TokenProvider;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.common.PageResponse;
import com.beautyboy.config.MethodSecurityConfig;
import com.beautyboy.config.SecurityConfig;
import com.beautyboy.qna.dto.AdminQnaResponse;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    @WithMockUser(roles = "USER")
    void 일반_회원이_문의_목록_조회를_부르면_403이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/qna"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자는_문의_목록을_조회할_수_있다() throws Exception {
        given(qnaService.adminList(0)).willReturn(PageResponse.of(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/admin/qna"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 문의_목록_응답의_isSecret_JSON_키가_고정된다() throws Exception {
        // record + Jackson의 is-접두 boolean 컴포넌트는 버전에 따라 "secret"으로 직렬화된 이력이
        // 있다(POJO getter 관례와 record 컴포넌트 관례가 다르다). 프론트가 item.isSecret을 읽으므로
        // 이 키가 흔들리면 목은 통과해도 실 서버 응답에서만 깨지는 사각지대가 생긴다.
        AdminQnaResponse 비밀글 = new AdminQnaResponse(
                1L, 10L, "재고 있나요?", true, "WAITING", LocalDateTime.now());
        given(qnaService.adminList(0)).willReturn(PageResponse.of(List.of(비밀글), 0, 20, 1));

        mockMvc.perform(get("/api/v1/admin/qna"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].isSecret").value(true));
    }
}
