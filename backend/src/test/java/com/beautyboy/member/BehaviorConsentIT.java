package com.beautyboy.member;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 행동 로그 동의를 실 시드 회원 위에서 검증한다. {@code db/seed}(V64 시드 회원 3명)는 기본
 * Flyway 경로가 아니라 {@code demo} 프로필에서만 로드되므로(application.yml 주석), 여기서는
 * Flyway locations에 {@code classpath:db/seed}를 직접 더해 시드 회원이 실제로 들어온 상태에서
 * 잰다. Wave A의 {@link V3SchemaIT#시드_회원의_동의_기본값은_0이다()}는 이 경로를 더하지 않아
 * {@code member_profile}에 행이 한 개도 없는 채로 {@code COUNT(*) = 0}을 확인한, 아무것도
 * 검증하지 못한 공허한 단언이었다. {@code V3SchemaIT}는 Wave A 산출물이고 이 태스크의 Files
 * 밖이라 고치지 않는다.
 *
 * <p>Docker가 필요하므로 {@code @Tag("integration")}이다. 실행 명령은 {@code ./gradlew integrationTest}.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
class BehaviorConsentIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @DynamicPropertySource
    static void 실_MySQL로_바꿔_끼운다(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/seed");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 기존_시드회원의_동의_기본값은_0이다() {
        Integer 전체_행 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member_profile", Integer.class);
        Integer 동의한_행 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_profile WHERE behavior_consent <> 0", Integer.class);

        assertThat(전체_행).isGreaterThanOrEqualTo(2);
        assertThat(동의한_행).isZero();
    }

    @Test
    void 동의_0_회원의_이벤트는_한_행도_안_남는다() throws Exception {
        String token = 로그인("dry@beautyboy.dev", "seed1234!");
        Long goodsId = 카탈로그_상품_id();

        for (int i = 0; i < 3; i++) {
            이벤트요청(token, goodsId, "VIEW").andExpect(status().isNoContent());
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM behavior_event WHERE member_id = 2", Integer.class)).isZero();
    }

    @Test
    void 동의_1_회원의_이벤트는_남는다() throws Exception {
        String token = 로그인("dry@beautyboy.dev", "seed1234!");
        Long goodsId = 카탈로그_상품_id();

        mockMvc.perform(patch("/api/v1/members/me/behavior-consent")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("consent", true))))
                .andExpect(status().isOk());

        for (int i = 0; i < 3; i++) {
            이벤트요청(token, goodsId, "VIEW").andExpect(status().isNoContent());
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM behavior_event WHERE member_id = 2", Integer.class)).isEqualTo(3);
    }

    @Test
    void 외래키가_없는_goods를_거부한다() throws Exception {
        String token = 로그인("dry@beautyboy.dev", "seed1234!");

        mockMvc.perform(patch("/api/v1/members/me/behavior-consent")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("consent", true))))
                .andExpect(status().isOk());

        이벤트요청(token, 999999999L, "VIEW").andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM behavior_event WHERE member_id = 2", Integer.class)).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions 이벤트요청(String token, Long goodsNo, String kind)
            throws Exception {
        return mockMvc.perform(post("/api/v1/events")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("goodsNo", goodsNo, "kind", kind))));
    }

    private Long 카탈로그_상품_id() {
        return jdbcTemplate.queryForObject("SELECT id FROM goods ORDER BY id LIMIT 1", Long.class);
    }

    private String 로그인(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", email, "password", password));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("accessToken").asText();
    }
}
