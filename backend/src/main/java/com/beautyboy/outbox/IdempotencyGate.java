package com.beautyboy.outbox;

/**
 * 컨슈머 멱등성 게이트 — outbox가 <b>다른 도메인에 내주는 유일한 창구</b>다.
 *
 * <p><b>왜 인터페이스인가</b>: 이 저장소는 "패키지 = 서비스 경계"를 규칙으로 쓴다(CLAUDE.md).
 * 원래 {@code ranking}의 판매 집계 컨슈머가 {@code outbox}의 엔티티({@link ProcessedEvent})와
 * 리포지토리({@link ProcessedEventRepository})를 직접 import했는데, 그것은 타 도메인의
 * <b>영속 모델</b>에 의존하는 것이라 규칙 위반이다 — {@code processed_event}의 PK 구성이나
 * 저장 방식을 바꾸면 랭킹 코드가 같이 깨진다.
 *
 * <p>그래서 주고받는 것을 <b>스칼라만</b>으로 줄였다. 이 인터페이스에는 엔티티도, Spring Data
 * 타입도 나오지 않는다. {@code OrderConfirmedEvent} 같은 계약(payload) 타입은 여러 도메인이
 * 공유하는 메시지 계약이므로 그대로 두는 것이 맞고, 여기서 문제 삼는 것은 엔티티+리포지토리다.
 */
public interface IdempotencyGate {

    /** 판매 집계 컨슈머의 이름. {@code processed_event.consumer} 컬럼 값이자 설계 §5 표의 그 이름이다. */
    String CONSUMER_SALES_AGGREGATION = "sales-aggregation";

    /**
     * "이 컨슈머가 이 이벤트를 처음 본다"를 원자적으로 주장한다.
     *
     * @return 처음이면 {@code true}(호출자가 처리를 진행해야 한다), 이미 처리했으면 {@code false}(스킵).
     *         중복 소비는 at-least-once에서 정상 경로이므로 예외가 아니라 반환값으로 알린다.
     */
    boolean markProcessed(long eventId, String consumer);
}
