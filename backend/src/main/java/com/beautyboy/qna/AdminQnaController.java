package com.beautyboy.qna;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.common.PageResponse;
import com.beautyboy.qna.dto.AdminQnaAnswerRequest;
import com.beautyboy.qna.dto.AdminQnaResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 위임만 하는 컨트롤러. ROLE_ADMIN 필요 — 핸들러마다 개별로 @PreAuthorize를 건다. */
@RestController
public class AdminQnaController {

    private final QnaService qnaService;

    public AdminQnaController(QnaService qnaService) {
        this.qnaService = qnaService;
    }

    /** 상품 필터 없이 전체 문의를 미답변 우선으로 본다. 비밀글도 마스킹 없이 내려간다(QnaService.adminList). */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/v1/admin/qna")
    public ResponseEntity<ApiResponse<PageResponse<AdminQnaResponse>>> list(
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(ApiResponse.ok(qnaService.adminList(page)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/v1/admin/qna/{qnaId}/answer")
    public ResponseEntity<ApiResponse<Void>> answer(@PathVariable Long qnaId,
                                                      @RequestBody AdminQnaAnswerRequest request) {
        qnaService.answer(qnaId, request.answer());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
