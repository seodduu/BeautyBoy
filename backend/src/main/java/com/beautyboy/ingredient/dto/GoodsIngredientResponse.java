package com.beautyboy.ingredient.dto;

import java.util.List;

public record GoodsIngredientResponse(
        Long goodsNo,
        List<IngredientBadge> ingredients,
        int maxIrritation,
        int maxComedogenic) {}
