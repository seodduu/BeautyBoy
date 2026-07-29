package com.beautyboy.payment;

import com.beautyboy.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 보상 행 조회 admin API(설계 §5-5). FAILED로 승격된 행은 자동으로 해소되지 않으므로
 * 사람이 볼 통로가 반드시 있어야 한다 — 그것이 이 엔드포인트의 존재 이유다.
 *
 * <p>위임만 하는 컨트롤러이고, 권한은 {@code DlqReplayController}와 같이 핸들러에
 * 개별 {@code @PreAuthorize}를 건다.
 */
@RestController
public class AdminCompensationController {

    private final PaymentCompensationRepository repository;

    public AdminCompensationController(PaymentCompensationRepository repository) {
        this.repository = repository;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/v1/admin/compensations")
    public ResponseEntity<ApiResponse<List<CompensationResponse>>> list(
            @RequestParam(required = false) String status) {
        List<CompensationResponse> rows = repository.findForAdmin(status).stream()
                .map(CompensationResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    record CompensationResponse(Long id, String orderNo, String paymentKey, String action,
                                int amount, String reason, String status, int retryCount,
                                String lastError, LocalDateTime createdAt,
                                LocalDateTime resolvedAt) {

        static CompensationResponse from(PaymentCompensation c) {
            return new CompensationResponse(c.getId(), c.getOrderNo(), c.getPaymentKey(),
                    c.getAction(), c.getAmount(), c.getReason(), c.getStatus(), c.getRetryCount(),
                    c.getLastError(), c.getCreatedAt(), c.getResolvedAt());
        }
    }
}
