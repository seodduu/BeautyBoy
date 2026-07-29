# 트래픽 개선기 구현 계획 (2026-07-29)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development(권장) 또는
> superpowers:executing-plans로 태스크 단위 실행. 스텝은 체크박스(`- [ ]`)로 추적한다.

**Goal:** 주문 확정 후처리 3종을 Kafka 이벤트로 분리(아웃박스+멱등성+DLQ)하고, 랭킹/목록/궁합을
Redis로 캐싱한 뒤, k6 before/after 수치로 효과를 증명한다.

**Architecture:** 설계 문서 [`2026-07-29-traffic-hardening-design.md`](2026-07-29-traffic-hardening-design.md)를
따른다. 동기 핵심(`PaymentService.confirm`)은 순서 불변, 트랜잭션 마지막에 아웃박스 INSERT만 추가.
릴레이(1초 폴링)가 Kafka `order-events`(파티션 3, 키=orderId)로 발행, 컨슈머 그룹
`beautyboy-post-order`의 리스너 3개가 소비. 보장 수준 at-least-once + 컨슈머 멱등성.
읽기는 Cache-Aside(Redis), 장애 시 DB 직행 폴백.

**Tech Stack:** Spring Kafka, Kafka KRaft 단일 노드(compose), Spring Data Redis + RedisCacheManager,
Testcontainers(Kafka+MySQL), k6.

## Global Constraints

- **설계 문서 §2 "현행 구조에서 출발점이 되는 사실"이 전제다.** 재검증하지 말고 그대로 따른다.
- 패키지 = 서비스 경계. 타 도메인 엔티티/리포지토리 직접 import 금지. 신설 패키지: `outbox`(이벤트
  공유 계약+발행 인프라), `notification`(알림 도메인).
- Flyway 번호는 **머지 직전 확정**(메모리 규칙): 작업 중엔 `V90_`대로 쓰고, 머지 게이트에서
  main의 최신 번호를 확인해 리네임한다. 현재 main 최신은 `V84__goods_review_count.sql`.
- DoD: `./gradlew test` + `./gradlew integrationTest` 통과 + compose 실기동 curl 확인.
  H2 녹색만으로 완료 선언 금지.
- 프론트 검증은 `npm test`(vitest 단독 실행 금지 — e2e 스펙 오수집), 타입은 `npx tsc -p tsconfig.app.json --noEmit`.
- 자기 태스크 Files 목록 밖 파일 수정 금지. Flyway·공통 타입·루트 빌드 설정은 공유 계약 —
  안 맞으면 보고.

## 모델 배분

CLAUDE.md 표 기준: 오케스트레이터 opus, 태스크 서브에이전트 기본 **sonnet**.
이번 계획의 opus 예외(근거: 정합성·동시성 판단): **A3(아웃박스 INSERT 위치), A4(릴레이),
A5(컨슈머 멱등성 + 이중 계상 방지 전환)**.

---

## 실행 개요 — 웨이브와 터미널

```
Wave 0 (메인 세션, main 직커밋) : 부하테스트 도구 + baseline 측정(집중·분산 두 모형)  ← A·B보다 먼저
Wave 1 (병렬 터미널 2개)
  터미널 A: feat/order-events   — 아웃박스 + Kafka 발행/소비 + DLQ   (태스크 A1~A7)
  터미널 B: feat/read-cache     — Redis 캐싱 + 무효화 + 워밍         (태스크 B1~B5)
Wave 2 (메인 세션, A·B 머지 후) : after 측정 + 리포트 + ADR + README (태스크 C1~C3)
```

- A·B의 파일 교집합은 `application.yml` 하나(각자 다른 키 추가)다. 머지 순서는 A → B로 하고
  B 머지 시 yml 충돌만 수동 해소한다. `RankingBatchService`·`GoodsDailyStatRepository`는 A만,
  `RankingScheduler`·`RankingService`는 B만 만진다.
- **사람이 할 일 (터미널 열기 전, 루트에서 2줄):**
  `git log --oneline -1` — Wave 0 커밋이 기점인지 확인.
  `git status` — 깨끗한지 확인.

### 터미널 A 실행 프롬프트 (그대로 붙여넣기)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add .claude/worktrees/뷰티보이-order-events -b feat/order-events
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 해당 worktree인지
  - git log --oneline -1 이 루트에서 본 기점 커밋과 같은지
  - docs/plans/2026-07-29-traffic-hardening.md 와 같은 폴더의 -design.md 가 존재하는지
  - git status가 깨끗한지
[2단계 — 실행]
docs/plans/2026-07-29-traffic-hardening.md 의 태스크 A1~A7을 순서대로 실행해라.
너는 오케스트레이터다: 태스크마다 서브에이전트를 스폰해 실행하고(모델: A1·A2·A6·A7은 sonnet,
A3·A4·A5는 opus), 태스크 사이마다 테스트 통과와 Files 목록 준수를 리뷰해라.
Global Constraints 절이 모든 태스크에 적용된다. 완료 기준: ./gradlew test 와
./gradlew integrationTest 녹색. 끝나면 브랜치에 커밋만 하고 머지는 하지 마라 — 머지는 메인 세션의 몫이다.
```

### 터미널 B 실행 프롬프트 (그대로 붙여넣기)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add .claude/worktrees/뷰티보이-read-cache -b feat/read-cache
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 해당 worktree인지
  - git log --oneline -1 이 루트에서 본 기점 커밋과 같은지
  - docs/plans/2026-07-29-traffic-hardening.md 와 같은 폴더의 -design.md 가 존재하는지
  - git status가 깨끗한지
[2단계 — 실행]
docs/plans/2026-07-29-traffic-hardening.md 의 태스크 B1~B5를 순서대로 실행해라.
너는 오케스트레이터다: 태스크마다 서브에이전트를 스폰해 실행하고(모델: 전부 sonnet),
태스크 사이마다 테스트 통과와 Files 목록 준수를 리뷰해라.
Global Constraints 절이 모든 태스크에 적용된다. 완료 기준: ./gradlew test 와
./gradlew integrationTest 녹색. 끝나면 브랜치에 커밋만 하고 머지는 하지 마라 — 머지는 메인 세션의 몫이다.
```

