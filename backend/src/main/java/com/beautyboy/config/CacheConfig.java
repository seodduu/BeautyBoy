package com.beautyboy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * B1 — 랭킹/목록/궁합 읽기 캐시 인프라. {@code beautyboy.cache.redis=true}일 때만 뜬다
 * (조회수 버퍼·이벤트 릴레이와 같은 토글 철학 — Redis 없이도 앱은 뜬다).
 *
 * <p>{@link #errorHandler()}가 Redis 장애 시 예외를 삼켜 {@code @Cacheable} 메서드가 항상
 * 원본 구현으로 폴백하게 한다. 기본 {@code SimpleCacheErrorHandler}는 rethrow라 캐시 장애가
 * 곧 서비스 장애가 되므로 반드시 오버라이드한다.
 */
/*
 * B3 추가: proxyTargetClass = true. GoodsService처럼 인터페이스(GoodsQueryService)를
 * 구현하면서 동시에 다른 빈(AdminGoodsService)에 구체 타입으로 주입되는 클래스에
 * @Cacheable을 걸면, 기본(JDK 동적 프록시)은 그 인터페이스만 구현한 프록시를 만들어
 * "expected GoodsService but was $Proxy" 예외로 컨텍스트가 죽는다. RankingService(B2)는
 * 인터페이스가 없어 우연히 문제가 없었을 뿐이다 — CGLIB(클래스 기반) 프록시로 강제해 이 클래스의
 * 구체 타입 주입을 전부 안전하게 만든다.
 */
@Configuration
@EnableCaching(proxyTargetClass = true)
@ConditionalOnProperty(name = "beautyboy.cache.redis", havingValue = "true")
public class CacheConfig implements CachingConfigurer {

    // 캐시명별 TTL — 설계 §6 표의 값. 바꾸려면 설계 문서부터 고친다.
    private static final Map<String, Duration> TTL = Map.of(
            "ranking", Duration.ofMinutes(10),
            "goodsList", Duration.ofMinutes(5),
            "compat", Duration.ofHours(24));

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("v1:")
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        TTL.forEach((name, ttl) -> perCache.put(name, base.entryTtl(ttl)));
        // B5 — 캐시 통계(hit/miss/get 카운터)를 켠다. Micrometer의 캐시 메트릭(cache.gets)이
        // 읽는 원천이 RedisCache#getStatistics()다. withInitialCacheConfigurations로 세 캐시를
        // 부팅 시점에 즉시 등록해두는 것(위)도 같은 이유 — 지연 생성되면 CacheMetricsRegistrar가
        // 부팅 때 바인딩할 캐시가 없어 놓친다.
        return RedisCacheManager.builder(factory)
                .withInitialCacheConfigurations(perCache)
                .enableStatistics()
                .build();
    }

    // Redis 다운 시 @Cacheable이 예외를 삼키고 원본 메서드로 직행하게 한다.
    // 기본 SimpleCacheErrorHandler는 rethrow라 캐시 장애 = 서비스 장애가 된다.
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            private final Logger log = LoggerFactory.getLogger("cacheFallback");

            @Override
            public void handleCacheGetError(RuntimeException e, Cache c, Object k) {
                log.warn("cache get 실패 — DB 직행: {}", k, e);
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache c, Object k, Object v) {
                log.warn("cache put 실패: {}", k, e);
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache c, Object k) {
                log.warn("cache evict 실패: {}", k, e);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache c) {
                log.warn("cache clear 실패", e);
            }
        };
    }
}
