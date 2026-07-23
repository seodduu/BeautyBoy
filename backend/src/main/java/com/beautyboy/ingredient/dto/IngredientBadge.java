package com.beautyboy.ingredient.dto;

public record IngredientBadge(
        Long ingredientId,
        String name,
        String category,
        int irritationLevel,
        int comedogenic,
        String summary,
        boolean key) {}