---

## Wave 0 — 부하테스트 도구와 baseline (메인 세션, main 직커밋)

### Task 0.1: loadtest 프로필 토스 스텁

**Files:**
- Create: `backend/src/main/java/com/beautyboy/payment/StubPaymentGateway.java`
- Test: `backend/src/test/java/com/beautyboy/payment/StubPaymentGatewayTest.java`

**Interfaces:**
- Consumes: `PaymentGateway` 인터페이스(`confirm(paymentKey, orderNo, amount)` → `PaymentApproval`, `cancel(paymentKey, reason)`).
- Produces: `@Profile("loadtest")` 빈. 이 프로필에서 `TossPaymentGateway`는 뜨지 않아야 한다
  (`TossPaymentGateway`에 `@Profile("!loadtest")`를 붙이는 수정 포함 — Files에 Modify로 추가:
  `backend/src/main/java/com/beautyboy/payment/TossPaymentGateway.java`).

**판단 코드 (전량 — 스텁이 진짜처럼 굴어야 측정이 의미 있다):**

```java
@Component
@Profile("loadtest")
public class StubPaymentGateway implements PaymentGateway {
    // 토스 실측 응답시간의 근사치. 이 지연이 없으면 confirm 개선 폭이 과장된다.
    private static final long SIMULATED_LATENCY_MS = 100;

    @Override
    public PaymentApproval confirm(String paymentKey, String orderNo, int amount) {
        try { Thread.sleep(SIMULATED_LATENCY_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        // 요청 금액을 그대로 승인한다 — 금액 대조 로직이 항상 통과하도록.
        return new PaymentApproval(paymentKey, amount, "{\"stub\":true}");
    }

    @Override
    public void cancel(String paymentKey, String reason) { /* no-op */ }
}
```

(`PaymentApproval` 생성자 시그니처는 실제 record 정의에 맞춘다 — 필드가 다르면 스텁을 맞추고 record는 불변.)

- [ ] 테스트 작성: `loadtest_프로필에서는_스텁이_지연과_함께_요청금액을_그대로_승인한다`
      (단언: 반환 `approvedAmount == 입력 amount`, 소요시간 ≥ 100ms),
      `loadtest_프로필이_아니면_스텁_빈이_없다`(컨텍스트에 `StubPaymentGateway` 부재)
- [ ] 실패 확인 → 구현 → 통과 확인 → 커밋

### Task 0.2: k6 스크립트 2종

**Files:**
- Create: `tools/loadtest/confirm.js`, `tools/loadtest/browse.js`, `tools/loadtest/README.md`

**판단 코드 (전량 — 부하 모형 숫자가 곧 측정 조건이다):**

```javascript
// tools/loadtest/confirm.js — 시나리오 ①: 주문 생성→확정. before/after에서 동일하게 사용.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  scenarios: {
    confirm: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        { duration: '30s', target: 50 },   // 워밍업
        { duration: '1m',  target: 200 },  // 본 측정 구간 — 200VU는 로컬 커넥션 풀 한계 직전
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: { http_req_failed: ['rate<0.01'] }, // 에러율 1% 초과면 측정 자체가 무효
};

export function setup() {
  const login = http.post(`${BASE}/api/auth/login`, JSON.stringify({
    email: __ENV.LOADTEST_EMAIL, password: __ENV.LOADTEST_PASSWORD,
  }), { headers: { 'Content-Type': 'application/json' } });
  return { token: login.json('accessToken') };
}

export default function (data) {
  const auth = { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.token}` } };
  // 매 반복 주문 생성 → 확정. goodsId/optionId는 시드의 재고 넉넉한 상품으로 고정(env로 주입).
  const order = http.post(`${BASE}/api/orders`, JSON.stringify({
    items: [{ goodsId: Number(__ENV.GOODS_ID), optionId: Number(__ENV.OPTION_ID), quantity: 1 }],
  }), auth);
  if (!check(order, { 'order created': (r) => r.status === 200 || r.status === 201 })) return;
  const orderNo = order.json('orderNo');
  const confirm = http.post(`${BASE}/api/payments/confirm`, JSON.stringify({
    orderNo, paymentKey: `stub-${orderNo}`, amount: order.json('payableAmount'),
  }), auth);
  check(confirm, { 'confirmed': (r) => r.status === 200 });
}
```

```javascript
// tools/loadtest/browse.js — 시나리오 ②: 조회 혼합 70/20/10.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  scenarios: {
    browse: { executor: 'constant-vus', vus: 100, duration: '2m' },
  },
  thresholds: { http_req_failed: ['rate<0.01'] },
};

