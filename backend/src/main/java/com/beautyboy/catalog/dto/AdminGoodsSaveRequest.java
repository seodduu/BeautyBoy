package com.beautyboy.catalog.dto;

/**
 * 관리자 상품 등록/수정 공용 요청. 등록(POST)과 수정(PUT)이 편집 항목이 동일하므로 DTO를 하나로 둔다.
 * 등록 시 status는 무시된다 — 새 상품은 항상 ON_SALE로 시작한다(Goods 기본값).
 */
public record AdminGoodsSaveRequest(
        Long brandId,
        String categoryCode,
        String name,
        String summary,
        String thumbnailUrl,
        int listPrice,
        int salePrice,
        String status) {
}
