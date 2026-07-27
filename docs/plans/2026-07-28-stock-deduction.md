# 구현 계획 — 재고 차감과 동시성 (Wave 5)

> 근거: `docs/plans/2026-07-26-다음-작업.md` §2. 별도 설계 문서는 없다 — 새 API·도메인 없이
> 기존 결제 승인 경로의 정합성 작업이라, **이 문서의 §2 "설계 결정"이 설계의 진실을 겸한다.**
> 마이그레이션 없음 — `goods_option.stock`(V10, INT)이 이미 있다. Flyway 번호 이슈 없음.
> 2026-07-28 결정으로 오늘드림을 하지 않으므로 재고는 `goods_option.stock` **한 벌**뿐이다.

## 0. 사람이 할 일 (사전 조건 2줄)

터미널을 열기 전에 프로젝트 루트에서:

```
git log --oneline -1     # 이 계획 커밋(제목: docs(plan): 재고 차감 구현 계획) 이상인지 확인
git status               # 깨끗한지 확인
```

## 1. 웨이브·터미널 분할

| 웨이브 | 터미널 | 브랜치 | 범위 | 모델 |
|---|---|---|---|---|
| 단일 | T1 | `feature/stock-deduction` | catalog 재고 커맨드 + 결제 승인 배선 + 동시성 IT | **opus** |

- 터미널 1개 직렬 — catalog·payment·order 세 패키지를 한 흐름으로 만지므로 나눌 이유가 없다.
- **opus인 이유**: CLAUDE.md 모델 배분 예외("결제 2단계 검증·재고 차감") 영역이다. 동시성·롤백
  경계·영속성 컨텍스트 함정(§3-2 주석) 판단이 계획서 코드 밖에서도 필요하다.
