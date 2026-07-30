package com.beautyboy.common;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 자격증명 시드가 <b>운영 경로에 실리지 않는다</b>를 테스트로 못 박는다 (설계 §3.6-3).
 *
 * <p>배경: {@code V64__seed_member.sql}(회원 3명, 비밀번호 {@code seed1234!})과
 * {@code V80__seed_admin_account.sql}(비밀번호 {@code admin}인 ADMIN)은 원래 기본 마이그레이션
 * 경로에 있었고, "실제 배포 환경에는 들어가면 안 된다"는 규칙이 <b>주석에만</b> 적혀 있었다.
 * 그 규칙을 설정({@code db/seed} 분리 + {@code demo} 프로필 게이트)으로 옮겼고, 이 테스트는
 * 그 설정이 되돌려지는 것을 막는다 — 누군가 자격증명 시드를 다시 {@code db/migration}에
 * 넣으면 첫 케이스가 깨진다. 주석이 아니라 테스트가 규칙을 지킨다.
 *
 * <p><b>Spring 컨텍스트를 띄우지 않는다.</b> Testcontainers MySQL을 직접 열고 Flyway API로
 * locations만 갈아 끼워 {@code migrate()}한 뒤 JDBC로 단언한다. 컨텍스트를 하나 더 만들면
 * 컨텍스트 캐시 LRU가 다른 테스트의 공유 H2 테이블까지 지우는 사고가 재발할 수 있고,
 * 여기서 필요한 것은 DB 상태뿐이라 컨텍스트가 애초에 필요 없다.
 *
 * <p>Docker가 필요하므로 {@code @Tag("integration")}. 실행: {@code ./gradlew integrationTest}
 */
@Tag("integration")
@Testcontainers
class SeedIsolationIT {

    /** docker-compose.yml·다른 IT들과 같은 이미지 — 로컬에서만 되는 상황을 만들지 않는다. */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    /** 시드 경로를 더한 대조군용 스키마. 같은 컨테이너를 재사용해 기동 비용을 두 배로 만들지 않는다. */
    private static final String SEED_SCHEMA = "seedcheck";

    /** 배포 경로(프로필 없이 뜨는 경로)와 같은 locations. */
    private static final String 운영_경로 = "classpath:db/migration";

    /** demo 프로필(application-demo.yml)과 같은 locations. */
    private static final String 데모_경로 = "classpath:db/migration,classpath:db/seed";

    @BeforeAll
    static void 두_경로를_각자의_스키마에_적용한다() throws SQLException {
        // 컨테이너 기본 스키마(test)에는 운영 경로만, 새로 만든 스키마에는 시드까지.
        // migrate()가 예외를 던지면 이 시점에 전 케이스가 실패한다 — 그것이 두 번째 케이스의 절반이다.
        migrate(MYSQL.getJdbcUrl(), 운영_경로);

        try (Connection conn = rootConnection(MYSQL.getJdbcUrl());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE IF NOT EXISTS " + SEED_SCHEMA);
        }
        migrate(jdbcUrlFor(SEED_SCHEMA), 데모_경로);
    }

    @Test
    void 운영_경로에는_자격증명_시드가_없다() throws SQLException {
        assertThat(정수_조회(MYSQL.getJdbcUrl(), "SELECT COUNT(*) FROM member"))
                .as("db/migration만 로드했는데 member가 있으면 자격증명 시드가 배포 경로로 되돌아온 것이다")
                .isZero();
    }

    @Test
    void 운영_경로_마이그레이션은_끝까지_성공한다() throws SQLException {
        // V64·V66을 빼도 뒤따르는 변환 마이그레이션(V81 concerns 어휘, V84 review_count 백필)이
        // 대상 행 0건으로 조용히 지나가는지를 본다. 시드 분리가 기동을 깨뜨리지 않는다는 증거.
        assertThat(정수_조회(MYSQL.getJdbcUrl(),
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE"))
                .as("실패한 마이그레이션이 하나라도 있으면 시드 분리가 기동을 깨뜨린 것이다")
                .isZero();
    }

    @Test
    void 시드_경로를_더하면_시드_회원이_생긴다() throws SQLException {
        // 분리가 "지우기"가 아니라 "가르기"임을 증명한다 — demo 프로필에서는 그대로 있어야 한다.
        // 4 = V64의 회원 3명 + V80의 관리자 1명.
        assertThat(정수_조회(jdbcUrlFor(SEED_SCHEMA), "SELECT COUNT(*) FROM member"))
                .as("db/seed를 더했는데 시드 회원이 없으면 demo 프로필이 아무 일도 하지 않는 것이다")
                .isEqualTo(4);
    }

    private static void migrate(String jdbcUrl, String locations) {
        Flyway.configure()
                .dataSource(jdbcUrl, "root", MYSQL.getPassword())
                .locations(locations.split(","))
                .load()
                .migrate();
    }

    private static Connection rootConnection(String jdbcUrl) throws SQLException {
        // 컨테이너 기본 사용자(test)는 자기 스키마에만 권한이 있어 CREATE DATABASE를 못 한다.
        // MySQLContainer는 root 비밀번호를 같은 값으로 맞춰 두므로 root로 붙는다.
        return DriverManager.getConnection(jdbcUrl, "root", MYSQL.getPassword());
    }

    /** {@code jdbc:mysql://host:port/test?...} 의 스키마 부분만 갈아 끼운다(쿼리스트링은 보존). */
    private static String jdbcUrlFor(String schema) {
        return MYSQL.getJdbcUrl()
                .replaceFirst("/" + MYSQL.getDatabaseName() + "(?=$|\\?)", "/" + schema);
    }

    private static int 정수_조회(String jdbcUrl, String sql) throws SQLException {
        try (Connection conn = rootConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
