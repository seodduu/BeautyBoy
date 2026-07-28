# 구현 계획 — 마감 3종: 토스 타임아웃 · 경계 위반 정리 · 랜딩 IA (2026-07-28)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development 또는
> superpowers:executing-plans로 태스크 단위 실행. 스텝은 체크박스로 추적한다.
>
> 근거: `docs/plans/2026-07-26-다음-작업.md` §5(코드 위생 상위 2건) + 디자인 리뷰 3-1 잔여(랜딩 IA).
> 별도 설계 문서 없음 — **이 문서의 §2 "설계 결정"이 설계의 진실을 겸한다.**
> **마이그레이션 없음**(Flyway 현재 V83). DESIGN.md 수정 없음(기존 사양 안에서 끝난다).

**Goal:** 결제 경로의 락 보유 시간 상한(토스 타임아웃), 서비스 경계 위반 2건 해소, 랜딩 자리표시
내비를 실제 IA로 교체.

**Architecture:** 경계 정리는 소유 도메인이 인터페이스를 내주는 기존 관례(`StockCommandService`·
`GoodsIngredientQueryService`)를 그대로 따른다. 동작(에러 코드·상태 전이·트랜잭션 경계)은 1비트도
바꾸지 않는 순수 리팩토링 + 설정 추가다.

**Tech Stack:** Spring Boot(RestClient, `@ConfigurationProperties`) / React(기존 Header 구조).

## Global Constraints (CLAUDE.md 재확인)

- 자기 터미널의 Files 목록 밖 파일 수정 금지. `common` 패키지는 열지 않는다(에러 코드 신설 없음).
- **기존 테스트의 단언은 수정 금지** — 리팩토링이므로 기존 테스트가 곧 동작 보존의 증거다.
  배선(생성자 주입 대상)만 바꾼다. 단언을 바꿔야 통과한다면 그것은 동작이 바뀐 것 — 중단·보고.
- 프론트 전체 판정 `npm test` + `npx tsc -b`. 백엔드 `./gradlew test`.
- 화면을 바꾸는 태스크(T3)는 스크린샷 DoD. 백엔드 터미널(T1·T2)은 해당 없음.
- 커밋 메시지·주석 한국어, 태스크 단위 원자 커밋.

---

## 0. 사람이 할 일 (사전 조건 2줄)

터미널을 열기 전에 프로젝트 루트에서:

```
git log --oneline -1     # 이 계획 커밋(docs(plan): 마감 3종 계획)인지 확인
git status               # 깨끗한지 확인
```

---

## 1. 웨이브·터미널 분할

| 웨이브 | 터미널 | 브랜치 | 범위 | 모델 |
|---|---|---|---|---|
| W1 (병렬 3) | T1 | `feature/payment-hardening` | 토스 타임아웃 + payment→order 경계 정리 | **opus** |
| W1 | T2 | `feature/compat-boundary` | compat→ingredient 규칙 경계 정리 | sonnet |
| W1 | T3 | `feature/landing-nav` | 랜딩 내비 실제 IA 교체 | sonnet |
| W2 (직렬) | 오케스트레이터 | — | 머지 게이트 + 전체 기준선 + E2E + 랜딩 스크린샷 | opus |

- **T1이 opus인 이유**: CLAUDE.md 모델 배분 예외(결제 2단계 검증) 영역. 승인 트랜잭션의 락·전이
  순서를 옮기는 작업이라, 계획서 코드 밖에서 기존 테스트 배선을 다시 묶는 판단이 필요하다.
- T1·T2는 백엔드지만 패키지가 겹치지 않고(payment·order·config vs compat·ingredient),
  T3는 프론트 전용 — 상호 충돌 없음.

### 파일 소유권