export default function () {
  const r = Math.random();
  let res;
  if (r < 0.7) {
    res = http.get(`${BASE}/api/ranking?category=ALL`);          // 70% 랭킹
  } else if (r < 0.9) {
    res = http.get(`${BASE}/api/goods?category=${__ENV.CATEGORY || '1010'}&page=0`); // 20% 목록
  } else {
    res = http.get(`${BASE}/api/compat/verdict?a=${__ENV.GOODS_A}&b=${__ENV.GOODS_B}`); // 10% 궁합
  }
  check(res, { '2xx': (x) => x.status >= 200 && x.status < 300 });
}
```

- 실제 엔드포인트 경로·응답 필드명은 컨트롤러 실물에 맞춰 수정하되, **부하 모형 숫자와 비율은
  위 값 고정**(바꾸면 before/after 비교가 깨진다). `tools/loadtest/README.md`에 실행 커맨드
  (bootRun + `--spring.profiles.active=local,loadtest` + 13306 MySQL + Redis)와 env 주입 값 기록.
- [ ] 스모크: VU 1로 30초 돌려 에러율 0 확인 → 커밋

### Task 0.3: baseline 측정

- [ ] curl 스모크 레시피(메모리)대로 스택 기동(loadtest 프로필 추가), 시드에서 재고 1만 이상인
      상품으로 env 구성 (없으면 admin API로 재고 올린다)
- [ ] `confirm.js`·`browse.js` 각 1회 본 측정, k6 summary(JSON)를
      `docs/loadtest/2026-07-29-baseline/` 에 저장. 측정 조건(하드웨어·프로필·시드 상태)을 같은
      폴더 `conditions.md`에 기록 — after 측정은 이 조건을 그대로 재현해야 한다
- [ ] 커밋

**측정 결과 (완료 — 커밋 `2646df7`)**: confirm p95 15.3s / p99 17.5s / 18.0 RPS, browse p95 74.3ms /
p99 79.3ms / 2484 RPS, 양쪽 에러율 0%. 조건은 `docs/loadtest/2026-07-29-baseline/conditions.md`.

### Task 0.4: 분산 모형 추가와 두 번째 baseline

**왜 이 태스크가 생겼나 (Task 0.3의 발견):** confirm p95 15.3초의 지배 원인은 동기 결제 경로의
일반적 지연이 아니라 **단일 `goods_option` 행에 대한 InnoDB 배타 락 직렬화**였다.
`PaymentService.confirm`이 한 트랜잭션 안에서 재고 차감 UPDATE(커밋까지 락 유지)를 한 뒤
스텁 토스 100ms를 기다리는데, `confirm.js`가 모든 VU를 같은 `OPTION_ID` 하나에 몰아넣어
200 × 100ms ≈ 20초 대기열이 만들어졌다(관측 max 18.6s와 일치).

**세트 A의 Kafka 후처리 비동기화는 커밋 이후 부수 작업만 옮기므로 이 수치를 개선하지 못한다.**
그래서 부하 모형을 둘로 나눈다 — 이 결정은 사용자 승인 사항이다:

| 모형 | 무엇을 재나 | 리포트에서 맡는 역할 |
|---|---|---|
| **집중**(기존, `OPTION_ID` 고정) | 한 SKU에 구매가 몰릴 때의 락 경합 | "병목을 발견하고 원인을 규명한" 진단 서사. 개선 대상은 이번 스코프 밖임을 명시 |
| **분산**(신규, 여러 옵션에 분산) | 일반적 다품목 트래픽에서의 확정 지연 | Kafka 후처리 비동기화의 before/after 비교 기준 |

**Files:**
- Modify: `tools/loadtest/confirm.js`(분산/집중 스위치 추가), `tools/loadtest/README.md`
- Create: `docs/loadtest/2026-07-29-baseline-spread/{confirm-summary.json,conditions.md}`

**부하 모형 계약 — 바꾸지 않는 것:** ramping-vus 단계(10 → 30s 50 → 1m 200 → 30s 0),
thresholds `http_req_failed: ['rate<0.01']`, 요청 순서(주문 생성 → 확정). 바뀌는 것은
**어떤 상품을 사는가** 하나뿐이다.

**분산 방식 (판단 — 전량):**

```javascript
// 환경변수 LOAD_MODEL=spread | single (기본 single — 기존 baseline을 그대로 재현할 수 있어야 한다)
// spread일 때 OPTION_IDS(쉼표 구분 목록)에서 VU×반복마다 하나를 고른다.
// __VU와 __ITER를 함께 쓰는 이유: __VU만 쓰면 같은 VU가 매 반복 같은 옵션을 사서
// VU 수보다 적은 옵션에 다시 몰린다.
const MODEL = __ENV.LOAD_MODEL || 'single';
const OPTION_IDS = (__ENV.OPTION_IDS || '').split(',').filter(Boolean).map(Number);

