package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.catalog.dto.GoodsSearchCondition;
import com.beautyboy.common.ApiResponse;
import com.beautyboy.common.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GoodsController {

    private static final int MAX_PAGE_SIZE = 100;

    private final GoodsService goodsService;

    public GoodsController(GoodsService goodsService) {
        this.goodsService = goodsService;
    }

    @GetMapping("/api/v1/goods")
    public ResponseEntity<ApiResponse<PageResponse<GoodsListItem>>> list(
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) List<Long> brandId,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(defaultValue = "popular") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        GoodsSort goodsSort = GoodsSort.fromParam(sort);
        int clampedSize = Math.min(size, MAX_PAGE_SIZE);

        GoodsSearchCondition condition =
                new GoodsSearchCondition(categoryCode, brandId, minPrice, maxPrice, goodsSort, page, clampedSize);

        return ResponseEntity.ok(ApiResponse.ok(goodsService.list(condition)));
    }
}
