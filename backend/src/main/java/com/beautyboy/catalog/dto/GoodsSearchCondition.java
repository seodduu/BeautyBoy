package com.beautyboy.catalog.dto;

import com.beautyboy.catalog.GoodsSort;

import java.util.List;

/**
 * 목록 조회 조건 + 정렬/페이지 정보. sort 문자열 파싱(실패 시 400)과
 * size 클램프(최대 100)는 컨트롤러가 끝내고, 이 레코드에는 이미 검증된 값만 담는다.
 */
public record GoodsSearchCondition(
        String categoryCode,
        List<Long> brandId,
        Integer minPrice,
        Integer maxPrice,
        GoodsSort sort,
        int page,
        int size,
        String tagSlug) {
}
