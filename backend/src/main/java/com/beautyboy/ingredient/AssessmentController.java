package com.beautyboy.ingredient;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.ingredient.dto.GoodsAssessmentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    /** 상품 성분 종합판정. 존재 검증·판정 파생은 서비스가 한다(없는 상품이면 GOODS_NOT_FOUND → 404). */
    @GetMapping("/api/v1/goods/{goodsNo}/assessment")
    public ResponseEntity<ApiResponse<GoodsAssessmentResponse>> assessment(@PathVariable Long goodsNo) {
        return ResponseEntity.ok(ApiResponse.ok(assessmentService.assess(goodsNo)));
    }
}
