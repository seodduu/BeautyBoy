package com.beautyboy.catalog;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 선점 필터의 기본값 배선. {@link RedisStockAdmission}이 토글로 켜지지 않았을 때만
 * 통과 구현을 넣는다 — 캐시·조회수 버퍼와 같은 "Redis 없이도 뜬다" 관례다.
 *
 * <p>{@code @ConditionalOnMissingBean}은 컴포넌트 스캔이 끝난 뒤 평가되므로,
 * {@code @Component}인 {@link RedisStockAdmission}이 이미 등록됐는지를 정확히 본다.
 */
@Configuration
public class StockAdmissionConfig {

    @Bean
    @ConditionalOnMissingBean(StockAdmission.class)
    public StockAdmission noOpStockAdmission() {
        return new NoOpStockAdmission();
    }
}