| 터미널 | 소유 파일 |
|---|---|
| T1 | `backend/…/order/OrderConfirmPort.java`(신규), `backend/…/order/OrderConfirmService.java`(신규), `backend/…/order/OrderConfirmServiceTest.java`(신규), `backend/…/payment/PaymentService.java`, `backend/…/payment/TossPaymentGateway.java`, `backend/…/config/TossProperties.java`, `backend/…/payment/TossPaymentGatewayTimeoutTest.java`(신규), 기존 payment 테스트들의 **배선 부분만**(`PaymentStockConfirmTest.java`, `StockConcurrencyMysqlIntegrationTest.java`, 그 외 PaymentService를 직접 조립하는 테스트) |
| T2 | `backend/…/ingredient/IngredientRuleQueryService.java`(신규), `backend/…/ingredient/IngredientRuleQueryServiceImpl.java`(신규), `backend/…/ingredient/IngredientRuleQueryServiceImplTest.java`(신규), `backend/…/compat/CompatService.java`, `backend/…/compat/CompatServiceTest.java`(배선만) |
| T3 | `frontend/src/components/layout/Header.tsx`, `Header.css`(필요시), `Header.test.tsx` |

- T1·T2 공통: `IngredientRule`/`Order` 엔티티와 리포지토리 파일 자체는 **수정하지 않는다**
  (경계를 만드는 것이지 도메인을 고치는 것이 아니다).

---

## 2. 설계 결정

### 결정 1 — 토스 타임아웃: `TossProperties`에 기본값 있는 설정으로 (connect 3s / read 10s)

- **왜 상수가 아니라 프로퍼티인가**: 타임아웃 동작은 실제로 기다려봐야 검증되는데, 상수 10s면
  테스트가 10초를 기다려야 한다. 프로퍼티(기본값 3s/10s, 테스트에서 0.5s 주입)로 하면
  **타임아웃이 실제로 걸리는지**를 빠른 테스트로 증명할 수 있다. yml·환경변수 추가는 불필요
  (기본값이 곧 운영값).
- **값의 근거**: 재고 차감(§Wave 5)이 토스 HTTP 호출 동안 `goods_option` 행 락을 쥔다.
  read 10s + connect 3s면 락 보유 최악 ~13s로, `innodb_lock_wait_timeout`(50s)보다 훨씬 앞서
  풀린다 — 같은 옵션 대기자가 500을 보기 전에 끝난다. 토스 테스트 API의 정상 응답은 1s 미만이라
  오탐 여지도 없다.
- 타임아웃 발화 시 `RestClient`가 던지는 예외는 기존 `catch (Exception e)` 경로로 들어가
  `PaymentGatewayException("토스 승인 응답 처리 실패")`이 된다 — **승인 전 단계이므로 트랜잭션
  롤백이 재고 차감을 되돌린다**(Wave 5 결정 2 그대로). 새 에러 처리 코드가 필요 없다.

### 결정 2 — payment→order 경계: order가 `OrderConfirmPort`를 내준다

- 재고 차감이 확립한 관례와 동형: 소유 도메인(order)이 소비자(payment)에게 커맨드 인터페이스를
  내주고, 엔티티는 경계를 넘지 않는다(§3-1 record가 넘는다).
- **트랜잭션·락 의미는 그대로**: 포트 구현은 `@Transactional(propagation = MANDATORY)` —
  `PaymentService.confirm`의 트랜잭션 안에서만 불리도록 강제한다(`StockService`와 같은 계약 강제).
  `findByOrderNoForUpdate`의 행 락은 같은 트랜잭션이므로 동시 승인 직렬화가 유지된다.
- **에러 코드·순서 불변**: 미존재/남의 주문 → `ORDER_NOT_FOUND`(404, 존재 숨김),
  PENDING 아님 → `PAYMENT_ALREADY_CONFIRMED`. 이 판정을 포트 구현으로 옮기지만 코드는 동일하다.
  (`PAYMENT_*` 코드를 order 패키지가 던지게 되는 것은 수용한다 — 이 판정의 사양 자체가
  "결제 승인 관점의 주문 상태 검증"이고, 코드를 새로 만들면 프론트 분기가 갈라진다.)