function pickOptionId() {
  if (MODEL !== 'spread') return Number(__ENV.OPTION_ID);
  return OPTION_IDS[(__VU + __ITER) % OPTION_IDS.length];
}
```

- `OPTION_IDS`는 재고를 보충한 옵션 중 **최소 200개**를 넘긴다(피크 VU 수 이상이어야 같은 행에
  두 트랜잭션이 겹치지 않는다). 목록은 README에 SQL로 뽑는 법을 적는다.
- `goodsNo`도 옵션에 맞는 값이어야 하므로, 옵션 id와 상품 id를 쌍으로 넘기거나 옵션 id로
  상품을 조회해 구성한다 — 실제 스키마에 맞는 방식을 구현자가 정하고 README에 근거를 남긴다.

**스텝:**
- [ ] `LOAD_MODEL=single`로 돌려 기존 수치가 재현되는지 확인 (하위 호환 회귀)
- [ ] 옵션 200개 이상에 재고 보충, `LOAD_MODEL=spread`로 본 측정
      (`--summary-trend-stats="avg,min,med,max,p(90),p(95),p(99)"` 포함)
- [ ] summary JSON을 커밋 전 `setup_data` 스크럽 후 `docs/loadtest/2026-07-29-baseline-spread/`에 저장
- [ ] 같은 폴더 `conditions.md`: 집중 모형 조건 문서를 참조하되 **다른 점만** 적고(옵션 목록,
      LOAD_MODEL, 재고 보충 범위), 두 모형의 수치를 나란히 놓은 비교표와 그 해석을 담는다
- [ ] 커밋 — **이 커밋이 터미널 A·B의 기점이다**

---

## 세트 A — 아웃박스 + Kafka (터미널 A, feat/order-events)

### Task A1: 인프라 — Kafka 의존성·compose·설정

**Files:**
- Modify: `backend/build.gradle.kts`, `docker-compose.yml`, `backend/src/main/resources/application.yml`

**공유 계약 (전량):**

build.gradle.kts dependencies에 추가:
```kotlin
implementation("org.springframework.kafka:spring-kafka")
testImplementation("org.springframework.kafka:spring-kafka-test")
testImplementation("org.testcontainers:kafka")
```

docker-compose.yml services에 추가(backend의 depends_on에 `kafka: { condition: service_healthy }`,
environment에 `KAFKA_BOOTSTRAP: kafka:9092` 추가 포함):
```yaml
  kafka:
    image: apache/kafka:3.9.0
    container_name: beautyboy-kafka
    ports: ["9092:9092"]
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_HEAP_OPTS: "-Xmx512m -Xms256m"   # 로컬 스택 보호 — 이 이상 필요하면 설계가 틀린 것
    healthcheck:
      test: ["CMD-SHELL", "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list"]
      interval: 10s
      timeout: 10s
      retries: 12
      start_period: 30s
```

application.yml에 추가:
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
    consumer:
      group-id: beautyboy-post-order
      auto-offset-reset: earliest
      enable-auto-commit: false      # 커밋은 리스너 컨테이너가 처리 성공 후에 한다
beautyboy:
  events:
    enabled: ${ORDER_EVENTS:false}   # 조회수 버퍼와 같은 철학 — Kafka 없이도 앱이 뜬다
    relay-batch-size: 100
    relay-delay-ms: 1000
```

**`enabled` 토글이 핵심 판단이다**: false면 릴레이·컨슈머·KafkaAdmin이 전부 안 뜨고(`@ConditionalOnProperty`),
아웃박스 INSERT만 일어난다(PENDING 적체 — 무해). 로컬 bootRun과 기존 테스트가 Kafka 없이 돌아야 하기 때문.
compose에서는 `ORDER_EVENTS: "true"`.

- [ ] 위 계약대로 반영, `./gradlew test`가 Kafka 없이 여전히 녹색인지 확인 → 커밋

### Task A2: 아웃박스 스키마 + 이벤트 계약

**Files:**
- Create: `backend/src/main/resources/db/migration/V90__outbox_event.sql`,
  `V91__processed_event.sql`, `V92__notification.sql` (번호는 머지 게이트에서 확정)
- Create: `backend/src/main/java/com/beautyboy/outbox/OutboxEvent.java`(엔티티),
  `OutboxEventRepository.java`, `OrderConfirmedEvent.java`(record), `OutboxAppender.java`
- Test: `backend/src/test/java/com/beautyboy/outbox/OutboxAppenderTest.java`

**공유 계약:** DDL 3본과 페이로드 JSON은 **설계 문서 §4.1·§4.3의 것을 그대로** 쓴다(재설계 금지).

**Interfaces (Produces):**
```java
// 확정 트랜잭션 안에서 호출된다. REQUIRED 전파 — 자체 트랜잭션을 만들지 않는다.
public interface OutboxAppender {
    void appendOrderConfirmed(OrderConfirmedEvent event);
}
// record OrderConfirmedEvent(int version, Long eventId, String eventType, Long orderId,
//     Long memberId, String orderNo, LocalDateTime confirmedAt, List<Line> lines)
//     — record Line(Long goodsId, Long optionId, int quantity)
//     eventId는 INSERT 후 채번되므로 append 시점엔 null로 넘기고 직렬화 시 채운다.
```

- [ ] 테스트: `append는_호출자_트랜잭션에_참여해_PENDING_행을_남긴다`(단언: status=PENDING,
      payload JSON에 orderNo·lines 포함, eventId=행 PK), `호출자_트랜잭션이_롤백되면_아웃박스도_남지_않는다`
- [ ] 실패 확인 → 구현(직렬화는 Jackson `ObjectMapper`) → 통과 → 커밋

### Task A3: confirm 트랜잭션에 발행 지점 추가 [opus]

**Files:**
- Modify: `backend/src/main/java/com/beautyboy/payment/PaymentService.java`
- Modify: `backend/src/main/java/com/beautyboy/order/OrderConfirmPort.java`,
  `backend/src/main/java/com/beautyboy/order/OrderConfirmService.java`
- Test: `backend/src/test/java/com/beautyboy/payment/PaymentServiceOutboxTest.java`

**판단 (전량 서술):** 이벤트에 `memberId`와 `lines(goodsId, optionId, quantity)`가 필요하지만
`ConfirmTarget`에는 없다. **포트를 넓힌다**: `ConfirmTarget`에 `Long memberId`와
`List<EventLine> eventLines`(전 주문 줄 — stockLines와 달리 optionId null 포함)를 추가한다.
아웃박스 INSERT는 `PaymentService.confirm`의 **(6) 다음, 반환 직전** — markPaid·payment 저장과
같은 트랜잭션이므로 커밋의 원자성이 성립한다. 토스 호출(4)보다 뒤인 이유: 토스 실패 시 롤백에
아웃박스도 포함돼 유령 이벤트가 없다.

