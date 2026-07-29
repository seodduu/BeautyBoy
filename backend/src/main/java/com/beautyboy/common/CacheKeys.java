package com.beautyboy.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * B3 — 목록/검색/궁합 캐시 키 정규화. 계획서 B3 절의 판단 코드를 그대로 옮긴다.
 * 키가 어긋나면 조용히 히트율이 0이 되므로 이 클래스의 알고리즘을 바꿀 때는 계획서부터 고친다.
 */
public final class CacheKeys {
    private CacheKeys() {}

    /**
     * 목록/검색 파라미터를 "카테고리:정렬:페이지:필터해시"로 누른다.
     * 필터해시: 파라미터를 이름 오름차순으로 "k=v&" 연접한 문자열의 SHA-256 앞 16 hex.
     * 순서를 고정하지 않으면 같은 조합이 다른 키가 되어 히트율이 조용히 죽는다.
     */
    public static String goodsList(String category, String sort, int page, Map<String, String> filters) {
        StringBuilder canonical = new StringBuilder();
        filters.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> canonical.append(e.getKey()).append('=').append(e.getValue()).append('&'));
        return category + ":" + sort + ":" + page + ":" + sha256Hex16(canonical.toString());
    }

    /** 궁합 키 — 항상 작은 id가 앞. (a,b)와 (b,a)가 같은 키여야 한다. */
    public static String compat(long a, long b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }

    private static String sha256Hex16(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) hex.append(String.format("%02x", d[i]));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
