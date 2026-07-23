package com.beautyboy.search;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 인기검색어 집계 결과 보관소.
 *
 * <p>조회할 때마다 집계하지 않는 이유: 인기검색어는 메인·검색창에서 거의 모든 방문자에게 노출되는데
 * 그때마다 24시간치 로그를 group by 하면 가장 흔한 요청이 가장 무거운 쿼리가 된다.
 * 설계 8장이 "매시 집계 → 캐시"로 정한 이유가 그것이다.
 *
 * <p><b>인메모리 보관이라 앱 1대 전제다.</b> 다중화하면 인스턴스마다 다른 목록을 보여주게 되므로,
 * 그 시점에 Redis로 옮긴다 — 이 클래스가 그 교체 지점이다.
 * (Redis는 1차 범위 밖: 로드맵 2026-07-24 결정)
 *
 * <p>{@link AtomicReference}에 불변 리스트를 통째로 갈아끼운다. 부분 갱신이 없으므로 락이 필요 없고,
 * 읽는 쪽은 항상 일관된 스냅샷을 본다(갱신 중 절반만 채워진 목록을 보는 일이 없다).
 */
@Component
public class PopularKeywordHolder {

    private static final int TOP_N = 10;
    private static final int WINDOW_HOURS = 24;

    private final SearchKeywordLogRepository searchKeywordLogRepository;
    private final AtomicReference<List<String>> keywords = new AtomicReference<>(List.of());

    public PopularKeywordHolder(SearchKeywordLogRepository searchKeywordLogRepository) {
        this.searchKeywordLogRepository = searchKeywordLogRepository;
    }

    /** 최근 24시간 로그를 집계해 보관값을 교체한다. 스케줄러(T1-7)가 매시 호출한다. */
    @Transactional(readOnly = true)
    public void refresh() {
        List<String> top = searchKeywordLogRepository.findTopKeywordsSince(
                LocalDateTime.now().minusHours(WINDOW_HOURS), PageRequest.of(0, TOP_N));
        keywords.set(List.copyOf(top));
    }

    /** 마지막 집계 결과. 한 번도 집계되지 않았으면 빈 목록(널이 아니다). */
    public List<String> current() {
        return keywords.get();
    }

    /** 테스트가 부팅 직후 상태를 재현할 때 쓴다. 운영 경로에서는 호출하지 않는다. */
    public void reset() {
        keywords.set(List.of());
    }
}
