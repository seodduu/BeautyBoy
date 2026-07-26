package com.beautyboy.auth;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * {@link AuthRefreshConcurrencyScenario}의 실 MySQL 실행분 — <b>이 태스크의 신뢰 근거</b>.
 *
 * <p>동시 refresh의 승패는 결국 엔진의 행 잠금 동작에 달려 있다. 두 트랜잭션이 같은 행에
 * {@code DELETE}를 걸면 InnoDB는 늦은 쪽을 <b>대기</b>시켰다가 앞선 쪽 커밋 후 영향 행 수 0을
 * 돌려주는데, H2(MVStore)는 잠금 타임아웃으로 예외를 던질 수도 있다. 그 차이가 프로덕션에서만
 * 터지는 것을 막으려고 같은 단언을 실 MySQL에서 한 번 더 돌린다.
 *
 * <p>컨테이너 구성은 {@code MysqlFulltextSearchIntegrationTest}와 같은 패턴이다
 * (mysql:8.4 + Flyway 실제 적용 + {@code ddl-auto=validate}).
 * {@code @ActiveProfiles}는 {@code "test"}만 쓴다 — {@code mysql-search}는 검색 전용 프로필이다.
 *
 * <p>실행: {@code ./gradlew integrationTest}
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AuthRefreshConcurrencyMysqlIntegrationTest extends AuthRefreshConcurrencyScenario {

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
}
