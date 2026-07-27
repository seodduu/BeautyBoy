package com.beautyboy.member;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V81 — member_profile.concerns 구 어휘 → tag.slug 마이그레이션 검증.
 *
 * <p>왜 실 MySQL인가: V81은 DDL이 아니라 데이터 UPDATE라 H2 + {@code create-drop}에서는
 * 아예 실행되지 않는다(테스트 프로필은 Flyway off). 시드(V64)와 마이그레이션(V81)이 같은
 * 순서로 얹힌 clean 로드에서만 "구 어휘가 한 건도 안 남았다"를 말할 수 있다.
 *
 * <p>Docker가 필요하므로 {@code @Tag("integration")} — 실행: {@code ./gradlew integrationTest}
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ConcernSlugMigrationIT {

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
    void V81이_clean_로드에_적용된다() {
        List<String> 적용된_버전 = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL",
                String.class);

        assertThat(적용된_버전).contains("64", "81");
    }

    @Test
    void 구_어휘가_한_건도_남지_않는다() {
        // 네 토큰 중 하나라도 대문자로 남아 있으면 프론트의 새 슬러그 집합과 어긋나 조용히
        // "선택 안 됨"으로 표시된다 — 화면이 깨지지 않으므로 여기서 잡지 않으면 안 잡힌다.
        //
        // LIKE BINARY인 이유: 컬럼 콜레이션이 대소문자 무시(utf8mb4_0900_ai_ci)라 그냥 LIKE로는
        // 마이그레이션 결과인 'pore'·'trouble'까지 걸려 항상 실패한다. 여기서 보려는 것은
        // "대문자 구 어휘가 남았는가"이므로 대소문자를 구분해야 한다.
        Integer 남은_행 = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM member_profile
                 WHERE concerns LIKE BINARY '%PORE%'
                    OR concerns LIKE BINARY '%TROUBLE%'
                    OR concerns LIKE BINARY '%WRINKLE%'
                    OR concerns LIKE BINARY '%DARK_SPOT%'
                """, Integer.class);

        assertThat(남은_행).isZero();
    }

    @Test
    void V64_시드_4건이_새_슬러그로_조회된다() {
        // V64: member 2 = 'PORE,WRINKLE' → 'pore,anti-aging' / member 3 = 'PORE,TROUBLE' → 'pore,trouble'
        String 건조맨 = jdbcTemplate.queryForObject(
                "SELECT concerns FROM member_profile WHERE member_id = 2", String.class);
        String 지성맨 = jdbcTemplate.queryForObject(
                "SELECT concerns FROM member_profile WHERE member_id = 3", String.class);

        assertThat(건조맨).isEqualTo("pore,anti-aging");
        assertThat(지성맨).isEqualTo("pore,trouble");
    }

    @Test
    void 옮겨진_슬러그는_모두_tag_테이블에_실존한다() {
        // 프로필 고민이 tag.slug와 "같은 어휘"라는 설계 §4.1의 전제를 스키마로 확인한다.
        List<String> 사용중인_슬러그 = List.of("pore", "trouble", "anti-aging", "bright");

        List<String> 실존하는_슬러그 = jdbcTemplate.queryForList(
                "SELECT slug FROM tag WHERE slug IN (?, ?, ?, ?)",
                String.class, 사용중인_슬러그.toArray());

        assertThat(실존하는_슬러그).containsExactlyInAnyOrderElementsOf(사용중인_슬러그);
    }
}
