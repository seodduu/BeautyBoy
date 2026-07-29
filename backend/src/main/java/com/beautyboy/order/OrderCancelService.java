package com.beautyboy.order;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.outbox.OrderCanceledEvent;
import com.beautyboy.outbox.OutboxAppender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 취소의 주문 쪽 절반. 락·검증·반영·이력·아웃박스까지가 여기 몫이고,
 * 토스 호출과 보상 기록은 호출자(payment)의 몫이다 — 그 경계가 {@link OrderCancelPort}다.
 */
@Service
public class OrderCancelService implements OrderCancelPort {

    /** 취소 이벤트 스키마 버전(설계 §8). 필드가 늘거나 의미가 바뀌면 올린다. */
    private static final int ORDER_CANCELED_VERSION = 1;
    private static final String ORDER_CANCELED = "ORDER_CANCELED";

    private final OrderRepository orderRepository;
    private final OrderCancelRepository orderCancelRepository;
    private final OutboxAppender outboxAppender;

    public OrderCancelService(OrderRepository orderRepository,
                              OrderCancelRepository orderCancelRepository,
                              OutboxAppender outboxAppender) {
        this.orderRepository = orderRepository;
        this.orderCancelRepository = orderCancelRepository;
        this.outboxAppender = outboxAppender;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CancelApplication applyCancel(String orderNo, Long memberId,
                                         List<CancelLine> lines, String reason) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_EMPTY);
        }
        // (1) 락 조회 — confirm과 같은 직렬화 지점. 소유 아니면 404로 존재 은닉(기존 관례).
        Order order = orderRepository.findByOrderNoForUpdate(orderNo)
                .filter(o -> o.ownedBy(memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        order.ensureCancelable();

        // (2) 항목 대조 — 이 주문 소속이 아닌 orderItemId는 404. Map으로 한 번만 훑는다.
        Map<Long, OrderItem> byId = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));

        // (3) 반영 + 환불액 계산. 같은 항목이 두 줄로 오면 cancel()이 잔여 검증으로 걸러낸다.
        int refundAmount = 0;
        List<StockLine> stockLines = new ArrayList<>();
        OrderCancel cancel = new OrderCancel(order.getId(), reason, LocalDateTime.now());
        for (CancelLine line : lines) {
            OrderItem item = byId.get(line.orderItemId());
            if (item == null) {
                throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
            }
            item.cancel(line.quantity());
            refundAmount += item.getUnitPrice() * line.quantity();
            cancel.addItem(new OrderCancelItem(item.getId(), line.quantity()));
            if (item.getOptionId() != null) {          // 재고 비관리 항목은 복원 대상이 아니다
                stockLines.add(new StockLine(item.getOptionId(), line.quantity()));
            }
        }
        String statusAfter = order.applyCancelStatus();

        // (4) 이력 저장. payment_key 컬럼이 없는 이유는 설계 §3-2 주석 — 결제 지식은 payment
        //     도메인의 것이고, 취소↔결제 연결은 payment_compensation과 감사 로그가 보존한다.
        cancel.recordRefund(refundAmount);
        orderCancelRepository.save(cancel);

        // (5) 확정 이벤트 — PaymentService (7)과 같은 근거: 같은 트랜잭션이라 유령 이벤트가 없다.
        LocalDateTime canceledAt = cancel.getCanceledAt();
        outboxAppender.appendOrderCanceled(new OrderCanceledEvent(ORDER_CANCELED_VERSION, null,
                ORDER_CANCELED, order.getId(), order.getMemberId(), order.getOrderNo(), canceledAt,
                refundAmount, 이벤트_라인들(lines, byId)));

        return new CancelApplication(order.getId(), order.getOrderNo(), refundAmount,
                statusAfter, canceledAt, stockLines);
    }

    /**
     * 이벤트 줄은 필터가 없다 — 취소 이벤트의 소비자(판매 집계)는 재고 비관리 상품(optionId
     * null)의 판매도 되돌려야 한다. stockLines와 갈라지는 이유가 이것이다(확정 쪽과 같은 구조).
     */
    private List<OrderCanceledEvent.Line> 이벤트_라인들(List<CancelLine> lines,
                                                  Map<Long, OrderItem> byId) {
        return lines.stream()
                .map(line -> {
                    OrderItem item = byId.get(line.orderItemId());
                    return new OrderCanceledEvent.Line(
                            item.getGoodsId(), item.getOptionId(), line.quantity());
                })
                .toList();
    }
}
