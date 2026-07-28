package com.beautyboy.qna;

import com.beautyboy.common.PageResponse;
import com.beautyboy.qna.dto.AdminQnaResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QnaService.adminList의 size 파라미터(기본 10, 상한 100)를 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QnaServiceTest {

    private static final Long 작성자 = 1L;
    private static final Long 상품 = 900L;

    @Autowired
    QnaService qnaService;
    @Autowired
    QnaRepository qnaRepository;

    @Test
    void size를_주면_그_크기로_페이징한다() {
        문의_N건_저장(25);

        PageResponse<AdminQnaResponse> page = qnaService.adminList(0, 20);

        assertThat(page.content()).hasSize(20);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    void size_상한은_100_그보다_크게_요청해도_100으로_깎는다() {
        문의_N건_저장(3);

        assertThat(qnaService.adminList(0, 1000).size()).isEqualTo(100);
    }

    @Test
    void size가_0_이하면_1로_올린다_PageRequest가_예외를_던지지_않게() {
        문의_N건_저장(3);

        assertThat(qnaService.adminList(0, 0).size()).isEqualTo(1);
    }

    @Test
    void size를_생략하면_기존과_같이_10건이다() {
        문의_N건_저장(15);

        PageResponse<AdminQnaResponse> page = qnaService.adminList(0, 10);

        assertThat(page.content()).hasSize(10);
        assertThat(page.size()).isEqualTo(10);
    }

    private void 문의_N건_저장(int n) {
        for (int i = 0; i < n; i++) {
            qnaRepository.save(new Qna(작성자, 상품, "질문 " + i, false));
        }
    }
}
