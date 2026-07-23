package com.beautyboy.ranking;

import com.beautyboy.search.PopularKeywordHolder;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매시 배치 트리거.
 *
 * <p>{@code @Profile("!test")}인 이유: 테스트에서 스케줄러가 저절로 돌면 테스트가 만든 픽스처를
 * 배치가 덮어쓰거나, 테스트가 검증하려던 스냅샷을 지워 간헐적으로 실패한다.
 * 테스트는 배치 메서드를 직접 호출해 검증한다.
 *
 * <p>{@code @EnableScheduling}이 여기 붙어 있는 것은 앱 전체 스케줄링을 켠다는 뜻이다 —
 * 인기검색어 집계도 이 설정에 얹혀 돈다. 스케줄 대상이 늘면 이 클래스에 메서드를 추가한다.
 *
 * <p>두 집계를 한 클래스에 둔 이유: 배치가 늘 때마다 도메인마다 스케줄러 클래스를 만들면
 * {@code @EnableScheduling}이 어디 붙어 있는지 추적하기 어려워진다. 트리거는 한 곳, 로직은 각 도메인.
 */
@Component
@Profile("!test")
@EnableScheduling
public class RankingScheduler {

    private final RankingBatchService rankingBatchService;
    private final PopularKeywordHolder popularKeywordHolder;

    public RankingScheduler(RankingBatchService rankingBatchService,
                            PopularKeywordHolder popularKeywordHolder) {
        this.rankingBatchService = rankingBatchService;
        this.popularKeywordHolder = popularKeywordHolder;
    }

    /** 매시 정각. 초기 지연 없이 부팅 직후 한 번 돌리지 않는 이유: 부팅 시점에 통계가 비어 있어도 정상이다. */
    @Scheduled(cron = "0 0 * * * *")
    public void 랭킹_재생성() {
        rankingBatchService.rebuild();
    }

    /** 인기검색어도 매시 갱신한다(설계 8장). 랭킹과 5분 어긋나게 둬 DB 부하가 겹치지 않게 한다. */
    @Scheduled(cron = "0 5 * * * *")
    public void 인기검색어_갱신() {
        popularKeywordHolder.refresh();
    }
}
