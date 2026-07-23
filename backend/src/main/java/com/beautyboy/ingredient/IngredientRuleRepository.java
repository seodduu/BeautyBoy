package com.beautyboy.ingredient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * ingredient_rule은 (category_a, category_b) 사전순 저장을 규약으로 한다(V11 주석 참고).
 * 저장/조회 모두 이 규약을 어긴 입력을 정규화해서 처리한다 — 호출부가 순서를 신경 쓰지 않아도 된다.
 */
public interface IngredientRuleRepository extends JpaRepository<IngredientRule, Long> {

    Optional<IngredientRule> findByCategoryAAndCategoryB(String categoryA, String categoryB);

    default IngredientRule saveNormalized(IngredientRule rule) {
        if (rule.getCategoryA().compareTo(rule.getCategoryB()) > 0) {
            rule = new IngredientRule(rule.getId(), rule.getCategoryB(), rule.getCategoryA(),
                    rule.getVerdict(), rule.getReason());
        }
        return save(rule);
    }

    default Optional<IngredientRule> findNormalized(String categoryA, String categoryB) {
        String a = categoryA.compareTo(categoryB) <= 0 ? categoryA : categoryB;
        String b = categoryA.compareTo(categoryB) <= 0 ? categoryB : categoryA;
        return findByCategoryAAndCategoryB(a, b);
    }
}
