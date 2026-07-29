package com.beautyboy.payment;

import com.beautyboy.catalog.StockCommandService;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.order.OrderConfirmPort;
import com.beautyboy.order.PostOrderTasks;
import com.beautyboy.outbox.OrderConfirmedEvent;
import com.beautyboy.payment.dto.PaymentApproval;
import com.beautyboy.payment.dto.PaymentConfirmRequest;
import com.beautyboy.payment.dto.PaymentConfirmResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 결제 승인 2단계 검증. 이 프로젝트에서 가장 조심스러운 로직이다.
 *
 * <p>순서와 그 이유:
 * <ol>
 *   <li><b>주문을 락과 함께 읽는다</b> — 같은 주문에 승인이 동시에 두 번 오면(더블클릭) 이중 청구가 된다.</li>
 *   <li><b>상태를 먼저 본다</b> — 이미 결제된 주문이면 토스를 부르지도 않고 거부한다.</li>
 *   <li><b>재고를 깎는다</b> — 토스 호출 전이라, 품절이면 돈이 움직이기 전에 끝난다.</li>
 *   <li><b>토스에 승인을 요청한다</b> — 이 시점에 실제로 돈이 움직인다.</li>
 *   <li><b>승인된 금액을 우리 payableAmount와 대조한다</b> — 다르면 <b>즉시 취소</b>하고 실패시킨다.</li>
 *   <li>모두 통과하면 주문을 결제완료로 전이하고 payment를 저장한다.</li>
 *   <li><b>확정 이벤트를 조립한다</b> — 후처리(장바구니·집계·알림)는 이 이벤트를 소비하는 쪽이 맡는다.</li>
 *   <li><b>후처리를 맡긴다</b>. A5부터 이 호출은 아웃박스 INSERT 하나로 끝나고, 실제 작업은
 *       커밋 이후 컨슈머 3종이 한다 — confirm은 더 이상 후처리를 기다리지도, 후처리 실패로
 *       롤백되지도 않는다. A4b(동기 기준선)와 호출부가 같다는 것이 두 측정 지점의 전제다.</li>
 * </ol>
 *
 * <p>왜 토스 호출을 상태 검사 뒤에 두는가: 먼저 부르면 이미 결제된 주문에도 토스를 때려
 * 불필요한 승인·취소가 오간다. 우리가 막을 수 있는 것은 우리 쪽에서 먼저 막는다.
 */
@Service
public class PaymentService {

    /** 확정 이벤트 스키마 버전(설계 §4.3). 필드가 늘거나 의미가 바뀌면 올린다. */
    private static final int ORDER_CONFIRMED_VERSION = 1;
    private static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";

    private final OrderConfirmPort orderConfirmPort;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final StockCommandService stockCommandService;
    private final PostOrderTasks postOrderTasks;

    public PaymentService(OrderConfirmPort orderConfirmPort,
                          PaymentRepository paymentRepository,
                          PaymentGateway paymentGateway,
                          StockCommandService stockCommandService,
                          PostOrderTasks postOrderTasks) {
        this.orderConfirmPort = orderConfirmPort;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.stockCommandService = stockCommandService;
        this.postOrderTasks = postOrderTasks;
    }

