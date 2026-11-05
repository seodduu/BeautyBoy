package com.beautyboy.routine;

import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 루틴 화면의 후보 30개 API(v3 설계 §10 3번, 로드맵 §3.3). 비로그인 사용자도 루틴 화면을
 * 보므로 공개 경로여야 하지만, SecurityConfig 등록은 이 태스크가 아니라 B2-6이 전담한다
 * (계획서 §5 B2-5). 그 등록이 끝나기 전까지 비로그인 요청은 401을 받는다.
 */
@RestController
public class RoutineCandidateController {

    private final RoutineCandidateService routineCandidateService;

    public RoutineCandidateController(RoutineCandidateService routineCandidateService) {
        this.routineCandidateService = routineCandidateService;
    }

    @GetMapping("/api/v1/routine/candidates")
    public ResponseEntity<ApiResponse<List<GoodsListItem>>> candidates(
            @RequestParam String categoryCode,
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(
                routineCandidateService.candidates(categoryCode, size, memberId)));
    }
}
