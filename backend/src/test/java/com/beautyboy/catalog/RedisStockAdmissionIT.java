package com.beautyboy.catalog;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis 선점 필터의 Lua 판정(설계 §3-3·§3-4·§3-5)을 <b>실 Redis</b>에서 확인한다.
 *
 * <p>H2나 인메모리 더블로는 검증할 수 없는 것만 여기서 본다 — 검사와 차감이 한 번의 Lua로
 * 원자인가, 만료된 키에 release가 유령 카운터를 만들지 않는가, Redis가 죽으면 통과로
 * 강등되는가. 이 셋이 무너지면 필터가 초과 판매를 만들거나(원자성) 시스템을 멈춘다(폴백).
 *
 * <p>DB는 띄우지 않는다. 이 클래스의 관심사는 Redis 쪽 판정이고, DB 재고는 적재 경로의
 * 입력값일 뿐이라 {@link GoodsOptionRepository}를 목으로 둔다 — MySQL 컨테이너를 함께
 * 띄우면 검증 대상이 아닌 것 때문에 느려지고 실패 지점만 늘어난다.
 */
@Tag("integration")
class RedisStockAdmissionIT {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);

    private static StringRedisTemplate redis;
    private static LettuceConnectionFactory connectionFactory;

    private GoodsOptionRepository goodsOptionRepository;
    private RedisStockAdmission admission;

    @BeforeAll
    static void 컨테이너_기동() {
        REDIS.start();
        connectionFactory = 연결(REDIS.getHost(), REDIS.getMappedPort(6379));
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void 컨테이너_종료() {
        connectionFactory.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void 초기화() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        goodsOptionRepository = mock(GoodsOptionRepository.class);
        admission = new RedisStockAdmission(redis, goodsOptionRepository);
    }

    @Test
    @DisplayName("키가 없으면 DB 재고를 적재한 뒤 선점한다 — TTL도 함께 붙는다")
    void 키없으면_DB적재후_선점한다() {
        when(goodsOptionRepository.findStockById(51L)).thenReturn(Optional.of(7));

        assertThat(admission.tryAcquire(List.of(new StockAdmission.Line(51L, 3)))).isTrue();

        assertThat(카운터(51L)).isEqualTo(4);
        // TTL이 없으면 드리프트를 교정할 수단이 사라진다(설계 §3-2) — 존재만 확인한다.
        assertThat(redis.getExpire("stock:adm:51")).isPositive();
    }

    @Test
    @DisplayName("남은 수량이 요청보다 적으면 REJECT — 카운터는 손대지 않는다")
    void 수량부족이면_REJECT() {
        카운터_설정(51L, 2);

        assertThat(admission.tryAcquire(List.of(new StockAdmission.Line(51L, 3)))).isFalse();

        assertThat(카운터(51L)).as("실패한 선점이 카운터를 깎으면 팔 수 있는 재고가 사라진다").isEqualTo(2);
    }

    @Test
    @DisplayName("경계 — 잔여와 요청이 같으면 성공하고 카운터는 0이 된다")
    void 경계_잔여와_요청이_같으면_성공() {
        카운터_설정(51L, 3);

        assertThat(admission.tryAcquire(List.of(new StockAdmission.Line(51L, 3)))).isTrue();

        assertThat(카운터(51L)).isZero();
    }

    @Test
    @DisplayName("다옵션 주문에서 뒤 라인이 실패하면 이미 선점한 앞 라인이 반환된다")
    void 다옵션_중간실패시_이미선점분이_반환된다() {
        카운터_설정(51L, 5);
        카운터_설정(52L, 1);

        boolean 결과 = admission.tryAcquire(List.of(
                new StockAdmission.Line(51L, 2),
                new StockAdmission.Line(52L, 3)));

        assertThat(결과).isFalse();
        assertThat(카운터(51L)).as("all-or-nothing — 부분 선점을 남기지 않는다").isEqualTo(5);
        assertThat(카운터(52L)).isEqualTo(1);
    }

    @Test
    @DisplayName("release는 키가 없으면 아무것도 하지 않는다 — TTL 없는 유령 카운터 방지")
    void release는_키가_없으면_아무것도_안한다() {
        // 만료된 상황을 그대로 재현한다(키 없음).
        admission.release(List.of(new StockAdmission.Line(51L, 3)));

        assertThat(redis.hasKey("stock:adm:51"))
                .as("만료 키에 INCRBY하면 TTL 없는 카운터가 영원히 남는다(설계 §3-4)")
                .isFalse();
    }

    @Test
    @DisplayName("정상 선점 뒤 release는 카운터를 되돌린다")
    void release는_선점분을_되돌린다() {
        카운터_설정(51L, 5);
        admission.tryAcquire(List.of(new StockAdmission.Line(51L, 2)));
        assertThat(카운터(51L)).isEqualTo(3);

        admission.release(List.of(new StockAdmission.Line(51L, 2)));

        assertThat(카운터(51L)).isEqualTo(5);
    }

    @Test
    @DisplayName("Redis를 내리면 통과로 강등된다 — 필터 장애가 결제를 막지 않는다")
    void Redis를_내리면_통과로_강등된다() {
        // 공용 컨테이너를 멈추면 뒤 테스트가 전부 죽으므로 이 케이스만 전용 컨테이너를 쓴다.
        try (GenericContainer<?> 죽일_레디스 = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379)) {
            죽일_레디스.start();
            LettuceConnectionFactory factory =
                    연결(죽일_레디스.getHost(), 죽일_레디스.getMappedPort(6379));
            StringRedisTemplate 죽을_템플릿 = new StringRedisTemplate(factory);
            죽을_템플릿.afterPropertiesSet();
            RedisStockAdmission 강등_대상 =
                    new RedisStockAdmission(죽을_템플릿, goodsOptionRepository);
            죽일_레디스.stop();

            assertThat(강등_대상.tryAcquire(List.of(new StockAdmission.Line(51L, 3))))
                    .as("Redis 장애는 필터 없이 통과 — 초과 판매는 DB 조건부 UPDATE가 막는다(설계 §3-5)")
                    .isTrue();
            // release도 삼킨다(카운터가 작아지는 안전한 방향).
            강등_대상.release(List.of(new StockAdmission.Line(51L, 3)));
            factory.destroy();
        }
    }

    @Test
    @DisplayName("DB에도 재고가 없으면 0으로 적재되고 선점은 거절된다")
    void DB재고가_없으면_거절된다() {
        when(goodsOptionRepository.findStockById(anyLong())).thenReturn(Optional.empty());

        assertThat(admission.tryAcquire(List.of(new StockAdmission.Line(99L, 1)))).isFalse();

        assertThat(카운터(99L)).isZero();
    }

    /** 타임아웃을 짧게 잡는다 — 죽은 Redis를 기다리느라 테스트가 멈추면 폴백 검증이 무의미해진다. */
    private static LettuceConnectionFactory 연결(String host, int port) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(host, port),
                LettuceClientConfiguration.builder()
                        .commandTimeout(Duration.ofSeconds(2))
                        .build());
        factory.afterPropertiesSet();
        return factory;
    }

    private void 카운터_설정(long optionId, int 값) {
        redis.opsForValue().set("stock:adm:" + optionId, String.valueOf(값),
                RedisStockAdmission.TTL);
    }

    private long 카운터(long optionId) {
        String v = redis.opsForValue().get("stock:adm:" + optionId);
        return v == null ? 0L : Long.parseLong(v);
    }
}