- [ ] 테스트: `확정_성공시_같은_트랜잭션에서_아웃박스가_남는다`,
      `금액_불일치로_실패하면_아웃박스도_남지_않는다`(기존 불일치 테스트 픽스처 재사용),
      기존 `PaymentService` 테스트 전부 녹색 유지
- [ ] 실패 확인 → 구현 → 통과 → 커밋

### Task A4: 아웃박스 릴레이 [opus]

**Files:**
- Create: `backend/src/main/java/com/beautyboy/outbox/OutboxRelay.java`,
  `backend/src/main/java/com/beautyboy/outbox/KafkaTopicConfig.java`(NewTopic `order-events` 파티션 3,
  `order-events.DLT` 파티션 3 — `@ConditionalOnProperty(beautyboy.events.enabled)`)
- Test: `backend/src/test/java/com/beautyboy/outbox/OutboxRelayTest.java`

**판단 코드 (핵심 루프 전량):**

```java
@Scheduled(fixedDelayString = "${beautyboy.events.relay-delay-ms:1000}")
public void relay() {
    List<OutboxEvent> pending = repository.findByStatusOrderByCreatedAtAsc(
            OutboxEvent.STATUS_PENDING, Limit.of(batchSize));
    for (OutboxEvent event : pending) {
        try {
            // key = orderId 문자열 — 같은 주문은 같은 파티션. send를 동기로 기다리는 이유:
            // 발행 성공이 확인된 것만 PUBLISHED로 마킹해야 at-least-once가 성립한다.
            kafkaTemplate.send("order-events", String.valueOf(event.getAggregateId()),
                    event.getPayload()).get(10, TimeUnit.SECONDS);
            event.markPublished(LocalDateTime.now());
            repository.save(event);   // 건별 커밋 — 중간에 죽으면 남은 건 다음 주기에 재발행(중복 허용)
        } catch (Exception e) {
            log.warn("outbox 발행 실패 — 다음 주기에 재시도. eventId={}", event.getId(), e);
            break;  // 순서 보존: 앞 건이 실패했는데 뒤 건을 발행하면 같은 주문 내 순서가 깨질 수 있다
        }
    }
}
```

- [ ] 테스트(EmbeddedKafka 또는 KafkaTemplate 목): `PENDING을_생성순으로_발행하고_PUBLISHED로_마킹한다`,
      `발행_실패시_마킹하지_않고_배치를_중단한다`, `PUBLISHED는_다시_발행하지_않는다`
- [ ] 실패 확인 → 구현 → 통과 → 커밋

### Task A5: 컨슈머 3종 + 폴링 집계 전환 [opus]

**Files:**
- Create: `backend/src/main/java/com/beautyboy/cart/CartClearOnOrderConfirmed.java`,
  `backend/src/main/java/com/beautyboy/ranking/SalesAggregationConsumer.java`,
  `backend/src/main/java/com/beautyboy/notification/Notification.java`,
  `NotificationRepository.java`, `NotificationConsumer.java`,
  `backend/src/main/java/com/beautyboy/outbox/KafkaConsumerConfig.java`(에러 핸들러)
- Modify: `backend/src/main/java/com/beautyboy/order/OrderService.java`(97행 `cartService.clear` 제거),
  `backend/src/main/java/com/beautyboy/ranking/RankingBatchService.java`(판매 수집 제거 — 찜·조회 유지),
  `backend/src/main/java/com/beautyboy/ranking/GoodsDailyStatRepository.java`(증분 upsert 추가),
  `backend/src/main/java/com/beautyboy/cart/CartService.java`(상품 단위 삭제 메서드 추가)
- Delete: `backend/src/main/java/com/beautyboy/order/OrderSalesStatProvider.java`
  (삭제하면 `RankingStatFallbackAutoConfiguration`의 빈 맵 폴백이 자동 복귀 — 배치의 판매 수집이
  0을 받아도 이제 증분 경로가 채우므로 무해. 단 배치의 판매 upsert 자체를 제거해 증분 값을 0으로
  덮지 않게 한다 — **이것이 이중 계상/소실 방지의 핵심이며 같은 커밋으로 묶는다**)
- Test: `backend/src/test/java/com/beautyboy/outbox/PostOrderConsumersTest.java`

**판단 코드 — 집계 멱등성 (전량):**

```java
@KafkaListener(topics = "order-events", groupId = "beautyboy-post-order")
@Transactional
public void on(ConsumerRecord<String, String> record) {
    OrderConfirmedEvent event = deserialize(record.value());
    // 멱등성 게이트: processed_event INSERT가 성공한 트랜잭션만 집계를 반영한다.
    // 중복 소비(재시도·리밸런싱·릴레이 재발행)면 PK 충돌 → 스킵. 같은 트랜잭션이므로
    // "기록됐는데 집계 안 됨" 또는 그 반대가 없다.
    try {
        processedEventRepository.insert(event.eventId(), "sales-aggregation");
    } catch (DataIntegrityViolationException dup) {
        return;
    }
    for (OrderConfirmedEvent.Line line : event.lines()) {
        goodsDailyStatRepository.upsertSalesIncrement(line.goodsId(),
                event.confirmedAt().toLocalDate(), line.quantity());
    }
}
```

**공유 계약 — 증분 upsert (전량):**

```sql
-- GoodsDailyStatRepository에 추가 (기존 upsertViewCount와 같은 네이티브 스타일)
INSERT INTO goods_daily_stat (goods_id, stat_date, view_count, sales_count, wish_count)
VALUES (:goodsId, :statDate, 0, :quantity, 0)
ON DUPLICATE KEY UPDATE sales_count = sales_count + :quantity
```

