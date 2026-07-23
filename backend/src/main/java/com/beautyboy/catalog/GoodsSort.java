package com.beautyboy.catalog;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;

/**
 * 정렬 5종. 모든 정렬에 2차 키 `g.id desc`를 붙여 offset 페이징 경계에서
 * 동점 상품이 중복/누락되지 않게 한다.
 */
public enum GoodsSort {

    POPULAR("popular", "g.viewCount desc, g.id desc"),
    NEW("new", "g.createdAt desc, g.id desc"),
    SALES("sales", "g.salesCount desc, g.id desc"),
    PRICE_ASC("priceAsc", "g.salePrice asc, g.id desc"),
    DISCOUNT("discount",
            "case when g.listPrice = 0 then 0 else (g.listPrice - g.salePrice) * 100 / g.listPrice end desc, g.id desc");

    private final String param;
    private final String orderByClause;

    GoodsSort(String param, String orderByClause) {
        this.param = param;
        this.orderByClause = orderByClause;
    }

    public String orderByClause() {
        return orderByClause;
    }

    public static GoodsSort fromParam(String param) {
        for (GoodsSort sort : values()) {
            if (sort.param.equals(param)) {
                return sort;
            }
        }
        throw new BusinessException(ErrorCode.GOODS_INVALID_SORT);
    }
}
