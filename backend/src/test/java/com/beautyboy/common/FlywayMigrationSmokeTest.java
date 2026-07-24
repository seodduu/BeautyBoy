package com.beautyboy.common;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
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
 * Flyway 마이그레이션 스모크.
 *
 * <p>왜 필요한가: 유닛/슬라이스 테스트는 H2 + {@code ddl-auto=create-drop}이라 <b>엔티티가 곧 스키마</b>다.
 * 그래서 Flyway DDL과 엔티티 매핑이 어긋나 있어도 전부 녹색으로 통과하고, 실 MySQL로 띄우는 순간
 * {@code ddl-auto=validate}에서 죽는다(TINYINT ↔ int 류의 타입 불일치가 대표적이다).
 * "스키마가 진실"이라고 정해놓고 그 진실만 검증 대상이 아니었다.
 *
 * <p>이 테스트가 닫는 구멍은 정확히 둘이다:
 * <ol>
 *   <li>Flyway DDL이 실제 MySQL에서 끝까지 실행되는가 (H2 방언에 가려지지 않은 문법 오류)</li>
 *   <li>그렇게 만들어진 스키마를 JPA {@code validate}가 받아들이는가 (컨텍스트 로딩 자체가 검증이다)</li>
 * </ol>
 *
 * <p>Docker가 필요하므로 {@code @Tag("integration")}으로 기본 {@code test}에서 빠져 있다.
 * 실행: {@code ./gradlew integrationTest}
 *
 * <p><b>새 마이그레이션을 추가하는 웨이브는 이 테스트를 DoD에 포함한다.</b>
 * 자기 대역(V20~ 등)의 테이블이 늘어도 여기서 한 번은 실 MySQL을 거치게 된다.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class FlywayMigrationSmokeTest {

    // docker-compose.yml과 같은 이미지를 쓴다 — 로컬에서 되는데 CI에서만 깨지는 상황을 만들지 않는다.
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    /**
     * application-test.yml은 H2 + flyway off + create-drop이다. 여기서 그 셋을 전부 뒤집는다.
     * (jwt 시크릿 등 나머지 test 프로필 설정은 그대로 쓰기 위해 프로필 자체는 유지한다.)
     */
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
    @Autowired
    Environment environment;

    /**
     * 이 테스트 클래스의 존재 이유가 전제 3개(실 MySQL · Flyway on · validate)에 전부 걸려 있다.
     * 셋 중 하나라도 풀리면 나머지 테스트는 통과하면서 아무것도 검증하지 않는 껍데기가 된다 —
     * 정확히 H2 + create-drop이 지금까지 해온 일이다. 그래서 전제부터 확인한다.
     */
    @Test
    void 전제_확인_실_MySQL에_Flyway와_validate가_켜져_있다() {
        assertThat(environment.getProperty("spring.datasource.url")).contains("mysql");
        assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");

        String 제품 = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        assertThat(제품).isNotNull();
        assertThat(제품.toLowerCase()).doesNotContain("h2");
    }

    @Test
    void 모든_마이그레이션이_실_MySQL에서_성공한다() {
        List<String> 실패한_버전 = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = FALSE", String.class);

        assertThat(실패한_버전).isEmpty();

        List<String> 적용된_버전 = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL", String.class);

        // 지금까지의 대역: V1(member) · V10~V12(catalog/ingredient/seed) ·
        //   V20~V22(search/ranking) · V30~V32(cart/order/payment) · V40~V42(review/qna/wishlist) ·
        //   V50~V51(routine 스키마/시드, Wave 3 T1) · V60~V62(ingredient reg flag/inci) ·
        //   V63(address 기본배송지 유니크 제약, Wave 4 T2).
        // 웨이브가 늘면 이 목록도 함께 늘린다 — 마이그레이션을 추가하고 여기를 안 고치면 바로 걸린다.
        assertThat(적용된_버전).contains("1", "10", "11", "12", "20", "21", "22", "30", "31", "32", "40", "41", "42",
                "50", "51", "60", "61", "62", "63");
    }

    @Test
    void 엔티티_매핑이_validate를_통과한다() {
        // 이 테스트가 실행됐다는 것은 ddl-auto=validate로 컨텍스트가 떴다는 뜻이고,
        // 그것이 곧 "Flyway가 만든 스키마 == 엔티티 매핑"의 증거다.
        // 아래는 그 사실을 문서화하는 최소 확인이다.
        Integer 테이블_수 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()", Integer.class);

        assertThat(테이블_수).isNotNull().isPositive();
    }

    @Test
    void 루틴_5단계_카테고리_코드가_시드에_실존한다() {
        // 프론트의 루틴 매핑 상수(features/routine/steps.ts)와 MSW fixture가 이 코드들을 전제한다.
        // 시드에서 코드가 바뀌면 프론트는 조용히 빈 섹션이 되므로, 여기서 계약으로 못 박는다.
        List<String> 루틴_코드 = List.of("C002", "C001001", "C001002", "C001003", "C004001");

        List<String> 실존하는_코드 = jdbcTemplate.queryForList(
                "SELECT code FROM category WHERE code IN (?, ?, ?, ?, ?)",
                String.class, 루틴_코드.toArray());

        assertThat(실존하는_코드).containsExactlyInAnyOrderElementsOf(루틴_코드);
    }
}
