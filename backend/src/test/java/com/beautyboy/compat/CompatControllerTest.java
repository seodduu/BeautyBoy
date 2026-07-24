package com.beautyboy.compat;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.compat.dto.CompatCheckResponse;
import com.beautyboy.compat.dto.CompatFinding;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompatControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    CompatService compatService;

    @Test
    void 인증없이_궁합을_확인하면_200과_판정을_반환한다() throws Exception {
        CompatCheckResponse stub = new CompatCheckResponse("CONFLICT", List.of(
                new CompatFinding("CONFLICT", "AHA", "RETINOID", "자극 중첩", List.of(1L, 2L))));
        given(compatService.check(any())).willReturn(stub);

        mockMvc.perform(post("/api/v1/compat/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goodsNos\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.overall").value("CONFLICT"))
                .andExpect(jsonPath("$.data.findings.length()").value(1))
                .andExpect(jsonPath("$.data.findings[0].verdict").value("CONFLICT"))
                .andExpect(jsonPath("$.data.findings[0].goodsNos[0]").value(1));
    }

    @Test
    void 빈_선택은_400을_반환한다() throws Exception {
        given(compatService.check(any()))
                .willThrow(new BusinessException(ErrorCode.COMPAT_EMPTY_SELECTION));

        mockMvc.perform(post("/api/v1/compat/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goodsNos\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
