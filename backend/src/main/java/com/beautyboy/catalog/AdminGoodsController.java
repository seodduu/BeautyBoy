package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.AdminGoodsDetailResponse;
import com.beautyboy.catalog.dto.AdminGoodsListItem;
import com.beautyboy.catalog.dto.AdminGoodsSaveRequest;
import com.beautyboy.common.ApiResponse;
import com.beautyboy.common.PageRequests;
import com.beautyboy.common.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 위임만 하는 컨트롤러. 전부 ROLE_ADMIN 필요 — 핸들러마다 개별로 @PreAuthorize를 건다. */
@RestController
public class AdminGoodsController {

    private final AdminGoodsService adminGoodsService;

    public AdminGoodsController(AdminGoodsService adminGoodsService) {
        this.adminGoodsService = adminGoodsService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/v1/admin/goods")
    public ResponseEntity<ApiResponse<PageResponse<AdminGoodsListItem>>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedPage = PageRequests.clampPage(page);
        int clampedSize = PageRequests.clampSize(size);
        return ResponseEntity.ok(ApiResponse.ok(adminGoodsService.list(clampedPage, clampedSize, q)));
    }

    /** HIDDEN 상품도 조회된다(GoodsService.detail과 다른 지점) — admin 인라인 수정 진입용. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/v1/admin/goods/{goodsNo}")
    public ResponseEntity<ApiResponse<AdminGoodsDetailResponse>> detail(@PathVariable Long goodsNo) {
        return ResponseEntity.ok(ApiResponse.ok(adminGoodsService.detail(goodsNo)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/v1/admin/goods")
    public ResponseEntity<ApiResponse<Long>> create(@RequestBody AdminGoodsSaveRequest request) {
        Long goodsNo = adminGoodsService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(goodsNo));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/api/v1/admin/goods/{goodsNo}")
    public ResponseEntity<ApiResponse<Void>> update(@PathVariable Long goodsNo,
                                                      @RequestBody AdminGoodsSaveRequest request) {
        adminGoodsService.update(goodsNo, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/api/v1/admin/goods/{goodsNo}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long goodsNo) {
        adminGoodsService.delete(goodsNo);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