- 옵션 없는 상품(optionId null) 필터링은 **포트 구현이 한다** — "재고 관리 단위는 옵션"이라는
  정의는 주문 줄을 아는 쪽(order)의 지식이다.

### 결정 3 — compat→ingredient 경계: `IngredientRuleQueryService` (조회 전용)

- `GoodsIngredientQueryService`와 같은 자리·같은 관례의 두 번째 조회 인터페이스.
  `findNormalized`(사전순 정규화 조회)와 `findAll`(루틴 verdict 배치용) 둘만 내준다 —
  compat이 실제로 쓰는 전부다.
- 반환은 엔티티가 아니라 `RuleVerdict` record. 규칙 저장·정규화 책임(`saveNormalized`)은
  경계 밖에 내주지 않는다(쓰는 곳이 없다 — YAGNI).

### 결정 4 — 랜딩 내비: 실제 라우트 3개 + 로그인, 전부 한글

- About/Work/Services/Packages(자리표시 span) → **루틴 가이드(`/routine`) · 랭킹(`/ranking`) ·
  전체 상품(`/goods`)** Link + 기존 로그인 Link. 세 라우트 모두 실존하고, `RequireAuth`가
  비로그인을 `/login`으로 보내는 것은 의도된 퍼널이다(랜딩의 Get started와 같은 종착).
- 라벨은 전부 한글 — DESIGN.md "영문/한글 혼용 규칙"(UI 문구는 한글, 영문은 아이브로우·배지만).
  기존 `Login` 라벨도 이 규칙 위반이므로 **로그인**으로 고친다.
- 기존 `bb-landing-nav` 클래스·레이아웃은 유지한다. 시각 변화는 라벨 텍스트뿐이므로
  DESIGN.md 수정 불필요.

---

## 3. 공유 계약 — 코드 전량

### 3-1. `order/OrderConfirmPort.java` (신규 인터페이스 — 도메인 경계)

```java
package com.beautyboy.order;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 승인이 주문에 요구하는 것 전부. 주문의 소유자는 order이고, 소비자는 결제 승인(payment)뿐이다.
 *
 * <p>호출 계약:
 * <ul>
 *   <li><b>호출자의 트랜잭션 안에서만 부른다</b>(구현이 {@code MANDATORY}로 강제한다).
 *       {@code lockPendingOrder}의 행 락은 그 트랜잭션이 끝날 때까지 유지된다 —
 *       동시 승인(더블클릭) 직렬화가 이 락에 걸려 있다.</li>
 *   <li>{@code lockPendingOrder}: 미존재·타인 소유면 {@code ORDER_NOT_FOUND}(존재를 숨겨 404),
 *       PENDING이 아니면 {@code PAYMENT_ALREADY_CONFIRMED}를 던진다.</li>
 *   <li>{@code stockLines}는 재고 관리 대상(optionId 비-null)만 담는다 — 필터링은 order의 책임.</li>
 *   <li>{@code markPaid}: PENDING→PAID 전이 후 전이된 상태 문자열을 반환한다.</li>
 * </ul>
 */
public interface OrderConfirmPort {

    ConfirmTarget lockPendingOrder(String orderNo, Long memberId);

    String markPaid(Long orderId, LocalDateTime paidAt);

    /** 재고 차감 한 줄 — StockCommandService.DeductionLine과 같은 모양(변환은 payment가 한다). */
    record StockLine(Long optionId, int quantity) {}

    /** 승인 검증에 필요한 주문 스냅샷. 엔티티는 경계를 넘지 않는다. */
    record ConfirmTarget(Long orderId, String orderNo, int payableAmount, List<StockLine> stockLines) {}
}
```

### 3-2. `order/OrderConfirmService.java` (신규 구현 — 판단 지점이라 전량)

```java
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

        return new ConfirmTarget(order.getId(), order.getOrderNo(), order.getPayableAmount(), stockLines);
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
```

### 3-3. `PaymentService.confirm` 최종 형태 (결제 경로 — 전량)

