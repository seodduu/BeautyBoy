package com.beautyboy.search;

import com.beautyboy.catalog.GoodsRatingProvider;
import com.beautyboy.catalog.WishedGoodsProvider;
import com.beautyboy.common.PageResponse;
import com.beautyboy.search.dto.SearchCondition;
import com.beautyboy.search.dto.SearchResultItem;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
    private final ObjectProvider<SearchService> self;

    public SearchService(GoodsSearchRepository goodsSearchRepository,
                         SearchKeywordLogRepository searchKeywordLogRepository,
                         PopularKeywordHolder popularKeywordHolder,
                         GoodsRatingProvider goodsRatingProvider,
                         WishedGoodsProvider wishedGoodsProvider,
                         ObjectProvider<SearchService> self) {
        this.goodsSearchRepository = goodsSearchRepository;
        this.searchKeywordLogRepository = searchKeywordLogRepository;
        this.popularKeywordHolder = popularKeywordHolder;
        this.goodsRatingProvider = goodsRatingProvider;
        this.wishedGoodsProvider = wishedGoodsProvider;
        // 캐시(@Cacheable)는 프록시가 가로채야 동작하는데, this.searchCached(...)처럼 클래스 안에서
        // 스스로를 부르면 프록시를 거치지 않아 캐시가 조용히 무시된다(Spring AOP 자기호출 함정).
        // 그렇다고 별도 컴포넌트로 쪼개면 이 서비스 밖에 로그-only 클래스가 하나 더 생기므로,
        // ObjectProvider로 스스로의 프록시 빈을 늦게(lazy) 받아 생성자 순환 참조 없이 자기주입한다.
        this.self = self;
    }

    /**
     * B3 — 검색어 로그를 남기고 캐시된 조회({@link #searchCached})에 위임한다.
     *
     * <p>로그(0건 검색어 파악용)는 인기검색어 집계의 원장이라 캐시 히트 여부와 무관하게 매번
     * 남아야 한다 — 그래서 {@code @Cacheable}은 이 메서드가 아니라 {@link #searchCached}에 걸어
     * 로그 기록을 캐시 경계 밖으로 뺐다. {@code this.searchCached(...)}로 직접 부르면 프록시를
     * 거치지 않아 캐시가 무시되므로, 반드시 {@link #self}(자기주입 프록시)를 통해 호출한다.
     */
    @Transactional
    public PageResponse<SearchResultItem> search(SearchCondition condition, Long memberId) {
        // 로그를 먼저 남긴다 — 결과가 0건인 검색어야말로 "찾는데 없는 것"이라 가장 알고 싶은 데이터다.
        // 캐시 히트/미스와 무관하게 항상 실행된다(이 메서드 자체에는 @Cacheable이 없다).
        searchKeywordLogRepository.save(
                new SearchKeywordLog(condition.keyword(), memberId, LocalDateTime.now()));

        // self(ObjectProvider) 자체가 null이거나(예: Mockito @InjectMocks처럼 생성자 인자를 다 채우지
        // 못한 채 Spring 컨테이너 밖에서 만들어진 경우) 그 안에 프록시가 없으면(getIfAvailable) this로
        // 안전하게 폴백한다 — 결과는 같고 캐싱만 안 될 뿐이다.
        SearchService proxy = (self == null) ? this : self.getIfAvailable(() -> this);
        return proxy.searchCached(condition, memberId);
    }

    /**
     * B3 — 검색 결과 캐시. {@code goodsList} 캐시명을 재사용하되(TTL 맵에 등록된 이름은
     * ranking/goodsList/compat 셋뿐이라 새 이름을 쓰면 TTL 없이 무한 보관된다) 키를
     * {@code "search:"}로 접두해 목록 캐시와 섞이지 않게 한다.
     *
     * <p>{@link SearchResultItem#wished()}가 {@code memberId}에 따라 갈리는 개인화 응답이라
     * (GoodsService.list의 B3 판단과 같은 함정), 키에 검색 조건뿐 아니라 {@code memberId}까지
     * 반영한다 — 그렇지 않으면 먼저 검색한 사용자의 찜 상태가 다른 사용자에게 그대로 캐시돼 나간다.
     *
     * <p>public이어야 하는 이유: Spring의 CGLIB/JDK 프록시 기반 {@code @Cacheable}은 프록시를
     * 거치는 외부 호출만 가로챈다 — package-private/private이면 프록시가 오버라이드/노출하지
     * 못해 캐시가 걸리지 않는다. 반드시 {@link #search}가 {@link #self}를 통해 호출해야 한다.
     */
    @Cacheable(cacheNames = "goodsList",
            key = "'search:' + T(com.beautyboy.common.CacheKeys).goodsList("
                    + "#condition.keyword(), #condition.sort().name(), #condition.page(), "
                    + "T(com.beautyboy.search.SearchService).filtersOf(#condition)) + ':' + #memberId")
    @Transactional(readOnly = true)
    public PageResponse<SearchResultItem> searchCached(SearchCondition condition, Long memberId) {
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

    /** {@link #search}의 캐시 키에 쓸 필터 맵. 검색 조건에는 size만 카테고리/정렬/페이지 밖에 남는다. */
    public static Map<String, String> filtersOf(SearchCondition condition) {
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("size", String.valueOf(condition.size()));
        return filters;
    }

    private int discountRate(int listPrice, int salePrice) {
        if (listPrice == 0) {
            return 0;
        }
        return (listPrice - salePrice) * 100 / listPrice;
    }
}
