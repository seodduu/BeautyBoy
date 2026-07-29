# 뷰티보이 트래픽 개선기 — 설계 (2026-07-29)

> **한 문장**: 쓰기는 이벤트로(Kafka), 읽기는 캐시로(Redis) 버티고, 그 효과를 부하테스트 수치로 증명한다.

1차 MVP가 끝난 뷰티보이에 대용량/동시성 역량을 얹는 고도화다. 목표는 기능 추가가 아니라
**"트래픽이 몰리면 어떻게 되는가"에 대한 답을 코드와 수치로 갖는 것**이며, 산출물(ADR·부하 리포트)이
곧 포트폴리오다.

## 1. 스코프

**넣는 것**
- 주문 확정 후처리 3종(장바구니 비우기 · 판매량 집계 · 알림 기록)을 Kafka 이벤트로 분리
  — 트랜잭셔널 아웃박스 + 컨슈머 멱등성 + DLQ
- 랭킹 / 상품 목록·검색 / 성분 궁합의 Redis 캐싱 + 무효화 전략
- k6 부하테스트: before/after 수치 리포트(응답시간·처리량·컨슈머 랙)
- ADR 3편: Kafka 선택 근거, at-least-once+멱등성, 캐시 전략

**빼는 것 (YAGNI)**
- 주문 접수 자체의 비동기화 — 후처리만 분리한다
- Kafka 멀티 브로커/복제 — KRaft 단일 노드. "운영이라면 3노드"는 ADR에 한 줄만
- 마이크로서비스 분리 — 모놀리스 유지, 이벤트는 모놀리스 내부 컨슈머가 소비
- 알림의 실제 발송(푸시/메일) — DB 레코드 적재까지만. 조회 화면도 이번 스코프 아님
- Grafana/Prometheus 풀 스택 — 리포트에 필요한 수치는 Actuator + k6 결과로 뽑는다

**인프라 변화**: docker-compose에 Kafka(KRaft 단일 노드, 힙 조임) 추가. Redis를 "선택"에서
**필수로 승격**(캐시가 핵심 기능이 되므로). 단 Redis 다운 시 서비스는 DB 직행으로 살아야 한다
(기존 조회수 버퍼의 폴백 철학 유지). 로컬 개발 경로(bootRun + npm run dev)도 동일하게 동작해야 한다.

## 2. 현행 구조에서 출발점이 되는 사실

설계는 아래 실측 사실 위에 서 있다. 구현 계획 작성 시 재검증하지 말고 이 절을 기준으로 삼는다.

1. **결제 확정의 동기 핵심은 `PaymentService.confirm`** — 락 조회 → 재고 차감 → 토스 승인 →
   금액 대조 → markPaid → payment 저장이 한 트랜잭션. 이 순서는 건드리지 않는다(기존 설계 주석의
   이유가 전부 유효). 아웃박스 INSERT는 이 트랜잭션의 마지막에 붙는다.
2. **장바구니 비우기는 현재 주문 "생성" 시점**(`OrderService:97`, `cartService.clear`)이다.
   결제를 포기해도 장바구니가 비워지는 구조. 이번에 이 호출을 **제거**하고 확정 이벤트 컨슈머로
   옮긴다 — "결제가 완료된 주문의 상품만 비운다"는 **의도된 행동 변경**이며, 관련 E2E
   (시드 계정 장바구니 전제)도 이 변경에 맞춰 조정한다.
3. **판매량 집계는 현재 랭킹 배치의 풀 방식** — `RankingBatchService.rebuild()`가 매 실행마다
   `OrderSalesStatProvider`(주문 테이블 집계 쿼리)로 오늘 판매를 당겨와 `GoodsDailyStat`에 반영한다.
   이번에 판매 수집을 **이벤트 증분 갱신으로 전환**한다: 컨슈머가 `GoodsDailyStat`의 판매 수량을
   증분하고, 배치의 판매 수집 단계는 제거한다(찜·조회 수집은 유지). 이중 계상이 나지 않도록
   **한 시점에 한 경로만** 살아 있어야 한다 — 전환 커밋에서 동시에 바꾼다.
4. **알림 도메인은 존재하지 않는다.** `notification` 테이블과 최소 엔티티를 신설한다.
   적재까지만이 스코프다.
5. **랭킹 조회는 이미 스냅샷 테이블**을 읽는다(통째 교체 방식). 따라서 랭킹 캐시의 원본 비용은
   낮은 편이지만, 최고 트래픽 경로이므로 캐시 가치는 여전히 있고 스탬피드 실험의 소재가 된다.

## 3. 아키텍처 개요

