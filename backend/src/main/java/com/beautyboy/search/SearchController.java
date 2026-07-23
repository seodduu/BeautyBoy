package com.beautyboy.search;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.common.PageResponse;
import com.beautyboy.search.dto.SearchCondition;
import com.beautyboy.search.dto.SearchResultItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    private static final int MAX_PAGE_SIZE = 100;
    /** ngram_token_size 기본값이 2다. 1자 검색어는 FULLTEXT에서 아무것도 매칭시키지 못한다. */
    private static final int MIN_KEYWORD_LENGTH = 2;

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/v1/search")
    public ResponseEntity<ApiResponse<PageResponse<SearchResultItem>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "accuracy") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String keyword = q.trim();
        if (keyword.length() < MIN_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.SEARCH_QUERY_TOO_SHORT);
        }

        SearchCondition condition = new SearchCondition(
                keyword, SearchSort.fromParam(sort), page, Math.min(size, MAX_PAGE_SIZE));

        return ResponseEntity.ok(ApiResponse.ok(searchService.search(condition)));
    }
}
