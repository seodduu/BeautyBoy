package com.beautyboy.experiment;

import com.beautyboy.experiment.dto.AffinityNextStepRequest;
import com.beautyboy.experiment.dto.AffinityNextStepResponse;
import com.beautyboy.routine.dto.ConcernRuleView;
import com.beautyboy.routine.dto.FlowRuleView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 클라이언트 위임 로직과 서버 참조 구현의 <b>동등성</b> — 이 벤치마크 전체의 전제다.
 * 여기가 깨지면 "클라 계산 vs 서버 왕복" 비교는 서로 다른 두 계산을 비교하는 것이 되어 무효다
 * (docs/plans/2026-07-30-affinity-benchmark-goal.md 할 일 2).
 *
 * <p>기대값 {@code affinity-cases.json}은 <b>클라이언트 코드가 실제로 낸 출력</b>이다
 * ({@code frontend/bench/exportCases.bench.ts}가 프로덕션 {@code composeStep}을 그대로 불러 생성).
 * 손으로 고치면 안 된다 — 고치는 순간 이 테스트는 "서버가 서버와 같다"만 말한다.
 *
 * <p>스프링 컨텍스트를 띄우지 않는다: 재는 대상이 순수 계산이고, DB 시드에 의존하지 않아야
 * 터미널 병렬에서도 안전하다(규칙은 번들에 함께 들어 있다).
 */
class AffinityEquivalenceTest {

    private static final String BUNDLE = "/experiment/affinity-cases.json";

    /** 생성기가 만드는 케이스 수. 파일이 비었는데 녹색이 나오는 사태를 먼저 막는다. */
    private static final int EXPECTED_CASE_COUNT = 26;
    private static final int EXPECTED_FLOW_RULES = 12;    // V75 시드 12행
    private static final int EXPECTED_CONCERN_RULES = 19; // V82 시드 19행

    private static Bundle bundle;

    record Expected(String stepId, Long pick, List<Long> alternatives, String reason,
                    List<String> matchedConcerns, List<String> matchedBehaviors) {
    }

    record GoldenCase(String name, AffinityNextStepRequest request, List<Expected> expected) {
    }

    record Bundle(String generatedFrom, List<FlowRuleView> flowRules,
                  List<ConcernRuleView> concernRules, List<GoldenCase> cases) {
    }

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = AffinityEquivalenceTest.class.getResourceAsStream(BUNDLE)) {
            assertThat(in).as("골든 케이스 번들이 클래스패스에 없다: %s", BUNDLE).isNotNull();
            bundle = new ObjectMapper().readValue(in, Bundle.class);
        }
    }

    @Test
    void 번들이_기대한_규모다() {
        assertThat(bundle.cases()).hasSize(EXPECTED_CASE_COUNT);
        assertThat(bundle.flowRules()).hasSize(EXPECTED_FLOW_RULES);
        assertThat(bundle.concernRules()).hasSize(EXPECTED_CONCERN_RULES);
        assertThat(bundle.cases()).extracting(GoldenCase::name).doesNotHaveDuplicates();
        // 케이스마다 5단계가 다 들어 있어야 한다 — 단계가 빠진 케이스는 조용히 통과한다.
        assertThat(bundle.cases()).allSatisfy(c -> assertThat(c.expected()).hasSize(5));
    }

    @Test
    void 모든_케이스에서_서버_참조_구현이_클라와_같은_결과를_낸다() {
        for (GoldenCase golden : bundle.cases()) {
            List<AffinityNextStepResponse.StepComposition> actual = AffinityComposer.composeChain(
                    golden.request().steps(),
                    golden.request().signals(),
                    AffinityComposer.aggregate(golden.request().events()),
                    golden.request().conflicts(),
                    bundle.flowRules(),
                    bundle.concernRules());

            assertThat(actual)
                    .as("[%s] 단계 수", golden.name())
                    .hasSameSizeAs(golden.expected());

            for (int s = 0; s < actual.size(); s++) {
                AffinityNextStepResponse.StepComposition got = actual.get(s);
                Expected want = golden.expected().get(s);
                String where = "[%s] 단계 %d(%s)".formatted(golden.name(), s, want.stepId());

                assertThat(got.stepId()).as("%s stepId", where).isEqualTo(want.stepId());
                assertThat(got.pick()).as("%s pick", where).isEqualTo(want.pick());
                // 순서까지 같아야 한다 — 대안의 순서가 곧 화면에 놓이는 순서다.
                assertThat(got.alternatives()).as("%s alternatives", where)
                        .containsExactlyElementsOf(want.alternatives());
                assertThat(got.reason()).as("%s reason", where).isEqualTo(want.reason());
                assertThat(got.matchedConcerns()).as("%s matchedConcerns", where)
                        .containsExactlyElementsOf(want.matchedConcerns());
                assertThat(got.matchedBehaviors()).as("%s matchedBehaviors", where)
                        .containsExactlyElementsOf(want.matchedBehaviors());
            }
        }
    }

    /**
     * 26번 케이스가 실제로 접두사 합산 분기를 밟는지 직접 확인한다.
     *
     * <p>클렌징 단계 코드는 4자(C002)인데 행동 이벤트 키는 중분류 7자(C002001)다. 이식에서 접두사
     * 매칭 대신 {@code Map.get}을 쓰면 친화도가 0이 되어 인기 1위가 그대로 나온다 — 그래도 위
     * 전량 비교는 "두 구현이 우연히 같은 실수"를 하지 않는 한 잡아낸다. 이 테스트는 케이스 자체가
     * 그 분기를 밟고 있다는 사실(=위 비교가 의미를 갖는다는 사실)을 못 박는다.
     */
    @Test
    void 접두사_합산_케이스가_행동_신호를_실제로_반영한다() {
        GoldenCase prefix = bundle.cases().stream()
                .filter(c -> c.name().equals("cleansing-prefix"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("cleansing-prefix 케이스가 번들에 없다"));

        Expected cleansing = prefix.expected().get(0);
        // 행동 이벤트의 태그가 근거 칩으로 잡혔다 = 7자 키가 4자 단계 코드에 접두사로 매칭됐다.
        assertThat(cleansing.matchedBehaviors())
                .as("접두사 합산이 없으면 이 목록이 비어야 한다(= 케이스가 분기를 못 밟는다)")
                .isNotEmpty();
        assertThat(prefix.request().events()).isNotEmpty();
        assertThat(prefix.request().events().get(0).cat3().length())
                .as("이벤트 키는 중분류 7자여야 한다")
                .isEqualTo(7);
        assertThat(prefix.request().steps().get(0).categoryCode())
                .as("클렌징 단계 코드는 대분류 4자여야 한다")
                .isEqualTo("C002");
    }
}
