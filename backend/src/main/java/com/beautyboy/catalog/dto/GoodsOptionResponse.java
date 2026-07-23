package com.beautyboy.catalog.dto;

public record GoodsOptionResponse(
        Long optionNo,
        String name,
        int addPrice,
        int stock,
        boolean soldOut) {
}
