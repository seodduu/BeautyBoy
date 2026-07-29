package com.beautyboy.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.metrics.cache.CacheMetricsRegistrar;
import org.springframework.boot.actuate.metrics.cache.RedisCacheMeterBinderProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    /**
     * B5 — 캐시 히트율 관측. 계획서 사양대로 Micrometer {@code cache.gets} 미터(결과 태그
     * hit/miss)가 실제로 hit≥1로 집계되는지를 확인한다. {@code spring-boot-starter-actuator}가
     * 이제 클래스패스에 있으므로(build.gradle.kts), 실제 프로덕션 바인딩 경로인
     * {@link CacheMetricsRegistrar}#{@code bindCacheToRegistry}로 {@link RedisCacheManager}가
     * 만든 캐시를 {@link SimpleMeterRegistry}에 바인딩해 미터를 직접 읽는다 — Redis 자체는
     * 여전히 목(target: put/get 왕복이 실제로 집계되는 stateful 목)이므로 Global Constraints의
     * "인프라 없이 테스트" 규칙을 지킨다. Redis 직렬화·TTL이 실제로 먹는지와
     * {@code /actuator/metrics} HTTP 노출 자체는 compose 실기동(C1)에서 확인한다.
     */
    @Test
    void 캐시_히트율_메트릭이_노출된다() {
        new ApplicationContextRunner()
                .withUserConfiguration(CacheConfig.class, StatefulRedisConnectionFactoryConfig.class)
                .withPropertyValues("beautyboy.cache.redis=true")
                .run(context -> {
                    CacheManager cacheManager = context.getBean(CacheManager.class);
                    Cache ranking = cacheManager.getCache("ranking");

                    ranking.put("key1", "value1");
                    ranking.get("key1");
                    ranking.get("key1");

                    SimpleMeterRegistry registry = new SimpleMeterRegistry();
                    new CacheMetricsRegistrar(registry, List.of(new RedisCacheMeterBinderProvider()))
                            .bindCacheToRegistry(ranking);

                    double hits = registry.get("cache.gets")
                            .tag("cache", "ranking")
                            .tag("result", "hit")
                            .functionCounter()
                            .count();
                    assertThat(hits).isGreaterThanOrEqualTo(1);
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

    // B5 — put/get을 실제로 왕복시켜(값을 메모리 맵에 저장) RedisCache.getStatistics()가 진짜
    // 집계되는지 보는 목. Redis TTL 값(entryTtl 有)이면 stringCommands().getEx(...)를 쓰므로
    // get/getEx 둘 다 스텁한다.
    @Configuration
    static class StatefulRedisConnectionFactoryConfig {
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            Map<ByteBuffer, byte[]> store = new ConcurrentHashMap<>();
            RedisStringCommands stringCommands = mock(RedisStringCommands.class);
            when(stringCommands.get(any(byte[].class)))
                    .thenAnswer(inv -> store.get(ByteBuffer.wrap(inv.getArgument(0))));
            when(stringCommands.getEx(any(byte[].class), any()))
                    .thenAnswer(inv -> store.get(ByteBuffer.wrap(inv.getArgument(0))));
            when(stringCommands.set(any(byte[].class), any(byte[].class)))
                    .thenAnswer(inv -> {
                        store.put(ByteBuffer.wrap(inv.getArgument(0)), inv.getArgument(1));
                        return true;
                    });
            when(stringCommands.set(any(byte[].class), any(byte[].class), any(), any()))
                    .thenAnswer(inv -> {
                        store.put(ByteBuffer.wrap(inv.getArgument(0)), inv.getArgument(1));
                        return true;
                    });

            RedisConnection connection = mock(RedisConnection.class);
            when(connection.stringCommands()).thenReturn(stringCommands);

            RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
            when(factory.getConnection()).thenReturn(connection);
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
