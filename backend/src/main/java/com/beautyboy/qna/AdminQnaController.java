package com.beautyboy.qna;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.qna.dto.AdminQnaAnswerRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 위임만 하는 컨트롤러. ROLE_ADMIN 필요. */
@RestController
public class AdminQnaController {

    private final QnaService qnaService;

    public AdminQnaController(QnaService qnaService) {
        this.qnaService = qnaService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/v1/admin/qna/{qnaId}/answer")
    public ResponseEntity<ApiResponse<Void>> answer(@PathVariable Long qnaId,
                                                      @RequestBody AdminQnaAnswerRequest request) {
        qnaService.answer(qnaId, request.answer());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
