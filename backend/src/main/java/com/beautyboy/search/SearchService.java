package com.beautyboy.search;

import com.beautyboy.common.PageResponse;
import com.beautyboy.search.dto.SearchCondition;
import com.beautyboy.search.dto.SearchResultItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 검색 오케스트레이션.
 *
 * <p>이 클래스에는 SQL이 한 줄도 없다 — 질의는 전부 {@link GoodsSearchRepository} 뒤에 있다.
 * 그래야 2차에서 Elasticsearch로 갈아끼울 때 서비스와 컨트롤러를 건드리지 않는다(설계 8장).
 */
@Service
public class SearchService {

    private final GoodsSearchRepository goodsSearchRepository;
    private final SearchKeywordLogRepository searchKeywordLogRepository;
    private final PopularKeywordHolder popularKeywordHolder;

    public SearchService(GoodsSearchRepository goodsSearchRepository,
                         SearchKeywordLogRepository searchKeywordLogRepository,
                         PopularKeywordHolder popularKeywordHolder) {
        this.goodsSearchRepository = goodsSearchRepository;
        this.searchKeywordLogRepository = searchKeywordLogRepository;
        this.popularKeywordHolder = popularKeywordHolder;
    }

    @Transactional
    public PageResponse<SearchResultItem> search(SearchCondition condition, Long memberId) {
        // 로그를 먼저 남긴다 — 결과가 0건인 검색어야말로 "찾는데 없는 것"이라 가장 알고 싶은 데이터다.
        searchKeywordLogRepository.save(
                new SearchKeywordLog(condition.keyword(), memberId, LocalDateTime.now()));

        List<GoodsSearchRepository.SearchRow> rows = goodsSearchRepository.search(condition);
        long totalElements = goodsSearchRepository.count(condition);

        List<SearchResultItem> items = rows.stream().map(this::toItem).toList();

        return PageResponse.of(items, condition.page(), condition.size(), totalElements);
    }

    /** 매시 집계된 인기검색어. 집계 전이면 빈 목록. */
    public List<String> popularKeywords() {
        return popularKeywordHolder.current();
    }

    /**
     * 자동완성 후보.
     *
     * <p>검색어가 짧으면 예외 대신 빈 목록을 준다 — 이 엔드포인트는 타이핑 중 매 글자 호출되므로,
     * 정상적인 입력 과정을 에러로 취급하면 프론트가 진짜 장애를 구분하지 못한다.
     */
    @Transactional(readOnly = true)
    public List<String> autocomplete(String keyword, int minLength, int limit) {
        String trimmed = keyword.trim();
        if (trimmed.length() < minLength) {
            return List.of();
        }
        return goodsSearchRepository.autocomplete(trimmed, limit);
    }

    private SearchResultItem toItem(GoodsSearchRepository.SearchRow row) {
        return new SearchResultItem(
                row.goodsId(),
                row.brandName(),
                row.name(),
                row.thumbnailUrl(),
                row.listPrice(),
                row.salePrice(),
                discountRate(row.listPrice(), row.salePrice()),
                // 배지는 promotion(catalog 소유) 조인이 필요한데 이번 웨이브에서 catalog는 T2 소유다.
                // 빈 목록으로 두고 Wave 4 통합에서 채운다 — 계약 형태는 이미 맞다.
                List.of(),
                0.0,
                0,
                false,
                false);
    }

    private int discountRate(int listPrice, int salePrice) {
        if (listPrice == 0) {
            return 0;
        }
        return (listPrice - salePrice) * 100 / listPrice;
    }
}
