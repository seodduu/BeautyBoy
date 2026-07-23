package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.CategoryTreeNode;
import com.beautyboy.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/api/v1/categories/tree")
    public ResponseEntity<ApiResponse<List<CategoryTreeNode>>> tree() {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.tree()));
    }
}
