package com.beautyboy.search;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.common.PageResponse;
import com.beautyboy.search.dto.SearchCondition;
import com.beautyboy.search.dto.SearchResultItem;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SearchController {

    private static final int MAX_PAGE_SIZE = 100;
    /** ngram_token_size 기본값이 2다. 1자 검색어는 FULLTEXT에서 아무것도 매칭시키지 못한다. */
    private static final int MIN_KEYWORD_LENGTH = 2;
    /** 프론트가 300ms 디바운스로 호출한다(설계 7장). 서버는 상위 10건만 준다. */
    private static final int AUTOCOMPLETE_LIMIT = 10;

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/v1/search")
    public ResponseEntity<ApiResponse<PageResponse<SearchResultItem>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "accuracy") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            // 공개 엔드포인트다. 비로그인이면 null이 들어오고, 그대로 로그에 남는다.
            @AuthenticationPrincipal Long memberId) {

        String keyword = q.trim();
        if (keyword.length() < MIN_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.SEARCH_QUERY_TOO_SHORT);
        }

        SearchCondition condition = new SearchCondition(
                keyword, SearchSort.fromParam(sort), page, Math.min(size, MAX_PAGE_SIZE));

        return ResponseEntity.ok(ApiResponse.ok(searchService.search(condition, memberId)));
    }

    @GetMapping("/api/v1/search/autocomplete")
    public ResponseEntity<ApiResponse<List<String>>> autocomplete(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.ok(
                searchService.autocomplete(q, MIN_KEYWORD_LENGTH, AUTOCOMPLETE_LIMIT)));
    }

    @GetMapping("/api/v1/search/popular-keywords")
    public ResponseEntity<ApiResponse<List<String>>> popularKeywords() {
        return ResponseEntity.ok(ApiResponse.ok(searchService.popularKeywords()));
    }
}
