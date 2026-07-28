package com.beautyboy.common;

/**
 * 페이지 파라미터 정규화. 손님이 보낸 page·size를 리포지토리에 넘기기 전에 안전한 범위로 조인다.
 *
 * <p>이 클래스가 생기기 전에는 같은 상한 100이 5곳에 복붙돼 있었고 하한 처리가 서로 달랐다 —
 * {@code GoodsController}·{@code AdminGoodsController}·{@code SearchController}에는 하한이 없어
 * {@code ?size=-1}·{@code ?page=-1}이 {@code PageRequest.of}에서 터져 500으로 샜다.
 * 판정 주체를 하나로 모아 그 클래스의 버그를 통째로 없앤다.
 */
public final class PageRequests {

    /**
     * 한 번에 내려주는 최대 행 수. 목록 화면 중 가장 큰 것이 admin 상품 목록(기본 20)이고,
     * 100이면 그 5배다 — 운영상 필요한 범위를 덮으면서 응답 직렬화가 폭주하지 않는 선.
     */
    public static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {
    }

    /**
     * size를 [1, MAX_PAGE_SIZE]로 조인다. 0과 음수는 1로 올린다 —
     * Spring Data의 {@code PageRequest.of}가 1 미만을 거부하기 때문이다.
     */
    public static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    /** page 음수를 0으로 내린다. {@code PageRequest.of}가 음수를 거부한다. */
    public static int clampPage(int page) {
        return Math.max(page, 0);
    }
}
