package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.AdminGoodsSaveRequest;
import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.catalog.dto.GoodsSearchCondition;
import com.beautyboy.common.PageResponse;
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
 * B3 — 목록 조회 캐시 히트/무효화 검증. Global Constraints의 인프라 규칙대로 Redis 대신
 * {@link ConcurrentMapCacheManager}를 주입한 슬라이스 컨텍스트로 돈다(Docker 불필요).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = GoodsListCacheTest.TestConfig.class)
class GoodsListCacheTest {

    @Autowired
    GoodsService goodsService;
    @Autowired
    AdminGoodsService adminGoodsService;
    @Autowired
    GoodsQueryRepository goodsQueryRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        reset(goodsQueryRepository, goodsRepository, categoryRepository, brandRepository);
        cacheManager.getCache("goodsList").clear();
        given(goodsQueryRepository.findValidBadges(any(), any())).willReturn(Map.of());
    }

    @Test
    void 같은_목록_두번째_조회는_DB를_때리지_않는다() {
        GoodsSearchCondition condition = new GoodsSearchCondition(
                "C001", null, null, null, GoodsSort.POPULAR, 0, 20, null);
        given(goodsQueryRepository.findList(condition)).willReturn(List.of(
                new GoodsQueryRepository.GoodsRow(1L, "브랜드", "상품1", "https://img/1.jpg", 10000, 8000)));
        given(goodsQueryRepository.count(condition)).willReturn(1L);

        PageResponse<GoodsListItem> first = goodsService.list(condition, null);
        PageResponse<GoodsListItem> second = goodsService.list(condition, null);

        assertThat(first.content()).extracting(GoodsListItem::goodsNo).containsExactly(1L);
        assertThat(second.content()).extracting(GoodsListItem::goodsNo).containsExactly(1L);
        verify(goodsQueryRepository, times(1)).findList(condition);
        verify(goodsQueryRepository, times(1)).count(condition);
    }

    @Test
    void 상품_수정이_목록_캐시를_비운다() {
        GoodsSearchCondition condition = new GoodsSearchCondition(
                "C001", null, null, null, GoodsSort.POPULAR, 0, 20, null);
        given(goodsQueryRepository.findList(condition)).willReturn(List.of(
                new GoodsQueryRepository.GoodsRow(1L, "브랜드", "상품1", "https://img/1.jpg", 10000, 8000)));
        given(goodsQueryRepository.count(condition)).willReturn(1L);
        goodsService.list(condition, null); // 캐시 적재
        goodsService.list(condition, null); // 캐시 히트 — 무효화 전이므로 DB를 다시 안 때린다

        Goods goods = mock(Goods.class);
        given(goodsRepository.findById(1L)).willReturn(java.util.Optional.of(goods));
        given(categoryRepository.existsById("C001")).willReturn(true);
        AdminGoodsSaveRequest request = new AdminGoodsSaveRequest(
                1L, "C001", "상품1(수정)", "요약", "https://img/1.jpg", 10000, 8000, "SALE");
        adminGoodsService.update(1L, request);

        goodsService.list(condition, null); // 캐시 미스 — 다시 DB를 때려야 한다

        verify(goodsQueryRepository, times(2)).findList(condition);
    }

    @Configuration
    @EnableCaching(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("goodsList");
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
                                             GoodsService goodsService) {
            return new AdminGoodsService(goodsRepository, goodsQueryRepository, categoryRepository,
                    brandRepository, goodsService);
        }
    }
}
