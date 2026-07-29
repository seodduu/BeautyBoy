package com.beautyboy.compat;

import com.beautyboy.catalog.AdminGoodsService;
import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.CategoryRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsQueryRepository;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.catalog.GoodsService;
import com.beautyboy.catalog.GoodsTagRepository;
import com.beautyboy.catalog.GoodsRatingProvider;
import com.beautyboy.catalog.WishedGoodsProvider;
import com.beautyboy.catalog.dto.AdminGoodsSaveRequest;
import com.beautyboy.ingredient.GoodsIngredientQueryService;
import com.beautyboy.ingredient.IngredientRuleQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * B4 — 성분 궁합 캐시 히트/무효화 검증. Global Constraints의 인프라 규칙대로 Redis 대신
 * {@link ConcurrentMapCacheManager}를 주입한 슬라이스 컨텍스트로 돈다(Docker 불필요).
 *
 * <p>개인화 확인: {@link CompatQueryService#worstVerdicts}의 반환값(Map&lt;Long,String&gt;)은
 * 사용자 식별자를 전혀 참조하지 않는다(성분 집합·규칙표로만 결정) — 캐시 키에 viewerId를 넣을
 * 필요가 없다.
 *
 * <p>대칭성 확인: {@code CompatService.worstVerdicts}는 base·candidate 각각의 분류 집합을
 * 구해 무순서 (categoryA,categoryB) 쌍만 검사하므로 (a,b)와 (b,a)의 판정값은 항상 같다 —
 * {@link CacheKeys#compat}가 만드는 대칭 키와 응답 대칭성이 일치한다.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CompatCacheTest.TestConfig.class)
class CompatCacheTest {

    @Autowired
    CompatQueryService compatQueryService;
    @Autowired
    AdminGoodsService adminGoodsService;
    @Autowired
    GoodsIngredientQueryService goodsIngredientQueryService;
    @Autowired
    IngredientRuleQueryService ingredientRuleQueryService;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        reset(goodsIngredientQueryService, ingredientRuleQueryService, goodsRepository, categoryRepository);
        cacheManager.getCache("compat").clear();
        given(goodsIngredientQueryService.findCategoriesByGoodsIds(any()))
                .willReturn(Map.of(1L, Set.of("AHA"), 2L, Set.of("RETINOID")));
        given(ingredientRuleQueryService.findAll()).willReturn(List.of(
                new IngredientRuleQueryService.RuleVerdict("AHA", "RETINOID", "CONFLICT", "자극 중첩")));
    }

    @Test
    void 상품쌍_순서를_바꿔도_같은_캐시를_쓴다() {
        String first = compatQueryService.worstVerdict(1L, 2L);
        String second = compatQueryService.worstVerdict(2L, 1L);

        assertThat(first).isEqualTo("CONFLICT");
        assertThat(second).isEqualTo("CONFLICT");
        verify(goodsIngredientQueryService, times(1)).findCategoriesByGoodsIds(any());
    }

    @Test
    void 성분이_바뀌면_궁합_캐시가_비워진다() {
        compatQueryService.worstVerdict(1L, 2L); // 캐시 적재
        compatQueryService.worstVerdict(1L, 2L); // 캐시 히트 — 무효화 전이므로 다시 안 때린다
        verify(goodsIngredientQueryService, times(1)).findCategoriesByGoodsIds(any());

        Goods goods = mock(Goods.class);
        given(goodsRepository.findById(1L)).willReturn(Optional.of(goods));
        given(categoryRepository.existsById("C001")).willReturn(true);
        AdminGoodsSaveRequest request = new AdminGoodsSaveRequest(
                1L, "C001", "상품1(수정)", "요약", "https://img/1.jpg", 10000, 8000, "SALE");
        adminGoodsService.update(1L, request);

        compatQueryService.worstVerdict(1L, 2L); // 캐시 미스 — 다시 때려야 한다

        verify(goodsIngredientQueryService, times(2)).findCategoriesByGoodsIds(any());
    }

    @Configuration
    @EnableCaching(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("compat", "goodsList");
        }

        @Bean
        GoodsIngredientQueryService goodsIngredientQueryService() {
            return mock(GoodsIngredientQueryService.class);
        }

        @Bean
        IngredientRuleQueryService ingredientRuleQueryService() {
            return mock(IngredientRuleQueryService.class);
        }

        @Bean
        CompatService compatService(GoodsIngredientQueryService goodsIngredientQueryService,
                                     IngredientRuleQueryService ingredientRuleQueryService) {
            return new CompatService(goodsIngredientQueryService, ingredientRuleQueryService);
        }

        @Bean
        GoodsQueryRepository goodsQueryRepository() {
            return mock(GoodsQueryRepository.class);
        }

        @Bean
        GoodsRepository goodsRepository() {
            return mock(GoodsRepository.class);
        }

        @Bean
        CategoryRepository categoryRepository() {
            return mock(CategoryRepository.class);
        }

        @Bean
        BrandRepository brandRepository() {
            return mock(BrandRepository.class);
        }

        @Bean
        GoodsRatingProvider goodsRatingProvider() {
            GoodsRatingProvider provider = mock(GoodsRatingProvider.class);
            given(provider.ratingsByGoods(any())).willReturn(Map.of());
            return provider;
        }

        @Bean
        WishedGoodsProvider wishedGoodsProvider() {
            WishedGoodsProvider provider = mock(WishedGoodsProvider.class);
            given(provider.wishedGoodsIds(any(), any())).willReturn(Set.of());
            return provider;
        }

        @Bean
        GoodsTagRepository goodsTagRepository() {
            GoodsTagRepository repository = mock(GoodsTagRepository.class);
            given(repository.findTagsByGoodsIds(any())).willReturn(Map.of());
            return repository;
        }

        @Bean
        GoodsService goodsService(GoodsRepository goodsRepository, GoodsQueryRepository goodsQueryRepository,
                                   CategoryRepository categoryRepository, GoodsRatingProvider goodsRatingProvider,
                                   WishedGoodsProvider wishedGoodsProvider, GoodsTagRepository goodsTagRepository) {
            return new GoodsService(goodsRepository, goodsQueryRepository, categoryRepository,
                    goodsRatingProvider, wishedGoodsProvider, goodsTagRepository);
        }

        @Bean
        AdminGoodsService adminGoodsService(GoodsRepository goodsRepository, GoodsQueryRepository goodsQueryRepository,
                                             CategoryRepository categoryRepository, BrandRepository brandRepository,
                                             GoodsService goodsService, CacheManager cacheManager) {
            AdminGoodsService service = new AdminGoodsService(goodsRepository, goodsQueryRepository,
                    categoryRepository, brandRepository, goodsService);
            service.setCacheManager(cacheManager);
            return service;
        }
    }
}
