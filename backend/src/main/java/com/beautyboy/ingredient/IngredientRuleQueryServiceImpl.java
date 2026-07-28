package com.beautyboy.ingredient;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * {@link IngredientRuleRepository}를 감싸 엔티티→record 변환만 한다. 로직은 없다 —
 * 정규화 규약도 리포지토리의 findNormalized에 그대로 위임한다.
 */
@Service
@Transactional(readOnly = true)
public class IngredientRuleQueryServiceImpl implements IngredientRuleQueryService {

    private final IngredientRuleRepository ingredientRuleRepository;

    public IngredientRuleQueryServiceImpl(IngredientRuleRepository ingredientRuleRepository) {
        this.ingredientRuleRepository = ingredientRuleRepository;
    }

    @Override
    public Optional<RuleVerdict> findNormalized(String categoryA, String categoryB) {
        return ingredientRuleRepository.findNormalized(categoryA, categoryB)
                .map(IngredientRuleQueryServiceImpl::toVerdict);
    }

    @Override
    public List<RuleVerdict> findAll() {
        return ingredientRuleRepository.findAll().stream()
                .map(IngredientRuleQueryServiceImpl::toVerdict)
                .toList();
    }

    private static RuleVerdict toVerdict(IngredientRule rule) {
        return new RuleVerdict(rule.getCategoryA(), rule.getCategoryB(),
                rule.getVerdict(), rule.getReason());
    }
}
