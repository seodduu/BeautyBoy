package com.beautyboy.common;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B3 — {@link CacheKeys#goodsList} 정규화 계약 검증. 필터 순서·빈값 취급이 어긋나면
 * 같은 조회 조합이 다른 키가 되어 캐시 히트율이 조용히 0이 된다.
 */
class CacheKeysTest {

    @Test
    void 필터_순서가_달라도_같은_키가_된다() {
        Map<String, String> filtersA = new LinkedHashMap<>();
        filtersA.put("brand", "1");
        filtersA.put("tag", "x");

        Map<String, String> filtersB = new LinkedHashMap<>();
        filtersB.put("tag", "x");
        filtersB.put("brand", "1");

        String keyA = CacheKeys.goodsList("C001", "popular", 0, filtersA);
        String keyB = CacheKeys.goodsList("C001", "popular", 0, filtersB);

        assertThat(keyA).isEqualTo(keyB);
    }

    @Test
    void 빈_필터와_누락_필터는_같은_키다() {
        Map<String, String> blankFilter = new LinkedHashMap<>();
        blankFilter.put("tag", "");
        blankFilter.put("brand", "1");

        Map<String, String> missingFilter = Map.of("brand", "1");

        String keyWithBlank = CacheKeys.goodsList("C001", "popular", 0, blankFilter);
        String keyWithoutKey = CacheKeys.goodsList("C001", "popular", 0, missingFilter);

        assertThat(keyWithBlank).isEqualTo(keyWithoutKey);
    }
}
