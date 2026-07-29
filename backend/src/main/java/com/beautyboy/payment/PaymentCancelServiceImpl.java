package com.beautyboy.payment;

import com.beautyboy.catalog.StockCommandService;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.order.OrderCancelPort;
import com.beautyboy.order.dto.OrderCancelRequest;
import com.beautyboy.order.dto.OrderCancelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 동기 취소. confirm의 거울상이고, 순서가 곧 설계 §4다.
 *
 * <ol>
 *   <li><b>락·검증·로컬 반영</b>(포트 경유) — 동시 취소를 직렬화한다.</li>
 *   <li><b>재고 복원</b> — 같은 트랜잭션. 토스가 실패하면 롤백이 이것까지 되돌린다.</li>
 *   <li><b>결제 조회</b> — 포트가 orderId를 주므로 이 자리다.</li>
 *   <li><b>보상 의도 기록</b>(REQUIRES_NEW) — 커밋 불명을 관측 가능하게 만든다.</li>
 *   <li><b>토스 부분 취소</b> — 트랜잭션의 마지막 외부 호출.</li>
 * </ol>
 *
 * <p><b>왜 토스가 마지막인가</b>: 앞 단계가 실패하면 토스를 부르지도 않았으므로 보상할 것이
 * 없고, 토스가 실패하면 전체 롤백이라 역시 보상할 것이 없다. 남는 위험은 "토스 성공 후 커밋
 * 실패" 하나뿐이고, 그것을 (4)의 IN_FLIGHT 행이 잡는다(설계 §4·§5-2).
 */
@Service
public class PaymentCancelServiceImpl implements PaymentCancelService {

    private static final Logger log = LoggerFactory.getLogger(PaymentCancelServiceImpl.class);

    private final OrderCancelPort orderCancelPort;
    private final StockCommandService stockCommandService;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final CompensationRecorder compensationRecorder;

    public PaymentCancelServiceImpl(OrderCancelPort orderCancelPort,
                                    StockCommandService stockCommandService,
                                    PaymentRepository paymentRepository,
                                    PaymentGateway paymentGateway,
                                    CompensationRecorder compensationRecorder) {
        this.orderCancelPort = orderCancelPort;
        this.stockCommandService = stockCommandService;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.compensationRecorder = compensationRecorder;
    }

    @Override
    @Transactional
    public OrderCancelResponse cancel(Long memberId, String orderNo, OrderCancelRequest request) {
        // (1) 락·검증·로컬 반영·이력·아웃박스 — order 도메인의 몫(설계 §4 1~4단계).
        OrderCancelPort.CancelApplication app = orderCancelPort.applyCancel(
                orderNo, memberId,
                취소줄들(request),
                request.reason());

        // (2) 재고 복원 — 같은 트랜잭션. 토스가 실패하면 롤백이 이것까지 되돌린다(설계 §4).
        stockCommandService.restoreAll(app.stockLines().stream()
                .map(l -> new StockCommandService.RestoreLine(l.optionId(), l.quantity()))
                .toList());

        // (3) 결제 조회 — PAID 검증을 통과했으므로 없으면 정합 버그다.
        Payment payment = paymentRepository.findByOrderId(app.orderId())
                .orElseThrow(() -> new IllegalStateException("PAID 주문에 결제가 없음: " + orderNo));

        // (4) 보상 의도 — REQUIRES_NEW. 이 행이 "커밋 불명"을 관측 가능하게 만든다(설계 §5-2).
        Long compensationId = compensationRecorder.recordInFlight(
                orderNo, payment.getPaymentKey(), app.refundAmount(), request.reason());

        // (5) 토스 부분 취소 — 트랜잭션의 마지막 외부 호출.
        try {
            paymentGateway.cancelPartial(payment.getPaymentKey(), request.reason(), app.refundAmount());
        } catch (PaymentGatewayException e) {
            compensationRecorder.markAfterGatewayFailure(compensationId, e);
            log.error("토스 부분 취소 실패 orderNo={} paymentKey={} amount={}",
                    orderNo, payment.getPaymentKey(), app.refundAmount(), e);
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);
        }

        // (6) 커밋 확인 후 DONE — 커밋이 실패하면 IN_FLIGHT로 남아 스케줄러가 감지한다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                compensationRecorder.markDone(compensationId);
            }
        });

        log.info("주문 취소 확정 orderNo={} paymentKey={} refund={} status={}",
                orderNo, payment.getPaymentKey(), app.refundAmount(), app.statusAfter());
        return new OrderCancelResponse(app.orderNo(), app.statusAfter(),
                app.refundAmount(), app.canceledAt());
    }

    /** 빈 목록 판정은 포트가 한다(ORDER_CANCEL_EMPTY) — 여기서 앞질러 던지면 코드가 갈린다. */
    private List<OrderCancelPort.CancelLine> 취소줄들(OrderCancelRequest request) {
        if (request.items() == null) {
            return List.of();
        }
        return request.items().stream()
                .map(i -> new OrderCancelPort.CancelLine(i.orderItemId(), i.quantity()))
                .toList();
    }
}
