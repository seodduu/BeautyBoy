package com.beautyboy.config;

import com.beautyboy.auth.JwtAuthenticationFilter;
import com.beautyboy.auth.TokenProvider;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.common.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    public SecurityConfig(TokenProvider tokenProvider, ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /error: 컨테이너가 미처리 예외(500)나 핸들러 미매칭(404)에서 내부적으로
                        // forward하는 경로. 여기를 permitAll하지 않으면 anyRequest().authenticated()에
                        // 걸려 진짜 상태(500/404) 대신 401이 나가버린다(무토큰 보호 경로 접근은
                        // 컨트롤러에 도달하기 전 authenticationEntryPoint에서 걸리므로 영향 없음).
                        .requestMatchers("/api/v1/auth/**", "/api/v1/health", "/error").permitAll()
                        // 설계 문서 7장 "공개" 목록을 선반영한다. 공개 경로는 이미 전부 확정돼 있으므로
                        // 지금 한 번에 등록해두면 이후 웨이브가 이 파일을 건드릴 일이 없다 —
                        // Wave 2는 터미널 3개가 병렬로 도는데, 그때 각자 여기를 고치면 하필
                        // 인가 설정에서 3방향 충돌이 난다. 아직 구현되지 않은 경로는 404이므로
                        // 미리 열어둬도 노출되는 것이 없다.
                        // 조회(GET)만 공개다. POST /reviews, POST /qna 등 쓰기는 인증이 필요하므로
                        // 여기 걸리지 않고 아래 anyRequest().authenticated()로 떨어진다.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/categories/tree",
                                "/api/v1/goods", "/api/v1/goods/**",
                                "/api/v1/reviews", "/api/v1/reviews/stats",
                                "/api/v1/search", "/api/v1/search/**",
                                "/api/v1/rankings",
                                "/api/v1/routines",
                                "/api/v1/qna",
                                "/api/v1/delivery/stores/nearby").permitAll()
                        // 궁합 진단은 상품ID 배열을 받아 판정만 하므로 비로그인도 쓸 수 있다(설계 7장).
                        .requestMatchers(HttpMethod.POST, "/api/v1/compat/check").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            ErrorResponse errorResponse = new ErrorResponse(
                                    ErrorCode.UNAUTHORIZED.name(), ErrorCode.UNAUTHORIZED.getMessage(), null);
                            objectMapper.writeValue(response.getWriter(), errorResponse);
                        })
                )
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
