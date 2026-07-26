package com.beautyboy.routine;

import com.beautyboy.auth.TokenProvider;
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

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인가 슬라이스 테스트 — /admin/routines는 ROLE_ADMIN만 통과해야 한다.
 * MethodSecurityConfig를 같이 import해야 @PreAuthorize가 이 슬라이스에서도 실제로 평가된다.
 * (AdminGoodsControllerTest/AdminQnaControllerTest와 같은 패턴.)
 */
@WebMvcTest(AdminRoutineController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class})
@ActiveProfiles("test")
class AdminRoutineControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AdminRoutineService adminRoutineService;

    // SecurityConfig가 TokenProvider를 필수 생성자 파라미터로 요구하므로 슬라이스 컨텍스트에도 스텁이 필요하다.
    @MockitoBean
    TokenProvider tokenProvider;

    private String 교체_바디() throws Exception {
        return objectMapper.writeValueAsString(Map.of("goodsNos", List.of(1, 2)));
    }

    @Test
    @WithMockUser(roles = "USER")
    void 일반_회원이_루틴_목록_조회를_부르면_403이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/routines"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자는_루틴_목록을_조회할_수_있다() throws Exception {
        // adminRoutineService.list()는 Mockito 기본 스텁(빈 List)으로 충분 — 여기서는 인가 통과만 본다.
        mockMvc.perform(get("/api/v1/admin/routines"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void 일반_회원이_단계_추천상품_교체를_부르면_403이다() throws Exception {
        mockMvc.perform(put("/api/v1/admin/routines/1/steps/1/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(교체_바디()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자는_단계_추천상품을_교체할_수_있다() throws Exception {
        mockMvc.perform(put("/api/v1/admin/routines/1/steps/1/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(교체_바디()))
                .andExpect(status().isOk());
    }
}