기존 6단계 주석·순서를 유지한 채 (1)·(6)만 포트 경유로 바뀐다. `Order`/`OrderRepository`
import가 사라지는 것이 이 태스크의 완료 정의다.

```java
@Transactional
public PaymentConfirmResponse confirm(Long memberId, PaymentConfirmRequest request) {
    // (1) 락과 함께 읽는다(포트 경유). 동시 승인 요청을 직렬화한다.
    //     상태 검사(2)도 포트 안에 있다 — 이미 결제됐으면 토스도 재고도 건드리지 않는다.
    OrderConfirmPort.ConfirmTarget target = orderConfirmPort.lockPendingOrder(request.orderNo(), memberId);

    // (3) 재고를 깎는다 — 토스 호출 전. (이하 기존 주석 유지)
    stockCommandService.deductAll(target.stockLines().stream()
            .map(line -> new StockCommandService.DeductionLine(line.optionId(), line.quantity()))
            .toList());

    // (4) 토스에 승인 요청. 여기서 실제 결제가 일어난다.
    PaymentApproval approval =
            paymentGateway.confirm(request.paymentKey(), request.orderNo(), request.amount());

    // (5) 금액 대조. 우리가 계산한 payableAmount가 유일한 진실이다. (기존 주석 유지)
    if (approval.approvedAmount() != target.payableAmount()) {
        paymentGateway.cancel(request.paymentKey(), "주문 금액과 승인 금액 불일치");
        throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    // (6) 확정. 주문 전이 → payment 저장 순서로.
    String status = orderConfirmPort.markPaid(target.orderId(), LocalDateTime.now());
    paymentRepository.save(new Payment(
            target.orderId(),
            approval.paymentKey(),
            approval.approvedAmount(),
            approval.rawJson(),
            LocalDateTime.now()));

    return new PaymentConfirmResponse(target.orderNo(), status, target.payableAmount());
}
```

### 3-4. `config/TossProperties.java` (타임아웃 필드 추가 — 전량)

```java
package com.beautyboy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 토스페이먼츠 연동 설정. (시크릿 키 주석 기존 유지)
 *
 * <p>타임아웃 기본값의 근거: 재고 차감이 토스 HTTP 호출 동안 goods_option 행 락을 쥔다
 * (Wave 5 설계). connect 3s + read 10s = 락 보유 최악 ~13s로 innodb_lock_wait_timeout(50s)
 * 훨씬 이전에 풀린다. 토스 테스트 API 정상 응답은 1s 미만이라 오탐 여지 없음.
 * 프로퍼티로 둔 것은 운영 튜닝용이 아니라 테스트에서 짧은 값을 주입해 타임아웃 발화를
 * 실제로 증명하기 위해서다.
 */
@ConfigurationProperties(prefix = "toss")
public record TossProperties(String secretKey, String baseUrl,
                             Duration connectTimeout, Duration readTimeout) {

    public TossProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.tosspayments.com";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(10);
        }
    }
}
```

`TossPaymentGateway` 생성자에서 requestFactory에 두 값을 적용한다(의도 코드 — Boot 3.5 계열 API):

```java
this.restClient = builder
        .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(
                ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(properties.connectTimeout())
                        .withReadTimeout(properties.readTimeout())))
        .baseUrl(properties.baseUrl())
        .defaultHeader("Authorization", "Basic " + basic)
        .build();
```

프로젝트의 Spring Boot 버전에서 `ClientHttpRequestFactoryBuilder`/`ClientHttpRequestFactorySettings`
시그니처가 다르면 **같은 의도(connect/read를 위 값으로 설정)의 그 버전 API로 맞추고 보고서에
어떤 API를 썼는지 명시**한다. 타임아웃이 안 걸리는 팩토리(기본 SimpleClientHttpRequestFactory
무설정)로 되돌아가는 것만은 금지 — §5 T1-A 테스트가 이를 잡는다.

### 3-5. `ingredient/IngredientRuleQueryService.java` (신규 인터페이스 — 전량)

