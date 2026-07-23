package com.beautyboy.search.dto;

import com.beautyboy.search.SearchSort;

/**
 * 검증이 끝난 검색 조건. 검색어 길이 검사와 size 클램프는 컨트롤러가 마치고,
 * 이 레코드에는 이미 안전한 값만 담긴다(catalog의 GoodsSearchCondition과 같은 규약).
 */
public record SearchCondition(String keyword, SearchSort sort, int page, int size) {
}
