package com.beautyboy.ranking;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Category;
import com.beautyboy.catalog.CategoryRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.support.TestPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GoodsViewCountTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    GoodsDailyStatRepository goodsDailyStatRepository;
    @Autowired
    PlatformTransactionManager transactionManager;
    @PersistenceContext
    EntityManager entityManager;

    /**
     * 인터셉터가 {@code REQUIRES_NEW}로 커밋하므로 goods_daily_stat에 남긴 행은
     * 테스트 메서드의 롤백 경계 밖에 있다 — 이전 테스트가 커밋한 행이 다음 테스트까지
     * 살아남는다. 그래서 테스트 트랜잭션(TX_outer)과 별개로, 이 정리도 자체 커밋되는
     * REQUIRES_NEW 트랜잭션으로 실행해야 매 테스트가 깨끗한 상태에서 시작한다.
     */
    @BeforeEach
    void 이전_테스트가_남긴_통계를_지운다() {
        TransactionTemplate 새_트랜잭션 = new TransactionTemplate(transactionManager);
        새_트랜잭션.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        새_트랜잭션.executeWithoutResult(status -> goodsDailyStatRepository.deleteAllInBatch());
    }

    @Test
    void 상품_상세를_보면_오늘자_조회수가_증가한다() throws Exception {
        Long goodsId = 상품_저장();

        mockMvc.perform(get("/api/v1/goods/" + goodsId)).andExpect(status().isOk());

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(조회수(goodsId)).isEqualTo(1);
    }

    @Test
    void 두_번_보면_같은_행이_누적된다() throws Exception {
        // PK가 (goods_id, stat_date) 복합이라 upsert가 성립해야 한다.
        // 여기서 실패하면 두 번째 조회가 PK 중복으로 터지거나 행이 2개 생긴다.
        Long goodsId = 상품_저장();

        mockMvc.perform(get("/api/v1/goods/" + goodsId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/goods/" + goodsId)).andExpect(status().isOk());

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(조회수(goodsId)).isEqualTo(2);
        assertThat(goodsDailyStatRepository.findAll()).hasSize(1);
    }

    @Test
    void 목록_조회로는_조회수가_오르지_않는다() throws Exception {
        // 목록에 노출된 것과 상세를 연 것은 다른 사건이다.
        // 목록까지 세면 첫 페이지에 있다는 이유만으로 랭킹이 오르는 되먹임이 생긴다.
        상품_저장();

        mockMvc.perform(get("/api/v1/goods")).andExpect(status().isOk());

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(goodsDailyStatRepository.findAll()).isEmpty();
    }

    @Test
    void 없는_상품을_조회하면_통계를_남기지_않는다() throws Exception {
        // 404 응답에 통계를 남기면 존재하지 않는 goods_id로 원장이 오염된다.
        mockMvc.perform(get("/api/v1/goods/999999")).andExpect(status().isNotFound());

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(goodsDailyStatRepository.findAll()).isEmpty();
    }

    private Long 상품_저장() {
        // GoodsService.detail()이 카테고리 경로(C001 → C001001 → C001001001)를 조회하므로
        // 조회수 인터셉터가 아닌 catalog 쪽 로직이 필요로 하는 카테고리 픽스처를 먼저 심는다.
        categoryRepository.save(new Category("C001", null, "스킨케어", 1, 0));
        categoryRepository.save(new Category("C001001", "C001", "토너/스킨", 2, 0));
        categoryRepository.save(new Category("C001001001", "C001001", "토너", 3, 0));

        Brand brand = brandRepository.save(new Brand("브랜드", null));
        Goods goods = goodsRepository.save(
                new Goods(brand, "C001001001", "토너", null, "https://img/1.jpg", 10000, 10000));
        return goods.getId();
    }

    private int 조회수(Long goodsId) {
        return goodsDailyStatRepository.findById(new GoodsDailyStat.Key(goodsId, LocalDate.now()))
                .map(GoodsDailyStat::getViewCount)
                .orElse(0);
    }
}