```java
package com.beautyboy.ingredient;

import java.util.List;
import java.util.Optional;

/**
 * 성분 궁합 규칙 조회 경계. 규칙의 소유자는 ingredient이고, 소비자는 compat뿐이다.
 * 저장·정규화(saveNormalized)는 내주지 않는다 — 경계 밖에서 규칙을 쓰는 곳이 없다.
 */
public interface IngredientRuleQueryService {

    /** (A,B) 사전순 정규화 조회 — 저장 규약(category_a < category_b)을 구현이 흡수한다. */
    Optional<RuleVerdict> findNormalized(String categoryA, String categoryB);

    /** 전체 규칙 — 루틴 조합기의 배치 verdict 산출용. */
    List<RuleVerdict> findAll();

    /** 엔티티는 경계를 넘지 않는다. */
    record RuleVerdict(String categoryA, String categoryB, String verdict, String reason) {}
}
```

구현(`IngredientRuleQueryServiceImpl`)은 `IngredientRuleRepository`를 감싸 엔티티→record 변환만
한다(`@Service`, 읽기 전용이라 `@Transactional(readOnly = true)`), 로직 추가 금지.
`CompatService`는 `IngredientRule`/`IngredientRuleRepository` import를 지우고 이 인터페이스로
바꾼다 — getter 호출(`getVerdict()` 등)이 record 접근자(`verdict()` 등)로 바뀌는 것 외에
로직 불변.

---

## 4. 태스크 상세

### T1-A: 토스 타임아웃

**Files:** `TossProperties.java`, `TossPaymentGateway.java`, `TossPaymentGatewayTimeoutTest.java`(신규)

- [ ] **1. 실패 테스트** — `backend/src/test/java/com/beautyboy/payment/TossPaymentGatewayTimeoutTest.java`:

```java
package com.beautyboy.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beautyboy.config.TossProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.time.Duration;

class TossPaymentGatewayTimeoutTest {

    private HttpServer server;

    @BeforeEach
    void 응답을_물고_있는_서버() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/payments/confirm", exchange -> {
            try {
                Thread.sleep(5_000); // read 타임아웃(0.5s)보다 훨씬 길게 물고 있는다
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void 정리() {
        server.stop(0);
    }

    @Test
    @DisplayName("read 타임아웃이 실제로 발화한다 — 무한 대기(락 보유 무제한)로 퇴행하면 이 테스트가 잡는다")
    void 읽기_타임아웃_발화() {
        TossProperties properties = new TossProperties(
                "test_sk_dummy",
                "http://localhost:" + server.getAddress().getPort(),
                Duration.ofSeconds(1),
                Duration.ofMillis(500));
        TossPaymentGateway gateway = new TossPaymentGateway(RestClient.builder(), properties);

        long start = System.nanoTime();
        assertThatThrownBy(() -> gateway.confirm("pk_test", "BB-TIMEOUT-1", 1000))
                .isInstanceOf(PaymentGatewayException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        // 서버는 5초를 물고 있다 — 3초 안에 끝났다면 타임아웃이 끊은 것이다(여유 6배).
        assertThat(elapsedMs).isLessThan(3_000);
    }

    @Test
    @DisplayName("타임아웃 미지정이면 기본값 connect 3s / read 10s")
    void 기본값() {
        TossProperties properties = new TossProperties("sk", null, null, null);
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
    }
}
```

- [ ] **2. RED 확인**: 기본값 테스트는 record 생성자 인자 수 불일치로 컴파일 실패, 발화 테스트는
  구현 후 팩토리를 안 붙이면 5초 대기 끝 200 응답 처리로 실패해야 정상
- [ ] **3. §3-4 구현 → GREEN.** `TossProperties` 기존 생성 지점이 있으면(설정 클래스·테스트)
  컴파일 에러를 따라 인자 추가(null, null 허용)
- [ ] **4. `./gradlew test` 전체 녹색 → 커밋** `fix(payment): 토스 RestClient에 connect/read 타임아웃 — 락 보유 상한`

### T1-B: payment→order 경계 정리

