package com.beautyboy.experiment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 실험 엔드포인트만 여는 별도 필터 체인.
 *
 * <p><b>{@code SecurityConfig}를 수정하지 않는 것이 요점이다.</b> 그 파일은 여러 웨이브가 동시에
 * 의존하는 공유 계약이고, 실험용 경로 하나 때문에 인가 설정을 건드리면 그 변경이 기본 기동에도
 * 남는다. 여기서 {@code securityMatcher}로 경로를 좁힌 체인을 {@code @Order(0)}으로 앞에 얹으면
 * 기존 체인은 손대지 않은 채 이 경로만 갈라진다({@code @Order}가 없는 기본 체인은
 * {@code LOWEST_PRECEDENCE}라 자동으로 뒤에 온다 — 순서를 뒤집으면 any-request 체인이 먼저 걸려
 * 이 설정이 죽는다).
 *
 * <p>인증을 붙이지 않는 이유: 클라이언트 계산에는 JWT 검증이 없다. 서버 쪽에만 필터 비용을 얹으면
 * 비교가 불공정해진다. 이 엔드포인트는 {@code experiment} 프로필에서만 존재하고 실서비스로
 * 승격되지 않으므로 공개해도 노출되는 것이 없다.
 */
@Configuration
@Profile("experiment")
public class ExperimentSecurityConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain experimentSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/v1/experiment/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