```
                    ┌─ 동기 (핵심 경로, 기존 유지) ────────────────────┐
confirm 요청 ─────→ │ 락 조회 → 재고 차감 → 토스 승인 → 금액 대조     │
                    │ → markPaid → payment 저장 → outbox INSERT      │→ 즉시 200
                    └───────────────── 한 트랜잭션 ───────────────────┘
                                          │
                  아웃박스 릴레이(1초 폴링) │ Kafka 발행 후 PUBLISHED 마킹
                                          ▼
                          Kafka topic: order-events (파티션 3, 키=orderId)
                                          │
                          컨슈머 그룹 beautyboy-post-order
                          ├─ 장바구니 비우기   (자연 멱등)
                          ├─ 판매량 집계 증분  (processed_event로 중복 차단)
                          └─ 알림 레코드 적재  (유니크 제약으로 중복 차단)
                          실패 → 백오프 재시도 3회 → order-events.DLT

읽기: 랭킹/목록/궁합 조회 → Redis HIT → 즉시 응답
                            └ MISS → DB/계산 → 적재 → 응답 (Redis 장애 시 DB 직행)
```

공식 보장 수준은 **at-least-once**다. 릴레이가 "발행 후 마킹 전"에 죽으면 재발행되므로
중복은 컨슈머 멱등성이 막는다. exactly-once를 흉내 내지 않는 이유는 ADR ②에 적는다.

## 4. 발행 측 — 트랜잭셔널 아웃박스

### 4.1 DDL (공유 계약 — 전량. Flyway 번호는 머지 직전 확정 규칙을 따른다)

```sql
-- V9x__outbox_event.sql
CREATE TABLE outbox_event (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(30)  NOT NULL,            -- 'ORDER'
    aggregate_id   BIGINT       NOT NULL,            -- orderId
    event_type     VARCHAR(50)  NOT NULL,            -- 'ORDER_CONFIRMED'
    payload        JSON         NOT NULL,            -- 아래 4.3 스키마
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | PUBLISHED
    created_at     DATETIME(6)  NOT NULL,
    published_at   DATETIME(6)  NULL,
    INDEX idx_outbox_pending (status, created_at)    -- 릴레이의 폴링 쿼리 전용
);

-- V9x__processed_event.sql
CREATE TABLE processed_event (
    event_id     BIGINT       NOT NULL,              -- outbox_event.id
    consumer     VARCHAR(50)  NOT NULL,              -- 'sales-aggregation' 등
    processed_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (event_id, consumer)
);

-- V9x__notification.sql
CREATE TABLE notification (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id  BIGINT       NOT NULL,
    event_id   BIGINT       NOT NULL,                -- outbox_event.id
    type       VARCHAR(30)  NOT NULL,                -- 'ORDER_CONFIRMED'
    message    VARCHAR(200) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_notification_dedup (member_id, event_id)  -- 중복 소비 차단
);
```

### 4.2 릴레이

- `@Scheduled(fixedDelay = 1000)` 스케줄러가 `status=PENDING`을 `created_at` 오름차순으로
  최대 100건 집어 발행 → 각 건 발행 성공 시 PUBLISHED + `published_at` 마킹.
- 발행 순서는 "같은 orderId 안에서"만 의미가 있고, 그것은 파티션 키가 보장한다.
  릴레이 자체는 단일 스레드 폴링으로 충분하다(멀티 인스턴스 아님).
- PENDING이 계속 쌓이면(카프카 다운 등) 릴레이는 로그만 남기고 다음 주기에 재시도 —
  주문 확정은 이미 커밋됐으므로 사용자 영향 없음. 이것이 아웃박스의 존재 이유다.

### 4.3 이벤트 페이로드 (공유 계약 — 전량)

```json
{
  "version": 1,
  "eventId": 123,               // outbox_event.id — 컨슈머 멱등성 키
  "eventType": "ORDER_CONFIRMED",
  "orderId": 456,
  "memberId": 789,
  "orderNo": "BB-20260729-0001",
  "confirmedAt": "2026-07-29T12:34:56",
  "lines": [ { "goodsId": 1, "optionId": 2, "quantity": 3 } ]
}
```

컨슈머가 DB를 다시 조회하지 않고 처리할 수 있는 최소 정보를 담는다(자기완결 이벤트).
`version` 필드는 스키마 진화 대비 — v1 컨슈머는 모르는 필드를 무시한다.

## 5. 토픽/파티션과 컨슈머

- 토픽 `order-events`, **파티션 3, 키 = orderId** — 같은 주문은 순서 보장, 다른 주문은 병렬.
  파티션 3의 근거: 단일 컨슈머 인스턴스에서도 파티션별 병렬 소비 실험이 가능한 최소 수.
- 컨슈머 그룹 `beautyboy-post-order` 하나에 리스너 3개.

| 컨슈머 | 하는 일 | 멱등성 |
|---|---|---|
| cart-clear | 이벤트 `lines`의 상품을 해당 회원 장바구니에서 삭제 | 자연 멱등 — 없는 항목 삭제는 no-op |
| sales-aggregation | `GoodsDailyStat` 판매 수량을 `lines` 기준 증분 | `processed_event` (event_id, consumer) INSERT 선행 — 중복 키면 스킵 |
| notification | 주문 완료 알림 레코드 INSERT | `uk_notification_dedup` — 중복 키면 스킵 |

