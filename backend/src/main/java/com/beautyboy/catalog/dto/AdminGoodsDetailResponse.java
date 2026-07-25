package com.beautyboy.catalog.dto;

/**
 * admin 전용 상세 조회 응답 — 인라인 수정 폼이 필요한 값만 담는다.
 * 필드는 프론트 {@code AdminGoodsSaveInput}(admin.ts)과 1:1로 맞춰서, admin이 이 응답을
 * 그대로 채웠다가 그대로 PUT에 되돌려 보낼 수 있게 한다({@code Goods.updateInfo}가 부분
 * 수정 없이 전체 덮어쓰기이므로 실제 값을 온전히 확보해야 무해하다).
 *
 * <p>일반 {@code GoodsService.detail()}(HIDDEN 제외)과 달리 이 조회는 HIDDEN도 포함한다 —
 * admin이 숨김 상품을 인라인 수정할 수 있어야 하기 때문이다({@link com.beautyboy.catalog.AdminGoodsService#detail}).
 */
public record AdminGoodsDetailResponse(
        Long goodsNo,
        Long brandId,
        String categoryCode,
        String name,
        String summary,
        String thumbnailUrl,
        int listPrice,
        int salePrice,
        String status) {
}