**나머지 컨슈머 (사양 문장):**
- cart-clear: `CartService`에 `removeByGoods(Long memberId, List<Long> goodsIds)`(본인 장바구니에서
  해당 상품 행 삭제, 없으면 no-op) 추가하고 컨슈머가 이벤트 `lines`의 goodsId로 호출. 통과 조건:
  주문한 상품만 지워지고 다른 담긴 상품은 남는다. `OrderService` 96-97행의 clear 호출과 주석은 제거.
- notification: `notification` 테이블에 INSERT. message는 `"주문 {orderNo} 결제가 완료됐어요."`.
  중복은 `uk_notification_dedup` 위반 catch 후 스킵.
- 에러 핸들러: `DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate),
  new ExponentialBackOff(1000, 2.0))` — backOff에 `setMaxAttempts` 상당(재시도 3회) 설정,
  DLT 토픽명은 기본 규칙(`order-events.DLT`) 사용.

- [ ] 테스트: `확정_이벤트를_받으면_주문_상품만_장바구니에서_지운다`,
      `같은_이벤트를_두번_소비해도_판매집계는_한번만_는다`,
      `같은_이벤트를_두번_소비해도_알림은_한건이다`,
      `컨슈머가_계속_실패하면_DLT로_이동하고_다음_메시지는_계속_소비된다`,
      `주문_생성_시점에는_장바구니가_비워지지_않는다`(OrderService 회귀)
- [ ] 실패 확인 → 구현 → 통과 → 커밋

### Task A6: DLQ 재처리 admin API

**Files:**
- Create: `backend/src/main/java/com/beautyboy/outbox/DlqReplayService.java`,
  `DlqReplayController.java`(`POST /api/admin/dlq/replay`, admin 권한 — 기존 admin 컨트롤러의
  권한 처리 방식을 그대로 따른다)
- Test: `backend/src/test/java/com/beautyboy/outbox/DlqReplayServiceTest.java`

**사양 문장:** 별도 그룹(`dlq-replay`)의 임시 KafkaConsumer로 `order-events.DLT`를 처음부터
poll → 각 메시지를 원 키 그대로 `order-events`에 재발행 → 오프셋 커밋 → 빈 poll이 나오면 종료.
응답: `{ "replayed": n }`. 통과 조건: DLT에 2건 있을 때 replay 후 원 토픽에 2건이 재적재되고,
두 번째 호출은 `replayed: 0`.

- [ ] 테스트 작성 → 실패 확인 → 구현 → 통과 → 커밋

### Task A7: 통합 테스트 (Testcontainers Kafka + MySQL)

**Files:**
- Create: `backend/src/test/java/com/beautyboy/outbox/OrderEventsFlowIT.java` (`@Tag("integration")` —
  기존 `StockConcurrencyMysqlIntegrationTest`의 컨테이너 구성 방식을 따르고 Kafka 컨테이너 추가,
  `beautyboy.events.enabled=true`)

**테스트 케이스 (전량 — 이름과 단언이 곧 사양):**
- `확정하면_아웃박스를_거쳐_컨슈머_3종이_모두_처리한다` — confirm 호출 후 최대 10초 await:
  장바구니에서 주문 상품 삭제됨, `goods_daily_stat.sales_count` 증분됨, `notification` 1건,
  `outbox_event.status=PUBLISHED`
- `릴레이가_같은_이벤트를_재발행해도_집계와_알림은_한번만_반영된다` — PUBLISHED 행을 PENDING으로
  되돌려 강제 재발행 후, sales_count·알림 건수 불변
- `컨슈머_예외가_계속되면_DLT로_가고_replay로_복구된다` — notification INSERT를 실패하게 만든 뒤
  (member 삭제 등 픽스처로) DLT 1건 확인 → 원인 복구 → replay API → 알림 1건 생성
- [ ] `./gradlew integrationTest` 녹색 → 커밋

---

## 세트 B — Redis 캐싱 (터미널 B, feat/read-cache)

### Task B1: 캐시 인프라 — RedisCacheManager + 장애 폴백

**Files:**
- Create: `backend/src/main/java/com/beautyboy/config/CacheConfig.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/beautyboy/config/CacheConfigTest.java`

**판단 코드 (전량 — 폴백이 이 세트의 존재 조건이다):**

```java
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "beautyboy.cache.redis", havingValue = "true")
public class CacheConfig implements CachingConfigurer {

    // 캐시명별 TTL — 설계 §6 표의 값. 바꾸려면 설계 문서부터 고친다.
    private static final Map<String, Duration> TTL = Map.of(
            "ranking", Duration.ofMinutes(10),
            "goodsList", Duration.ofMinutes(5),
            "compat", Duration.ofHours(24));

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("v1:")
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        TTL.forEach((name, ttl) -> perCache.put(name, base.entryTtl(ttl)));
        return RedisCacheManager.builder(factory).withInitialCacheConfigurations(perCache).build();
    }

    // Redis 다운 시 @Cacheable이 예외를 삼키고 원본 메서드로 직행하게 한다.
    // 기본 SimpleCacheErrorHandler는 rethrow라 캐시 장애 = 서비스 장애가 된다.
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            private static final Logger log = LoggerFactory.getLogger("cacheFallback");
            public void handleCacheGetError(RuntimeException e, Cache c, Object k) { log.warn("cache get 실패 — DB 직행: {}", k, e); }
            public void handleCachePutError(RuntimeException e, Cache c, Object k, Object v) { log.warn("cache put 실패: {}", k, e); }
            public void handleCacheEvictError(RuntimeException e, Cache c, Object k) { log.warn("cache evict 실패: {}", k, e); }
            public void handleCacheClearError(RuntimeException e, Cache c) { log.warn("cache clear 실패", e); }
        };
    }
}
```

