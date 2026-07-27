package com.beautyboy.compat;

import java.util.List;
import java.util.Map;

import com.beautyboy.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 궁합 게이트(설계 §3.3) — 기준상품 1개 대 후보 N개의 최악 판정을 한 번에 돌려준다.
 *
 * <p>메인 조합기가 단계 경계마다 1회 호출해 CONFLICT 후보를 풀에서 빼는 용도다. 판정 로직은
 * 전부 {@link CompatQueryService#worstVerdicts}에 있고 여기서는 상한만 걸어 위임한다 —
 * 존재하지 않는 base의 동작(200 + 전량 "OK")도 그 구현을 그대로 따른다(CompatVerdictsApiIT).
 */
@RestController
public class CompatVerdictsController {

    /** 한 번에 판정할 후보 상한 — 조합기 풀(12)보다 넉넉하되 무한 배치를 막는다. */
    private static final int MAX_CANDIDATES = 50;

    private final CompatQueryService compatQueryService;

    public CompatVerdictsController(CompatQueryService compatQueryService) {
        this.compatQueryService = compatQueryService;
    }

    @GetMapping("/api/v1/compat/verdicts")
    public ResponseEntity<ApiResponse<Map<Long, String>>> verdicts(
            @RequestParam Long base,
            @RequestParam List<Long> candidates) {
        List<Long> clamped = candidates.size() > MAX_CANDIDATES
                ? candidates.subList(0, MAX_CANDIDATES) : candidates;
        return ResponseEntity.ok(ApiResponse.ok(compatQueryService.worstVerdicts(base, clamped)));
    }
}
