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
 * QnaService.adminList + visibleQuestion의 admin 예외를 검증한다.
 * AdminQnaController를 거치지 않고 서비스를 직접 호출한다 — admin 예외는 서비스 메서드
 * (adminList) 자체가 트러스트 경계이고, 그 경계는 @PreAuthorize("hasRole('ADMIN')")로
 * 컨트롤러가 이미 게이트한 뒤에만 이 메서드에 도달할 수 있다는 것이 근거다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminQnaListTest {

    private static final Long 작성자 = 1L;
    private static final Long 상품 = 900L;

    @Autowired
    QnaService qnaService;
    @Autowired
    QnaRepository qnaRepository;

    @Test
    void 관리자_목록은_비밀글도_본문을_그대로_보여준다() {
        qnaRepository.save(new Qna(작성자, 상품, "비밀 질문입니다", true));

        PageResponse<AdminQnaResponse> result = qnaService.adminList(0, 10);

        assertThat(result.content()).extracting(AdminQnaResponse::question)
                .contains("비밀 질문입니다");
    }

    @Test
    void 관리자_목록은_goodsNo를_포함한다() {
        Qna 저장됨 = qnaRepository.save(new Qna(작성자, 상품, "재고 있나요?", false));

        PageResponse<AdminQnaResponse> result = qnaService.adminList(0, 10);

        assertThat(result.content()).filteredOn(item -> item.qnaId().equals(저장됨.getId()))
                .extracting(AdminQnaResponse::goodsNo)
                .containsExactly(상품);
    }

    @Test
    void 관리자_목록은_상태_원문_값을_그대로_낸다() {
        qnaRepository.save(new Qna(작성자, 상품, "답변 안 된 질문", false));

        PageResponse<AdminQnaResponse> result = qnaService.adminList(0, 10);

        assertThat(result.content()).extracting(AdminQnaResponse::status)
                .contains("WAITING");
    }

    @Test
    void 미답변_문의를_먼저_보여준다() {
        Qna 답변됨 = qnaRepository.save(new Qna(작성자, 상품, "답변된 질문", false));
        qnaService.answer(답변됨.getId(), "네");
        Qna 대기중 = qnaRepository.save(new Qna(작성자, 상품, "대기중 질문", false));

        PageResponse<AdminQnaResponse> result = qnaService.adminList(0, 10);

        int 대기중_인덱스 = indexOf(result, 대기중.getId());
        int 답변됨_인덱스 = indexOf(result, 답변됨.getId());
        assertThat(대기중_인덱스).isLessThan(답변됨_인덱스);
    }

    @Test
    void 일반_목록_조회의_비밀글_마스킹은_회귀하지_않는다() {
        // admin 예외를 추가해도 공개 목록(qnaService.list)의 기존 동작은 그대로여야 한다.
        Long 남 = 2L;
        qnaRepository.save(new Qna(작성자, 상품, "비밀 질문입니다", true));

        var 공개목록 = qnaService.list(상품, 남, 0);

        assertThat(공개목록.content()).extracting(item -> item.question())
                .contains("비밀글입니다.");
    }

    private int indexOf(PageResponse<AdminQnaResponse> result, Long qnaId) {
        for (int i = 0; i < result.content().size(); i++) {
            if (result.content().get(i).qnaId().equals(qnaId)) {
                return i;
            }
        }
        throw new AssertionError("qnaId " + qnaId + "를 찾지 못했다");
    }
}