application.yml 추가: `beautyboy.cache.redis: ${CACHE_REDIS:false}` (조회수 버퍼·events.enabled와
같은 토글 철학. compose에서 `CACHE_REDIS: "true"`, docker-compose.yml의 backend environment에 추가).

- [ ] 테스트: `토글_off면_캐시_매니저가_없어도_앱이_뜬다`, `캐시명별_TTL이_설계값과_같다`,
      `Redis_연결이_죽어도_조회_메서드는_원본값을_반환한다`(연결 팩토리 목으로 예외 유발)
- [ ] 실패 확인 → 구현 → 통과 → 커밋

### Task B2: 랭킹 캐시 + 배치 후 evict·워밍

**Files:**
- Modify: `backend/src/main/java/com/beautyboy/ranking/RankingService.java`(조회 메서드에 `@Cacheable`),
  `backend/src/main/java/com/beautyboy/ranking/RankingScheduler.java`(rebuild 후 refresher 호출)
- Create: `backend/src/main/java/com/beautyboy/ranking/RankingCacheRefresher.java`
- Test: `backend/src/test/java/com/beautyboy/ranking/RankingCacheTest.java`

**사양 문장:** `@Cacheable(cacheNames = "ranking", key = "카테고리(+기간 파라미터가 있으면 포함)")`.
`RankingCacheRefresher.refreshAfterRebuild()`는 (1) `ranking` 캐시 전체 clear →
(2) 스냅샷에 존재하는 카테고리 전부(`ALL` 포함)에 대해 조회 메서드를 호출해 다시 채운다(워밍 —
설계 §6의 1안. 배치가 매시 도는 것을 그대로 이용하므로 확률적 soft-TTL은 구현하지 않는다).
`RankingScheduler`의 rebuild 스텝 뒤에 호출을 잇는다 — 배치 실패 시 워밍은 스킵(이전 캐시 유지가
빈 캐시보다 낫다). 통과 조건: 두 번째 조회는 리포지토리를 호출하지 않는다 / rebuild 후 캐시가 새
스냅샷 값을 반환한다.

- [ ] 테스트: `같은_카테고리_두번째_조회는_DB를_때리지_않는다`, `rebuild가_끝나면_캐시는_새_스냅샷을_반환한다`,
      `rebuild가_실패하면_기존_캐시가_유지된다`
- [ ] 실패 확인 → 구현 → 통과 → 커밋

### Task B3: 목록/검색 캐시 + 무효화

**Files:**
- Modify: `backend/src/main/java/com/beautyboy/catalog/GoodsQueryService.java`(목록 조회 `@Cacheable`),
  `backend/src/main/java/com/beautyboy/catalog/AdminGoodsService.java`(등록/수정 시 무효화),
  `backend/src/main/java/com/beautyboy/search/SearchService.java`(검색 결과 `@Cacheable`)
- Create: `backend/src/main/java/com/beautyboy/common/CacheKeys.java`
- Test: `backend/src/test/java/com/beautyboy/common/CacheKeysTest.java`,
  `backend/src/test/java/com/beautyboy/catalog/GoodsListCacheTest.java`

**판단 코드 — 키 정규화 (전량. 키가 어긋나면 조용히 히트율 0이 된다):**

```java
public final class CacheKeys {
    private CacheKeys() {}

    /**
     * 목록/검색 파라미터를 "카테고리:정렬:페이지:필터해시"로 누른다.
     * 필터해시: 파라미터를 이름 오름차순으로 "k=v&" 연접한 문자열의 SHA-256 앞 16 hex.
     * 순서를 고정하지 않으면 같은 조합이 다른 키가 되어 히트율이 조용히 죽는다.
     */
    public static String goodsList(String category, String sort, int page, Map<String, String> filters) {
        StringBuilder canonical = new StringBuilder();
        filters.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> canonical.append(e.getKey()).append('=').append(e.getValue()).append('&'));
        return category + ":" + sort + ":" + page + ":" + sha256Hex16(canonical.toString());
    }

    /** 궁합 키 — 항상 작은 id가 앞. (a,b)와 (b,a)가 같은 키여야 한다. */
    public static String compat(long a, long b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }

    private static String sha256Hex16(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) hex.append(String.format("%02x", d[i]));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
```

**사양 문장:** 무효화는 `AdminGoodsService`의 등록/수정 경로에서 `goodsList` 캐시 **전체 clear**로
한다 — 카테고리별 패턴 삭제(SCAN)는 키 구조 결합이 생기는 데 비해 admin 변경 빈도가 낮아 이득이
없다(설계 §6에서 "카테고리 패턴 삭제"라 했으나 구현 단순화로 전체 clear 선택 — ADR ③에 한 줄 기록).

- [ ] 테스트: `필터_순서가_달라도_같은_키가_된다`, `빈_필터와_누락_필터는_같은_키다`,
      `같은_목록_두번째_조회는_DB를_때리지_않는다`, `상품_수정이_목록_캐시를_비운다`
- [ ] 실패 확인 → 구현 → 통과 → 커밋

### Task B4: 성분 궁합 캐시

**Files:**
- Modify: `backend/src/main/java/com/beautyboy/compat/CompatQueryService.java`,
  `backend/src/main/java/com/beautyboy/catalog/AdminGoodsService.java`(성분 변경 시 `compat` 전체 clear —
  성분 수정은 admin에서만, 빈도 극히 낮음)
- Test: `backend/src/test/java/com/beautyboy/compat/CompatCacheTest.java`

