package com.beautyboy.ingredient;

import java.util.List;
import java.util.Optional;

/**
 * 성분 궁합 규칙 조회 경계. 규칙의 소유자는 ingredient이고, 소비자는 compat뿐이다.
 * 저장·정규화(saveNormalized)는 내주지 않는다 — 경계 밖에서 규칙을 쓰는 곳이 없다.
 */
public interface IngredientRuleQueryService {

    /** (A,B) 사전순 정규화 조회 — 저장 규약(category_a < category_b)을 구현이 흡수한다. */
    Optional<RuleVerdict> findNormalized(String categoryA, String categoryB);

    /** 전체 규칙 — 루틴 조합기의 배치 verdict 산출용. */
    List<RuleVerdict> findAll();

    /** 엔티티는 경계를 넘지 않는다. */
    record RuleVerdict(String categoryA, String categoryB, String verdict, String reason) {}
}
