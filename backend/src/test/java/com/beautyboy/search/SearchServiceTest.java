package com.beautyboy.search;

import com.beautyboy.catalog.GoodsRatingProvider;
import com.beautyboy.catalog.WishedGoodsProvider;
import com.beautyboy.common.PageResponse;
import com.beautyboy.search.dto.SearchCondition;
import com.beautyboy.search.dto.SearchResultItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * catalog의 두 공급자를 통해 검색 카드에 별점·찜이 채워지는지 검증한다(GoodsServiceTest와 같은 계약).
 * 질의 자체(정렬·페이징·FULLTEXT)는 {@code SearchApiTest}/{@code MysqlFulltextSearchIntegrationTest}가 본다.
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    GoodsSearchRepository goodsSearchRepository;
    @Mock
    SearchKeywordLogRepository searchKeywordLogRepository;
    @Mock
    PopularKeywordHolder popularKeywordHolder;
    @Mock
    GoodsRatingProvider goodsRatingProvider;
    @Mock
    WishedGoodsProvider wishedGoodsProvider;
    @InjectMocks
    SearchService searchService;

    private static final Long 상품A = 1L;
    private static final Long 상품B = 2L;

    private static GoodsSearchRepository.SearchRow 행(Long goodsId) {
        return new GoodsSearchRepository.SearchRow(goodsId, "브랜드" + goodsId, "상품" + goodsId,
                "https://img.example/" + goodsId + ".jpg", 10000, 9000);
    }

    private SearchCondition 조건() {
        return new SearchCondition("토너", SearchSort.ACCURACY, 0, 20);
    }

    @Test
    void 검색_카드에_별점과_리뷰수가_공급자_값으로_채워진다() {
        given(goodsSearchRepository.search(any())).willReturn(List.of(행(상품A), 행(상품B)));
        given(goodsSearchRepository.count(any())).willReturn(2L);
        given(goodsRatingProvider.ratingsByGoods(any()))
                .willReturn(Map.of(상품A, new GoodsRatingProvider.RatingStat(4.5, 12)));
        given(wishedGoodsProvider.wishedGoodsIds(any(), any())).willReturn(Set.of());

        PageResponse<SearchResultItem> response = searchService.search(조건(), null);

        assertThat(response.content()).filteredOn(i -> i.goodsNo().equals(상품A)).singleElement()
                .satisfies(i -> {
                    assertThat(i.rating()).isEqualTo(4.5);
                    assertThat(i.reviewCount()).isEqualTo(12);
                });
        assertThat(response.content()).filteredOn(i -> i.goodsNo().equals(상품B)).singleElement()
                .satisfies(i -> {
                    assertThat(i.rating()).isEqualTo(0.0);
                    assertThat(i.reviewCount()).isEqualTo(0);
                });
    }

    @Test
    void 로그인한_회원이_찜한_상품만_wished가_true다() {
        given(goodsSearchRepository.search(any())).willReturn(List.of(행(상품A), 행(상품B)));
        given(goodsSearchRepository.count(any())).willReturn(2L);
        given(goodsRatingProvider.ratingsByGoods(any())).willReturn(Map.of());
        given(wishedGoodsProvider.wishedGoodsIds(1L, List.of(상품A, 상품B))).willReturn(Set.of(상품A));

        PageResponse<SearchResultItem> response = searchService.search(조건(), 1L);

        assertThat(response.content()).filteredOn(SearchResultItem::wished)
                .extracting(SearchResultItem::goodsNo).containsExactly(상품A);
    }

    @Test
    void 비로그인이면_wished는_전부_false이고_공급자에_null이_전달된다() {
        given(goodsSearchRepository.search(any())).willReturn(List.of(행(상품A)));
        given(goodsSearchRepository.count(any())).willReturn(1L);
        given(goodsRatingProvider.ratingsByGoods(any())).willReturn(Map.of());
        given(wishedGoodsProvider.wishedGoodsIds(isNull(), any())).willReturn(Set.of());

        PageResponse<SearchResultItem> response = searchService.search(조건(), null);

        assertThat(response.content()).allMatch(i -> !i.wished());
        verify(wishedGoodsProvider).wishedGoodsIds(isNull(), any());
    }
}
