package com.beautyboy.common;

import java.util.List;

/**
 * 목록 응답의 공통 포맷. `ApiResponse.data`에 실려 나간다.
 * <p>
 * 설계 문서 7장의 페이징 엔드포인트(`/goods`, `/reviews`, `/search`, `/rankings`)는 전부 이 형태를
 * 쓴다. 도메인마다 페이징 응답을 따로 만들면 프론트가 파싱 로직을 여러 벌 갖게 되고,
 * `common`은 Wave 0 이후 동결이라 나중에는 통일할 수 없어 지금 계약으로 고정한다.
 * <p>
 * offset 방식을 택한 이유: 설계 문서가 목록 화면을 "브랜드/가격 필터, 정렬 5종, 페이지네이션"으로
 * 정의하므로 페이지 번호 UI와 "총 N개" 표시가 필요하다. cursor 방식은 둘 다 만들 수 없다.
 * COUNT 쿼리 비용은 MVP 규모(상품 150개)에서 문제되지 않는다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    /**
     * @param page 0-based 페이지 번호
     */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages, page + 1 < totalPages);
    }
}
