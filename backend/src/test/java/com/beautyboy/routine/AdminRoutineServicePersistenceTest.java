package com.beautyboy.routine;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Category;
import com.beautyboy.catalog.CategoryRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.support.TestPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * replaceStepGoods가 실제로 DB에 delete-then-insert를 반영하는지 검증.
 *
 * <p>{@link AdminRoutineServiceTest}는 Mockito 단위 테스트라 RoutineStep.stepGoods를 in-memory Set으로만
 * 관찰한다 — cascade=ALL + orphanRemoval=true가 flush 시점에 실제로 DELETE/INSERT SQL을 내는지는 증명하지
 * 못한다. 이 프로젝트에는 "H2 create-drop이 validate 불일치를 가린다"는 교훈이 있고, 여기서는 그 종류의
 * 문제(영속성 컨텍스트 캐시가 실제 DB 상태를 가리는 것)를 {@link TestPersistence#DB_왕복_강제}로 걷어내고
 * routine_step_goods 테이블을 네이티브 쿼리로 직접 읽어 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminRoutineServicePersistenceTest {

    @Autowired
    AdminRoutineService adminRoutineService;
    @Autowired
    RoutineTemplateRepository routineTemplateRepository;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    GoodsRepository goodsRepository;

    @PersistenceContext
    EntityManager em;

    @Test
    void 단계_추천상품을_교체하면_빠진_행은_DB에서_실제로_삭제되고_순서는_1부터_다시_매겨진다() {
        Brand brand = brandRepository.save(new Brand("브랜드1", null));
        categoryRepository.save(new Category("C001001001", null, "카테고리", 3, 0));
        Goods 상품1 = 상품_저장(brand, "상품1");
        Goods 상품2 = 상품_저장(brand, "상품2");
        Goods 상품3 = 상품_저장(brand, "상품3");
        Goods 상품4 = 상품_저장(brand, "상품4");

        RoutineTemplate template = new RoutineTemplate(null, "건성 루틴", "DRY", "PERSIST_TEST", "설명",
                List.of(new RoutineStep(null, 1, "클렌징", "팁", List.of(
                        new RoutineStepGoods(null, 상품1.getId(), 1),
                        new RoutineStepGoods(null, 상품2.getId(), 2),
                        new RoutineStepGoods(null, 상품3.getId(), 3)))));
        template = routineTemplateRepository.save(template);
        TestPersistence.DB_왕복_강제(em);

        Long templateId = template.getId();
        Long stepId = routineTemplateRepository.findGraphById(templateId).orElseThrow()
                .getSteps().iterator().next().getId();

        // 3개(1,2,3) → 2개(4,2), 순서도 뒤바뀐 세트로 통째 교체
        adminRoutineService.replaceStepGoods(templateId, 1, List.of(상품4.getId(), 상품2.getId()));
        TestPersistence.DB_왕복_강제(em);

        List<Object[]> rows = em.createNativeQuery(
                        "select goods_no, sort_order from routine_step_goods where step_id = ?1 order by sort_order")
                .setParameter(1, stepId)
                .getResultList();

        assertThat(rows).hasSize(2);
        assertThat(rows.stream().map(r -> ((Number) r[0]).longValue()).toList())
                .containsExactly(상품4.getId(), 상품2.getId());
        assertThat(rows.stream().map(r -> ((Number) r[1]).intValue()).toList())
                .containsExactly(1, 2);

        // 빠진 상품1·상품3은 정말 사라졌다 — 남은 2건에 없는지 재확인
        assertThat(rows.stream().map(r -> ((Number) r[0]).longValue()).toList())
                .doesNotContain(상품1.getId(), 상품3.getId());

        long totalCount = ((Number) em.createNativeQuery(
                        "select count(*) from routine_step_goods where step_id = ?1")
                .setParameter(1, stepId)
                .getSingleResult()).longValue();
        assertThat(totalCount).isEqualTo(2);
    }

    private Goods 상품_저장(Brand brand, String name) {
        Goods goods = new Goods(brand, "C001001001", name, "요약", "https://img.example/" + name + ".jpg",
                10000, 9000);
        return goodsRepository.save(goods);
    }
}
