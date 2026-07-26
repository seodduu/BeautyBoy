package com.beautyboy.catalog;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 조회수 기록 구현 선택 지점.
 *
 * <p><b>왜 일반 {@code @Configuration}이 아니라 {@link AutoConfiguration}인가:</b>
 * {@link ConditionalOnMissingBean}은 평가 시점에 이미 등록된 빈만 본다. {@link RedisViewCountRecorder}는
 * {@code @Component}라 컴포넌트 스캔에서 등록되는데, DB 폴백을 일반 {@code @Component
 * @ConditionalOnMissingBean}으로 두면 스캔 순서가 보장되지 않아(클래스명상 Db가 Redis보다 먼저 평가되면)
 * Redis가 켜져 있어도 폴백이 함께 등록돼 {@code ViewCountRecorder} 빈이 둘이 된다.
 * 자동 설정은 사용자 빈이 전부 등록된 <b>뒤에</b> 처리되므로 이 조건이 의도대로 동작한다.
 * (등록은 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} —
 * ranking/catalog의 다른 폴백과 같은 패턴)
 *
 * <p>따라서 Redis 토글이 켜지면 이 DB 폴백은 자동으로 물러난다.
 */
@AutoConfiguration
public class ViewCountRecorderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ViewCountRecorder.class)
    public ViewCountRecorder dbViewCountRecorder(GoodsRepository goodsRepository,
                                                  PlatformTransactionManager transactionManager) {
        return new DbViewCountRecorder(goodsRepository, transactionManager);
    }

    /**
     * 조회수 버퍼 전용 RedisTemplate. Redis 토글이 켜졌을 때만 만든다.
     *
     * <p>key/hashKey는 String("bb:viewcount", goodsNo)라 그대로 읽히도록 {@link StringRedisSerializer}를,
     * hashValue는 HINCRBY가 남긴 정수 문자열을 Long으로 되돌려 받도록
     * {@link GenericToStringSerializer}를 쓴다 — Boot 기본 {@code redisTemplate}(JDK 직렬화)로는
     * 필드 키·값이 깨져 HGETALL 결과를 못 읽는다. 이름을 붙여 기본 템플릿과 {@code @Qualifier}로 가른다.
     */
    @Bean
    @ConditionalOnProperty(name = "beautyboy.view-count.redis", havingValue = "true")
    public RedisTemplate<String, Long> viewCountRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Long> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer strings = new StringRedisSerializer();
        GenericToStringSerializer<Long> longs = new GenericToStringSerializer<>(Long.class);
        template.setKeySerializer(strings);
        template.setHashKeySerializer(strings);
        template.setHashValueSerializer(longs);
        template.setValueSerializer(longs);
        template.afterPropertiesSet();
        return template;
    }
}
