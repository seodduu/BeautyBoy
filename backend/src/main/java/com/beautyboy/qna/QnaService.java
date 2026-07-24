package com.beautyboy.qna;

import com.beautyboy.common.PageResponse;
import com.beautyboy.qna.dto.QnaCreateRequest;
import com.beautyboy.qna.dto.QnaResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Q&A 질문 등록·조회 + 비밀글 마스킹.
 *
 * <p>답변 등록은 이 서비스의 몫이 아니다 — {@code answer}/{@code answeredAt}은 스키마에만 존재하고
 * Wave 4 admin이 채운다.
 */
@Service
public class QnaService {

    private static final int DEFAULT_PAGE_SIZE = 10;

    private final QnaRepository qnaRepository;

    public QnaService(QnaRepository qnaRepository) {
        this.qnaRepository = qnaRepository;
    }

    @Transactional
    public void create(Long memberId, QnaCreateRequest request) {
        qnaRepository.save(new Qna(memberId, request.goodsNo(), request.question(), request.isSecret()));
    }

    @Transactional(readOnly = true)
    public PageResponse<QnaResponse> list(Long goodsNo, Long viewerId, int page) {
        List<Qna> items = qnaRepository.findByGoodsIdOrderByCreatedAtDesc(
                goodsNo, PageRequest.of(page, DEFAULT_PAGE_SIZE));
        long total = qnaRepository.countByGoodsId(goodsNo);
        List<QnaResponse> responses = items.stream()
                .map(qna -> toResponse(qna, viewerId))
                .toList();
        return PageResponse.of(responses, page, DEFAULT_PAGE_SIZE, total);
    }

    private QnaResponse toResponse(Qna qna, Long viewerId) {
        return new QnaResponse(qna.getId(), visibleQuestion(qna, viewerId), qna.isSecret(),
                qna.getStatus(), qna.getCreatedAt());
    }

    /**
     * 비밀글 본문 마스킹.
     *
     * <p>비밀글은 작성자 본인에게만 본문을 보인다. 그 외에게는 질문 내용을 "비밀글입니다"로 가리되
     * 항목 자체(작성일·답변여부·닉네임 자리)는 목록에 남긴다 — 존재를 숨기면 "몇 번째 질문"의 흐름이 깨진다.
     *
     * <p>viewerId가 null이면(비로그인) 작성자일 수 없으므로 무조건 마스킹된다.
     * 관리자 노출은 admin(Wave 4)에서 role 검사로 확장한다 — 지금은 작성자 본인만.
     */
    private String visibleQuestion(Qna qna, Long viewerId) {
        boolean 작성자본인 = viewerId != null && qna.getMemberId().equals(viewerId);
        if (qna.isSecret() && !작성자본인) {
            return "비밀글입니다.";
        }
        return qna.getQuestion();
    }
}
