package com.beautyboy.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B1 — RedisCacheManager 설정과 장애 폴백을 검증한다. Redis 컨테이너 없이 목으로 돈다
 * (Global Constraints — test 태스크는 Docker 없이 녹색이어야 한다).
 */
class CacheConfigTest {

    @Test
    void 토글_off면_캐시_매니저가_없어도_앱이_뜬다() {
        new ApplicationContextRunner()
                .withUserConfiguration(CacheConfig.class)
                .withPropertyValues("beautyboy.cache.redis=false")
                .run(context -> assertThat(context).doesNotHaveBean(CacheConfig.class));
    }

    @Test
    void 캐시명별_TTL이_설계값과_같다() {
        new ApplicationContextRunner()
                .withUserConfiguration(CacheConfig.class, WorkingRedisConnectionFactoryConfig.class)
                .withPropertyValues("beautyboy.cache.redis=true")
                .run(context -> {
                    CacheManager cacheManager = context.getBean(CacheManager.class);
                    assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
                    RedisCacheManager redisCacheManager = (RedisCacheManager) cacheManager;

                    assertThat(redisCacheManager.getCacheConfigurations().get("ranking").getTtl())
                            .isEqualTo(Duration.ofMinutes(10));
                    assertThat(redisCacheManager.getCacheConfigurations().get("goodsList").getTtl())
                            .isEqualTo(Duration.ofMinutes(5));
                    assertThat(redisCacheManager.getCacheConfigurations().get("compat").getTtl())
                            .isEqualTo(Duration.ofHours(24));
                });
    }

    @Test
    void Redis_연결이_죽어도_조회_메서드는_원본값을_반환한다() {
        new ApplicationContextRunner()
                .withUserConfiguration(CacheConfig.class, FailingRedisConnectionFactoryConfig.class, ProbeService.class)
                .withPropertyValues("beautyboy.cache.redis=true")
                .run(context -> {
                    ProbeService probe = context.getBean(ProbeService.class);

                    String result = probe.lookup("key1");

                    assertThat(result).isEqualTo("value-key1");
                    assertThat(probe.callCount()).isEqualTo(1);
                });
    }

    @Configuration
    static class WorkingRedisConnectionFactoryConfig {
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }
    }

    @Configuration
    static class FailingRedisConnectionFactoryConfig {
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
            when(factory.getConnection()).thenThrow(new RuntimeException("redis 연결 실패(테스트)"));
            return factory;
        }
    }

    @Component
    static class ProbeService {
        private final AtomicInteger calls = new AtomicInteger();

        @Cacheable(cacheNames = "ranking")
        public String lookup(String key) {
            calls.incrementAndGet();
            return "value-" + key;
        }

        int callCount() {
            return calls.get();
        }
    }
}
