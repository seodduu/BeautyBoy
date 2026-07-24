package com.beautyboy.catalog;

import com.beautyboy.auth.TokenProvider;
import com.beautyboy.config.MethodSecurityConfig;
import com.beautyboy.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인가 슬라이스 테스트 — /admin/goods는 ROLE_ADMIN만 통과해야 한다.
 * MethodSecurityConfig를 같이 import해야 @PreAuthorize가 이 슬라이스에서도 실제로 평가된다.
 */
@WebMvcTest(AdminGoodsController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class})
@ActiveProfiles("test")
class AdminGoodsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminGoodsService adminGoodsService;

    // SecurityConfig가 TokenProvider를 필수 생성자 파라미터로 요구하므로 슬라이스 컨텍스트에도 스텁이 필요하다.
    @MockitoBean
    TokenProvider tokenProvider;

    @Test
    @WithMockUser(roles = "USER")
    void 일반_회원이_관리자_API를_부르면_403이다() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/goods/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자는_상품을_삭제할_수_있다() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/goods/1"))
                .andExpect(status().isOk());
    }

    @Test
    void 인증없이_관리자_API를_부르면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/goods"))
                .andExpect(status().isUnauthorized());
    }
}
