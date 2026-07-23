package com.beautyboy.payment;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.order.Order;
import com.beautyboy.order.OrderRepository;
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
 *   <li><b>토스에 승인을 요청한다</b> — 이 시점에 실제로 돈이 움직인다.</li>
 *   <li><b>승인된 금액을 우리 payableAmount와 대조한다</b> — 다르면 <b>즉시 취소</b>하고 실패시킨다.</li>
 *   <li>모두 통과하면 주문을 결제완료로 전이하고 payment를 저장한다.</li>
 * </ol>
 *
 * <p>왜 토스 호출을 상태 검사 뒤에 두는가: 먼저 부르면 이미 결제된 주문에도 토스를 때려
 * 불필요한 승인·취소가 오간다. 우리가 막을 수 있는 것은 우리 쪽에서 먼저 막는다.
 */
@Service
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    public PaymentService(OrderRepository orderRepository,
                          PaymentRepository paymentRepository,
                          PaymentGateway paymentGateway) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
    }

    @Transactional
    public PaymentConfirmResponse confirm(Long memberId, PaymentConfirmRequest request) {
        // (1) 락과 함께 읽는다. 동시 승인 요청을 직렬화한다.
        Order order = orderRepository.findByOrderNoForUpdate(request.orderNo())
                .filter(o -> o.ownedBy(memberId))   // 남의 주문이면 존재를 숨겨 404로 답한다.
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // (2) 상태를 먼저 본다. 이미 결제됐으면 토스를 부르지 않는다.
        if (!Order.STATUS_PENDING.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_CONFIRMED);
        }

        // (3) 토스에 승인 요청. 여기서 실제 결제가 일어난다.
        PaymentApproval approval =
                paymentGateway.confirm(request.paymentKey(), request.orderNo(), request.amount());

        // (4) 금액 대조. 우리가 계산한 payableAmount가 유일한 진실이다.
        //     토스가 알려준 승인액이 그와 다르면 조작이므로 승인을 취소한다.
        if (approval.approvedAmount() != order.getPayableAmount()) {
            paymentGateway.cancel(request.paymentKey(), "주문 금액과 승인 금액 불일치");
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // (5) 확정. 주문 전이 → payment 저장 순서로.
        order.markPaid(LocalDateTime.now());
        paymentRepository.save(new Payment(
                order.getId(),
                approval.paymentKey(),
                approval.approvedAmount(),
                approval.rawJson(),
                LocalDateTime.now()));

        return new PaymentConfirmResponse(order.getOrderNo(), order.getStatus(), order.getPayableAmount());
    }
}
