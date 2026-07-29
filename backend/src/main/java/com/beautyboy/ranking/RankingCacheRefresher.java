package com.beautyboy.ranking;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 랭킹 배치(rebuild) 직후 {@code ranking} 캐시를 비우고 다시 채운다(설계 §6의 워밍 1안).
 *
 * <p>배치가 매시 도는 것을 그대로 이용하므로 확률적 soft-TTL(2안)은 구현하지 않는다.
 * clear만 하고 워밍을 생략하면 배치 직후 몰리는 조회가 한꺼번에 캐시 미스로 DB를 때리는
 * 스탬피드가 나므로, 카테고리 전부(ALL 포함)를 미리 조회해 캐시에 채워 둔다.
 *
 * <p>{@link ObjectProvider}로 {@link CacheManager}를 받는 이유: {@code beautyboy.cache.redis}
 * 토글이 꺼져 있으면 {@code CacheManager} 빈 자체가 없다({@code CacheConfig} 참고). 그 상태에서도
 * 배치는 정상적으로 돌아야 하므로 캐시 매니저·캐시 부재 모두 조용히 스킵한다(NPE 금지).
 */
@Component
public class RankingCacheRefresher {

    private static final String CACHE_NAME = "ranking";

    private final ObjectProvider<CacheManager> cacheManagerProvider;
    private final RankingSnapshotRepository rankingSnapshotRepository;
    private final RankingService rankingService;

    public RankingCacheRefresher(ObjectProvider<CacheManager> cacheManagerProvider,
                                  RankingSnapshotRepository rankingSnapshotRepository,
                                  RankingService rankingService) {
        this.cacheManagerProvider = cacheManagerProvider;
        this.rankingSnapshotRepository = rankingSnapshotRepository;
        this.rankingService = rankingService;
    }

    public void refreshAfterRebuild() {
        CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
        if (cacheManager == null) {
            return;
        }
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            return;
        }

        cache.clear();

        // 워밍은 비로그인(viewerId=null) 응답만 미리 채운다 — 로그인 회원의 찜 상태는 카테고리 키에
        // 묻어 있지 않아(캐시 키가 카테고리+viewerId 조합이라 사용자별) 미리 구울 수 없고, 그 부분은
        // 어차피 첫 조회 때 자연히 채워진다. 익명 트래픽이 조회의 대다수라 스탬피드 대응 목적은 달성된다.
        Set<String> categories = rankingSnapshotRepository.findAll().stream()
                .map(RankingSnapshot::getCategoryCode)
                .collect(Collectors.toSet());
        for (String category : categories) {
            rankingService.rankings(category, null);
        }
    }
}
