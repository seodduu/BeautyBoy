package com.beautyboy.member;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V96·V97 — 행동 로그 동의 컬럼과 behavior_event 테이블 검증.
 *
 * <p>왜 실 MySQL인가: 테스트 프로필은 H2 + {@code create-drop}이라 엔티티에서 스키마를 만들어
 * 쓴다. TINYINT(1)↔boolean, DEFAULT 0이 기존 행에 먹는지, 외래키가 실제로 거는지는 clean 로드된
 * 진짜 MySQL에서만 드러난다(과거 TINYINT↔int 불일치가 H2 녹색인 채 실서버에서 터진 적이 있다).
 *
 * <p>Docker가 필요하므로 {@code @Tag("integration")} — 실행: {@code ./gradlew integrationTest}
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class V3SchemaIT {

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

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void V96_V97이_clean_로드에_적용된다() {
        List<String> 적용된_버전 = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL",
                String.class);

        assertThat(적용된_버전).contains("96", "97");
    }

    @Test
    void 엔티티가_실_MySQL_스키마와_validate를_통과한다() {
        // 뜨는 것이 단언이다: ddl-auto=validate라 MemberProfile.behaviorConsent(boolean)나
        // BehaviorEvent의 컬럼이 DDL과 어긋나면 컨텍스트 기동 자체가 실패한다.
    }

    @Test
    void 시드_회원의_동의_기본값은_0이다() {
        // V64 시드 회원은 V96보다 먼저 들어간 기존 행이다. DEFAULT 0이 그 행들에도 먹는지 본다.
        Integer 동의한_행 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_profile WHERE behavior_consent <> 0", Integer.class);

        assertThat(동의한_행).isZero();
    }

    @Test
    void behavior_event_외래키가_없는_goods를_거부한다() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO behavior_event (member_id, goods_id, kind, created_at) VALUES (?, ?, ?, ?)",
                2L, 999999999L, "VIEW", Timestamp.valueOf(LocalDateTime.of(2026, 9, 4, 14, 22))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
