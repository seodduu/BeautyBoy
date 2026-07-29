package com.beautyboy.order;

import com.beautyboy.cart.CartService;
import com.beautyboy.notification.NotificationRepository;
import com.beautyboy.outbox.OrderConfirmedEvent;
import com.beautyboy.ranking.GoodsDailyStatRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 확정 후처리 3종(장바구니 비우기 · 판매 집계 · 알림 적재)의 진입점.
 *
 * <p>지금(A4b)은 {@code PaymentService.confirm}의 트랜잭션 안에서 직접 실행된다.
 * A5에서 <b>호출부는 그대로 둔 채</b> 구현만 이벤트 발행/소비로 바뀐다. 인터페이스로 가른 이유가
 * 이것이다 — 동기 버전과 비동기 버전의 diff가 "어디서 실행되는가"로만 갈려 두 측정 지점이
 * 같은 기능을 가리킨다는 것이 코드로 보장된다.
 */
public interface PostOrderTasks {

    void onOrderConfirmed(OrderConfirmedEvent event);
}

/**
 * 동기 구현(A4b — 비교 기준선). <b>A5는 이 클래스만 지우고 아웃박스 발행 구현으로 갈아끼운다.</b>
 *
 * <p><b>이 구현의 대가:</b> {@code PaymentService.confirm}의 트랜잭션 안에서 돌기 때문에
 * 후처리가 하나라도 실패하면 <b>이미 승인된 결제까지 롤백된다.</b> 알림 테이블이 잠기거나
 * 집계 upsert가 데드락에 걸리는 것 같은, 돈과 아무 상관 없는 사고가 결제 실패로 번진다.
 * 응답 시간도 세 번의 추가 DB 왕복만큼 그대로 늘어난다.
 * <b>이것이 A5가 없애려는 바로 그 성질이다</b> — 결함이 아니라 측정 대상이다.
 *
 * <p><b>패키지 경계에 대한 기록(의도된 임시 위반):</b> {@code order}가 ranking·notification의
 * 리포지토리를 직접 주입한다. 원칙(CLAUDE.md "타 도메인 리포지토리 직접 import 금지")대로라면
 * 각 도메인이 자기 쓰기 인터페이스를 내놓아야 하지만, 이 클래스의 수명은 A5까지다 —
 * A5에서 로직이 각 도메인 소유의 컨슈머({@code ranking.SalesAggregationConsumer},
 * {@code notification.NotificationConsumer})로 옮겨가며 경계가 제자리로 돌아온다.
 * 지금 각 도메인에 포트를 새로 파면 한 커밋 뒤에 전부 지워질 코드가 된다.
 * 대신 <b>엔티티는 한 개도 넘기지 않는다</b> — 두 리포지토리 모두 스칼라만 받는 메서드로 부른다.
 * (장바구니는 예외 없이 {@code CartService}라는 서비스 인터페이스를 경유한다.)
 */
@Component
class SyncPostOrderTasks implements PostOrderTasks {

    /** 알림 종류. 지금은 이 한 종류뿐이다(V92 DDL의 type 컬럼 주석과 같은 값). */
    private static final String TYPE_ORDER_CONFIRMED = "ORDER_CONFIRMED";

    private final CartService cartService;
    private final GoodsDailyStatRepository goodsDailyStatRepository;
    private final NotificationRepository notificationRepository;

    SyncPostOrderTasks(CartService cartService,
                       GoodsDailyStatRepository goodsDailyStatRepository,
                       NotificationRepository notificationRepository) {
        this.cartService = cartService;
        this.goodsDailyStatRepository = goodsDailyStatRepository;
        this.notificationRepository = notificationRepository;
    }

    /**
     * 호출자(confirm)의 트랜잭션에 그대로 참여한다 — 별도 {@code @Transactional}을 붙이지 않는 것이
     * 의도다. 여기에 {@code REQUIRES_NEW}를 달면 "결제는 롤백됐는데 알림은 남는" 상태가 생기고,
     * 그것은 동기 방식의 대가를 재는 이 태스크의 목적 자체를 흐린다.
     */
    @Override
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        List<OrderConfirmedEvent.Line> lines = event.lines();

        // (1) 결제가 끝난 상품만 장바구니에서 뺀다. 주문 생성 시점의 전체 비우기(clear)를 대체한다 —
        //     결제를 포기해도 장바구니가 비워지던 기존 동작의 수정이다(설계 §2-2).
        cartService.removeByGoods(event.memberId(), lines.stream()
                .map(OrderConfirmedEvent.Line::goodsId)
                .distinct()
                .toList());

        // (2) 판매 집계를 증분한다. 랭킹 배치의 풀 방식(주문 테이블 재집계 후 덮어쓰기)을 대체한다.
        //     같은 상품이 여러 줄로 나뉘어 올 수 있으므로 줄마다 더한다(덮어쓰기가 아니다).
        for (OrderConfirmedEvent.Line line : lines) {
            goodsDailyStatRepository.upsertSalesIncrement(
                    line.goodsId(), event.confirmedAt().toLocalDate(), line.quantity());
        }

        // (3) 알림 적재. 중복이면 no-op이다(uk_notification_dedup).
        //     dedup 키로 쓸 eventId가 없으면 orderId로 대신한다 — 동기 경로는 아웃박스 행 id를
        //     돌려받지 않기 때문이다. 주문 하나는 한 번만 확정되므로 유일성은 같다.
        //     A5는 소비한 이벤트의 실제 eventId를 넘긴다.
        Long dedupKey = event.eventId() != null ? event.eventId() : event.orderId();
        notificationRepository.insertIfAbsent(
                event.memberId(),
                dedupKey,
                TYPE_ORDER_CONFIRMED,
                "주문 " + event.orderNo() + " 결제가 완료됐어요.",
                LocalDateTime.now());
    }
}
