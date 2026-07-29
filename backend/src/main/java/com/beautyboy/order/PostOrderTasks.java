package com.beautyboy.order;

import com.beautyboy.outbox.OrderConfirmedEvent;
import com.beautyboy.outbox.OutboxAppender;
import org.springframework.stereotype.Component;

/**
 * 주문 확정 후처리 3종(장바구니 비우기 · 판매 집계 · 알림 적재)의 진입점.
 *
 * <p>A4b에서는 {@code PaymentService.confirm}의 트랜잭션 안에서 셋을 직접 실행했고(동기 기준선),
 * A5에서 <b>호출부는 그대로 둔 채</b> 구현만 아웃박스 발행으로 바뀌었다 — 실제 작업은 컨슈머
 * 3종({@code cart.CartClearOnOrderConfirmed}, {@code ranking.SalesAggregationConsumer},
 * {@code notification.NotificationConsumer})이 맡는다. 인터페이스로 가른 이유가 이것이다:
 * 두 버전의 diff가 "어디서 실행되는가"로만 갈려, 두 측정 지점이 같은 기능을 가리킨다는 것이
 * 코드로 보장된다.
 */
public interface PostOrderTasks {

    void onOrderConfirmed(OrderConfirmedEvent event);
}

/**
 * 아웃박스 발행 구현(A5). 후처리를 <b>하지 않고</b>, 하겠다는 사실만 확정 트랜잭션에 남긴다.
 *
 * <p><b>왜 발행 지점이 여기인가(계획서 이탈, 오케스트레이터 승인):</b> A3는 {@code PaymentService}
 * (7)에서 {@code outboxAppender.appendOrderConfirmed}를 직접 불렀다. A5가 이 구현을 발행으로
 * 바꾸면서 그 호출을 그대로 두면 한 주문에 아웃박스 행이 두 개 생긴다. 그래서 발행을 이 구현으로
 * 내리고 {@code PaymentService}의 (7)을 제거했다. 결과적으로 <b>아웃박스 행의 수와 내용은 A4b와
 * 똑같고</b>, 두 커밋의 diff는 "후처리가 confirm 안에서 도는가, 컨슈머에서 도는가" 하나로만 갈린다 —
 * C2의 두 측정 지점이 같은 기능을 재는 이유다.
 *
 * <p><b>트랜잭션</b>: {@link OutboxAppender}가 호출자(confirm)의 트랜잭션에 그대로 참여한다.
 * "결제는 됐는데 이벤트가 없다"(또는 그 반대)가 원천적으로 불가능하고, Kafka로의 실제 발행은
 * 릴레이가 커밋 이후에 맡으므로 confirm은 브로커를 기다리지 않는다.
 *
 * <p><b>A4b와 달라진 성질</b>: 후처리 실패가 더 이상 결제 실패가 되지 않는다. 알림 테이블이
 * 잠기거나 집계 upsert가 데드락에 걸려도 결제는 이미 커밋돼 있고, 컨슈머가 재시도하다 안 되면
 * DLT로 간다. 응답 시간에서도 세 번의 추가 DB 왕복이 빠진다 — 남는 비용은 아웃박스 INSERT 하나뿐이다.
 * 대가는 최종 일관성이다: 확정 직후 몇 초간 장바구니에 주문 상품이 남아 보일 수 있다(설계 §5).
 *
 * <p>패키지 경계도 제자리로 돌아왔다. A4b의 동기 구현이 임시로 안고 있던 ranking·notification
 * 리포지토리 직접 주입이 사라지고, 각 도메인이 자기 컨슈머로 자기 테이블만 쓴다.
 */
@Component
class OutboxPublishingPostOrderTasks implements PostOrderTasks {

    private final OutboxAppender outboxAppender;

    OutboxPublishingPostOrderTasks(OutboxAppender outboxAppender) {
        this.outboxAppender = outboxAppender;
    }

    @Override
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        outboxAppender.appendOrderConfirmed(event);
    }
}