**Files:** `OrderConfirmPort.java`(신규), `OrderConfirmService.java`(신규), `OrderConfirmServiceTest.java`(신규), `PaymentService.java`, 기존 payment 테스트 배선

- [ ] **1. 실패 테스트** — `backend/src/test/java/com/beautyboy/order/OrderConfirmServiceTest.java`.
  픽스처는 기존 payment/order 테스트가 주문을 만드는 관례를 그대로 쓴다. 케이스와 단언:

```java
@Test
@DisplayName("남의 주문은 존재를 숨긴다 — ORDER_NOT_FOUND(404)")
void 타인_주문_404() {
    assertThatThrownBy(() -> tx(() -> orderConfirmService.lockPendingOrder(orderNo, 다른회원Id)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
}

@Test
@DisplayName("PENDING이 아니면 PAYMENT_ALREADY_CONFIRMED")
void 이미_결제됨() {
    tx(() -> orderConfirmService.markPaid(orderId, LocalDateTime.now()));
    assertThatThrownBy(() -> tx(() -> orderConfirmService.lockPendingOrder(orderNo, 회원Id)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_ALREADY_CONFIRMED);
}

@Test
@DisplayName("stockLines는 optionId 있는 줄만 담는다 — 옵션 없는 상품은 재고 비관리")
void 재고줄_필터링() {
    // 옵션 있는 줄 1 + 옵션 없는 줄(optionId=null) 1로 주문 픽스처 구성
    OrderConfirmPort.ConfirmTarget target = tx(() -> orderConfirmService.lockPendingOrder(orderNo, 회원Id));
    assertThat(target.stockLines()).hasSize(1);
    assertThat(target.stockLines().get(0).optionId()).isNotNull();
    assertThat(target.payableAmount()).isEqualTo(주문의_payableAmount);
}

@Test
@DisplayName("트랜잭션 밖 호출은 예외 — MANDATORY 계약")
void 트랜잭션_강제() {
    assertThatThrownBy(() -> orderConfirmService.lockPendingOrder(orderNo, 회원Id))
            .isInstanceOf(IllegalTransactionStateException.class);
}
```

  (`tx(...)`는 `TransactionTemplate` 헬퍼 — `StockServiceTest`가 MANDATORY를 검증한 방식이
  이미 있으니 그 관례를 그대로 쓴다.)
- [ ] **2. RED 확인 → §3-1·§3-2 구현 → GREEN**
- [ ] **3. `PaymentService`를 §3-3으로 교체.** 기존 payment 테스트(단위 8건 + 동시성 IT)는
  **단언 무수정**, 생성자 배선만 `OrderConfirmPort` 실구현/스텁으로 교체. 스텁이 필요한
  테스트는 §3-1 record를 그대로 만들어 넘긴다
- [ ] **4. 완료 검증**: `grep -n "import com.beautyboy.order" backend/src/main/java/com/beautyboy/payment/PaymentService.java`
  → `OrderConfirmPort` 한 줄만 남아야 한다(`Order`·`OrderRepository` 소멸)
- [ ] **5. `./gradlew test` + `./gradlew integrationTest` 전체 녹색 → 커밋**
  `refactor(order): 결제 승인의 주문 접근을 OrderConfirmPort 경계로 — 엔티티·리포지토리 직접 import 제거`

### T2-A: compat→ingredient 규칙 경계 정리

**Files:** `IngredientRuleQueryService.java`(신규), `IngredientRuleQueryServiceImpl.java`(신규), `IngredientRuleQueryServiceImplTest.java`(신규), `CompatService.java`, `CompatServiceTest.java`(배선만)

- [ ] **1. 실패 테스트** — `IngredientRuleQueryServiceImplTest.java`. 기존 규칙 시드/픽스처 관례 사용:

```java
@Test
@DisplayName("역순으로 물어도 사전순 정규화로 같은 규칙을 찾는다")
void 정규화_조회() {
    // (AHA, RETINOID) 규칙을 저장해 두고 (RETINOID, AHA)로 묻는다
    Optional<RuleVerdict> rule = ruleQueryService.findNormalized("RETINOID", "AHA");
    assertThat(rule).isPresent();
    assertThat(rule.get().verdict()).isEqualTo("CONFLICT");
    assertThat(rule.get().reason()).isNotBlank();
}

@Test
@DisplayName("없는 쌍은 empty — 예외가 아니다")
void 규칙_없음() {
    assertThat(ruleQueryService.findNormalized("PEPTIDE", "CENTELLA")).isEmpty();
}

@Test
@DisplayName("findAll은 저장된 규칙 전부를 record로 변환해 반환한다")
void 전체_조회() {
    assertThat(ruleQueryService.findAll()).hasSize(저장한_규칙_수);
}
```

- [ ] **2. RED 확인 → §3-5 구현 → GREEN**
- [ ] **3. `CompatService` 교체**: `IngredientRule`·`IngredientRuleRepository` import 제거,
  `IngredientRuleQueryService` 주입. 로직 불변(getter→record 접근자 치환만).
  `CompatServiceTest`는 단언 무수정, 배선만 교체
- [ ] **4. 완료 검증**: `grep -n "IngredientRule\b\|IngredientRuleRepository" backend/src/main/java/com/beautyboy/compat/CompatService.java` → 0건
- [ ] **5. `./gradlew test` 전체 녹색 → 커밋**
  `refactor(ingredient): 궁합 규칙 조회를 IngredientRuleQueryService 경계로 — compat의 엔티티·리포지토리 직접 import 제거`

### T3-A: 랜딩 내비 실제 IA 교체

**Files:** `Header.tsx`, `Header.css`(필요시), `Header.test.tsx`

- [ ] **1. 실패 테스트** — `Header.test.tsx`에 추가 (기존 렌더 관례 — 라우터 래핑 헬퍼 사용):

```tsx
it('랜딩(/) 내비는 실제 라우트로 가는 링크다 — 자리표시 텍스트가 아니다', () => {
  renderHeaderAt('/');
  expect(screen.getByRole('link', { name: '루틴 가이드' })).toHaveAttribute('href', '/routine');
  expect(screen.getByRole('link', { name: '랭킹' })).toHaveAttribute('href', '/ranking');
  expect(screen.getByRole('link', { name: '전체 상품' })).toHaveAttribute('href', '/goods');
  expect(screen.getByRole('link', { name: '로그인' })).toHaveAttribute('href', '/login');
});

it('자리표시 항목(About/Work/Services/Packages)은 더 이상 없다', () => {
  renderHeaderAt('/');
  for (const stale of ['About', 'Work', 'Services', 'Packages', 'Login']) {
    expect(screen.queryByText(stale)).not.toBeInTheDocument();
  }
});
```

- [ ] **2. RED 확인** → **3. 구현**: `LANDING_NAV`를 `{ label, to }` 배열로 바꾸고 span→`Link`.
  기존 `bb-landing-nav__item`(+`--link` modifier) 클래스 유지 — 전 항목이 링크가 됐으므로
  modifier를 기본 스타일로 합쳐도 된다(시각 결과 동일할 것). 자리표시였던 주석
  (`라우트가 없는 자리표시 항목들`, `후속 웨이브 범위`)을 삭제·갱신
- [ ] **4. GREEN + `npm test` + `npx tsc -b`**
- [ ] **5. 스크린샷**: `VITE_USE_MOCK=true npm run dev` → 랜딩(`/`) 상단 내비가 한글 링크 4개인
  상태 → 파일 경로 보고
- [ ] **6. 커밋** `feat(landing): 자리표시 내비를 실제 IA(루틴·랭킹·전체 상품·로그인)로 교체`

---

## 5. W2 — 직렬 검증 웨이브 (오케스트레이터, 머지 후)

