package com.beautyboy.ingredient;

import com.beautyboy.ingredient.IngredientRuleQueryService.RuleVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * compat이 규칙을 소비하는 경계(조회 전용)의 계약 테스트.
 * 사전순 정규화 규약을 구현이 흡수하는지, 엔티티가 아니라 record로 넘어오는지를 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IngredientRuleQueryServiceImplTest {

    @Autowired
    IngredientRuleRepository ingredientRuleRepository;
    @Autowired
    IngredientRuleQueryService ruleQueryService;

    @Test
    @DisplayName("역순으로 물어도 사전순 정규화로 같은 규칙을 찾는다")
    void 정규화_조회() {
        ingredientRuleRepository.saveNormalized(
                new IngredientRule(null, "AHA", "RETINOID", "CONFLICT", "자극 중첩"));

        Optional<RuleVerdict> rule = ruleQueryService.findNormalized("RETINOID", "AHA");

        assertThat(rule).isPresent();
        assertThat(rule.get().categoryA()).isEqualTo("AHA");
        assertThat(rule.get().categoryB()).isEqualTo("RETINOID");
        assertThat(rule.get().verdict()).isEqualTo("CONFLICT");
        assertThat(rule.get().reason()).isNotBlank();
    }

    @Test
    @DisplayName("없는 쌍은 empty — 예외가 아니다")
    void 규칙_없음() {
        assertThat(ruleQueryService.findNormalized("PEPTIDE", "CENTELLA")).isEmpty();
    }

    @Test
    @DisplayName("findAll은 저장된 규칙 전부를 record로 변환해 반환한다")
    void 전체_조회() {
        ingredientRuleRepository.saveNormalized(
                new IngredientRule(null, "AHA", "RETINOID", "CONFLICT", "자극 중첩"));
        ingredientRuleRepository.saveNormalized(
                new IngredientRule(null, "VITAMIN_C", "NIACINAMIDE", "CAUTION", "동시 사용 주의"));

        assertThat(ruleQueryService.findAll())
                .hasSize(2)
                .containsExactlyInAnyOrder(
                        new RuleVerdict("AHA", "RETINOID", "CONFLICT", "자극 중첩"),
                        // saveNormalized가 사전순으로 눕혀 저장하므로 NIACINAMIDE가 A로 나온다
                        new RuleVerdict("NIACINAMIDE", "VITAMIN_C", "CAUTION", "동시 사용 주의"));
    }
}
