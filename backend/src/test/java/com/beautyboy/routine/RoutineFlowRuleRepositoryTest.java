package com.beautyboy.routine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoutineFlowRuleRepositoryTest {

    @Autowired
    RoutineFlowRuleRepository repository;

    @Test
    void 규칙을_priority_id순으로_전량_조회한다() {
        규칙_저장("C001001", "exfoliate", "C001002", "soothe", "BUFFER", "완충", 10);
        규칙_저장("C001001", null, "C001002", null, "NEXT_STEP", "다음", 20);

        List<RoutineFlowRule> rules = repository.findAllByOrderByPriorityAscIdAsc();

        assertThat(rules).extracting(RoutineFlowRule::getEdgeKind).containsExactly("BUFFER", "NEXT_STEP");
        assertThat(rules.get(0).getFromTagSlug()).isEqualTo("exfoliate");
        assertThat(rules.get(1).getFromTagSlug()).isNull();
    }

    private void 규칙_저장(String fromCategoryCode, String fromTagSlug, String toCategoryCode, String toTagSlug,
                       String edgeKind, String reason, int priority) {
        repository.save(new RoutineFlowRule(null, fromCategoryCode, fromTagSlug, toCategoryCode, toTagSlug,
                edgeKind, reason, priority));
    }
}
