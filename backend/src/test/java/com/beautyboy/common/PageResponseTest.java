package com.beautyboy.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void 총_개수와_크기로_전체_페이지수와_다음여부를_계산한다() {
        PageResponse<String> first = PageResponse.of(List.of("a", "b"), 0, 20, 1234);

        assertThat(first.totalPages()).isEqualTo(62);
        assertThat(first.hasNext()).isTrue();
    }

    @Test
    void 마지막_페이지면_hasNext가_false다() {
        // 총 62페이지(0~61)이므로 61이 마지막
        PageResponse<String> last = PageResponse.of(List.of("a"), 61, 20, 1234);

        assertThat(last.hasNext()).isFalse();
    }

    @Test
    void 결과가_없으면_페이지수는_0이고_다음도_없다() {
        PageResponse<String> empty = PageResponse.of(List.of(), 0, 20, 0);

        assertThat(empty.content()).isEmpty();
        assertThat(empty.totalPages()).isZero();
        assertThat(empty.hasNext()).isFalse();
    }

    @Test
    void 한_페이지에_다_들어가면_다음이_없다() {
        PageResponse<String> single = PageResponse.of(List.of("a", "b", "c"), 0, 20, 3);

        assertThat(single.totalPages()).isEqualTo(1);
        assertThat(single.hasNext()).isFalse();
    }
}
