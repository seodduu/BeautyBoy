package com.beautyboy.outbox;

import com.beautyboy.common.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DLQ 재처리 admin API(A6, 설계 §5). 위임만 하는 컨트롤러 — 권한 처리 방식은
 * {@code AdminGoodsController}와 동일하게 핸들러에 개별 {@code @PreAuthorize}를 건다.
 *
 * <p>{@link DlqReplayService}와 같은 이유로 {@code beautyboy.events.enabled=true}일 때만
 * 빈이 뜬다 — 꺼져 있으면 이 엔드포인트 자체가 없다(404).
 */
@RestController
@ConditionalOnProperty(name = "beautyboy.events.enabled", havingValue = "true")
public class DlqReplayController {

    private final DlqReplayService dlqReplayService;

    public DlqReplayController(DlqReplayService dlqReplayService) {
        this.dlqReplayService = dlqReplayService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/admin/dlq/replay")
    public ResponseEntity<ApiResponse<DlqReplayResponse>> replay() {
        int replayed = dlqReplayService.replay();
        return ResponseEntity.ok(ApiResponse.ok(new DlqReplayResponse(replayed)));
    }

    record DlqReplayResponse(int replayed) {
    }
}
