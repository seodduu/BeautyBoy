package com.beautyboy.qna;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.common.PageRequests;
import com.beautyboy.common.PageResponse;
import com.beautyboy.qna.dto.AdminQnaResponse;
import com.beautyboy.qna.dto.QnaCreateRequest;
import com.beautyboy.qna.dto.QnaResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Q&A 질문 등록·조회 + 비밀글 마스킹 + (Wave 4) 관리자 답변.
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

    /** 관리자 답변 등록. 이미 답변된 문의면 QNA_ALREADY_ANSWERED — 답변은 한 번만 달린다. */
    @Transactional
    public void answer(Long qnaId, String answer) {
        Qna qna = qnaRepository.findById(qnaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QNA_NOT_FOUND));
        if (qna.getAnswer() != null) {
            throw new BusinessException(ErrorCode.QNA_ALREADY_ANSWERED);
        }
        qna.answer(answer);
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

    /**
     * admin 문의 목록. 상품 필터 없이 전체를 미답변 우선으로 훑는다.
     *
     * <p>이 메서드가 곧 admin 판정의 근거다: 호출자({@code AdminQnaController})가
     * {@code @PreAuthorize("hasRole('ADMIN')")}로 이미 게이트돼 있으므로(Spring Security가
     * 서버에서 검증한 JWT의 role 클레임 — 클라이언트가 보낸 플래그가 아니다), 이 메서드에
     * 도달했다는 사실 자체가 호출자가 ADMIN임을 증명한다. 그래서 viewerId 없이(=작성자 여부와
     * 무관하게) {@code visibleQuestion}을 admin 예외 인자로 호출해 비밀글도 마스킹하지 않는다.
     * 공개 {@link #list}는 이 경로를 타지 않으므로 기존 동작(작성자 본인만, 비로그인 마스킹)은
     * 그대로다.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminQnaResponse> adminList(int page, int size) {
        int pageSize = PageRequests.clampSize(size);
        List<Qna> items = qnaRepository.findAllOrderByWaitingFirst(PageRequest.of(page, pageSize));
        long total = qnaRepository.count();
        List<AdminQnaResponse> responses = items.stream()
                .map(this::toAdminResponse)
                .toList();
        return PageResponse.of(responses, page, pageSize, total);
    }

    private QnaResponse toResponse(Qna qna, Long viewerId) {
        return new QnaResponse(qna.getId(), visibleQuestion(qna, viewerId, false), qna.isSecret(),
                qna.getStatus(), qna.getCreatedAt());
    }

    private AdminQnaResponse toAdminResponse(Qna qna) {
        return new AdminQnaResponse(qna.getId(), qna.getGoodsId(), visibleQuestion(qna, null, true),
                qna.isSecret(), qna.getStatus(), qna.getCreatedAt());
    }

    /**
     * 비밀글 본문 마스킹.
     *
     * <p>비밀글은 작성자 본인 또는 admin에게만 본문을 보인다. 그 외에게는 질문 내용을
     * "비밀글입니다"로 가리되 항목 자체(작성일·답변여부·닉네임 자리)는 목록에 남긴다 —
     * 존재를 숨기면 "몇 번째 질문"의 흐름이 깨진다.
     *
     * <p>viewerId가 null이면(비로그인) 작성자일 수 없으므로 무조건 마스킹된다(viewerIsAdmin이
     * false인 한).
     *
     * <p>viewerIsAdmin 판정 근거는 호출자가 서버에서 검증한 것이어야 한다 — 이 메서드가
     * 클라이언트 요청의 어떤 필드도 직접 들여다보지 않는 이유다. 실제로 이 인자를 true로 넘기는
     * 유일한 호출자는 {@link #adminList}이고, 그 메서드는 {@code @PreAuthorize}로 게이트된
     * admin 컨트롤러를 통해서만 도달한다(위 adminList Javadoc 참고). 공개 {@link #list}는
     * 항상 false를 넘긴다 — 기존 동작(작성자 본인만, 비로그인 마스킹)을 회귀시키지 않는다.
     */
    private String visibleQuestion(Qna qna, Long viewerId, boolean viewerIsAdmin) {
        boolean 작성자본인 = viewerId != null && qna.getMemberId().equals(viewerId);
        if (qna.isSecret() && !작성자본인 && !viewerIsAdmin) {
            return "비밀글입니다.";
        }
        return qna.getQuestion();
    }
}
