package com.beautyboy.routine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 규칙 배포 API(설계 §5.2)를 실 시드 위에서 검증한다. H2 프로필은 Flyway를 끄고 create-drop으로
 * 도는 탓에 두 규칙 테이블이 비어 있어 "V75의 12행이 실린다"를 물을 수 없다 — 그래서 통합 테스트다.
 *
 * <p>비로그인(토큰 없음) 호출이 200인지도 여기서 같이 확인된다. SecurityConfig에 permitAll을
 * 넣지 않았다면 401이 나와 전 테스트가 깨진다.
 *
 * <p>실행: {@code ./gradlew integrationTest}
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class FlowRuleApiIT {

    private static final String PATH = "/api/v1/routine/flow-rules";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @DynamicPropertySource
    static void 실_MySQL로_바꿔_끼운다(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ConcernTargetRuleRepository concernRuleRepository;

    @Test
    void flow_rules_응답에_flowRules_12행과_concernRules_전량이_실린다() throws Exception {
        long concernCount = concernRuleRepository.count();

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.flowRules.length()").value(12))
                .andExpect(jsonPath("$.data.concernRules.length()").value((int) concernCount))
                .andExpect(jsonPath("$.data.flowRules[0].edgeKind").isNotEmpty())
                .andExpect(jsonPath("$.data.flowRules[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.data.concernRules[0].concernTagSlug").isNotEmpty())
                .andExpect(jsonPath("$.data.concernRules[0].reason").isNotEmpty());
    }

    /**
     * ETag 헤더는 RFC 9110이 요구하는 따옴표에 싸여 나간다(Spring이 감싼다). 클라이언트가 보는
     * 실체가 같은 값인지가 요점이므로 따옴표만 벗겨 비교한다 — 따옴표를 없애려고 헤더를 비표준으로
     * 만드는 것은 본말전도다. 대신 따옴표 안의 값이 body.version과 정확히 같아야 한다.
     */
    @Test
    void 응답_ETag_헤더와_body_version이_같은_값이다() throws Exception {
        MvcResult result = mockMvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.ETAG))
                .isNotBlank()
                .isEqualTo("\"" + 버전(result) + "\"");
    }

    @Test
    void 같은_ETag로_If_None_Match_재요청하면_304이고_본문이_비어_있다() throws Exception {
        String version = 버전(mockMvc.perform(get(PATH)).andReturn());

        MvcResult notModified = mockMvc.perform(get(PATH).header(HttpHeaders.IF_NONE_MATCH, version))
                .andExpect(status().isNotModified())
                .andReturn();

        assertThat(notModified.getResponse().getContentAsString()).isEmpty();
    }

    /** 브라우저·표준 HTTP 클라이언트는 받은 ETag를 따옴표째 되돌려준다. 그 경로도 304여야 한다. */
    @Test
    void 따옴표째_돌려준_ETag로도_304가_나온다() throws Exception {
        MvcResult first = mockMvc.perform(get(PATH)).andReturn();
        String etagHeader = first.getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get(PATH).header(HttpHeaders.IF_NONE_MATCH, etagHeader))
                .andExpect(status().isNotModified());
    }

    @Test
    void 다른_ETag로_요청하면_200이고_본문이_실린다() throws Exception {
        mockMvc.perform(get(PATH).header(HttpHeaders.IF_NONE_MATCH, "0000000000000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flowRules.length()").value(12));
    }

    /**
     * 기동 시 1회 계산·캐싱의 증거. 요청마다 DB를 다시 읽어 해시를 만든다면 정렬이 빠졌을 때
     * 두 값이 갈릴 수 있고, 애초에 이 엔드포인트가 없애려던 비용이 그대로 남는다.
     */
    @Test
    void 두_번_호출해도_version이_같다() throws Exception {
        String first = 버전(mockMvc.perform(get(PATH)).andReturn());
        String second = 버전(mockMvc.perform(get(PATH)).andReturn());

        assertThat(first).isNotBlank().isEqualTo(second);
    }

    private String 버전(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("version").asText();
    }
}
