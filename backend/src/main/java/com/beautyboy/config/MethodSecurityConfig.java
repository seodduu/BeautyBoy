package com.beautyboy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * {@code @PreAuthorize}를 켠다. SecurityConfig를 열지 않기 위해 별도 파일로 둔다 —
 * SecurityConfig는 Wave 0 이후 동결이고, 인가 규칙을 두 곳에서 고치면 어느 쪽이 진실인지 모르게 된다.
 * 경로 단위 인가는 SecurityConfig가, 역할 단위 인가는 메서드 애노테이션이 담당한다.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
