package com.beautyboy.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageRequestsTest {

    @Test
    @DisplayName("size 0은 1로 올린다 — PageRequest.of가 0을 거부한다")
    void size_0은_1() {
        assertThat(PageRequests.clampSize(0)).isEqualTo(1);
    }

    @Test
    @DisplayName("size 음수는 1로 올린다")
    void size_음수는_1() {
        assertThat(PageRequests.clampSize(-1)).isEqualTo(1);
    }

    @Test
    @DisplayName("size 1은 그대로 1")
    void size_1은_그대로() {
        assertThat(PageRequests.clampSize(1)).isEqualTo(1);
    }

    @Test
    @DisplayName("size 100은 그대로 100 — 상한은 포함이다")
    void size_100은_그대로() {
        assertThat(PageRequests.clampSize(100)).isEqualTo(100);
    }

    @Test
    @DisplayName("size 101은 100으로 내린다")
    void size_101은_100() {
        assertThat(PageRequests.clampSize(101)).isEqualTo(100);
    }

    @Test
    @DisplayName("size 정수 최대값도 100으로 내린다 — 오버플로 없음")
    void size_최대값도_100() {
        assertThat(PageRequests.clampSize(Integer.MAX_VALUE)).isEqualTo(100);
    }

    @Test
    @DisplayName("page 음수는 0으로 내린다")
    void page_음수는_0() {
        assertThat(PageRequests.clampPage(-1)).isEqualTo(0);
    }

    @Test
    @DisplayName("page 0과 양수는 그대로")
    void page_양수는_그대로() {
        assertThat(PageRequests.clampPage(0)).isEqualTo(0);
        assertThat(PageRequests.clampPage(5)).isEqualTo(5);
    }

    @Test
    @DisplayName("MAX_PAGE_SIZE는 100이다 — 다섯 곳이 공유하던 값과 같아야 한다")
    void 상한은_100() {
        assertThat(PageRequests.MAX_PAGE_SIZE).isEqualTo(100);
    }
}
