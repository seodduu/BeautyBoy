package com.beautyboy.ingredient;

import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.common.ApiResponse;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.ingredient.dto.GoodsIngredientResponse;
import com.beautyboy.ingredient.dto.IngredientBadge;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class IngredientController {

    private final GoodsQueryService goodsQueryService;
    private final GoodsIngredientQueryService goodsIngredientQueryService;

    public IngredientController(GoodsQueryService goodsQueryService,
                                 GoodsIngredientQueryService goodsIngredientQueryService) {
        this.goodsQueryService = goodsQueryService;
        this.goodsIngredientQueryService = goodsIngredientQueryService;
    }

    @GetMapping("/api/v1/goods/{goodsNo}/ingredients")
    public ResponseEntity<ApiResponse<GoodsIngredientResponse>> ingredients(@PathVariable Long goodsNo) {
        if (!goodsQueryService.exists(goodsNo)) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }

        List<IngredientBadge> badges = goodsIngredientQueryService.findBadges(goodsNo);
        int maxIrritation = badges.stream().mapToInt(IngredientBadge::irritationLevel).max().orElse(0);
        int maxComedogenic = badges.stream().mapToInt(IngredientBadge::comedogenic).max().orElse(0);

        return ResponseEntity.ok(ApiResponse.ok(new GoodsIngredientResponse(goodsNo, badges, maxIrritation, maxComedogenic)));
    }
}
