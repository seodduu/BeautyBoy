package com.beautyboy.ranking;

import com.beautyboy.catalog.GoodsRatingProvider;
import com.beautyboy.catalog.WishedGoodsProvider;
import com.beautyboy.ranking.dto.RankingItem;
import com.beautyboy.search.PopularKeywordHolder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * B2 — 랭킹 캐시 히트/무효화·워밍 검증. Global Constraints의 인프라 규칙대로 Redis 대신
 * {@link ConcurrentMapCacheManager}를 주입한 슬라이스 컨텍스트로 돈다(Docker 불필요).
 * 검증 대상은 "두 번째 조회가 리포지토리를 다시 안 때린다"는 캐싱 동작이다.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RankingCacheTest.TestConfig.class)
class RankingCacheTest {

    @Autowired
    RankingService rankingService;
    @Autowired
    RankingCacheRefresher rankingCacheRefresher;
    @Autowired
    RankingSnapshotRepository rankingSnapshotRepository;
    @Autowired
    GoodsRatingProvider goodsRatingProvider;
    @Autowired
    WishedGoodsProvider wishedGoodsProvider;
    @Autowired
    EntityManager entityManager;
    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        reset(rankingSnapshotRepository, goodsRatingProvider, wishedGoodsProvider, entityManager);
        cacheManager.getCache("ranking").clear();
        given(goodsRatingProvider.ratingsByGoods(any())).willReturn(Map.of());
        given(wishedGoodsProvider.wishedGoodsIds(any(), any())).willReturn(Set.of());
        상품_조회_스텁(entityManager);
    }

    @Test
    void 같은_카테고리_두번째_조회는_DB를_때리지_않는다() {
        given(rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc("ALL"))
                .willReturn(List.of(스냅샷(1L, 1, 100.0)));

        rankingService.rankings("ALL", null);
        rankingService.rankings("ALL", null);

        verify(rankingSnapshotRepository, times(1)).findByCategoryCodeOrderByRankNoAsc("ALL");
    }

    @Test
    void rebuild가_끝나면_캐시는_새_스냅샷을_반환한다() {
        given(rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc("ALL"))
                .willReturn(List.of(스냅샷(1L, 1, 100.0)));
        List<RankingItem> before = rankingService.rankings("ALL", null);
        assertThat(before).extracting(RankingItem::goodsNo).containsExactly(1L);

        // 배치가 스냅샷을 교체했다고 가정 — 새 goodsId 2L이 1위가 됨
        given(rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc("ALL"))
                .willReturn(List.of(스냅샷(2L, 1, 200.0)));
        given(rankingSnapshotRepository.findAll())
                .willReturn(List.of(스냅샷(2L, 1, 200.0)));

        rankingCacheRefresher.refreshAfterRebuild();

        List<RankingItem> after = rankingService.rankings("ALL", null);
        assertThat(after).extracting(RankingItem::goodsNo).containsExactly(2L);
        // 워밍이 이미 채웠으므로 이 호출은 DB를 다시 안 때린다:
        // find는 초기캐싱 1회 + 워밍 1회 = 2회만 발생해야 한다.
        verify(rankingSnapshotRepository, times(2)).findByCategoryCodeOrderByRankNoAsc("ALL");
    }

    @Test
    void rebuild가_실패하면_기존_캐시가_유지된다() {
        given(rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc("ALL"))
                .willReturn(List.of(스냅샷(1L, 1, 100.0)));
        rankingService.rankings("ALL", null); // 캐시 적재

        RankingBatchService failingBatch = mock(RankingBatchService.class);
        willThrow(new RuntimeException("배치 실패")).given(failingBatch).rebuild();
        PopularKeywordHolder popularKeywordHolder = mock(PopularKeywordHolder.class);
        RankingScheduler scheduler = new RankingScheduler(failingBatch, popularKeywordHolder, rankingCacheRefresher);

        assertThatThrownBy(scheduler::랭킹_재생성).isInstanceOf(RuntimeException.class);

        // 워밍이 스킵됐으므로 캐시는 그대로 — 다시 조회해도 findByCategoryCodeOrderByRankNoAsc가 안 늘어난다
        List<RankingItem> stillCached = rankingService.rankings("ALL", null);
        assertThat(stillCached).extracting(RankingItem::goodsNo).containsExactly(1L);
        verify(rankingSnapshotRepository, times(1)).findByCategoryCodeOrderByRankNoAsc("ALL");
        verify(rankingSnapshotRepository, never()).findAll();
    }

    private RankingSnapshot 스냅샷(Long goodsId, int rankNo, double score) {
        return new RankingSnapshot(RankingSnapshot.CATEGORY_ALL, goodsId, rankNo, score, LocalDateTime.now());
    }

    private void 상품_조회_스텁(EntityManager em) {
        Query query = mock(Query.class);
        given(em.createNativeQuery(anyString())).willReturn(query);
        given(query.setParameter(anyString(), any())).willReturn(query);
        given(query.getResultList()).willReturn(List.of(
                new Object[]{1L, "브랜드", "상품1", "https://img/1.jpg", 10000, 8000},
                new Object[]{2L, "브랜드", "상품2", "https://img/2.jpg", 20000, 15000}));
    }

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("ranking");
        }

        @Bean
        RankingSnapshotRepository rankingSnapshotRepository() {
            return mock(RankingSnapshotRepository.class);
        }

        @Bean
        EntityManager entityManager() {
            return mock(EntityManager.class);
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
        RankingService rankingService(RankingSnapshotRepository rankingSnapshotRepository, EntityManager entityManager,
                                       GoodsRatingProvider goodsRatingProvider, WishedGoodsProvider wishedGoodsProvider) {
            return new RankingService(rankingSnapshotRepository, entityManager, goodsRatingProvider, wishedGoodsProvider);
        }

        @Bean
        RankingCacheRefresher rankingCacheRefresher(ObjectProvider<CacheManager> cacheManagerProvider,
                                                      RankingSnapshotRepository rankingSnapshotRepository,
                                                      RankingService rankingService) {
            return new RankingCacheRefresher(cacheManagerProvider, rankingSnapshotRepository, rankingService);
        }
    }
}