    @Transactional
    public PaymentConfirmResponse confirm(Long memberId, PaymentConfirmRequest request) {
        // (1) 락과 함께 읽는다(포트 경유). 동시 승인 요청을 직렬화한다.
        // (2) 상태 검사도 포트 안에 있다 — 이미 결제됐으면 토스도 재고도 건드리지 않는다.
        OrderConfirmPort.ConfirmTarget target = orderConfirmPort.lockPendingOrder(request.orderNo(), memberId);

        // (3) 재고를 깎는다 — 토스 호출 전. 품절이면 돈이 움직이기 전에 여기서 끝나므로
        //     승인 취소가 필요 없다. 이후 단계가 실패하면 이 트랜잭션의 롤백이 차감을 되돌린다 —
        //     복원 코드는 존재하지 않는 것이 설계다(계획서 §2 결정 2).
        //     옵션 없는 상품(optionId null)을 거르는 것은 포트가 한다(재고 관리 단위는 주문 줄을 아는 쪽의 지식).
        stockCommandService.deductAll(target.stockLines().stream()
                .map(line -> new StockCommandService.DeductionLine(line.optionId(), line.quantity()))
                .toList());

        // (4) 토스에 승인 요청. 여기서 실제 결제가 일어난다.
        PaymentApproval approval =
                paymentGateway.confirm(request.paymentKey(), request.orderNo(), request.amount());

        // (5) 금액 대조. 우리가 계산한 payableAmount가 유일한 진실이다.
        //     토스가 알려준 승인액이 그와 다르면 조작이므로 승인을 취소한다 — 롤백이 (3)의 차감도 되돌린다.
        if (approval.approvedAmount() != target.payableAmount()) {
            paymentGateway.cancel(request.paymentKey(), "주문 금액과 승인 금액 불일치");
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // (6) 확정. 주문 전이 → payment 저장 순서로.
        //     한 시각을 세 곳(전이·payment·이벤트)에 함께 쓴다 — 이벤트의 confirmedAt이
        //     주문의 paidAt과 어긋나면 소비 측 집계가 날짜 경계에서 갈린다.
        LocalDateTime confirmedAt = LocalDateTime.now();
        String status = orderConfirmPort.markPaid(target.orderId(), confirmedAt);
        paymentRepository.save(new Payment(
                target.orderId(),
                approval.paymentKey(),
                approval.approvedAmount(),
                approval.rawJson(),
                confirmedAt));

        // (7) 확정 이벤트를 조립한다. 발행 지점이 이 자리인 이유는 두 가지다.
        //     같은 트랜잭션이라 "결제는 됐는데 이벤트가 없다"(또는 그 반대)가 원천적으로 불가능하고,
        //     토스 호출(4)보다 뒤라 앞 단계가 실패하면 롤백이 아웃박스 행까지 지워 유령 이벤트가 없다.
        //     Kafka로의 실제 발행은 릴레이가 커밋 이후에 맡는다 — 여기서 브로커를 기다리지 않는다.
        //     A3에서는 여기서 outboxAppender를 직접 불렀지만, A5가 PostOrderTasks의 구현을 아웃박스
        //     발행으로 바꾸면서 그 호출을 (8) 안으로 합쳤다 — 둘 다 두면 한 주문에 아웃박스 행이
        //     두 개 생긴다. 트랜잭션 경계는 그대로다((8)이 이 트랜잭션 안에서 돈다).
        OrderConfirmedEvent 확정_이벤트 = new OrderConfirmedEvent(
                ORDER_CONFIRMED_VERSION,
                null,                       // eventId는 아웃박스 INSERT로 채번된다(행 PK).
                ORDER_CONFIRMED,
                target.orderId(),
                target.memberId(),
                target.orderNo(),
                confirmedAt,
                target.eventLines().stream()
                        .map(line -> new OrderConfirmedEvent.Line(
                                line.goodsId(), line.optionId(), line.quantity()))
                        .toList());

        // (8) 후처리 3종(장바구니·집계·알림)을 맡긴다. A4b에서는 이 호출이 곧 후처리 실행이었고
        //     후처리 실패가 결제 실패였다. A5부터는 같은 호출이 아웃박스 INSERT 하나로 끝나고,
        //     실제 작업은 컨슈머 3종이 커밋 이후에 맡는다 — 호출부를 그대로 둔 채 구현만 바뀌었다.
        //     이 한 줄이 두 측정 지점(C2)의 유일한 차이다.
        postOrderTasks.onOrderConfirmed(확정_이벤트);

        return new PaymentConfirmResponse(target.orderNo(), status, target.payableAmount());
    }
}