집계처럼 두 번 하면 값이 틀어지는 곳만 처리 기록 테이블을 쓴다. 전부에 쓰는 것은 과설계.

**실패 처리**: `DefaultErrorHandler` 백오프 재시도 3회(1s→2s→4s) → `order-events.DLT`로 이동
(원본 토픽·파티션·오프셋·예외를 헤더에 보존). 재처리는 admin API
`POST /api/admin/dlq/replay` 하나 — DLT 메시지를 원 토픽으로 재발행. 관리 화면은 만들지 않는다.

**경계 케이스**
- 결제 실패/취소: 아웃박스 INSERT가 확정 트랜잭션 안에만 있으므로 이벤트 자체가 없다.
- 컨슈머 지연 중 장바구니 조회: 몇 초간 주문 상품이 남아 보일 수 있음 — **의도된 최종 일관성**.
  README와 ADR에 명시한다.

## 6. 읽기 — Redis 캐싱

Cache-Aside. Spring `@Cacheable` + RedisCacheManager 기반, 궁합처럼 수동 조작이 필요한 곳만 직접.
캐시 계층 예외는 삼켜 DB 직행 폴백. 키에 `v1:` 버전 프리픽스.

| 대상 | 키 | TTL | 무효화 |
|---|---|---|---|
| 랭킹 | `v1:ranking:{기간}:{카테고리}` | 10분 | TTL만. 단 랭킹 배치가 스냅샷 교체 직후 해당 키 삭제 |
| 목록/검색 | `v1:goods:list:{카테고리}:{정렬}:{페이지}:{필터해시}` | 5분 | TTL + 상품 등록/수정 시 카테고리 패턴 삭제 |
| 성분 궁합 | `v1:compat:{idA}:{idB}` (idA<idB 정규화) | 24시간 | 상품 성분 변경 시 해당 상품 포함 키 삭제 |

- 검색 필터 조합은 해시로 눌러 키 폭발 방지. 드문 조합은 TTL로 자연 소멸.
- **스탬피드 대응은 랭킹에만**: 랭킹 배치가 이미 주기 실행되므로 **배치 직후 캐시를 미리 굽는
  워밍 방식**을 1안으로 한다(soft-TTL 확률 재계산은 워밍이 불충분할 때의 2안 — 선택 결과를 ADR ③에
  기록). 목록/궁합은 원본 비용이 낮아 대응 생략.

## 7. 검증 — 부하테스트와 관측

**k6 시나리오 2개**, 개선 전/후를 같은 조건에서 측정한다.

| 시나리오 | 부하 모형 | 지표 | 기대 스토리 |
|---|---|---|---|
| ① 주문 확정 | 10→200 VU 램프업, confirm 반복 | p50/p95/p99, RPS, 에러율 + 컨슈머 랙 추이 | 후처리 분리로 confirm p95 감소, 랙이 쌓였다 풀리는 완충 그래프 |
| ② 조회 혼합 | 랭킹 70% + 목록 20% + 궁합 10% | p95, RPS, 캐시 히트율 | 캐시로 p95 급감 + DB 부하 감소 |

- 토스 승인은 부하테스트 프로필에서만 **지연 ~100ms 스텁**으로 치환. 리포트에 정직하게 명시 —
  "외부 결제 구간을 제외한 우리 서버의 처리 능력 측정".
- 관측은 Actuator + Micrometer(컨슈머 랙, 캐시 히트율)와 k6 출력으로 충분.

**테스트 전략**
- 단위: 릴레이(발행/마킹/재발행 경계), 멱등성 스킵, 캐시 키 생성.
- 통합: Testcontainers **Kafka + MySQL** — confirm → 아웃박스 → 발행 → 컨슈머 3종 처리 전 구간,
  DLQ 이동/재처리 시나리오. 테스트 케이스 이름과 단언은 구현 계획에서 전량 명세한다.
- E2E: 기존 주문 플로우 회귀 확인만. 장바구니 비우기 시점 변경에 따른 기존 스펙 수정 포함.
- DoD: `integrationTest` 통과 + compose 실기동 curl 확인 (H2 녹색만으로 완료 선언 금지).

## 8. 산출물

1. ADR `docs/adr/` — ① Kafka 선택(vs Redis Streams / Spring 이벤트, 비교표 포함),
   ② at-least-once + 멱등성(vs exactly-once 흉내), ③ 캐시 전략(스탬피드 대응 선택 포함)
2. 부하테스트 리포트 — before/after 표 + 컨슈머 랙 그래프 + 측정 조건
3. README "트래픽 개선기" 섹션 — 한 장 요약 + 리포트/ADR 링크

## 9. 다음 단계

구현 계획(`superpowers:writing-plans`)에서 웨이브/터미널 분할, 각 터미널 실행 프롬프트,
테스트 케이스 전량 명세를 작성한다. 예상 분할 축: (A) 아웃박스+Kafka 발행/소비,
(B) 캐싱, (C) 부하테스트+리포트 — A와 B는 병렬 가능, C는 A·B 머지 후.
