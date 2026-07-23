package com.beautyboy.member;

import com.beautyboy.config.SecurityConfig;
import com.beautyboy.member.dto.SignupRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class MemberControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    MemberService memberService;

    @Test
    void 이메일_형식이_잘못되면_400을_반환한다() throws Exception {
        String body = objectMapper.writeValueAsString(
                new SignupRequest("invalid-email", "pw123456", "민수", null, null, null));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 비밀번호가_8자_미만이면_400을_반환한다() throws Exception {
        String body = objectMapper.writeValueAsString(
                new SignupRequest("a@b.com", "short", "민수", null, null, null));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
