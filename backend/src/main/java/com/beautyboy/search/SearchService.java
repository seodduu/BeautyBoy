package com.beautyboy.search;

import com.beautyboy.catalog.GoodsRatingProvider;
import com.beautyboy.catalog.WishedGoodsProvider;
import com.beautyboy.common.PageResponse;
import com.beautyboy.search.dto.SearchCondition;
import com.beautyboy.search.dto.SearchResultItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 검색 오케스트레이션.
 *
 * <p>이 클래스에는 SQL이 한 줄도 없다 — 질의는 전부 {@link GoodsSearchRepository} 뒤에 있다.
 * 그래야 2차에서 Elasticsearch로 갈아끼울 때 서비스와 컨트롤러를 건드리지 않는다(설계 8장).
 *
 * <p>별점·찜은 catalog가 정의한 {@link GoodsRatingProvider}/{@link WishedGoodsProvider}를 통해서만
 * 채운다 — search는 review/wishlist 테이블을 직접 알 수 없다(패키지 = 서비스 경계).
 */
@Service
public class SearchService {

    private final GoodsSearchRepository goodsSearchRepository;
    private final SearchKeywordLogRepository searchKeywordLogRepository;
    private final PopularKeywordHolder popularKeywordHolder;
    private final GoodsRatingProvider goodsRatingProvider;
    private final WishedGoodsProvider wishedGoodsProvider;

    public SearchService(GoodsSearchRepository goodsSearchRepository,
                         SearchKeywordLogRepository searchKeywordLogRepository,
                         PopularKeywordHolder popularKeywordHolder,
                         GoodsRatingProvider goodsRatingProvider,
                         WishedGoodsProvider wishedGoodsProvider) {
        this.goodsSearchRepository = goodsSearchRepository;
        this.searchKeywordLogRepository = searchKeywordLogRepository;
        this.popularKeywordHolder = popularKeywordHolder;
        this.goodsRatingProvider = goodsRatingProvider;
        this.wishedGoodsProvider = wishedGoodsProvider;
    }

    @Transactional
    public PageResponse<SearchResultItem> search(SearchCondition condition, Long memberId) {
        // 로그를 먼저 남긴다 — 결과가 0건인 검색어야말로 "찾는데 없는 것"이라 가장 알고 싶은 데이터다.
        searchKeywordLogRepository.save(
                new SearchKeywordLog(condition.keyword(), memberId, LocalDateTime.now()));

        List<GoodsSearchRepository.SearchRow> rows = goodsSearchRepository.search(condition);
        long totalElements = goodsSearchRepository.count(condition);

        // N+1 방지: 배지(원래 미채움)와 별개로, 별점·찜은 goodsId를 모아 각각 한 번씩만 조회한다.
        List<Long> goodsIds = rows.stream().map(GoodsSearchRepository.SearchRow::goodsId).toList();
        Map<Long, GoodsRatingProvider.RatingStat> ratingsByGoodsId = goodsRatingProvider.ratingsByGoods(goodsIds);
        Set<Long> wishedGoodsIds = wishedGoodsProvider.wishedGoodsIds(memberId, goodsIds);

        List<SearchResultItem> items = rows.stream()
                .map(row -> toItem(row, ratingsByGoodsId.get(row.goodsId()), wishedGoodsIds.contains(row.goodsId())))
                .toList();

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

    private SearchResultItem toItem(GoodsSearchRepository.SearchRow row,
                                     GoodsRatingProvider.RatingStat ratingStat, boolean wished) {
        return new SearchResultItem(
                row.goodsId(),
                row.brandName(),
                row.name(),
                row.thumbnailUrl(),
                row.listPrice(),
                row.salePrice(),
                discountRate(row.listPrice(), row.salePrice()),
                // 배지는 promotion(catalog 소유) 조인이 필요한데 검색 쿼리 교체 지점(GoodsSearchRepository)
                // 밖에서는 SQL을 못 쓴다. 빈 목록으로 두고 이후 웨이브에서 채운다 — 계약 형태는 이미 맞다.
                List.of(),
                ratingStat == null ? 0.0 : ratingStat.rating(),
                ratingStat == null ? 0 : ratingStat.reviewCount(),
                wished,
                false);
    }

    private int discountRate(int listPrice, int salePrice) {
        if (listPrice == 0) {
            return 0;
        }
        return (listPrice - salePrice) * 100 / listPrice;
    }
}
