package com.beautyboy.cart.dto;

import java.util.List;

/** 루틴 전체 담기(설계 7장 {@code POST /cart/items/bulk}). 항목별로 담기와 같은 규칙을 적용한다. */
public record CartBulkAddRequest(List<CartAddRequest> items) {
}
