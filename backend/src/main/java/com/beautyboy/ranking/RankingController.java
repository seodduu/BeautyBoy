package com.beautyboy.ranking;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.ranking.dto.RankingItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    /** categoryCode 생략 시 전체 랭킹. 설계 7장 공개 목록이라 인증이 필요 없다. */
    @GetMapping("/api/v1/rankings")
    public ResponseEntity<ApiResponse<List<RankingItem>>> rankings(
            @RequestParam(required = false) String categoryCode) {
        return ResponseEntity.ok(ApiResponse.ok(rankingService.rankings(categoryCode)));
    }
}
