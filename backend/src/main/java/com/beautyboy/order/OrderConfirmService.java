package com.beautyboy.order;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderConfirmService implements OrderConfirmPort {

    private final OrderRepository orderRepository;

    public OrderConfirmService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ConfirmTarget lockPendingOrder(String orderNo, Long memberId) {
        Order order = orderRepository.findByOrderNoForUpdate(orderNo)
                .filter(o -> o.ownedBy(memberId))   // 남의 주문이면 존재를 숨겨 404로 답한다.
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!Order.STATUS_PENDING.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_CONFIRMED);
        }

        List<StockLine> stockLines = order.getItems().stream()
                .filter(item -> item.getOptionId() != null)  // 옵션 없는 상품은 재고 비관리(기존 정의)
                .map(item -> new StockLine(item.getOptionId(), item.getQuantity()))
                .toList();

        // 이벤트 줄은 필터가 없다 — 확정 이벤트의 소비자는 재고 비관리 상품(optionId null)도
        // 장바구니에서 지우고 판매로 집계해야 한다. 조립이 여기 있는 이유는 주문 줄의 소유자가
        // order이기 때문이다(payment가 OrderItem을 직접 읽으면 경계가 깨진다).
        List<EventLine> eventLines = order.getItems().stream()
                .map(item -> new EventLine(item.getGoodsId(), item.getOptionId(), item.getQuantity()))
                .toList();

        return new ConfirmTarget(order.getId(), order.getOrderNo(), order.getMemberId(),
                order.getPayableAmount(), stockLines, eventLines);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String markPaid(Long orderId, LocalDateTime paidAt) {
        // lockPendingOrder와 같은 트랜잭션 — 영속성 컨텍스트의 락 잡힌 그 인스턴스가 돌아온다.
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        order.markPaid(paidAt);
        return order.getStatus();
    }
}
