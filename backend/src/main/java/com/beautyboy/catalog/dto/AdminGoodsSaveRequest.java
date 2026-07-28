package com.beautyboy.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 관리자 상품 등록/수정 공용 요청. 등록(POST)과 수정(PUT)이 편집 항목이 동일하므로 DTO를 하나로 둔다.
 * 등록 시 status는 무시된다 — 새 상품은 항상 ON_SALE로 시작한다(Goods 기본값).
 *
 * <p>여기 붙은 애노테이션은 "구조적 결손"(null·공백·컬럼 길이 초과)만 막는다. {@code categoryCode}의
 * 유효성({@code GOODS_CATEGORY_INVALID})과 {@code listPrice}/{@code salePrice}의 관계·범위
 * ({@code GOODS_PRICE_INVALID})는 이미 도메인 ErrorCode를 가진 값이라 여기서 판정하지 않는다 —
 * {@link com.beautyboy.catalog.AdminGoodsService}가 계속 판정한다(§2 결정 2).
 */
public record AdminGoodsSaveRequest(
        @NotNull Long brandId,
        @NotBlank @Size(max = 12) String categoryCode,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 300) String summary,
        @NotBlank @Size(max = 300) String thumbnailUrl,
        int listPrice,
        int salePrice,
        @Size(max = 20) String status) {
}
