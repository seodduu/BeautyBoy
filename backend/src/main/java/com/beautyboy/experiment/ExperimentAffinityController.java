package com.beautyboy.experiment;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.experiment.dto.AffinityNextStepRequest;
import com.beautyboy.experiment.dto.AffinityNextStepResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개인화 위임 비용 실측용 참조 엔드포인트. 클라이언트가 브라우저에서 하는 계산을 그대로 서버에서
 * 수행해 "같은 로직을 서버에 뒀을 때"의 왕복 시간과 부하 비용을 재는 것이 유일한 목적이다.
 *
 * <p><b>실서비스 경로가 아니다.</b> {@code @Profile("experiment")}로 묶여 있어 기본 기동에는 매핑이
 * 등록되지 않는다(404). 측정은 {@code --spring.profiles.active=local,experiment}로 별도 기동한다.
 */
@RestController
@Profile("experiment")
public class ExperimentAffinityController {

    private final AffinityChainService affinityChainService;
    private final ExperimentAffinityEventStore eventStore;

    public ExperimentAffinityController(AffinityChainService affinityChainService,
                                        ExperimentAffinityEventStore eventStore) {
        this.affinityChainService = affinityChainService;
        this.eventStore = eventStore;
    }

    @PostMapping("/api/v1/experiment/affinity/next-step")
    public ApiResponse<AffinityNextStepResponse> nextStep(@RequestBody AffinityNextStepRequest request) {
        return ApiResponse.ok(affinityChainService.compose(request));
    }

    // ── 상태 있는 서버형(B') — "서버형 개인화라면 생겼을 I/O"를 붙인 변형 ────────────────

    /** 행동 이벤트 수집 — 서버형이라면 조회·찜·담기마다 클라이언트가 쐈을 요청이다. INSERT 1회. */
    @PostMapping("/api/v1/experiment/affinity/events")
    public ApiResponse<Void> ingest(@RequestParam String memberKey,
                                    @RequestBody AffinityNextStepRequest.Event event) {
        eventStore.append(memberKey, event);
        return ApiResponse.ok(null);
    }

    /** 개인화 — 바디의 events 대신 DB에서 최신 50건을 읽는다(SELECT 1회 + 계산). */
    @PostMapping("/api/v1/experiment/affinity/next-step-stateful")
    public ApiResponse<AffinityNextStepResponse> nextStepStateful(
            @RequestParam String memberKey, @RequestBody AffinityNextStepRequest request) {
        return ApiResponse.ok(affinityChainService.composeStateful(memberKey, request));
    }
}
