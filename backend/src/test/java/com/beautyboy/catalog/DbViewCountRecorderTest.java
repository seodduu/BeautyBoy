package com.beautyboy.catalog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 기본(폴백) 조회수 기록 경로. Redis 토글이 꺼진(기본) 컨텍스트라 주입되는 구현은 {@link DbViewCountRecorder}다 —
 * 즉 이 테스트는 "docker compose 없이 백엔드만 띄우기"의 조회수 경로를 그대로 검증한다.
 *
 * <p><b>클래스에 {@code @Transactional}을 걸지 않는다.</b> 운영에서 이 구현은 호출자의
 * {@code readOnly} 트랜잭션을 피해 <b>자기 트랜잭션(REQUIRES_NEW)</b>을 연다. 테스트를 트랜잭션으로
 * 감싸면 새 트랜잭션이 아직 커밋되지 않은 테스트 데이터를 못 봐 0행만 갱신되므로, 실제 동작과 같게
 * 커밋된 데이터로 검증하고 만든 데이터는 {@link #정리()}에서 직접 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DbViewCountRecorderTest {

    @Autowired
    ViewCountRecorder recorder;
    @Autowired
    MockMvc mockMvc;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    PlatformTransactionManager transactionManager;
    @MockBean
    GoodsRatingProvider goodsRatingProvider;
    @MockBean
    WishedGoodsProvider wishedGoodsProvider;

    private Long 상품Id;
    private Long 브랜드Id;

    @AfterEach
    void 정리() {
        if (상품Id != null) {
            goodsRepository.deleteById(상품Id);
            상품Id = null;
        }
        if (브랜드Id != null) {
            brandRepository.deleteById(브랜드Id);
            브랜드Id = null;
        }
        categoryRepository.deleteAllById(List.of("C009001001", "C009001", "C009"));
    }

    /**
     * 이 테스트는 커밋을 하므로 다른 테스트와 겹치지 않는 전용 카테고리 코드(C009*)를 쓴다.
     * 흔한 C001*을 커밋해두면, 같은 코드를 {@code existsById}로 건너뛰고 이름을 단언하는 다른 테스트
     * (예: GoodsDetailApiTest의 categoryPath 검증)를 깨뜨린다. 만든 것은 {@link #정리()}에서 지운다.
     */
    private Goods 상품_저장() {
        Brand brand = brandRepository.save(new Brand("조회수브랜드", null));
        브랜드Id = brand.getId();
        // 상세는 depth 1~3 전 계층을 이름으로 조립하므로 3단을 모두 만든다(하나라도 없으면 상세가 500).
        categoryRepository.save(new Category("C009", null, "조회수대분류", 1, 0));
        categoryRepository.save(new Category("C009001", "C009", "조회수중분류", 2, 0));
        categoryRepository.save(new Category("C009001001", "C009001", "조회수소분류", 3, 0));
        Goods goods = goodsRepository.save(
                new Goods(brand, "C009001001", "토너", "요약", "https://img.example/토너.jpg", 10000, 9000));
        상품Id = goods.getId();
        return goods;
    }

    @Test
    void 폴백은_DB_구현이다() {
        assertThat(recorder).isInstanceOf(DbViewCountRecorder.class);
    }

    @Test
    void 조회수를_1_증가시킨다() {
        Goods 상품A = 상품_저장();
        int 기존 = 상품A.getViewCount();

        recorder.record(상품Id);

        assertThat(goodsRepository.findById(상품Id)).get()
                .satisfies(g -> assertThat(g.getViewCount()).isEqualTo(기존 + 1));
    }

    @Test
    void 없는_상품을_기록해도_예외를_던지지_않는다() {
        assertThatCode(() -> recorder.record(999999L)).doesNotThrowAnyException();
    }

    /**
     * 상세 <b>엔드포인트</b>가 실제로 조회수를 올리는지. 기록 호출이 서비스가 아니라 컨트롤러에 있으므로
     * (커넥션 2개 동시 점유를 피하려고 트랜잭션 바깥으로 뺐다) 그 배선을 여기서 지킨다.
     * 200 응답과 +1이 함께 확인돼야 한다 — 기록이 상세를 깨뜨리지 않는다는 뜻이기도 하다.
     */
    @Test
    void 상세_엔드포인트_조회는_조회수를_올린다() throws Exception {
        상품_저장();

        mockMvc.perform(get("/api/v1/goods/" + 상품Id)).andExpect(status().isOk());

        assertThat(goodsRepository.findById(상품Id)).get()
                .satisfies(g -> assertThat(g.getViewCount()).isEqualTo(1));
    }

    @Test
    void 없는_상품_상세는_404이고_조회수를_남기지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/goods/999999")).andExpect(status().isNotFound());

        assertThat(goodsRepository.findById(999999L)).isEmpty();
    }

    /**
     * 이 태스크에서 실제로 터진 결함의 회귀 테스트.
     *
     * <p>상세 조회는 {@code readOnly} 트랜잭션이라, 기록이 그 트랜잭션에 <b>합류</b>하면 실 MySQL에서
     * "Connection is read-only"로 거부돼 상세가 500이 된다. H2는 readOnly를 강제하지 않아 그 자체를
     * 재현할 수 없으므로, 대신 <b>바깥 트랜잭션과 독립적인 트랜잭션인지</b>를 검증한다 — 바깥이 롤백돼도
     * 증가분이 살아남으면 합류한 게 아니라는 증거이고, 그것이 곧 readOnly를 피하는 성질이다.
     */
    @Test
    void 바깥_트랜잭션이_롤백돼도_조회수는_남는다() {
        상품_저장();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            recorder.record(상품Id);
            status.setRollbackOnly();
        });

        assertThat(goodsRepository.findById(상품Id)).get()
                .satisfies(g -> assertThat(g.getViewCount()).isEqualTo(1));
    }
}
