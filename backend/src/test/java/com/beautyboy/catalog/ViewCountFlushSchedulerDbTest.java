package com.beautyboy.catalog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 플러시의 <b>트랜잭션 경계</b> 회귀 테스트. Redis만 mock으로 대체하고 나머지(트랜잭션 프록시,
 * 실제 {@code @Modifying} 벌크 UPDATE, 실 DB)는 진짜를 쓴다 — 그래서 Redis 없이도 돈다.
 *
 * <p>왜 필요한가: mock 리포지토리만 쓰는 {@link ViewCountFlushSchedulerTest}는 {@code addViewCount}가
 * 호출됐다는 사실만 보므로, {@code flush()}에 {@code @Transactional}이 없어 매 주기
 * "Executing an update/delete query"로 죽는 결함을 통과시킨다. 실제로 수동 Redis 확인에서 그 결함이
 * 나왔고, 이 테스트가 그 재발을 막는다.
 *
 * <p><b>클래스에 {@code @Transactional}을 걸지 않는다.</b> 걸면 테스트의 트랜잭션이 벌크 UPDATE를
 * 대신 만족시켜 버려서 정확히 검증하려는 결함을 다시 가린다 — 스케줄러가 <b>스스로</b> 트랜잭션을
 * 열어야 한다는 것이 이 테스트의 요지다. 대신 만든 데이터는 {@link #정리()}에서 직접 지운다.
 */
@SpringBootTest(properties = "beautyboy.view-count.redis=true")
@ActiveProfiles("test")
class ViewCountFlushSchedulerDbTest {

    @Autowired
    ViewCountFlushScheduler scheduler;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @MockBean
    GoodsRatingProvider goodsRatingProvider;
    @MockBean
    WishedGoodsProvider wishedGoodsProvider;

    /** 토글이 켜져 있어 자동 설정이 만드는 조회수 전용 템플릿. 그 자리를 mock으로 바꿔 Redis 없이 돌린다. */
    @MockBean(name = "viewCountRedisTemplate")
    RedisTemplate<String, Long> redisTemplate;

    private Long 상품Id;
    private Long 브랜드Id;

    @AfterEach
    void 정리() {
        if (상품Id != null) {
            goodsRepository.deleteById(상품Id);
        }
        if (브랜드Id != null) {
            brandRepository.deleteById(브랜드Id);
        }
    }

    private Goods 상품_저장() {
        Brand brand = brandRepository.save(new Brand("플러시브랜드", null));
        브랜드Id = brand.getId();
        if (!categoryRepository.existsById("C001001001")) {
            categoryRepository.save(new Category("C001001001", null, "카테고리", 3, 0));
        }
        Goods goods = goodsRepository.save(
                new Goods(brand, "C001001001", "플러시상품", "요약", "https://img.example/f.jpg", 10000, 9000));
        상품Id = goods.getId();
        return goods;
    }

    @Test
    void 플러시는_스스로_트랜잭션을_열어_view_count를_실제로_더한다() {
        Goods 상품A = 상품_저장();
        int 기존 = 상품A.getViewCount();

        HashOperations<String, String, Long> hashOps = mock(HashOperations.class);
        given(redisTemplate.<String, Long>opsForHash()).willReturn(hashOps);
        given(hashOps.entries("bb:viewcount")).willReturn(Map.of(상품Id.toString(), 7L));

        // @Transactional 없이 호출한다 — 스케줄러가 자기 트랜잭션을 열지 못하면 여기서 터진다.
        scheduler.flush();

        assertThat(goodsRepository.findById(상품Id)).get()
                .satisfies(g -> assertThat(g.getViewCount()).isEqualTo(기존 + 7));
        verify(redisTemplate).delete("bb:viewcount");
    }

    @Test
    void 토글이_켜지면_Redis_구현이_주입된다(@Autowired ViewCountRecorder recorder) {
        assertThat(recorder).isInstanceOf(RedisViewCountRecorder.class);
    }
}