- [ ] 3개 브랜치 리뷰(단언 무수정 원칙 준수 확인 포함) 후 main 머지
- [ ] 경계 위반 잔량 전수 확인:
  `grep -rn "import com.beautyboy" backend/src/main/java/com/beautyboy/{payment,compat}/*.java`
  에서 타 도메인 **엔티티/리포지토리** import 0건 (인터페이스·record는 허용)
- [ ] `./gradlew test` + `integrationTest` + `npm test` + `npx tsc -b` + E2E
  (E2E는 [[e2e-needs-e2e-profile-backend]] 절차 — compose 백엔드 내리고 e2e 프로필 bootRun,
  시드 계정 장바구니 비우기)
- [ ] 랜딩 스크린샷 열어 판정 + 링크 클릭이 로그인 퍼널로 흐르는지 실스택 확인
- [ ] `docs/plans/2026-07-26-다음-작업.md` §5에서 완료 2건 소거·기록

---

## 6. 터미널 실행 프롬프트

> 사람은 프로젝트 루트에서 터미널을 열고 아래를 통째로 붙여넣는다. git 명령을 손으로 치지 않는다.

### T1 — 결제 강화 (opus)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add ../뷰티보이-결제강화 -b feature/payment-hardening
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 해당 worktree인지
  - git log --oneline -1 이 루트에서 본 기점 커밋(docs(plan): 마감 3종 계획)과 같은지
  - docs/plans/2026-07-28-hardening-and-landing.md 가 실제로 존재하는지
  - git status가 깨끗한지

[2단계 — 실행] docs/plans/2026-07-28-hardening-and-landing.md 의 T1-A → T1-B를 순서대로 실행해라.
너는 이 계획의 T1 실행 서브에이전트다(model: opus — 결제 2단계 검증 영역).
CLAUDE.md 공통 규칙과 계획서의 Global Constraints·파일 소유권 표를 지켜라.
가장 중요한 제약: **기존 payment 테스트의 단언을 한 글자도 바꾸지 마라** — 배선만 바꾼다.
단언을 바꿔야 통과한다면 동작이 바뀐 것이니 중단하고 보고해라.
ErrorCode.java 등 common 패키지는 열지 않는다. 스텝별 TDD는 계획서 그대로.
```

### T2 — 궁합 경계 (sonnet)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add ../뷰티보이-궁합경계 -b feature/compat-boundary
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 해당 worktree인지
  - git log --oneline -1 이 루트에서 본 기점 커밋(docs(plan): 마감 3종 계획)과 같은지
  - docs/plans/2026-07-28-hardening-and-landing.md 가 실제로 존재하는지
  - git status가 깨끗한지

[2단계 — 실행] docs/plans/2026-07-28-hardening-and-landing.md 의 T2-A를 실행해라.
너는 이 계획의 T2 실행 서브에이전트다(model: sonnet). CLAUDE.md 공통 규칙과 계획서의
Global Constraints·파일 소유권 표를 지켜라. CompatService의 로직은 한 줄도 바꾸지 않는다 —
타입 치환(getter→record 접근자)만이다. CompatServiceTest 단언 무수정.
스텝별 TDD는 계획서 그대로.
```

### T3 — 랜딩 내비 (sonnet)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add ../뷰티보이-랜딩내비 -b feature/landing-nav
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 해당 worktree인지
  - git log --oneline -1 이 루트에서 본 기점 커밋(docs(plan): 마감 3종 계획)과 같은지
  - docs/plans/2026-07-28-hardening-and-landing.md 와 DESIGN.md 의 "영문/한글 혼용 규칙"이 실제로 존재하는지
  - git status가 깨끗한지

[2단계 — 실행] docs/plans/2026-07-28-hardening-and-landing.md 의 T3-A를 실행해라.
너는 이 계획의 T3 실행 서브에이전트다(model: sonnet). CLAUDE.md 공통 규칙과 계획서의
Global Constraints·파일 소유권 표를 지켜라. 라벨은 전부 한글(DESIGN.md 혼용 규칙),
기존 bb-landing-nav 레이아웃 유지. 스텝별 TDD와 스크린샷 DoD는 계획서 그대로.
```
