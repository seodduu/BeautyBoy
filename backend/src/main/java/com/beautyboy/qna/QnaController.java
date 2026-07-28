package com.beautyboy.qna;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.common.PageResponse;
import com.beautyboy.qna.dto.QnaCreateRequest;
import com.beautyboy.qna.dto.QnaResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Q&A 질문 등록·조회. 등록은 인증 필요(SecurityConfig의 anyRequest().authenticated()),
 * 목록 조회는 SecurityConfig에서 공개 처리돼 있다 — 비로그인은 viewerId가 null로 넘어온다.
 * 전부 서비스에 위임한다.
 */
@RestController
public class QnaController {

    private final QnaService qnaService;

    public QnaController(QnaService qnaService) {
        this.qnaService = qnaService;
    }

    @PostMapping("/api/v1/qna")
    public ResponseEntity<ApiResponse<Void>> create(@AuthenticationPrincipal Long memberId,
                                                     @Valid @RequestBody QnaCreateRequest request) {
        qnaService.create(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null));
    }

    @GetMapping("/api/v1/qna")
    public ResponseEntity<ApiResponse<PageResponse<QnaResponse>>> list(
            @AuthenticationPrincipal Long viewerId,
            @RequestParam Long goodsNo,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(ApiResponse.ok(qnaService.list(goodsNo, viewerId, page)));
    }
}
