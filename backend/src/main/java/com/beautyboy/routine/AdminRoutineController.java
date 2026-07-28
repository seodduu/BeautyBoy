package com.beautyboy.routine;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.routine.dto.AdminRoutineTemplateResponse;
import com.beautyboy.routine.dto.RoutineStepGoodsRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 위임만 하는 컨트롤러. 전부 ROLE_ADMIN 필요. */
@RestController
public class AdminRoutineController {

    private final AdminRoutineService adminRoutineService;

    public AdminRoutineController(AdminRoutineService adminRoutineService) {
        this.adminRoutineService = adminRoutineService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/v1/admin/routines")
    public ResponseEntity<ApiResponse<List<AdminRoutineTemplateResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(adminRoutineService.list()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/api/v1/admin/routines/{templateId}/steps/{stepOrder}/goods")
    public ResponseEntity<ApiResponse<Void>> replaceStepGoods(@PathVariable Long templateId,
                                                                @PathVariable int stepOrder,
                                                                @Valid @RequestBody RoutineStepGoodsRequest request) {
        adminRoutineService.replaceStepGoods(templateId, stepOrder, request.goodsNos());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
