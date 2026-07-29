package com.beautyboy.compat;

import java.util.LinkedHashMap;
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
 * {@link CompatQueryService#worstVerdict}에 있다 — 설계 §6이 궁합 캐시 키를
 * {@code v1:compat:{idA}:{idB}}(쌍 단위)로 못박아, 배치 진입점(worstVerdicts) 대신 후보를
 * 순회하며 쌍 단위 캐시 가능 메서드를 부른다. 트레이드오프: 콜드 캐시에서는 배치 1쿼리가
 * 후보 N건 조회로 갈라지지만, 히트 시에는 DB를 전혀 안 때린다.
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
        Map<Long, String> verdicts = new LinkedHashMap<>();
        for (Long candidate : clamped) {
            verdicts.put(candidate, compatQueryService.worstVerdict(base, candidate));
        }
        return ResponseEntity.ok(ApiResponse.ok(verdicts));
    }
}
