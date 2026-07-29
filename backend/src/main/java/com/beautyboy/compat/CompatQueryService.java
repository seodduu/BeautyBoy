package com.beautyboy.compat;

import com.beautyboy.common.CacheKeys;
import org.springframework.cache.annotation.Cacheable;

import java.util.Collection;
import java.util.List;
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

    /**
     * B4 — 상품 두 개 사이의 궁합 최악 판정. 배치 진입점(worstVerdicts)은 후보 컬렉션을 받아
     * (a,b) 단일 키로 캐싱할 수 없으므로, 캐싱 가능한 단일쌍 진입점을 별도로 둔다.
     *
     * <p>개인화 없음: 반환값은 성분 집합·규칙표로만 결정되고 조회자(viewer)를 참조하지 않는다.
     * 대칭성: {@code worstVerdicts}는 base·candidate의 분류 집합 사이 무순서 쌍만 검사하므로
     * (a,b)와 (b,a)의 판정값은 항상 같다 — {@link CacheKeys#compat}의 대칭 키와 응답이 일치한다.
     *
     * <p>기본 메서드로 둔 이유: CompatQueryService.java만 수정 대상인 태스크 경계라
     * 구현체({@code CompatService})는 건드리지 않고 기존 배치 로직(worstVerdicts)에 위임한다.
     */
    @Cacheable(cacheNames = "compat", key = "T(com.beautyboy.common.CacheKeys).compat(#a, #b)")
    default String worstVerdict(long a, long b) {
        return worstVerdicts(a, List.of(b)).getOrDefault(b, "OK");
    }
}
