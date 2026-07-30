package com.beautyboy.experiment;

import com.beautyboy.experiment.dto.AffinityNextStepRequest;
import com.beautyboy.experiment.dto.AffinityNextStepResponse;
import com.beautyboy.routine.FlowRuleService;
import com.beautyboy.routine.dto.FlowRulesResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 실험 전용 조합 서비스 — 규칙은 {@link FlowRuleService}(기동 시 1회 캐싱)에서 받고, 계산은
 * {@link AffinityComposer}에 위임한다. 이 클래스에 판단은 없다.
 *
 * <p>{@code @Profile("experiment")} — 기본 기동(local/test/loadtest)에는 빈이 생기지 않는다.
 * 실서비스 API로 승격하지 않는다.
 */
@Service
@Profile("experiment")
public class AffinityChainService {

    private final FlowRuleService flowRuleService;
    private final ExperimentAffinityEventStore eventStore;

    public AffinityChainService(FlowRuleService flowRuleService,
                                ExperimentAffinityEventStore eventStore) {
        this.flowRuleService = flowRuleService;
        this.eventStore = eventStore;
    }

    /** 무상태(B) — 이벤트를 요청 바디로 받는다. 계산 비용만 잰다. */
    public AffinityNextStepResponse compose(AffinityNextStepRequest request) {
        return composeWith(request, AffinityComposer.aggregate(request.events()));
    }

    /**
     * 상태 있는 서버형(B') — 바디의 events를 무시하고 DB에서 최신 50건을 읽는다.
     * 무상태와의 차이가 곧 "프로필 읽기 I/O"의 비용이다(쓰기 비용은 수집 엔드포인트가 진다).
     */
    public AffinityNextStepResponse composeStateful(String memberKey, AffinityNextStepRequest request) {
        return composeWith(request, AffinityComposer.aggregate(eventStore.recentEvents(memberKey)));
    }

    private AffinityNextStepResponse composeWith(AffinityNextStepRequest request,
                                                 java.util.Map<String, Integer> affinity) {
        FlowRulesResponse rules = flowRuleService.rules();
        return new AffinityNextStepResponse(AffinityComposer.composeChain(
                request.steps(),
                request.signals(),
                affinity,
                request.conflicts(),
                rules.flowRules(),
                rules.concernRules()));
    }
}