- 프론트 수정 없음 — 품절 시 confirm이 409 `ORDER_OUT_OF_STOCK`("재고가 부족한 상품이
  있습니다")을 반환하고, 이 에러 봉투는 기존 결제 실패 처리로 흐른다. 부족하면 고치지 말고 보고.

### 파일 소유권

| 터미널 | 소유 파일 |
|---|---|
| T1 | `backend/…/catalog/StockCommandService.java`(신규), `catalog/StockService.java`(신규), `catalog/GoodsOptionRepository.java`(신규), `payment/PaymentService.java`, `order/OrderService.java`(주석만), `catalog/GoodsQueryService.java`(주석만), `backend/src/test/java/com/beautyboy/catalog/StockServiceTest.java`(신규), `payment/PaymentStockConfirmTest.java`(신규), `payment/StockConcurrencyMysqlIntegrationTest.java`(신규) |

목록 밖 파일은 수정하지 않는다. 특히 **`ErrorCode.java`는 열지 않는다** — 기존
`ORDER_OUT_OF_STOCK`(409)을 그대로 쓴다(§2 결정 4). 안 맞으면 고치지 말고 보고한다.

---

## 2. 설계 결정 (2026-07-28 사용자 확정)

### 결정 1 — 차감 시점: **결제 승인 트랜잭션 안**

주문 생성이 아니라 `PaymentService.confirm`의 트랜잭션 안에서 깎는다.

- 근거: 주문 생성 시 깎으면 결제를 안 한 PENDING 주문이 재고를 잠근다 → TTL 복원 배치와
  만료 상태가 추가로 필요해진다(배송 스케줄러도 안 하기로 한 프로젝트 범위와 안 맞는다).
  승인 시점 차감은 **미결제 주문이 재고를 전혀 잠그지 않고**, 상태도 PENDING/PAID 둘로 끝난다.
- 주문 생성 시점의 기존 재고 **검증**(`OrderService.create`)은 그대로 둔다 — UX 게이트다.
  품절 상품으로 주문서를 만들게 두면 결제창까지 가서야 실패를 본다.

### 결정 2 — 복원: **트랜잭션 롤백이 곧 복원이다 (복원 코드를 만들지 않는다)**

차감이 confirm 트랜잭션 안에 있으므로, 이후 단계(토스 통신 실패, 금액 불일치)가 예외를
던지면 **롤백이 차감을 자동으로 되돌린다.** 별도 보상 UPDATE·복원 배치를 두지 않는다.

- 이 보장은 "차감이 반드시 트랜잭션 안에서 불린다"에 의존하므로, 구현은
  `Propagation.MANDATORY`로 **트랜잭션 없는 호출을 예외로 만든다**(§3-3) — 계약을 코드로 강제.
- 결제 취소·환불 기능은 1차 범위 밖이므로 PAID 이후의 재고 복원 경로는 존재하지 않는다(의도).

### 결정 3 — 부분 실패: **한 트랜잭션 all-or-nothing + optionId 오름차순 차감**

여러 옵션을 순서대로 조건부 UPDATE하다가 하나라도 영향 행 0이면 `ORDER_OUT_OF_STOCK`을
던진다 → 롤백이 앞서 깎인 것까지 전부 되돌린다. "일부만 깎인 주문"은 존재할 수 없다.

- **optionId 오름차순 고정**: 두 주문이 같은 옵션 집합을 서로 다른 순서로 깎으면 InnoDB
  데드락이 난다. 정렬이 락 획득 순서를 전역으로 일치시킨다(§3-3의 `TreeMap`이 이 역할).
- 같은 옵션이 여러 줄이면 **합산 후 1회 차감** — 줄 단위로 깎으면 "합계는 부족한데 각 줄은
  통과"가 없도록.

### 결정 4 — 나머지 확정 사항

- **차감 순서: 토스 호출 전.** 품절이면 돈이 움직이기 전에 거절되므로 승인 취소(보상 호출)가
  필요 없다. 대가로 토스 HTTP 호출 동안 `goods_option` 행 락이 유지되는데, 같은 옵션의 동시
  구매를 직렬화하는 부수 효과라 이 규모에서는 올바른 방향이다.
- **차감 실패 시 토스 결제는 부르지 않은 채 방치한다** — 승인되지 않은 결제창 세션은 토스가
  스스로 만료시킨다. 우리가 취소할 것이 없다.
- **락 방식: 조건부 UPDATE의 영향 행 수** (`UPDATE … SET stock = stock - :qty WHERE … AND
  stock >= :qty`). 낙관적 락(@Version) 예외에 의존하지 않는다 — H2와 InnoDB의 예외 타입이
  달라 실 MySQL에서 조용히 깨진 이력이 있는 프로젝트다. 리프레시 토큰 회전(4-16a)에서 이미
  검증된 패턴이다.
- **에러 코드: 기존 `ORDER_OUT_OF_STOCK`(409) 재사용.** 사용자 시나리오("주문·결제 중 품절")가
  같고 메시지("재고가 부족한 상품이 있습니다")가 그대로 맞는다. 새 코드를 만들면 프론트가
  분기할 이유 없는 분기가 생긴다.
- **옵션 없는 상품(스냅샷 `optionId=null`, stock `MAX_VALUE`)은 차감 대상이 아니다** — 재고
  관리 단위가 옵션이라는 기존 정의를 따른다. 호출자(PaymentService)가 거른다.
- **알려진 트레이드오프**: Playwright E2E·수동 시연을 같은 DB에 반복하면 시드 재고가 실제로
  소진된다. 리셋은 시드 재적용(`docker compose down -v && up`)으로 한다. E2E 시나리오가 사는
  goods들의 시드 재고(80 등)면 수십 회는 버틴다 — 배치로 리필하지 않는다(범위 밖).

---

## 3. 공유 계약 — 코드 전량

이 절은 동시성·정합성 판단이 걸린 곳이라 전량으로 적는다(plan-conventions). 시그니처·쿼리·
전이 순서를 임의로 바꾸지 않는다. 바꿔야 할 이유를 발견하면 중단하고 보고한다.

### 3-1. `catalog/StockCommandService.java` (신규 인터페이스 — 도메인 경계)

`order`/`payment`는 `goods_option`을 직접 못 만지므로(패키지 = 서비스 경계) catalog가
커맨드 인터페이스를 내준다. `GoodsQueryService`와 같은 관례다.

```java
package com.beautyboy.catalog;

import java.util.List;

/**
 * 재고 차감 커맨드 경계. 재고의 소유자는 catalog이고, 소비자는 결제 승인(payment)뿐이다.
 *
 * <p>호출 계약:
 * <ul>
 *   <li><b>호출자의 트랜잭션 안에서만 부른다</b>(구현이 {@code MANDATORY}로 강제한다).
 *       이후 단계가 실패해 트랜잭션이 롤백되면 차감도 함께 되돌아간다 — 그것이 복원의 전부다.</li>
 *   <li>{@code optionId}는 null이면 안 된다. 옵션 없는 상품(재고 비관리)은 호출자가 거른다.</li>
 *   <li>전부 성공하거나(반환), 하나라도 부족하면 {@code ORDER_OUT_OF_STOCK}을 던진다(all-or-nothing).</li>
 * </ul>
 */
public interface StockCommandService {

    /** 한 옵션에서 깎을 수량. quantity는 양수여야 한다(주문 생성이 이미 검증했다). */
    record DeductionLine(Long optionId, int quantity) {
    }

    void deductAll(List<DeductionLine> lines);
}
```

### 3-2. `catalog/GoodsOptionRepository.java` (신규 — 조건부 UPDATE)

```java
package com.beautyboy.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoodsOptionRepository extends JpaRepository<GoodsOption, Long> {

    /**
     * 조건부 차감. 영향 행 1 = 확보, 0 = 재고 부족. 이 한 문장이 검증과 차감을 원자로 묶어
     * "확인한 재고를 남이 먼저 가져가는" 틈을 없앤다. 낙관적 락 예외에 의존하지 않는 이유는
     * 계획서 §2 결정 4.
     *
     * <p><b>clearAutomatically를 켜지 않는다.</b> 이 쿼리는 confirm 트랜잭션 중간에 불리는데,
     * 영속성 컨텍스트를 비우면 락과 함께 읽어 둔 Order가 detach되어 뒤따르는
     * {@code markPaid()} 변경이 조용히 유실된다(더티체킹 대상에서 빠진다). 이 트랜잭션은
     * GoodsOption 엔티티를 읽지 않으므로 1차 캐시가 낡을 일도 없다.
     * 회귀 방어: PaymentStockConfirmTest의 "차감 뒤에도 결제 완료 전이가 유실되지 않는다".
     */
    @Modifying(flushAutomatically = true)
    @Query("update GoodsOption o set o.stock = o.stock - :qty "
            + "where o.id = :optionId and o.stock >= :qty")
    int deduct(@Param("optionId") Long optionId, @Param("qty") int qty);
}
```

### 3-3. `catalog/StockService.java` (신규 구현)

```java
package com.beautyboy.catalog;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class StockService implements StockCommandService {

    private final GoodsOptionRepository goodsOptionRepository;

    public StockService(GoodsOptionRepository goodsOptionRepository) {
        this.goodsOptionRepository = goodsOptionRepository;
    }

    /**
     * MANDATORY인 이유: 트랜잭션 없이 부르면 차감이 그 자리에서 커밋되어 "롤백이 복원"이라는
     * 계약(계획서 §2 결정 2)이 조용히 깨진다. 그 오용을 예외로 바꾼다.
     *
     * <p>TreeMap인 이유 둘: (1) 같은 옵션 여러 줄을 합산해 한 번에 깎는다 — 줄 단위로 깎으면
     * 합계는 부족한데 각 줄은 통과할 수 있다. (2) optionId 오름차순이 락 획득 순서를 전역으로
     * 일치시켜 교차 주문 데드락을 없앤다(§2 결정 3).
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deductAll(List<DeductionLine> lines) {
        Map<Long, Integer> merged = new TreeMap<>();
        for (DeductionLine line : lines) {
            merged.merge(line.optionId(), line.quantity(), Integer::sum);
        }
        for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
            if (goodsOptionRepository.deduct(entry.getKey(), entry.getValue()) == 0) {
                // 어느 옵션이 모자랐는지는 응답에 싣지 않는다 — 메시지는 공용 문장으로 충분하고,
                // 실패 시 전체 롤백이라 부분 상태를 설명할 필요가 없다.
                throw new BusinessException(ErrorCode.ORDER_OUT_OF_STOCK);
            }
        }
    }
}
```

### 3-4. `payment/PaymentService.confirm` 수정본 (전문 — 단계 (3)이 신설)

기존 (1)(2)(4)(5)(6)은 한 글자도 바꾸지 않는다. 신설되는 (3)과 클래스 javadoc의 순서 목록,
생성자 주입(`StockCommandService` 추가)만 바뀐다.

```java
@Transactional
public PaymentConfirmResponse confirm(Long memberId, PaymentConfirmRequest request) {
    // (1) 락과 함께 읽는다. 동시 승인 요청을 직렬화한다.
    Order order = orderRepository.findByOrderNoForUpdate(request.orderNo())
            .filter(o -> o.ownedBy(memberId))   // 남의 주문이면 존재를 숨겨 404로 답한다.
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    // (2) 상태를 먼저 본다. 이미 결제됐으면 토스도 재고도 건드리지 않는다.
    if (!Order.STATUS_PENDING.equals(order.getStatus())) {
        throw new BusinessException(ErrorCode.PAYMENT_ALREADY_CONFIRMED);
    }

    // (3) 재고를 깎는다 — 토스 호출 전. 품절이면 돈이 움직이기 전에 여기서 끝나므로
    //     승인 취소가 필요 없다. 이후 단계가 실패하면 이 트랜잭션의 롤백이 차감을 되돌린다 —
    //     복원 코드는 존재하지 않는 것이 설계다(계획서 §2 결정 2).
    //     옵션 없는 상품(optionId null)은 재고 비관리라 거른다(스냅샷 stock=MAX_VALUE와 같은 정의).
    stockCommandService.deductAll(order.getItems().stream()
            .filter(item -> item.getOptionId() != null)
            .map(item -> new StockCommandService.DeductionLine(
                    item.getOptionId(), item.getQuantity()))
            .toList());

    // (4) 토스에 승인 요청. 여기서 실제 결제가 일어난다.
    PaymentApproval approval =
            paymentGateway.confirm(request.paymentKey(), request.orderNo(), request.amount());

    // (5) 금액 대조. 다르면 승인을 취소하고 실패시킨다 — 롤백이 (3)의 차감도 되돌린다.
    if (approval.approvedAmount() != order.getPayableAmount()) {
        paymentGateway.cancel(request.paymentKey(), "주문 금액과 승인 금액 불일치");
        throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    // (6) 확정. 주문 전이 → payment 저장 순서로.
    order.markPaid(LocalDateTime.now());
    paymentRepository.save(new Payment(
            order.getId(),
            approval.paymentKey(),
            approval.approvedAmount(),
            approval.rawJson(),
            LocalDateTime.now()));

    return new PaymentConfirmResponse(order.getOrderNo(), order.getStatus(), order.getPayableAmount());
}
```

- `StockCommandService`는 **인터페이스** 주입이다 — catalog 엔티티/리포지토리를 payment가
  직접 import하지 않는다(기존 `order` 직접 import 부채를 늘리지 않는다).

---

## 4. 터미널 T1 — 실행

### 실행 프롬프트 (그대로 붙여넣기)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add ../뷰티보이-재고차감 -b feature/stock-deduction
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 ../뷰티보이-재고차감 인지
  - git log --oneline -1 이 루트에서 본 기점 커밋과 같은지
  - docs/plans/2026-07-28-stock-deduction.md 가 실제로 존재하는지
  - git status가 깨끗한지

[2단계 — 실행]
docs/plans/2026-07-28-stock-deduction.md 를 처음부터 끝까지 읽고 Task 1 ~ 3을 순서대로
실행해라. 서브에이전트를 스폰할 때 model은 opus다(CLAUDE.md 모델 배분 예외 — 재고 정합성).

규칙:
- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다. §3의 코드를 그대로 옮긴다 — 쿼리·전파 속성·
  전이 순서·정렬을 임의로 바꾸지 마라. 바꿔야 할 이유를 발견하면 중단하고 보고해라.
- ErrorCode.java와 Flyway 마이그레이션을 만들거나 수정하지 마라 — 이 태스크에는 없다.
- 롤백 복원을 검증하는 테스트 클래스에 @Transactional을 붙이지 마라(계획서 Task 2 주의).
- 동시성의 신뢰 근거는 실 MySQL이다: ./gradlew integrationTest (Docker 필요).
- 상태 변경 재조회 전에는 TestPersistence.DB_왕복_강제(em)를 호출한다(프로젝트 규약).
완료하면 커밋하고, e2e 프로필 curl 스모크 2건(정상 차감·품절 409)의 실제 응답을 보고서에 남겨라.
```

### Task 1 — catalog 재고 커맨드 (`StockCommandService` + `GoodsOptionRepository` + `StockService`)

§3-1~§3-3 전량 그대로. 테스트 `catalog/StockServiceTest.java` — `@SpringBootTest`
`@ActiveProfiles("test")` `@Transactional`(MANDATORY 전파를 테스트 트랜잭션이 충족한다).
테스트 이름·단언 전량:

- `재고 5에서 3을 깎으면 2가 남는다` — deductAll 후 `DB_왕복_강제` 재조회로 stock=2.
- `재고 2에서 3을 깎으면 ORDER_OUT_OF_STOCK이고 그 옵션 재고는 줄지 않는다` —
  BusinessException의 ErrorCode 단언 + 재조회 stock=2.
- `같은 옵션 두 줄(2+2)은 합산돼 재고 5가 1이 된다` — 합산 없이 줄 단위로 깎으면 이 테스트가
  다른 값을 낸다.
- `같은 옵션 두 줄의 합(4)이 재고(3)를 넘으면 실패한다` — 줄 단위(2·2)로는 첫 줄이 통과해버리는
  회귀를 잡는다.
- `트랜잭션 없이 부르면 예외다` — `@Transactional(propagation = NOT_SUPPORTED)` 메서드(또는
  TransactionTemplate 없이 별도 스레드)에서 호출해 `IllegalTransactionStateException` 단언.
  "롤백이 복원"이라는 계약의 강제 장치가 살아 있는지 확인한다.
- `수량 0 이하 줄은 들어오지 않는 것이 계약이다` — 주문 생성이 이미 막는다(OrderService의
  CART_QUANTITY_INVALID). 여기서는 테스트하지 않고 이 이름의 주석만 남긴다(이중 검증 금지).

### Task 2 — 결제 승인 배선 (`PaymentService` §3-4) + 주석 정리

테스트 `payment/PaymentStockConfirmTest.java` — `PaymentConfirmTest`와 같은 하네스
(`@SpringBootTest` + MockMvc + `@TestConfiguration` 가짜 게이트웨이. 가짜에 confirm 호출
기록·예외 주입 스위치를 추가한 자체 사본을 쓴다). **클래스 `@Transactional` 금지** — 이
테스트의 핵심이 "서비스 트랜잭션의 롤백이 재고를 되돌리는가"라서, 테스트가 트랜잭션을
감싸면 롤백 경계가 사라져 아무것도 검증되지 않는다. `@AfterEach`에서 만든 데이터를 지운다
(`AuthRefreshConcurrencyScenario`가 같은 이유로 같은 구조다). 테스트 이름·단언 전량:

- `승인하면 재고가 수량만큼 줄어든다` — 재고 10, 수량 3 → 승인 200 + 재조회 stock=7 +
  주문 status PAID.
- `품절이면 토스를 부르지 않고 409 ORDER_OUT_OF_STOCK이다` — 재고 1, 수량 2 → 409 +
  가짜 게이트웨이 confirm 호출 기록 0건 + stock=1 그대로.
- `토스 통신이 실패하면 차감이 되돌아간다` — 가짜가 PaymentGatewayException을 던지게 주입,
  재고 10 수량 3 → 5xx 응답 + 재조회 stock=10 + 주문 PENDING 유지.
- `금액이 불일치하면 승인을 취소하고 차감이 되돌아간다` — approvedAmount 조작 → 409
  PAYMENT_AMOUNT_MISMATCH + cancel 호출 1건 + stock 원상 + PENDING 유지.
- `부분 품절이면 다른 상품도 깎이지 않는다` — 옵션 A(재고 10)·B(재고 0) 두 항목 주문 →
  409 + A 재조회 stock=10(앞서 깎였다 롤백으로 복원) + PENDING 유지.
- `옵션 없는 상품은 차감 없이 승인된다` — optionId null 항목만으로 승인 200 + PAID.
- `차감 뒤에도 결제 완료 전이가 유실되지 않는다` — §3-2 clearAutomatically 함정 회귀.
  승인 200 후 **새 트랜잭션 재조회**로 status=PAID **와** stock 감소를 동시에 단언
  (clearAutomatically를 켜면 markPaid 유실로 status가 PENDING으로 남아 이 테스트가 잡는다).
- `이미 결제된 주문은 재고를 다시 깎지 않는다` — PAID 주문에 재승인 → 409
  PAYMENT_ALREADY_CONFIRMED + stock 변화 없음(상태 검사가 차감보다 앞이라는 순서 회귀).

주석 정리(코드 변경 없음, 같은 커밋): `OrderService.create`의 "차감은 Wave 3의 몫" 블록 주석을
"검증만 한다(UX 게이트). 차감은 결제 승인 트랜잭션에서 한다 — PaymentService.confirm (3)"으로,
`GoodsQueryService.OrderGoodsSnapshot`의 "이 웨이브는 재고를 검증만 하고 차감하지 않는다
(차감은 Wave 3)" 문구를 "차감은 결제 승인 시점에 StockCommandService가 한다"로 교체한다.

### Task 3 — 동시성 통합 테스트 (실 MySQL)

`payment/StockConcurrencyMysqlIntegrationTest.java` — `@Tag("integration")`, Testcontainers
MySQL 8.4 + Flyway + `ddl-auto=validate` (`FlywayMigrationSmokeTest`·
`AuthRefreshConcurrencyMysqlIntegrationTest`와 같은 기동 방식). H2 쌍둥이는 만들지 않는다 —
H2는 InnoDB와 잠금 동작이 달라 이 시나리오의 신뢰 근거가 못 되고, 거짓 녹색이 더 위험하다
(프로젝트에 H2가 실 MySQL 문제를 가린 이력이 있다). 서비스는 MockMvc가 아니라
`PaymentService`를 직접 부른다(스레드별 인증 컨텍스트 관리를 피한다). 게이트웨이는 승인을
그대로 돌려주는 테스트 빈. 테스트 이름·단언 전량:

- `마지막 재고 1개를 두 주문이 다투면 정확히 한쪽만 성공한다` — 같은 옵션(stock=1)으로
  회원 2명이 주문 각 1건(수량 1) 생성 커밋 → CyclicBarrier로 두 스레드가 동시에 confirm →
  단언: 성공 정확히 1 · 실패 1은 BusinessException(ORDER_OUT_OF_STOCK) · 그 외 예외(500류) 0 ·
  재조회 stock=0(음수 아님) · PAID 주문 정확히 1건.
- `재고 3을 두 주문(각 수량 2)이 다투면 한쪽만 성공하고 재고 1이 남는다` — 경계값(부분 충족)
  에서도 음수·이중 차감이 없음: 성공 1 · ORDER_OUT_OF_STOCK 1 · stock=1.

### T1 완료 조건

- `./gradlew test` 전량 + `./gradlew integrationTest` 전량 통과 (Docker 필요).
- **실기동 curl 스모크 2건** (H2 함정 방어 — 실 MySQL + `e2e` 프로필 bootRun, memory의
  "curl 스모크 레시피"의 기동 방법 재사용. `e2e`인 이유: FakePaymentGateway가 승인을
  성공시켜 실제 토스 없이 전 구간이 돈다):
  1. **정상 차감** — 주문 생성 → confirm → 200, DB에서 `SELECT stock`이 수량만큼 감소.
  2. **품절 409** — 주문 생성 후 DB에서 그 옵션 `UPDATE stock=0` → confirm → 409
     `ORDER_OUT_OF_STOCK` (차감이 토스 호출보다 앞임을 실기동으로 확인).
- 두 스모크의 실제 요청·응답을 보고서에 남긴다.

---

## 5. 머지 게이트 (오케스트레이터 세션)

1. 파일 소유권 준수 — 목록 밖 파일(특히 `ErrorCode.java`, Flyway) 변경 없음
2. `./gradlew test` + `./gradlew integrationTest` 전량 통과 — **integrationTest를 빼지 않는다**
   (동시성 2건의 유일한 신뢰 근거)
3. §3 계약 준수를 diff로 확인 — `MANDATORY` 전파, `clearAutomatically` 미사용, `TreeMap`
   (합산+정렬), confirm 단계 순서 (2)상태→(3)차감→(4)토스
4. curl 스모크 2건의 실제 응답이 보고서에 있는가
5. 롤백 복원 테스트 클래스에 `@Transactional`이 없는가 (있으면 그 테스트는 아무것도 검증하지
   않는다)
6. 머지 후 main에서 전체 테스트 재실행 (`npm test`는 프론트 무변경이라 기존 기준선 유지 확인만)
