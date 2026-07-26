package com.beautyboy.routine;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.routine.dto.NextStepResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다음 단계 추천 조회. 기존 SecurityConfig의 GET /api/v1/goods/** permitAll에 자동 포함되므로
 * 보안 설정 변경이 없다(설계 §4). 로그인 사용자는 찜 여부(wished) 반영을 위해 memberId를 넘긴다.
 */
@RestController
public class NextStepController {

    private final NextStepService nextStepService;

    public NextStepController(NextStepService nextStepService) {
        this.nextStepService = nextStepService;
    }

    @GetMapping("/api/v1/goods/{goodsNo}/next-step")
    public ResponseEntity<ApiResponse<NextStepResponse>> nextStep(
            @PathVariable Long goodsNo,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(nextStepService.find(goodsNo, memberId)));
    }
}
