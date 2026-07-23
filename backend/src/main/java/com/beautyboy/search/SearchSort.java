package com.beautyboy.search;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;

/**
 * 검색 결과 정렬.
 *
 * <p>catalog의 {@code GoodsSort}를 재사용하지 않는 이유가 둘이다.
 * (1) 검색에만 있는 ACCURACY(관련도)가 필요하고, catalog에는 그 개념이 없다.
 * (2) catalog는 이번 웨이브에서 다른 터미널 소유라 손댈 수 없다.
 *
 * <p>모든 정렬에 2차 키 {@code g.id desc}를 붙인다 — 동점이 흔한 정렬(관련도·판매량)에서
 * offset 페이징 경계의 상품이 중복되거나 누락되는 것을 막는다.
 */
public enum SearchSort {

    /** 관련도. 구현체마다 의미가 다르다(FULLTEXT=MATCH 점수, LIKE=이름 일치 우선). */
    ACCURACY("accuracy"),
    POPULAR("popular"),
    NEW("new"),
    PRICE_ASC("priceAsc");

    private final String param;

    SearchSort(String param) {
        this.param = param;
    }

    public static SearchSort fromParam(String param) {
        for (SearchSort sort : values()) {
            if (sort.param.equals(param)) {
                return sort;
            }
        }
        throw new BusinessException(ErrorCode.SEARCH_INVALID_SORT);
    }
}