**사양 문장:** 궁합 판정 메서드에 `@Cacheable(cacheNames = "compat",
key = "T(com.beautyboy.common.CacheKeys).compat(#a, #b)")`. 통과 조건: (a,b) 조회 후 (b,a) 조회가
캐시 히트다.

- [ ] 테스트: `상품쌍_순서를_바꿔도_같은_캐시를_쓴다`, `성분이_바뀌면_궁합_캐시가_비워진다`
- [ ] 실패 확인 → 구현 → 통과 → 커밋

### Task B5: 캐시 히트율 관측

**Files:**
- Modify: `backend/src/main/resources/application.yml`(actuator에 `metrics` 노출 추가 — 기존
  노출 목록에 병기), `backend/src/main/java/com/beautyboy/config/CacheConfig.java`
- Test: 기존 `CacheConfigTest`에 케이스 추가

**사양 문장:** `RedisCacheManager.builder(...).enableStatistics()`로 캐시 통계를 켜고
Micrometer `cache.gets` 메트릭(hit/miss 태그)이 `/actuator/metrics`에 노출되는 것을 확인한다.
C의 부하 리포트가 이 수치를 읽는다.

- [ ] 테스트: `캐시_히트율_메트릭이_노출된다`(조회 2회 후 `cache.gets` hit≥1) → 커밋

---

## 세트 C — 검증과 산출물 (메인 세션, A·B 머지 후)

### Task C1: 머지 게이트

- [ ] A 머지: Flyway 번호를 main 최신 기준으로 확정 리네임 → `./gradlew test integrationTest` →
      merge. B 머지: `application.yml` 충돌 해소 → 동일 검증 → merge
- [ ] compose 실기동(`ORDER_EVENTS=true`, `CACHE_REDIS=true`): 주문→결제 curl(메모리의 스모크
      레시피) 후 30초 내 장바구니 비워짐·알림 행·sales_count 증분을 DB로 확인
- [ ] E2E: `npm test` — 장바구니 비우기 시점 변경으로 깨지는 스펙은 "결제 완료 후 비워짐"으로 수정

### Task C2: after 측정 + 리포트

- [ ] baseline과 **동일 조건**으로 재측정 — **세 벌**이다: confirm 분산 모형
      (`LOAD_MODEL=spread`, 조건은 `2026-07-29-baseline-spread/conditions.md`), confirm 집중 모형
      (`LOAD_MODEL=single`, 조건은 `2026-07-29-baseline/conditions.md`), browse.
      결과를 `docs/loadtest/2026-07-29-after/`에 저장.
      **집중 모형은 개선이 거의 없을 것으로 예측돼 있다**(Task 0.4 참고) — 그 예측이 맞는지가
      리포트의 논점 하나이므로, 수치가 그대로여도 실패가 아니라 검증된 예측으로 적는다. 추가 수집: 컨슈머 랙 추이
      (`kafka-consumer-groups.sh --describe`를 측정 중 5초 간격 폴링한 로그), 캐시 히트율
      (`/actuator/metrics/cache.gets`)
- [ ] `docs/loadtest/2026-07-29-report.md` 작성: before/after 표(p50/p95/p99·RPS·에러율),
      랙이 쌓였다 풀리는 추이, 히트율, 측정 조건과 한계(토스 스텁 명시)

### Task C3: ADR 3편 + README

- [ ] `docs/adr/0001-kafka-over-redis-streams.md` — 후보 3안 비교표(Redis Streams/Kafka/Spring 이벤트
      × 내구성·순서·운영 부담·학습 목적), 선택 근거와 "운영이라면 3노드" 한 줄
- [ ] `docs/adr/0002-at-least-once-with-idempotent-consumers.md` — exactly-once를 흉내 내지 않은
      이유(릴레이 재발행 창 + 컨슈머 재시도는 어차피 중복을 만든다 → 소비 측 멱등성이 유일한 해법)
- [ ] `docs/adr/0003-cache-strategy.md` — 대상 선정 기준, 워밍 1안 채택(soft-TTL 미채택 이유),
      B3의 전체-clear 단순화 결정
- [ ] `docs/adr/0004-stock-lock-contention-out-of-scope.md` — Task 0.4가 발견한 단일 SKU 락 직렬화를
      기록한다: 측정으로 드러난 현상, 코드상 원인(재고 차감 락이 외부 호출을 포함한 채 커밋까지 유지),
      **이번 스코프에서 고치지 않은 이유**(후처리 비동기화와 다른 층위의 문제이고, 결제 정합성을
      건드리는 변경이라 별도 검증이 필요하다), 고친다면의 선택지(낙관적 락 / 재고 차감을 토스 호출
      뒤로 / Redis 카운터)와 각각의 트레이드오프. **발견하고 진단했으나 의도적으로 미루었다**는
      것이 이 문서의 요지다 — 못 본 것과 안 고친 것은 다르다.
- [ ] README "트래픽 개선기" 섹션: 한 장 요약 + 리포트/ADR 링크 + 최종 일관성(장바구니 지연) 명시

---

## Self-Review 결과

- 설계 §1~§8 전 항목이 태스크에 매핑됨(§4→A2~A4, §5→A5~A7, §6→B1~B5, §7→0.1~0.3·C2, §8→C3).
- 타입 일관성: `OrderConfirmedEvent`(A2 정의)를 A3·A5가 소비, `CacheKeys`(B3 정의)를 B4가 소비 —
  시그니처 일치 확인.
- 설계와 다른 결정 1건: B3 무효화를 카테고리 패턴 삭제 → 전체 clear로 단순화(근거 본문, ADR ③ 기록).
