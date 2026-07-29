package com.beautyboy.search;

import com.beautyboy.catalog.GoodsRatingProvider;
import com.beautyboy.catalog.WishedGoodsProvider;
import com.beautyboy.common.PageResponse;
import com.beautyboy.search.dto.SearchCondition;
import com.beautyboy.search.dto.SearchResultItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * B3 회귀 수정 — 검색 결과 캐시는 히트해야 하지만, 검색어 로그(인기검색어/0건 검색어 집계 원천)는
 * 캐시 히트 여부와 무관하게 매번 남아야 한다. {@link ConcurrentMapCacheManager}를 주입한 슬라이스
 * 컨텍스트로 돈다(Global Constraints — Redis/Docker 불필요).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SearchCacheTest.TestConfig.class)
class SearchCacheTest {

    @Autowired
    SearchService searchService;
    @Autowired
    GoodsSearchRepository goodsSearchRepository;
    @Autowired
    SearchKeywordLogRepository searchKeywordLogRepository;
    @Autowired
    GoodsRatingProvider goodsRatingProvider;
    @Autowired
    WishedGoodsProvider wishedGoodsProvider;
    @Autowired
    CacheManager cacheManager;

    private SearchCondition 조건() {
        return new SearchCondition("토너", SearchSort.ACCURACY, 0, 20);
    }

    @BeforeEach
    void setUp() {
        reset(goodsSearchRepository, searchKeywordLogRepository, goodsRatingProvider, wishedGoodsProvider);
        cacheManager.getCache("goodsList").clear();
        given(goodsSearchRepository.search(any())).willReturn(List.of());
        given(goodsSearchRepository.count(any())).willReturn(0L);
        given(goodsRatingProvider.ratingsByGoods(any())).willReturn(Map.of());
        given(wishedGoodsProvider.wishedGoodsIds(any(), any())).willReturn(Set.of());
    }

    @Test
    void 같은_검색어_두번째_조회는_DB를_때리지_않는다() {
        SearchCondition condition = 조건();

        PageResponse<SearchResultItem> first = searchService.search(condition, null);
        PageResponse<SearchResultItem> second = searchService.search(condition, null);

        verify(goodsSearchRepository, times(1)).search(condition);
        verify(goodsSearchRepository, times(1)).count(condition);
    }

    @Test
    void 캐시_히트여도_검색어_로그는_매번_남는다() {
        SearchCondition condition = 조건();

        searchService.search(condition, null); // 캐시 적재 + 로그 1
        searchService.search(condition, null); // 캐시 히트 + 로그 2(캐시와 무관하게 남아야 함)

        verify(searchKeywordLogRepository, times(2)).save(any());
    }

    @Configuration
    @EnableCaching(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("goodsList");
        }

        @Bean
        GoodsSearchRepository goodsSearchRepository() {
            return mock(GoodsSearchRepository.class);
        }

        @Bean
        SearchKeywordLogRepository searchKeywordLogRepository() {
            return mock(SearchKeywordLogRepository.class);
        }

        @Bean
        PopularKeywordHolder popularKeywordHolder() {
            return mock(PopularKeywordHolder.class);
        }

        @Bean
        GoodsRatingProvider goodsRatingProvider() {
            return mock(GoodsRatingProvider.class);
        }

        @Bean
        WishedGoodsProvider wishedGoodsProvider() {
            return mock(WishedGoodsProvider.class);
        }

        @Bean
        SearchService searchService(GoodsSearchRepository goodsSearchRepository,
                                     SearchKeywordLogRepository searchKeywordLogRepository,
                                     PopularKeywordHolder popularKeywordHolder,
                                     GoodsRatingProvider goodsRatingProvider,
                                     WishedGoodsProvider wishedGoodsProvider,
                                     ObjectProvider<SearchService> self) {
            return new SearchService(goodsSearchRepository, searchKeywordLogRepository, popularKeywordHolder,
                    goodsRatingProvider, wishedGoodsProvider, self);
        }
    }
}
