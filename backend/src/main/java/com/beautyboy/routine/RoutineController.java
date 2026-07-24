package com.beautyboy.routine;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.routine.dto.RoutineResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoutineController {

    private final RoutineQueryService routineQueryService;

    public RoutineController(RoutineQueryService routineQueryService) {
        this.routineQueryService = routineQueryService;
    }

    @GetMapping("/api/v1/routines")
    public ResponseEntity<ApiResponse<RoutineResponse>> routines(
            @RequestParam(required = false) String skinType,
            @RequestParam(required = false) String time,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(routineQueryService.find(skinType, time, memberId)));
    }
}
