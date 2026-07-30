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

    public AffinityChainService(FlowRuleService flowRuleService) {
        this.flowRuleService = flowRuleService;
    }

    public AffinityNextStepResponse compose(AffinityNextStepRequest request) {
        FlowRulesResponse rules = flowRuleService.rules();
        return new AffinityNextStepResponse(AffinityComposer.composeChain(
                request.steps(),
                request.signals(),
                AffinityComposer.aggregate(request.events()),
                request.conflicts(),
                rules.flowRules(),
                rules.concernRules()));
    }
}
