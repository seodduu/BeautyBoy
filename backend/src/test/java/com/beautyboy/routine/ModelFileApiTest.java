package com.beautyboy.routine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ModelFileApiTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 상품_임베딩을_내려준다() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/model/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").exists())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo("\"v3.0\"");
    }

    @Test
    void 사용자_탑을_내려준다() throws Exception {
        mockMvc.perform(get("/api/v1/model/user-tower"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layers.length()").value(2));
    }

    @Test
    void 본문에_봉투가_없다() throws Exception {
        mockMvc.perform(get("/api/v1/model/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void 같은_ETag면_304다() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/v1/model/items"))
                .andExpect(status().isOk())
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);

        MvcResult second = mockMvc.perform(get("/api/v1/model/items")
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andReturn();

        assertThat(second.getResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    void 따옴표없는_ETag도_304다() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/v1/model/items"))
                .andExpect(status().isOk())
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        String unquoted = etag.substring(1, etag.length() - 1);

        mockMvc.perform(get("/api/v1/model/items")
                        .header(HttpHeaders.IF_NONE_MATCH, unquoted))
                .andExpect(status().isNotModified());
    }

    @Test
    void 다른_ETag면_200이다() throws Exception {
        mockMvc.perform(get("/api/v1/model/items")
                        .header(HttpHeaders.IF_NONE_MATCH, "다른버전"))
                .andExpect(status().isOk());
    }

    @Test
    void 비로그인도_받을_수_있다() throws Exception {
        mockMvc.perform(get("/api/v1/model/items"))
                .andExpect(status().isOk());
    }

    @Test
    void 두_파일_version이_같다() throws Exception {
        MvcResult items = mockMvc.perform(get("/api/v1/model/items"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult userTower = mockMvc.perform(get("/api/v1/model/user-tower"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(items.getResponse().getHeader(HttpHeaders.ETAG))
                .isEqualTo(userTower.getResponse().getHeader(HttpHeaders.ETAG));
    }
}
