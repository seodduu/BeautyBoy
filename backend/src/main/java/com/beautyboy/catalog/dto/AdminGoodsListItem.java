package com.beautyboy.catalog.dto;

import java.util.List;

/**
 * admin 전용 상품 목록 아이템. {@link GoodsListItem}("설계 7장 동결 형태")과 필드 순서를
 * 그대로 따르되 마지막에 {@code status}만 추가한다 — 프론트 admin.ts의 {@code AdminGoodsListItem}
 * 타입(status?: 'ON_SALE' | 'HIDDEN')이 기대하는 형태다.
 *
 * <p>{@code GoodsListItem} 자체는 목록/검색/랭킹/루틴이 전부 의존하는 동결 계약이라 한 글자도
 * 바꾸지 않는다(Task 4-14a 지시). admin 화면만 이 DTO를 쓴다
 * ({@code AdminGoodsController.list}/{@code AdminGoodsService.list}).
 */
public record AdminGoodsListItem(
        Long goodsNo,
        String brandName,
        String name,
        String thumbnailUrl,
        int listPrice,
        int salePrice,
        int discountRate,
        List<String> badges,
        double rating,
        int reviewCount,
        boolean wished,
        boolean todayDreamAvailable,
        String status) {
}
