package com.beautyboy.compat;

import java.util.Collection;
import java.util.Map;

/**
 * 기준상품 1개 × 후보상품 N개의 배치 pairwise 궁합 최악 판정.
 *
 * routine(Task 4) 등 타 도메인은 compat 도메인의 세부 규칙 엔진을 몰라도 되도록
 * 이 인터페이스 하나만 소비한다(패키지 = 서비스 경계).
 */
public interface CompatQueryService {

    /**
     * baseGoodsNo와 candidateGoodsNos 각각의 사이에서 나올 수 있는 모든 (분류A, 분류B) 규칙 중
     * 가장 심각한 판정("CONFLICT" > "CAUTION" > "SYNERGY" > "OK")을 후보별로 돌려준다.
     * 후보가 비어 있으면 빈 맵을 돌려준다.
     */
    Map<Long, String> worstVerdicts(Long baseGoodsNo, Collection<Long> candidateGoodsNos);
}
