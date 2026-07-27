package com.beautyboy.compat;

import java.util.stream.Collectors;
import java.util.stream.LongStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
 * 궁합 게이트 API(설계 §3.3)를 실 시드 위에서 검증한다. 판정은 상품→성분분류 매핑과 규칙 시드
 * 양쪽이 실려 있어야 나오는데, H2 프로필은 Flyway를 끄고 create-drop으로 도는 탓에 두 테이블이
 * 비어 있어 "AHA 토너 기준 레티노이드가 CONFLICT다"를 물을 수 없다 — 그래서 통합 테스트다.
 *
 * <p>실행: {@code ./gradlew integrationTest}
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CompatVerdictsApiIT {

    private static final String PATH = "/api/v1/compat/verdicts";

    /** 컨트롤러의 MAX_CANDIDATES와 같은 값. 상한을 넘긴 요청이 잘리는지 확인하는 데만 쓴다. */
    private static final int MAX_CANDIDATES = 50;

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

    /**
     * 게이트의 본론. 응답 맵의 키는 JSON에서 문자열이 된다(프론트 계약: {@code Record<string, string>}).
     */
    @Test
    void AHA_토너_goods_2_기준_RETINOID와_BHA_세럼이_CONFLICT다() throws Exception {
        mockMvc.perform(get(PATH).param("base", "2").param("candidates", "159,190,4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.159").value("CONFLICT"))
                .andExpect(jsonPath("$.data.190").value("CONFLICT"))
                .andExpect(jsonPath("$.data.4").value(org.hamcrest.Matchers.not("CONFLICT")));
    }

    @Test
    void 후보가_MAX_CANDIDATES를_넘으면_앞_50개만_판정된다() throws Exception {
        String 초과후보 = LongStream.rangeClosed(1, MAX_CANDIDATES + 10)
                .mapToObj(Long::toString)
                .collect(Collectors.joining(","));

        MvcResult result = mockMvc.perform(get(PATH).param("base", "2").param("candidates", 초과후보))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.size()).isEqualTo(MAX_CANDIDATES);
        assertThat(data.has("50")).isTrue();
        assertThat(data.has("51")).isFalse();
    }

    /** SecurityConfig에 permitAll을 넣지 않았다면 401이 나온다. 메인은 비로그인도 조합을 본다. */
    @Test
    void 비로그인으로도_200이다() throws Exception {
        mockMvc.perform(get(PATH).param("base", "2").param("candidates", "159"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    /**
     * 실동작 고정: 존재하지 않는 base는 404가 아니라 <b>200 + 후보 전량 "OK"</b>다.
     * worstVerdicts가 base의 성분분류를 빈 집합으로 보고(getOrDefault) 걸릴 규칙이 없어 "OK"로
     * 떨어지기 때문이다. 새 분기를 만들지 않기로 한 결정(계획 Task 2-1)의 귀결이며, 클라이언트
     * 입장에서도 "게이트가 아무도 막지 않는다"는 안전한 실패다.
     */
    @Test
    void 존재하지_않는_base의_동작을_고정한다() throws Exception {
        mockMvc.perform(get(PATH).param("base", "99999999").param("candidates", "159,190"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.159").value("OK"))
                .andExpect(jsonPath("$.data.190").value("OK"));
    }
}
