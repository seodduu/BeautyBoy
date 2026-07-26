package com.beautyboy.auth;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@link AuthRefreshConcurrencyScenario}의 H2 실행분.
 *
 * <p>태그가 없으므로 {@code ./gradlew test}에서 매번 돈다 — Docker 없이도 회귀를 잡는 쪽이다.
 * 다만 H2(MVStore)의 행 잠금은 InnoDB와 다르므로 <b>여기가 녹색이어도 신뢰의 근거는
 * {@link AuthRefreshConcurrencyMysqlIntegrationTest}</b>다. 이 프로젝트엔 H2가 실 MySQL 문제를
 * 가린 이력이 있다(create-drop이 ddl validate 불일치를 가림).
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthRefreshConcurrencyTest extends AuthRefreshConcurrencyScenario {
}
