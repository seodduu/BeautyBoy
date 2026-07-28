package com.beautyboy.cart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 루틴 전체 담기(설계 7장 {@code POST /cart/items/bulk}). 항목별로 담기와 같은 규칙을 적용한다.
 *
 * <p>{@code @NotNull}만 붙인다 — 빈 목록은 지금도 "아무것도 담지 않음"(201)이고
 * 그 동작을 바꾸지 않는다.
 */
public record CartBulkAddRequest(@NotNull @Valid List<CartAddRequest> items) {
}
