# Wave 2 T2 — 장바구니(cart) + 주문(order) + 결제(payment) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: `superpowers:subagent-driven-development`(권장) 또는
> `superpowers:executing-plans`로 태스크 단위 실행. 스텝은 체크박스(`- [ ]`)로 추적한다.

**Goal:** 장바구니 담기부터 토스 결제 승인·검증까지의 구매 경로를 만들어, 설계 7장의
`/cart/items` · `POST /orders` · `POST /payments/confirm` · `GET /orders`를 채운다.

**Architecture:** 핵심은 **"돈은 서버가 다시 계산한다"** 하나다. 클라이언트가 보낸 금액을 절대 믿지 않고,
주문 생성 시 상품 가격을 DB에서 다시 읽어 합계를 만들고, 결제 승인 시 토스가 알려준 승인 금액이
그 합계와 같은지 한 번 더 본다. 다르면 **승인을 취소하고** 주문을 실패시킨다.
주문 시점의 가격·상품명·배송지는 전부 **스냅샷**으로 저장한다 — 참조로 남기면 나중에 상품 가격이
바뀔 때 과거 주문서의 금액이 조용히 달라진다.
외부 결제사 호출은 `PaymentGateway` 인터페이스 뒤에 두어, 유닛테스트가 네트워크 없이 돈다.

**Tech Stack:** Spring Boot 3.5, Java 21, JPA, Flyway, MySQL 8.4, `RestClient`(starter-web 내장 — 추가 의존성 없음),
테스트는 H2(MySQL 모드) + Testcontainers(통합).

**근거 문서:** 설계 `docs/superpowers/specs/2026-07-23-beautyboy-design.md` 5장(cart/order/payment)·7장(인증 API·결제 2단계).
로드맵 `docs/plans/2026-07-23-roadmap.md`(Flyway 대역, 공유 계약, Wave 2 사전 정리).

---

## Global Constraints

모든 태스크에 암묵적으로 포함된다.

- **Flyway 대역은 V30~V39뿐이다.** 이 밖의 번호를 쓰지 않는다. 다른 터미널(T1=V20~V29, T3=V40~V49)의 파일을 만들지 않는다.
- **아래 파일은 열지 않는다** (Wave 2 사전 정리에서 이미 닫힘):
  - `common/ErrorCode.java` — `CART_*` `ORDER_*` `PAYMENT_*` 상수가 **이미 들어가 있다**. 추가가 필요하면 중단하고 보고.
  - `config/SecurityConfig.java` — 이 계획의 엔드포인트는 전부 인증 대상이라 `anyRequest().authenticated()`로
    **자동으로 걸린다.** 아무것도 추가할 필요가 없다. 확인만 하고 수정하지 않는다.
  - `backend/build.gradle.kts` — 동결. **토스 호출에 새 HTTP 라이브러리를 추가하지 않는다** —
    `RestClient`가 `spring-boot-starter-web`에 이미 들어 있다. 다른 의존성이 필요하면 중단하고 보고.
  - `common/ApiResponse.java` · `common/PageResponse.java` — 동결.
  - `ranking/**` — T1 소유. 단 **`ranking.SalesStatProvider`는 이 계획이 구현한다**(T2-8).
    인터페이스 파일 자체는 수정하지 않고 `implements`만 한다. 시그니처를 바꾸면 T1이 깨진다.
  - `search/**` — T1 소유. 절대 만들지도 고치지도 않는다.
- **`catalog/**`는 이번 웨이브에서 이 터미널(T2) 소유다.** T2-2에서 `GoodsQueryService`에 메서드를 **추가만** 한다.
  기존 메서드 시그니처를 바꾸지 않는다 — `ingredient`가 `exists(Long)`을 쓰고 있다.
- **돈과 재고는 서버.** 클라이언트가 보낸 가격·할인·합계를 **어떤 경로로도 신뢰하지 않는다.**
  요청 DTO에 금액 필드를 두지 않는다(있으면 언젠가 쓰게 된다). 유일한 예외가 결제 승인 요청의 `amount`인데,
  그것도 "검증 대상"이지 "사용 값"이 아니다.
- **주문 시점 데이터는 스냅샷.** 가격·상품명·옵션명·배송지를 주문 테이블에 복사해 저장한다.
  FK 참조로 남기고 조회 시 조인하지 않는다.
- **시크릿을 코드·커밋에 넣지 않는다.** 토스 테스트 키는 환경변수(`TOSS_SECRET_KEY`)로만 주입한다.
  기본값을 소스에 적지 않는다 — 적는 순간 그것이 커밋된 시크릿이다.
- **패키지 = 서비스 경계.** `cart`·`order`·`payment`는 자기 테이블만 접근한다.
  상품 정보는 `catalog.GoodsQueryService` 인터페이스로만 받는다. `member`의 배송지는
  `member.AddressService`를 경유한다. 타 도메인 엔티티/리포지토리를 직접 import하지 않는다.
- **테스트는 H2 + 픽스처만.** 실제 토스 API를 호출하는 테스트를 만들지 않는다 —
  외부 자원을 만지는 E2E는 Wave 4 직렬 웨이브 몫이다(로드맵 §5).
- **상태 변경을 검증할 때는 재조회 전에 `com.beautyboy.support.TestPersistence.DB_왕복_강제(em)`를 호출한다.**
  Wave 0에서 프로필 갱신 500을 유닛테스트가 통째로 놓친 원인이 1차 캐시다.
  **결제 금액·주문 상태 전이가 정확히 그 영역이므로 이 계획에서는 선택이 아니라 의무다.**
- **커밋 메시지·주석·문서는 한국어.** 태스크 단위 원자적 커밋.
- **모델 배분:** T2-1 ~ T2-4, T2-7 ~ T2-9는 **sonnet**. **T2-5·T2-6(결제 2단계 검증)은 `opus`** —
  CLAUDE.md 모델 배분 예외 3종 중 "결제(2단계 검증)"에 해당한다.
- 명령은 모두 `backend/`에서 실행한다.

---

## 착수 전 확인 (사람 몫)

```bash
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" log --oneline -1   # 0e990a7 (chore(backend): Wave 2 병렬 분기 전 사전 정리) 이후여야 함
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" status --short     # 비어 있어야 한다
```

---

## 파일 구조 (이 계획이 만들거나 고치는 것)

| 파일 | 책임 | 태스크 |
|---|---|---|
| `resources/db/migration/V30__cart.sql` | `cart_item` | T2-1 |
| `resources/db/migration/V31__order.sql` | `orders` · `order_item` | T2-1 |
| `resources/db/migration/V32__payment.sql` | `payment` | T2-1 |
| `test/.../common/FlywayMigrationSmokeTest.java` | 적용 버전 단언에 30·31·32 추가 | T2-1 |
| `catalog/GoodsQueryService.java` | 주문용 상품 스냅샷 조회 메서드 **추가** | T2-2 |
| `catalog/GoodsService.java` | 위 메서드 구현 | T2-2 |
| `cart/CartItem.java` · `CartItemRepository.java` · `CartService.java` · `CartController.java` · `dto/*` | 장바구니 CRUD + 루틴 일괄 담기 | T2-3 |
| `order/Order.java` · `OrderItem.java` · `OrderRepository.java` · `OrderService.java` · `OrderController.java` · `dto/*` | 주문 생성(서버 재계산·스냅샷) | T2-4 |
| `payment/PaymentGateway.java` · `TossPaymentGateway.java` · `dto/*` | 결제사 호출 경계 | T2-5 |
| `payment/Payment.java` · `PaymentRepository.java` · `PaymentService.java` · `PaymentController.java` | 승인 2단계 검증 | T2-6 |
| `order/OrderQueryController` 확장 (`OrderController`에 병합) | `GET /orders` · `/orders/{orderNo}` | T2-7 |
| `order/OrderSalesStatProvider.java` | `ranking.SalesStatProvider` 구현 | T2-8 |

**범위 밖(YAGNI):**
- **재고 차감** — 주문 시 재고를 **검증만** 하고 차감하지 않는다. 차감은 Wave 3 T1(오늘드림 `store_stock` 조건부 차감) 몫이며,
  그쪽이 동시성 설계의 주인이다. 지금 `goods_option.stock`을 깎으면 Wave 3에서 두 벌의 재고 개념이 충돌한다.
- **`point_history`** — 로드맵의 V30~V39 대역 목록에는 있지만 적립·사용 로직이 이 계획에 없다.
  **쓰지 않는 테이블을 미리 만들지 않는다.** 필요해지는 웨이브가 자기 대역에서 만든다.
- **쿠폰·할인** — `orders.discount_amount` 컬럼만 두고 항상 0. 계산 주체가 생기는 웨이브가 채운다.
- **비회원 장바구니 localStorage 병합** — 프론트 몫. 서버는 회원 장바구니만 안다.
- **주문 취소·환불** — 설계 3장 1차 범위 밖.
- **토스 결제창 연동(프론트)** — Wave 4.

---

## Task 1: Flyway V30~V32 — 장바구니 · 주문 · 결제 스키마

**왜 이게 1번인가:** 이후 모든 태스크가 이 스키마에 엔티티를 맞춘다. CLAUDE.md는 "설계 문서와 실제 스키마가
다르면 스키마가 진실"이라고 못 박았으므로 스키마를 먼저 확정한다.

**Files:**
- Create: `backend/src/main/resources/db/migration/V30__cart.sql`
- Create: `backend/src/main/resources/db/migration/V31__order.sql`
- Create: `backend/src/main/resources/db/migration/V32__payment.sql`
- Modify: `backend/src/test/java/com/beautyboy/common/FlywayMigrationSmokeTest.java` (적용 버전 단언 1줄)

**Interfaces:**
- Produces: `cart_item(id, member_id, goods_id, option_id, quantity, created_at)` UNIQUE(member_id, goods_id, option_id)
- Produces: `orders(id, order_no, member_id, status, total_amount, discount_amount, payable_amount,
  receiver_name, receiver_phone, zipcode, address1, address2, delivery_type, ordered_at, paid_at)`
- Produces: `order_item(id, order_id, goods_id, option_id, goods_name, option_name, unit_price, quantity, line_amount)`
- Produces: `payment(id, order_id, payment_key, amount, status, raw_response, approved_at)`

- [ ] **Step 1: `V30__cart.sql` 작성**

```sql
-- 회원 장바구니. 비회원은 프론트가 localStorage로 들고 있다가 로그인 시 병합하므로 서버는 회원 것만 안다.
--
-- option_id가 NULL 허용인 이유: 옵션이 없는 상품이 있다(단일 규격).
-- 그런데 MySQL의 UNIQUE는 NULL을 서로 다른 값으로 취급해서, 옵션 없는 같은 상품을 여러 번 담으면
-- 유니크 제약이 막지 못한다. 그래서 애플리케이션이 "같은 상품+옵션이면 수량을 더한다"로 처리하고
-- 유니크 제약은 최후의 방어선으로만 둔다(T2-3에서 이 동작을 테스트로 고정한다).
CREATE TABLE cart_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL,
  goods_id BIGINT NOT NULL,
  option_id BIGINT NULL,
  quantity INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cart_member_goods_option (member_id, goods_id, option_id),
  -- 장바구니 조회는 항상 "내 것 전부"라 member_id 단독 인덱스면 충분하다(위 유니크의 선행 컬럼이라 별도 인덱스 불필요).
  CONSTRAINT fk_cart_item_member FOREIGN KEY (member_id) REFERENCES member(id)
);
```

> **확인:** `member` 테이블의 PK 컬럼명이 `id`가 맞는지 `V1__member.sql`을 열어 확인하고,
> 다르면 FK를 실제 컬럼명에 맞춘다. **스키마가 진실이므로 V1을 고치지 않는다.**

- [ ] **Step 2: `V31__order.sql` 작성**

```sql
-- 주문. 배송지는 member.address를 참조하지 않고 이 행에 복사해 둔다(스냅샷).
-- 참조로 두면 회원이 배송지를 수정하는 순간 과거 주문서의 배송지가 조용히 바뀐다 —
-- "어디로 보냈는가"는 그 시점의 사실이라 나중에 달라지면 안 된다.
CREATE TABLE orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  -- 외부에 노출되는 주문번호. PK(연번)를 노출하면 총 주문 수가 새어나가고 남의 주문을 추측할 수 있다.
  order_no VARCHAR(30) NOT NULL UNIQUE,
  member_id BIGINT NOT NULL,
  -- PENDING(결제대기) → PAID(결제완료) → PREPARING → SHIPPING → DONE / CANCELED
  status VARCHAR(20) NOT NULL,
  total_amount INT NOT NULL,           -- 상품 합계(정가 아님, 판매가 기준)
  discount_amount INT NOT NULL DEFAULT 0,  -- 쿠폰·포인트. 1차에서는 항상 0.
  payable_amount INT NOT NULL,         -- 실제 결제할 금액 = total - discount. 결제 검증의 기준값.
  receiver_name VARCHAR(50) NOT NULL,
  receiver_phone VARCHAR(20) NOT NULL,
  zipcode VARCHAR(10) NOT NULL,
  address1 VARCHAR(200) NOT NULL,
  address2 VARCHAR(200) NULL,
  delivery_type VARCHAR(20) NOT NULL,  -- NORMAL|TODAY_DREAM|PICKUP
  ordered_at DATETIME NOT NULL,
  paid_at DATETIME NULL,
  -- 주문 목록은 "내 주문 최신순"이라 이 순서가 그대로 인덱스가 된다.
  INDEX idx_orders_member_ordered_at (member_id, ordered_at),
  -- 랭킹 배치가 "그 날 결제된 주문"을 훑는다(T2-8).
  INDEX idx_orders_status_paid_at (status, paid_at),
  CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES member(id)
);

-- 주문 상품. 상품명·옵션명·단가를 전부 복사한다.
-- goods_id는 "무엇을 샀는지" 추적용으로만 남기고, 표시는 스냅샷 컬럼으로 한다.
CREATE TABLE order_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  goods_id BIGINT NOT NULL,
  option_id BIGINT NULL,
  goods_name VARCHAR(200) NOT NULL,    -- 스냅샷
  option_name VARCHAR(100) NULL,       -- 스냅샷
  unit_price INT NOT NULL,             -- 스냅샷(옵션 추가금 포함한 1개 가격)
  quantity INT NOT NULL,
  line_amount INT NOT NULL,            -- unit_price * quantity. 저장해 두면 합계 검산이 쉽다.
  INDEX idx_order_item_goods (goods_id),
  CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders(id)
);
```

- [ ] **Step 3: `V32__payment.sql` 작성**

```sql
-- 결제. 주문 1건당 최대 1건(재결제는 1차 범위 밖)이라 order_id에 유니크를 건다 —
-- 이것이 이중 승인(같은 주문을 두 번 결제)에 대한 DB 차원의 마지막 방어선이다.
CREATE TABLE payment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  payment_key VARCHAR(200) NOT NULL,   -- 토스가 발급한 식별자
  amount INT NOT NULL,                 -- 토스가 승인했다고 응답한 금액
  status VARCHAR(20) NOT NULL,         -- APPROVED|CANCELED
  raw_response TEXT NOT NULL,          -- 승인 응답 원문. 분쟁 시 우리 해석이 아니라 원문이 근거다.
  approved_at DATETIME NOT NULL,
  UNIQUE KEY uk_payment_order (order_id),
  UNIQUE KEY uk_payment_key (payment_key),
  CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders(id)
);
```

- [ ] **Step 4: 스모크 테스트의 적용 버전 단언 갱신**

`FlywayMigrationSmokeTest`의 `모든_마이그레이션이_실_MySQL에서_성공한다()` 안에서 아래 한 줄을 **교체**한다:

```java
        assertThat(적용된_버전).contains("1", "10", "11", "12", "30", "31", "32");
```

> T1도 같은 줄을 자기 버전(20·21·22)으로 고친다. **머지 시 충돌이 나면 양쪽 번호를 모두 합친다** —
> 최종 형태는 `"1", "10", "11", "12", "20", "21", "22", "30", "31", "32"`가 된다.

- [ ] **Step 5: 실 MySQL에서 마이그레이션 검증**

Run: `./gradlew integrationTest`
Expected: PASS (4 tests). FK 제약이 실제로 걸리는지는 여기서만 확인된다 —
H2는 `application-test.yml`에서 Flyway가 꺼져 있어 이 DDL을 돌지 않는다.

실패하면 로그에서 Flyway가 멈춘 버전을 본다. `member(id)` FK 오류면 Step 1의 확인 사항이다.

- [ ] **Step 6: 유닛테스트 회귀 확인**

Run: `./gradlew test`
Expected: PASS (91 tests)

- [ ] **Step 7: 커밋**

```bash
git add src/main/resources/db/migration src/test/java/com/beautyboy/common/FlywayMigrationSmokeTest.java
git commit -m "feat(cart,order,payment): V30~V32 — 장바구니·주문·결제 스키마

주문의 가격·상품명·배송지를 전부 스냅샷 컬럼으로 둔다.
참조로 남기면 상품 가격이나 배송지가 바뀔 때 과거 주문서가 조용히 달라진다."
```

---

## Task 2: catalog에 주문용 상품 조회 추가

**근거:** 주문 생성은 클라이언트가 보낸 가격을 믿지 않고 서버가 다시 계산해야 한다(CLAUDE.md "돈과 재고는 서버").
그러려면 `order`가 상품의 현재 판매가·상품명·옵션 추가금·재고를 알아야 하는데,
`order`는 catalog 테이블에 직접 접근할 수 없다(패키지 = 서비스 경계). 그래서 catalog가 인터페이스로 내준다.

**Files:**
- Modify: `backend/src/main/java/com/beautyboy/catalog/GoodsQueryService.java` (메서드 **추가만**)
- Modify: `backend/src/main/java/com/beautyboy/catalog/GoodsService.java` (구현 추가)
- Test: `backend/src/test/java/com/beautyboy/catalog/GoodsOrderSnapshotTest.java`

**Interfaces:**
- Produces: `GoodsQueryService.findOrderSnapshot(Long goodsNo, Long optionNo)` →
  `Optional<OrderGoodsSnapshot>` where
  `OrderGoodsSnapshot(Long goodsId, Long optionId, String goodsName, String optionName, int unitPrice, int stock)`.
  T2-3(장바구니 표시)·T2-4(주문 생성)가 이 타입을 소비한다.
- **기존 `exists(Long)`의 시그니처를 바꾸지 않는다** — `ingredient`가 쓰고 있다.

- [ ] **Step 1: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/catalog/GoodsOrderSnapshotTest.java`

```java
package com.beautyboy.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GoodsOrderSnapshotTest {

    @Autowired
    GoodsQueryService goodsQueryService;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    @Test
    void 옵션이_없으면_상품_판매가가_단가다() {
        Goods goods = 상품_저장("그린티 토너", 20000, 16000);

        Optional<GoodsQueryService.OrderGoodsSnapshot> snapshot =
                goodsQueryService.findOrderSnapshot(goods.getId(), null);

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().goodsName()).isEqualTo("그린티 토너");
        assertThat(snapshot.get().optionName()).isNull();
        // 정가(20000)가 아니라 판매가(16000)다. 여기가 틀리면 손님이 정가로 결제한다.
        assertThat(snapshot.get().unitPrice()).isEqualTo(16000);
    }

    @Test
    void 옵션이_있으면_판매가에_추가금을_더한_값이_단가다() {
        Goods goods = 상품_저장("선크림", 30000, 24000);
        Long optionId = 옵션_저장(goods, "50ml 대용량", 3000, 7);

        GoodsQueryService.OrderGoodsSnapshot snapshot =
                goodsQueryService.findOrderSnapshot(goods.getId(), optionId).orElseThrow();

        assertThat(snapshot.optionName()).isEqualTo("50ml 대용량");
        assertThat(snapshot.unitPrice()).isEqualTo(27000);
        assertThat(snapshot.stock()).isEqualTo(7);
    }

    @Test
    void 없는_상품이면_비어_있다() {
        assertThat(goodsQueryService.findOrderSnapshot(999999L, null)).isEmpty();
    }

    @Test
    void 숨김_상품은_주문할_수_없도록_비어_있다() {
        // 목록·상세에서 숨긴 상품을 주문 경로로 우회해 살 수 있으면 숨김이 의미가 없다.
        Goods goods = 상품_저장("단종 상품", 10000, 10000);
        goods.hide();
        goodsRepository.save(goods);

        assertThat(goodsQueryService.findOrderSnapshot(goods.getId(), null)).isEmpty();
    }

    @Test
    void 다른_상품의_옵션을_붙이면_비어_있다() {
        // 옵션 id만 바꿔치기하면 싼 상품 가격에 비싼 옵션을 붙이는 식의 조작이 가능해진다.
        Goods 상품A = 상품_저장("상품A", 10000, 10000);
        Goods 상품B = 상품_저장("상품B", 90000, 90000);
        Long 상품B의_옵션 = 옵션_저장(상품B, "옵션", 0, 5);

        assertThat(goodsQueryService.findOrderSnapshot(상품A.getId(), 상품B의_옵션)).isEmpty();
    }

    private Goods 상품_저장(String name, int listPrice, int salePrice) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        return goodsRepository.save(
                new Goods(brand, "C001001001", name, null, "https://img/x.jpg", listPrice, salePrice));
    }

    private Long 옵션_저장(Goods goods, String name, int addPrice, int stock) {
        GoodsOption option = new GoodsOption(goods, name, addPrice, stock);
        goods.getOptions().add(option);
        goodsRepository.save(goods);
        goodsRepository.flush();
        return option.getId();
    }
}
```

> **먼저 확인할 것:** `GoodsOption`의 생성자 시그니처가 위 `new GoodsOption(goods, name, addPrice, stock)`과
> 다를 수 있다. `catalog/GoodsOption.java`를 열어 실제 생성자에 맞춰 이 헬퍼를 고쳐라 —
> **테스트를 실제 코드에 맞춘다. 엔티티 생성자를 테스트 편의로 바꾸지 않는다.**

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*GoodsOrderSnapshotTest*'`
Expected: FAIL — `findOrderSnapshot` 메서드가 없어 컴파일 에러.

- [ ] **Step 3: 인터페이스에 메서드 추가** — `catalog/GoodsQueryService.java`

기존 인터페이스 본문 끝에 아래를 **추가**한다(기존 `exists` 선언은 그대로 둔다):

```java
    /**
     * 주문·장바구니가 쓰는 상품 스냅샷 조회.
     *
     * <p>왜 catalog가 내주는가: 주문 금액은 서버가 다시 계산해야 하는데(클라이언트 금액 불신),
     * order 패키지는 goods 테이블에 직접 접근할 수 없다. 필요한 값만 이 인터페이스로 넘긴다.
     *
     * <p>숨김(HIDDEN) 상품과 상품-옵션 불일치는 <b>빈 값</b>으로 답한다. 예외를 던지지 않는 이유는
     * 호출자(주문)가 "여러 건 중 어느 것이 문제인지"를 모아서 판단해야 하기 때문이다.
     *
     * @param goodsNo  상품 번호
     * @param optionNo 옵션 번호. 옵션 없는 상품이면 null.
     * @return 주문 가능한 상품이면 스냅샷, 아니면 빈 값
     */
    Optional<OrderGoodsSnapshot> findOrderSnapshot(Long goodsNo, Long optionNo);

    /**
     * 주문 시점에 복사해 둘 상품 정보.
     *
     * @param unitPrice 1개 가격. 옵션 추가금이 이미 더해진 값이다 — 호출자가 다시 더하면 중복 계산이 된다.
     * @param stock     옵션 재고. 옵션이 없으면 {@link Integer#MAX_VALUE}(재고 관리 대상 아님).
     *                  이 웨이브는 재고를 <b>검증만</b> 하고 차감하지 않는다(차감은 Wave 3).
     */
    record OrderGoodsSnapshot(
            Long goodsId,
            Long optionId,
            String goodsName,
            String optionName,
            int unitPrice,
            int stock) {
    }
```

파일 상단에 `import java.util.Optional;`를 추가한다.

- [ ] **Step 4: 구현** — `catalog/GoodsService.java`의 `exists(...)` 아래에 추가

```java
    @Override
    @Transactional(readOnly = true)
    public Optional<OrderGoodsSnapshot> findOrderSnapshot(Long goodsNo, Long optionNo) {
        Optional<Goods> found = goodsRepository.findById(goodsNo)
                .filter(goods -> !Goods.STATUS_HIDDEN.equals(goods.getStatus()));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Goods goods = found.get();

        if (optionNo == null) {
            // 옵션이 없는 상품. 재고 관리 단위가 옵션이므로 상품 단위 재고는 무제한으로 본다.
            return Optional.of(new OrderGoodsSnapshot(
                    goods.getId(), null, goods.getName(), null, goods.getSalePrice(), Integer.MAX_VALUE));
        }

        // 옵션은 반드시 그 상품의 것이어야 한다. 남의 옵션을 붙이는 조작을 여기서 끊는다.
        return goods.getOptions().stream()
                .filter(option -> option.getId().equals(optionNo))
                .findFirst()
                .map(option -> new OrderGoodsSnapshot(
                        goods.getId(),
                        option.getId(),
                        goods.getName(),
                        option.getName(),
                        goods.getSalePrice() + option.getAddPrice(),
                        option.getStock()));
    }
```

파일 상단에 `import java.util.Optional;`를 추가한다.

> **확인:** `GoodsOption`의 접근자 이름이 `getName()`/`getAddPrice()`/`getStock()`이 맞는지
> `catalog/GoodsOption.java`를 열어 확인하고, 다르면 실제 이름에 맞춘다.

- [ ] **Step 5: green 확인**

Run: `./gradlew test --tests '*GoodsOrderSnapshotTest*'`
Expected: PASS (5 tests)

`옵션이_있으면...`에서 단가가 24000(추가금 미반영)이면 `getSalePrice() + option.getAddPrice()`를 확인한다.
`다른_상품의_옵션을...`이 값을 반환하면 옵션 소유 검사가 빠진 것이다 — **이건 실제 결제 금액 조작 경로다.**

- [ ] **Step 6: catalog 회귀 확인 + 커밋**

Run: `./gradlew test`
Expected: PASS (96 tests). 기존 catalog·ingredient 테스트가 그대로 통과해야 한다 —
`GoodsQueryService`에 메서드를 **추가만** 했으므로 `exists`를 쓰는 `ingredient`는 영향받지 않는다.

```bash
git add src/main/java/com/beautyboy/catalog src/test/java/com/beautyboy/catalog/GoodsOrderSnapshotTest.java
git commit -m "feat(catalog): 주문용 상품 스냅샷 조회 추가

order가 goods 테이블에 직접 접근하지 않고 가격·재고를 얻는 통로다.
옵션이 그 상품의 것인지 검사한다 — 남의 옵션을 붙이는 금액 조작 경로를 끊는다."
```

---

## Task 3: 장바구니 CRUD + 루틴 일괄 담기

**근거:** 설계 7장 인증 목록 — `GET|POST|PATCH|DELETE /cart/items` (+ `POST /cart/items/bulk` = 루틴 담기).

**Files:**
- Create: `backend/src/main/java/com/beautyboy/cart/CartItem.java`
- Create: `backend/src/main/java/com/beautyboy/cart/CartItemRepository.java`
- Create: `backend/src/main/java/com/beautyboy/cart/CartService.java`
- Create: `backend/src/main/java/com/beautyboy/cart/CartController.java`
- Create: `backend/src/main/java/com/beautyboy/cart/dto/CartAddRequest.java`
- Create: `backend/src/main/java/com/beautyboy/cart/dto/CartBulkAddRequest.java`
- Create: `backend/src/main/java/com/beautyboy/cart/dto/CartQuantityRequest.java`
- Create: `backend/src/main/java/com/beautyboy/cart/dto/CartItemResponse.java`
- Test: `backend/src/test/java/com/beautyboy/cart/CartApiTest.java`

**Interfaces:**
- Consumes: `catalog.GoodsQueryService.findOrderSnapshot(...)`(T2-2) · V30의 `cart_item`(T2-1).
- Produces: `CartService.itemsOf(Long memberId)` → `List<CartItemResponse>` — T2-4가 주문 생성 후 비우기에 쓴다.
- Produces: `CartService.clear(Long memberId)` — T2-4가 호출한다.

- [ ] **Step 1: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/cart/CartApiTest.java`

```java
package com.beautyboy.cart;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 장바구니 API 테스트.
 *
 * <p>인증은 SecurityContext에 memberId를 principal로 넣어 흉내낸다 —
 * JwtAuthenticationFilter가 그렇게 세팅하므로(@AuthenticationPrincipal Long memberId)
 * 실제 토큰을 만들지 않아도 컨트롤러가 보는 값은 같다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CartApiTest {

    private static final Long 회원 = 1L;
    private static final Long 다른회원 = 2L;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    @Test
    void 담기와_조회가_동작한다() throws Exception {
        Long goodsId = 상품_저장("토너", 16000);

        담기(회원, goodsId, 2).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/cart/items").with(로그인(회원)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].goodsName").value("토너"))
                .andExpect(jsonPath("$.data[0].quantity").value(2))
                // 가격은 저장하지 않고 조회 시점의 상품 판매가를 보여준다 —
                // 장바구니는 아직 구매가 아니므로 스냅샷을 뜰 시점이 아니다.
                .andExpect(jsonPath("$.data[0].unitPrice").value(16000));
    }

    @Test
    void 같은_상품을_또_담으면_수량이_합쳐진다() throws Exception {
        Long goodsId = 상품_저장("토너", 16000);

        담기(회원, goodsId, 2).andExpect(status().isCreated());
        담기(회원, goodsId, 3).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/cart/items").with(로그인(회원)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].quantity").value(5));
    }

    @Test
    void 수량이_0_이하면_400과_CART_QUANTITY_INVALID() throws Exception {
        Long goodsId = 상품_저장("토너", 16000);

        담기(회원, goodsId, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_QUANTITY_INVALID"));
    }

    @Test
    void 없는_상품을_담으면_404와_GOODS_NOT_FOUND() throws Exception {
        담기(회원, 999999L, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOODS_NOT_FOUND"));
    }

    @Test
    void 수량_변경이_동작한다() throws Exception {
        Long goodsId = 상품_저장("토너", 16000);
        담기(회원, goodsId, 1);
        Long cartItemId = 첫_장바구니_id(회원);

        mockMvc.perform(patch("/api/v1/cart/items/" + cartItemId)
                        .with(로그인(회원))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 4))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/cart/items").with(로그인(회원)))
                .andExpect(jsonPath("$.data[0].quantity").value(4));
    }

    @Test
    void 삭제가_동작한다() throws Exception {
        Long goodsId = 상품_저장("토너", 16000);
        담기(회원, goodsId, 1);
        Long cartItemId = 첫_장바구니_id(회원);

        mockMvc.perform(delete("/api/v1/cart/items/" + cartItemId).with(로그인(회원)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cart/items").with(로그인(회원)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 남의_장바구니_항목은_수정할_수_없다() throws Exception {
        // 여기가 뚫리면 남의 장바구니를 조작할 수 있다. id만 알면 되는 가장 흔한 취약점이다.
        Long goodsId = 상품_저장("토너", 16000);
        담기(회원, goodsId, 1);
        Long cartItemId = 첫_장바구니_id(회원);

        mockMvc.perform(patch("/api/v1/cart/items/" + cartItemId)
                        .with(로그인(다른회원))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 99))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
    }

    @Test
    void 루틴_일괄_담기가_여러_건을_한_번에_넣는다() throws Exception {
        Long 토너 = 상품_저장("토너", 16000);
        Long 크림 = 상품_저장("크림", 24000);

        String body = objectMapper.writeValueAsString(Map.of("items", java.util.List.of(
                Map.of("goodsNo", 토너, "quantity", 1),
                Map.of("goodsNo", 크림, "quantity", 2))));

        mockMvc.perform(post("/api/v1/cart/items/bulk")
                        .with(로그인(회원))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/cart/items").with(로그인(회원)))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/cart/items")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions 담기(Long memberId, Long goodsNo, int quantity)
            throws Exception {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("goodsNo", goodsNo);
        body.put("quantity", quantity);
        return mockMvc.perform(post("/api/v1/cart/items")
                .with(로그인(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Long 첫_장바구니_id(Long memberId) throws Exception {
        String json = mockMvc.perform(get("/api/v1/cart/items").with(로그인(memberId)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get(0).get("cartItemId").asLong();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor 로그인(Long memberId) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                memberId, null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Long 상품_저장(String name, int salePrice) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        return goodsRepository.save(
                new Goods(brand, "C001001001", name, null, "https://img/x.jpg", salePrice, salePrice)).getId();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*CartApiTest*'`
Expected: FAIL — 404/컴파일 에러. `CartController`가 없다.

- [ ] **Step 3: 엔티티 구현** — `cart/CartItem.java`

```java
package com.beautyboy.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 장바구니 항목.
 *
 * <p>가격을 저장하지 않는 이유: 장바구니는 아직 구매가 아니다. 가격을 여기 복사해 두면
 * 할인이 시작돼도 손님이 옛 가격을 보게 되고, 반대로 값이 오르면 옛 가격으로 결제하려 든다.
 * 스냅샷을 뜨는 시점은 <b>주문 생성</b>이다(설계 5장 order_item).
 *
 * <p>{@code memberId}/{@code goodsId}가 엔티티 참조가 아니라 스칼라인 이유:
 * member·catalog는 타 도메인이라 엔티티를 직접 참조할 수 없다(패키지 = 서비스 경계).
 * DB에는 FK가 있지만 그것은 데이터 정합성 장치이고, 코드 결합과는 별개다.
 */
@Entity
@Table(name = "cart_item")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    @Column(name = "option_id")
    private Long optionId;

    @Column(nullable = false)
    private int quantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CartItem() {
    }

    public CartItem(Long memberId, Long goodsId, Long optionId, int quantity) {
        this.memberId = memberId;
        this.goodsId = goodsId;
        this.optionId = optionId;
        this.quantity = quantity;
    }

    public void addQuantity(int amount) {
        this.quantity += amount;
    }

    public void changeQuantity(int quantity) {
        this.quantity = quantity;
    }

    /** 남의 항목을 조작하지 못하게 하는 소유 검사. 서비스가 수정·삭제 전에 반드시 부른다. */
    public boolean ownedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public Long getOptionId() {
        return optionId;
    }

    public int getQuantity() {
        return quantity;
    }
}
```

- [ ] **Step 4: 리포지토리 구현** — `cart/CartItemRepository.java`

```java
package com.beautyboy.cart;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByMemberIdOrderByIdAsc(Long memberId);

    /**
     * 같은 상품+옵션이 이미 담겨 있는지. optionId가 null인 경우를 Spring Data가
     * {@code option_id is null}로 풀어주므로 별도 분기가 필요 없다.
     */
    Optional<CartItem> findByMemberIdAndGoodsIdAndOptionId(Long memberId, Long goodsId, Long optionId);

    void deleteByMemberId(Long memberId);
}
```

- [ ] **Step 5: DTO 구현**

`cart/dto/CartAddRequest.java`:

```java
package com.beautyboy.cart.dto;

/**
 * 담기 요청.
 *
 * <p><b>가격 필드가 없는 것이 의도다.</b> 클라이언트가 금액을 보낼 수 있게 두면 언젠가 그 값을 쓰게 된다
 * (CLAUDE.md "돈과 재고는 서버"). 서버가 goodsNo로 가격을 다시 읽는다.
 */
public record CartAddRequest(Long goodsNo, Long optionNo, int quantity) {
}
```

`cart/dto/CartBulkAddRequest.java`:

```java
package com.beautyboy.cart.dto;

import java.util.List;

/** 루틴 전체 담기(설계 7장 {@code POST /cart/items/bulk}). 항목별로 담기와 같은 규칙을 적용한다. */
public record CartBulkAddRequest(List<CartAddRequest> items) {
}
```

`cart/dto/CartQuantityRequest.java`:

```java
package com.beautyboy.cart.dto;

public record CartQuantityRequest(int quantity) {
}
```

`cart/dto/CartItemResponse.java`:

```java
package com.beautyboy.cart.dto;

/**
 * 장바구니 표시용. 가격은 <b>조회 시점의 상품 판매가</b>다 —
 * 저장된 값이 아니라 매번 catalog에서 다시 읽는다.
 */
public record CartItemResponse(
        Long cartItemId,
        Long goodsNo,
        Long optionNo,
        String goodsName,
        String optionName,
        int unitPrice,
        int quantity,
        int lineAmount) {
}
```

- [ ] **Step 6: 서비스 구현** — `cart/CartService.java`

```java
package com.beautyboy.cart;

import com.beautyboy.cart.dto.CartAddRequest;
import com.beautyboy.cart.dto.CartItemResponse;
import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final GoodsQueryService goodsQueryService;

    public CartService(CartItemRepository cartItemRepository, GoodsQueryService goodsQueryService) {
        this.cartItemRepository = cartItemRepository;
        this.goodsQueryService = goodsQueryService;
    }

    @Transactional
    public void add(Long memberId, CartAddRequest request) {
        if (request.quantity() <= 0) {
            throw new BusinessException(ErrorCode.CART_QUANTITY_INVALID);
        }
        // 담는 시점에 존재·판매 가능 여부를 확인한다. 없는 상품이 장바구니에 남으면
        // 주문 단계에서야 실패해 손님이 결제 직전에 막힌다.
        goodsQueryService.findOrderSnapshot(request.goodsNo(), request.optionNo())
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));

        cartItemRepository
                .findByMemberIdAndGoodsIdAndOptionId(memberId, request.goodsNo(), request.optionNo())
                .ifPresentOrElse(
                        // 이미 있으면 더한다. 유니크 제약에 부딪히기 전에 애플리케이션이 먼저 처리한다.
                        existing -> existing.addQuantity(request.quantity()),
                        () -> cartItemRepository.save(new CartItem(
                                memberId, request.goodsNo(), request.optionNo(), request.quantity())));
    }

    @Transactional
    public void addAll(Long memberId, List<CartAddRequest> requests) {
        // 루틴 담기는 "전부 담기거나 전부 안 담기거나"여야 한다 —
        // 한 건이 품절이라 절반만 담기면 손님은 무엇이 빠졌는지 모른 채 결제로 간다.
        // @Transactional이 한 건 실패 시 전체를 되돌린다.
        for (CartAddRequest request : requests) {
            add(memberId, request);
        }
    }

    @Transactional(readOnly = true)
    public List<CartItemResponse> itemsOf(Long memberId) {
        List<CartItemResponse> responses = new ArrayList<>();
        for (CartItem item : cartItemRepository.findByMemberIdOrderByIdAsc(memberId)) {
            // 담은 뒤 숨겨진 상품은 목록에서 제외한다. 지우지는 않는다 —
            // 다시 판매되면 그대로 살아나는 편이 손님에게 자연스럽다.
            goodsQueryService.findOrderSnapshot(item.getGoodsId(), item.getOptionId())
                    .ifPresent(snapshot -> responses.add(new CartItemResponse(
                            item.getId(),
                            item.getGoodsId(),
                            item.getOptionId(),
                            snapshot.goodsName(),
                            snapshot.optionName(),
                            snapshot.unitPrice(),
                            item.getQuantity(),
                            snapshot.unitPrice() * item.getQuantity())));
        }
        return responses;
    }

    @Transactional
    public void changeQuantity(Long memberId, Long cartItemId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.CART_QUANTITY_INVALID);
        }
        내_항목(memberId, cartItemId).changeQuantity(quantity);
    }

    @Transactional
    public void remove(Long memberId, Long cartItemId) {
        cartItemRepository.delete(내_항목(memberId, cartItemId));
    }

    /** 주문이 성립하면 장바구니를 비운다(T2-4가 호출). */
    @Transactional
    public void clear(Long memberId) {
        cartItemRepository.deleteByMemberId(memberId);
    }

    /**
     * 소유 검사.
     *
     * <p>남의 항목에 403이 아니라 404를 주는 이유: 403은 "그 id는 존재한다"는 정보를 흘린다.
     * 존재 여부 자체를 숨기는 편이 안전하다.
     */
    private CartItem 내_항목(Long memberId, Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
                .filter(item -> item.ownedBy(memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
    }
}
```

- [ ] **Step 7: 컨트롤러 구현** — `cart/CartController.java`

```java
package com.beautyboy.cart;

import com.beautyboy.cart.dto.CartAddRequest;
import com.beautyboy.cart.dto.CartBulkAddRequest;
import com.beautyboy.cart.dto.CartItemResponse;
import com.beautyboy.cart.dto.CartQuantityRequest;
import com.beautyboy.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 전부 인증 대상이다 — SecurityConfig의 anyRequest().authenticated()에 걸리므로 설정 추가가 필요 없다. */
@RestController
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/api/v1/cart/items")
    public ResponseEntity<ApiResponse<List<CartItemResponse>>> items(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.itemsOf(memberId)));
    }

    @PostMapping("/api/v1/cart/items")
    public ResponseEntity<ApiResponse<Void>> add(@AuthenticationPrincipal Long memberId,
                                                 @RequestBody CartAddRequest request) {
        cartService.add(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null));
    }

    /** 루틴 전체 담기. 한 건이라도 실패하면 전부 되돌린다(CartService.addAll의 트랜잭션). */
    @PostMapping("/api/v1/cart/items/bulk")
    public ResponseEntity<ApiResponse<Void>> addAll(@AuthenticationPrincipal Long memberId,
                                                    @RequestBody CartBulkAddRequest request) {
        cartService.addAll(memberId, request.items());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null));
    }

    @PatchMapping("/api/v1/cart/items/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> changeQuantity(@AuthenticationPrincipal Long memberId,
                                                            @PathVariable Long cartItemId,
                                                            @RequestBody CartQuantityRequest request) {
        cartService.changeQuantity(memberId, cartItemId, request.quantity());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/api/v1/cart/items/{cartItemId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Long memberId, @PathVariable Long cartItemId) {
        cartService.remove(memberId, cartItemId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 8: green 확인**

Run: `./gradlew test --tests '*CartApiTest*'`
Expected: PASS (9 tests)

`남의_장바구니_항목은...`이 200이면 소유 검사가 빠진 것이다 — **가장 중요한 테스트다.**
`비로그인은_401이다`가 403이면 `SecurityConfig`가 아니라 인증 진입점 문제다. **설정 파일을 고치지 말고 보고한다.**

- [ ] **Step 9: 전체 회귀 + 커밋**

Run: `./gradlew test`
Expected: PASS (105 tests)

```bash
git add src/main/java/com/beautyboy/cart src/test/java/com/beautyboy/cart
git commit -m "feat(cart): 장바구니 CRUD + 루틴 일괄 담기

가격을 저장하지 않고 조회 시점에 catalog에서 다시 읽는다 —
장바구니는 아직 구매가 아니므로 스냅샷을 뜰 시점이 아니다."
```

---

## Task 4: 주문 생성 — 서버 재계산 + 스냅샷

**근거:** 설계 7장 결제 2단계 1항 — "`POST /orders`: 장바구니를 서버가 재계산(가격 위변조 차단),
'결제대기' 주문 + 결제 예정 금액 반환". CLAUDE.md "돈과 재고는 서버"의 핵심 지점이다.

**Files:**
- Create: `backend/src/main/java/com/beautyboy/order/Order.java`
- Create: `backend/src/main/java/com/beautyboy/order/OrderItem.java`
- Create: `backend/src/main/java/com/beautyboy/order/OrderRepository.java`
- Create: `backend/src/main/java/com/beautyboy/order/OrderService.java`
- Create: `backend/src/main/java/com/beautyboy/order/OrderController.java`
- Create: `backend/src/main/java/com/beautyboy/order/dto/OrderCreateRequest.java`
- Create: `backend/src/main/java/com/beautyboy/order/dto/OrderCreateResponse.java`
- Test: `backend/src/test/java/com/beautyboy/order/OrderCreateTest.java`

**Interfaces:**
- Consumes: `catalog.GoodsQueryService.findOrderSnapshot(...)`(T2-2) · `cart.CartService.clear(...)`(T2-3) ·
  `member.AddressService`(기존).
- Produces: `Order.STATUS_PENDING`/`STATUS_PAID` 상수 · `Order.markPaid(LocalDateTime)` ·
  `OrderRepository.findByOrderNo(String)` — **T2-6 결제가 전부 쓴다.**
- Produces: `POST /api/v1/orders` → `ApiResponse<OrderCreateResponse(orderNo, payableAmount)>`.
  프론트가 이 금액으로 토스 결제창을 연다.

- [ ] **Step 1: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/order/OrderCreateTest.java`

```java
package com.beautyboy.order;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.member.Member;
import com.beautyboy.member.MemberRepository;
import com.beautyboy.support.TestPersistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderCreateTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    OrderRepository orderRepository;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    void 서버가_다시_계산한_금액으로_주문이_생성된다() throws Exception {
        Long memberId = 회원_저장();
        Long goodsId = 상품_저장("토너", 16000);

        주문요청(memberId, List.of(Map.of("goodsNo", goodsId, "quantity", 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderNo").isNotEmpty())
                .andExpect(jsonPath("$.data.payableAmount").value(32000));
    }

    @Test
    void 클라이언트가_보낸_금액은_무시된다() throws Exception {
        // 요청 본문에 금액을 끼워 넣어도 서버 계산이 이긴다.
        // 이 테스트가 깨지면 가격 위변조가 가능하다는 뜻이다.
        Long memberId = 회원_저장();
        Long goodsId = 상품_저장("토너", 16000);

        String body = objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("goodsNo", goodsId, "quantity", 1, "unitPrice", 10)),
                "payableAmount", 10,
                "deliveryType", "NORMAL",
                "receiverName", "홍길동",
                "receiverPhone", "010-1234-5678",
                "zipcode", "06234",
                "address1", "서울시 강남구",
                "address2", "101호"));

        mockMvc.perform(post("/api/v1/orders")
                        .with(로그인(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.payableAmount").value(16000));
    }

    @Test
    void 주문_상품은_이름과_가격이_스냅샷으로_저장된다() throws Exception {
        Long memberId = 회원_저장();
        Long goodsId = 상품_저장("그린티 토너", 16000);

        주문요청(memberId, List.of(Map.of("goodsNo", goodsId, "quantity", 1)));

        TestPersistence.DB_왕복_강제(entityManager);

        Order order = orderRepository.findAll().get(0);
        OrderItem item = order.getItems().get(0);
        assertThat(item.getGoodsName()).isEqualTo("그린티 토너");
        assertThat(item.getUnitPrice()).isEqualTo(16000);
        assertThat(item.getLineAmount()).isEqualTo(16000);
    }

    @Test
    void 상품_가격이_바뀌어도_과거_주문서의_금액은_그대로다() throws Exception {
        // 스냅샷의 존재 이유 그 자체. 참조로 뒀다면 여기서 금액이 따라 바뀐다.
        Long memberId = 회원_저장();
        Long goodsId = 상품_저장("토너", 16000);
        주문요청(memberId, List.of(Map.of("goodsNo", goodsId, "quantity", 1)));

        TestPersistence.DB_왕복_강제(entityManager);

        Goods goods = goodsRepository.findById(goodsId).orElseThrow();
        entityManager.createNativeQuery("update goods set sale_price = 99000 where id = :id")
                .setParameter("id", goods.getId())
                .executeUpdate();

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(orderRepository.findAll().get(0).getPayableAmount()).isEqualTo(16000);
    }

    @Test
    void 초기_상태는_결제대기다() throws Exception {
        Long memberId = 회원_저장();
        Long goodsId = 상품_저장("토너", 16000);

        주문요청(memberId, List.of(Map.of("goodsNo", goodsId, "quantity", 1)));

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(orderRepository.findAll().get(0).getStatus()).isEqualTo(Order.STATUS_PENDING);
    }

    @Test
    void 주문번호는_서로_겹치지_않는다() throws Exception {
        Long memberId = 회원_저장();
        Long goodsId = 상품_저장("토너", 16000);

        주문요청(memberId, List.of(Map.of("goodsNo", goodsId, "quantity", 1)));
        주문요청(memberId, List.of(Map.of("goodsNo", goodsId, "quantity", 1)));

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(orderRepository.findAll()).extracting(Order::getOrderNo).doesNotHaveDuplicates();
    }

    @Test
    void 없는_상품이_섞이면_404이고_주문이_하나도_남지_않는다() throws Exception {
        Long memberId = 회원_저장();
        Long goodsId = 상품_저장("토너", 16000);

        주문요청(memberId, List.of(
                Map.of("goodsNo", goodsId, "quantity", 1),
                Map.of("goodsNo", 999999L, "quantity", 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOODS_NOT_FOUND"));

        TestPersistence.DB_왕복_강제(entityManager);

        // 부분 저장되면 결제할 수 없는 반쪽 주문이 남는다.
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void 주문_상품이_비어_있으면_400과_CART_EMPTY() throws Exception {
        Long memberId = 회원_저장();

        주문요청(memberId, List.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
    }

    @Test
    void 재고보다_많이_주문하면_409와_ORDER_OUT_OF_STOCK() throws Exception {
        Long memberId = 회원_저장();
        Long goodsId = 상품_저장("토너", 16000);
        Long optionId = 옵션_저장(goodsId, "50ml", 0, 2);

        주문요청(memberId, List.of(Map.of("goodsNo", goodsId, "optionNo", optionId, "quantity", 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_OUT_OF_STOCK"));
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions 주문요청(
            Long memberId, List<Map<String, Object>> items) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "items", items,
                "deliveryType", "NORMAL",
                "receiverName", "홍길동",
                "receiverPhone", "010-1234-5678",
                "zipcode", "06234",
                "address1", "서울시 강남구",
                "address2", "101호"));
        return mockMvc.perform(post("/api/v1/orders")
                .with(로그인(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor 로그인(Long memberId) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                memberId, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Long 회원_저장() {
        // Member 생성자 시그니처는 member/Member.java를 열어 실제에 맞춘다.
        // FK(orders.member_id → member.id) 때문에 실재하는 회원이 필요하다.
        Member member = memberRepository.save(
                new Member("buyer" + System.nanoTime() + "@beautyboy.dev", "encoded-password", "민수"));
        return member.getId();
    }

    private Long 상품_저장(String name, int salePrice) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        return goodsRepository.save(
                new Goods(brand, "C001001001", name, null, "https://img/x.jpg", salePrice, salePrice)).getId();
    }

    private Long 옵션_저장(Long goodsId, String name, int addPrice, int stock) {
        Goods goods = goodsRepository.findById(goodsId).orElseThrow();
        com.beautyboy.catalog.GoodsOption option =
                new com.beautyboy.catalog.GoodsOption(goods, name, addPrice, stock);
        goods.getOptions().add(option);
        goodsRepository.saveAndFlush(goods);
        return option.getId();
    }
}
```

> **먼저 확인할 것:** `Member`와 `GoodsOption`의 생성자 시그니처를 실제 파일에서 확인하고 헬퍼를 맞춘다.
> **테스트를 실제 코드에 맞춘다 — 엔티티를 테스트 편의로 바꾸지 않는다.**

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*OrderCreateTest*'`
Expected: FAIL — `Order` 등이 없어 컴파일 에러.

- [ ] **Step 3: 주문 엔티티 구현** — `order/Order.java`

```java
package com.beautyboy.order;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문. 배송지와 금액은 이 시점의 사실로 고정된다(스냅샷).
 *
 * <p>{@code payableAmount}가 결제 검증의 유일한 기준이다 — 토스가 승인했다고 알려온 금액이
 * 이 값과 다르면 승인을 취소한다(T2-6).
 */
@Entity
@Table(name = "orders")
public class Order {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PAID = "PAID";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 30, unique = true)
    private String orderNo;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @Column(name = "discount_amount", nullable = false)
    private int discountAmount;

    @Column(name = "payable_amount", nullable = false)
    private int payableAmount;

    @Column(name = "receiver_name", nullable = false, length = 50)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Column(nullable = false, length = 10)
    private String zipcode;

    @Column(nullable = false, length = 200)
    private String address1;

    @Column(length = 200)
    private String address2;

    @Column(name = "delivery_type", nullable = false, length = 20)
    private String deliveryType;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(String orderNo, Long memberId, String receiverName, String receiverPhone,
                 String zipcode, String address1, String address2, String deliveryType,
                 LocalDateTime orderedAt) {
        this.orderNo = orderNo;
        this.memberId = memberId;
        this.status = STATUS_PENDING;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.zipcode = zipcode;
        this.address1 = address1;
        this.address2 = address2;
        this.deliveryType = deliveryType;
        this.orderedAt = orderedAt;
        this.discountAmount = 0;
    }

    /** 항목을 추가하고 합계를 다시 계산한다. 금액을 밖에서 주입받지 않는 것이 핵심이다. */
    public void addItem(OrderItem item) {
        this.items.add(item);
        recalculate();
    }

    private void recalculate() {
        this.totalAmount = items.stream().mapToInt(OrderItem::getLineAmount).sum();
        // 1차에서 discountAmount는 항상 0이다. 쿠폰이 생기는 웨이브가 이 자리를 채운다.
        this.payableAmount = this.totalAmount - this.discountAmount;
    }

    /**
     * 결제 완료 전이.
     *
     * <p>결제대기가 아닌 주문에는 전이하지 않는다 — 이미 결제된 주문에 승인이 한 번 더 들어오면
     * 두 번 청구된다. 상태 검사가 그 이중 승인의 첫 방어선이다(두 번째는 payment의 유니크 제약).
     */
    public void markPaid(LocalDateTime paidAt) {
        if (!STATUS_PENDING.equals(this.status)) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
        this.status = STATUS_PAID;
        this.paidAt = paidAt;
    }

    public boolean ownedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getStatus() {
        return status;
    }

    public int getTotalAmount() {
        return totalAmount;
    }

    public int getDiscountAmount() {
        return discountAmount;
    }

    public int getPayableAmount() {
        return payableAmount;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public String getZipcode() {
        return zipcode;
    }

    public String getAddress1() {
        return address1;
    }

    public String getAddress2() {
        return address2;
    }

    public String getDeliveryType() {
        return deliveryType;
    }

    public LocalDateTime getOrderedAt() {
        return orderedAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }
}
```

- [ ] **Step 4: 주문 상품 엔티티 구현** — `order/OrderItem.java`

```java
package com.beautyboy.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 주문 상품. 이름·가격이 전부 복사본이다.
 *
 * <p>{@code goodsId}는 "무엇을 샀는지" 추적(랭킹 집계·재구매)용으로만 남긴다.
 * 화면 표시는 반드시 스냅샷 컬럼을 쓴다 — goods를 조인해 보여주면
 * 상품명이 바뀌었을 때 과거 주문서의 상품명이 따라 바뀐다.
 */
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    @Column(name = "option_id")
    private Long optionId;

    @Column(name = "goods_name", nullable = false, length = 200)
    private String goodsName;

    @Column(name = "option_name", length = 100)
    private String optionName;

    @Column(name = "unit_price", nullable = false)
    private int unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_amount", nullable = false)
    private int lineAmount;

    protected OrderItem() {
    }

    public OrderItem(Long goodsId, Long optionId, String goodsName, String optionName,
                     int unitPrice, int quantity) {
        this.goodsId = goodsId;
        this.optionId = optionId;
        this.goodsName = goodsName;
        this.optionName = optionName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        // 합계를 밖에서 받지 않고 여기서 곱한다. 밖에서 받으면 그것이 곧 조작 지점이 된다.
        this.lineAmount = unitPrice * quantity;
    }

    public Long getId() {
        return id;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public Long getOptionId() {
        return optionId;
    }

    public String getGoodsName() {
        return goodsName;
    }

    public String getOptionName() {
        return optionName;
    }

    public int getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getLineAmount() {
        return lineAmount;
    }
}
```

- [ ] **Step 5: 리포지토리 구현** — `order/OrderRepository.java`

```java
package com.beautyboy.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    List<Order> findByMemberIdOrderByOrderedAtDesc(Long memberId);

    boolean existsByOrderNo(String orderNo);

    /**
     * 결제 승인용 비관적 락 조회.
     *
     * <p>같은 주문에 승인 요청이 동시에 두 번 들어오면(사용자 더블클릭·재시도)
     * 둘 다 "결제대기"를 읽고 둘 다 승인 처리해 이중 청구가 된다.
     * 행을 잠가 한 번에 하나만 상태 전이를 시도하게 만든다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderNo = :orderNo")
    Optional<Order> findByOrderNoForUpdate(@Param("orderNo") String orderNo);
}
```

- [ ] **Step 6: 요청·응답 DTO 구현**

`order/dto/OrderCreateRequest.java`:

```java
package com.beautyboy.order.dto;

import java.util.List;

/**
 * 주문 생성 요청.
 *
 * <p><b>금액 필드가 없는 것이 의도다.</b> 클라이언트가 보낸 금액을 담을 자리를 만들지 않으면
 * 그 값을 쓰는 실수 자체가 불가능해진다. 요청 본문에 금액이 끼어 있어도 Jackson이 무시한다.
 *
 * <p>배송지를 addressId 참조가 아니라 값으로 받는 이유: 주문서에 스냅샷으로 남길 것이고,
 * 프론트가 새 주소를 즉석에서 입력하는 흐름도 지원해야 한다.
 */
public record OrderCreateRequest(
        List<OrderItemRequest> items,
        String receiverName,
        String receiverPhone,
        String zipcode,
        String address1,
        String address2,
        String deliveryType) {

    /** 무엇을 몇 개. 가격은 서버가 정한다. */
    public record OrderItemRequest(Long goodsNo, Long optionNo, int quantity) {
    }
}
```

`order/dto/OrderCreateResponse.java`:

```java
package com.beautyboy.order.dto;

/**
 * 주문 생성 결과. 프론트가 이 두 값으로 토스 결제창을 연다(설계 7장 결제 2단계 2항).
 * payableAmount는 서버가 계산한 값이며, 승인 검증의 기준이기도 하다.
 */
public record OrderCreateResponse(String orderNo, int payableAmount) {
}
```

- [ ] **Step 7: 서비스 구현** — `order/OrderService.java`

```java
package com.beautyboy.order;

import com.beautyboy.cart.CartService;
import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.order.dto.OrderCreateRequest;
import com.beautyboy.order.dto.OrderCreateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 주문 생성.
 *
 * <p>이 클래스의 존재 이유 한 줄: <b>클라이언트가 보낸 금액을 쓰지 않는다.</b>
 * goodsNo와 수량만 받아 가격을 catalog에서 다시 읽고, 합계를 서버가 계산한다(설계 7장 결제 2단계 1항).
 */
@Service
public class OrderService {

    private static final DateTimeFormatter ORDER_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ORDER_NO_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ORDER_NO_RANDOM_LENGTH = 8;
    private static final int ORDER_NO_MAX_ATTEMPTS = 5;

    private final OrderRepository orderRepository;
    private final GoodsQueryService goodsQueryService;
    private final CartService cartService;
    private final SecureRandom random = new SecureRandom();

    public OrderService(OrderRepository orderRepository,
                        GoodsQueryService goodsQueryService,
                        CartService cartService) {
        this.orderRepository = orderRepository;
        this.goodsQueryService = goodsQueryService;
        this.cartService = cartService;
    }

    @Transactional
    public OrderCreateResponse create(Long memberId, OrderCreateRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        Order order = new Order(
                주문번호_생성(),
                memberId,
                request.receiverName(),
                request.receiverPhone(),
                request.zipcode(),
                request.address1(),
                request.address2(),
                request.deliveryType(),
                LocalDateTime.now());

        for (OrderCreateRequest.OrderItemRequest item : request.items()) {
            if (item.quantity() <= 0) {
                throw new BusinessException(ErrorCode.CART_QUANTITY_INVALID);
            }

            // 가격을 여기서 다시 읽는다. 요청에 담긴 어떤 금액도 보지 않는다.
            GoodsQueryService.OrderGoodsSnapshot snapshot =
                    goodsQueryService.findOrderSnapshot(item.goodsNo(), item.optionNo())
                            .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));

            // 재고는 검증만 한다. 차감은 Wave 3(오늘드림 store_stock 조건부 차감)의 몫이며,
            // 여기서 깎으면 두 벌의 재고 개념이 생겨 정합성이 무너진다.
            if (snapshot.stock() < item.quantity()) {
                throw new BusinessException(ErrorCode.ORDER_OUT_OF_STOCK);
            }

            order.addItem(new OrderItem(
                    snapshot.goodsId(),
                    snapshot.optionId(),
                    snapshot.goodsName(),
                    snapshot.optionName(),
                    snapshot.unitPrice(),
                    item.quantity()));
        }

        Order saved = orderRepository.save(order);

        // 주문이 성립했으므로 장바구니를 비운다. 같은 트랜잭션이라 주문이 실패하면 장바구니도 그대로 남는다.
        cartService.clear(memberId);

        return new OrderCreateResponse(saved.getOrderNo(), saved.getPayableAmount());
    }

    /**
     * 주문번호 생성: {@code yyyyMMdd + 랜덤 8자}.
     *
     * <p>PK 연번을 노출하지 않는 이유: 총 주문 수가 새어나가고, 앞뒤 번호로 남의 주문을 찔러볼 수 있다.
     * 알파벳에서 {@code I O 0 1}을 뺀 것은 고객센터에서 번호를 불러줄 때 헷갈리기 때문이다.
     *
     * <p>중복 시 재시도하는 이유: 랜덤이라 충돌 확률이 극히 낮지만 0은 아니다.
     * DB 유니크 제약이 최종 방어선이고, 여기서 미리 피해 500을 줄인다.
     */
    private String 주문번호_생성() {
        for (int attempt = 0; attempt < ORDER_NO_MAX_ATTEMPTS; attempt++) {
            StringBuilder builder = new StringBuilder(LocalDateTime.now().format(ORDER_NO_DATE));
            for (int i = 0; i < ORDER_NO_RANDOM_LENGTH; i++) {
                builder.append(ORDER_NO_ALPHABET.charAt(random.nextInt(ORDER_NO_ALPHABET.length())));
            }
            String candidate = builder.toString();
            if (!orderRepository.existsByOrderNo(candidate)) {
                return candidate;
            }
        }
        // 5번 연속 충돌은 랜덤이 고장났다는 뜻이다. 조용히 넘어가면 원인을 못 찾는다.
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
```

- [ ] **Step 8: 컨트롤러 구현** — `order/OrderController.java`

```java
package com.beautyboy.order;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.order.dto.OrderCreateRequest;
import com.beautyboy.order.dto.OrderCreateResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/v1/orders")
    public ResponseEntity<ApiResponse<OrderCreateResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @RequestBody OrderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(orderService.create(memberId, request)));
    }
}
```

- [ ] **Step 9: green 확인**

Run: `./gradlew test --tests '*OrderCreateTest*'`
Expected: PASS (10 tests)

**`클라이언트가_보낸_금액은_무시된다`가 실패하면 즉시 멈춰라** — 가격 위변조가 가능하다는 뜻이다.
`없는_상품이_섞이면...`에서 주문이 남아 있으면 트랜잭션이 롤백되지 않은 것이다
(`BusinessException`은 `RuntimeException`이라 기본 롤백 대상이다 — `@Transactional`이 붙었는지 확인).

- [ ] **Step 10: 전체 회귀 + 커밋**

Run: `./gradlew test`
Expected: PASS (115 tests)

```bash
git add src/main/java/com/beautyboy/order src/test/java/com/beautyboy/order
git commit -m "feat(order): 주문 생성 — 서버 가격 재계산 + 스냅샷 저장

요청 DTO에 금액 필드를 두지 않는다. 담을 자리가 없으면 쓰는 실수도 불가능하다.
재고는 검증만 하고 차감하지 않는다 — 차감은 Wave 3 오늘드림의 몫이다."
```

---

## Task 5: 결제사 호출 경계 — PaymentGateway 인터페이스 + 토스 구현

> **모델: opus** (CLAUDE.md 결제 2단계 예외)

**근거:** 설계 7장 결제 2단계 3항 — "서버가 토스 승인 API 호출". 외부 호출을 인터페이스 뒤에 두는 이유:
유닛테스트가 실제 토스를 때리면 네트워크·키에 의존해 터미널 병렬 안전이 깨지고(로드맵 §5), 실패 케이스
(금액 불일치 → 취소 호출)를 재현할 수 없다. 가짜 게이트웨이로 모든 분기를 테스트한다.

**Files:**
- Create: `backend/src/main/java/com/beautyboy/payment/PaymentGateway.java`
- Create: `backend/src/main/java/com/beautyboy/payment/TossPaymentGateway.java`
- Create: `backend/src/main/java/com/beautyboy/payment/dto/PaymentApproval.java`
- Create: `backend/src/main/java/com/beautyboy/config/TossProperties.java`
- Test: `backend/src/test/java/com/beautyboy/payment/TossPaymentGatewayTest.java`

**Interfaces:**
- Produces: `PaymentGateway.confirm(String paymentKey, String orderNo, int amount)` → `PaymentApproval` ·
  `PaymentGateway.cancel(String paymentKey, String reason)`. T2-6이 소비한다.
- Produces: `PaymentApproval(String paymentKey, int approvedAmount, String status, String rawJson)`.
- Produces: `PaymentGatewayException`(런타임) — 토스 호출 자체가 실패했을 때. `ErrorCode.PAYMENT_GATEWAY_FAILED`로 매핑.

- [ ] **Step 1: 토스 설정 프로퍼티** — `config/TossProperties.java`

```java
package com.beautyboy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토스페이먼츠 연동 설정.
 *
 * <p>시크릿 키의 기본값을 코드에 적지 않는다 — 적는 순간 그것이 커밋된 시크릿이다.
 * 환경변수 {@code TOSS_SECRET_KEY}로만 주입하고, 없으면 앱이 뜨지 않게 둔다(뜨더라도 결제에서 실패한다).
 *
 * <p>baseUrl은 테스트 결제 서버 주소라 소스에 둬도 시크릿이 아니다.
 */
@ConfigurationProperties(prefix = "toss")
public record TossProperties(String secretKey, String baseUrl) {

    public TossProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.tosspayments.com";
        }
    }
}
```

`application.yml`의 **맨 끝**에 아래를 추가한다(T1이 만지는 `spring.profiles` 근처가 아니라 최상위 `toss:` 블록이라
라인이 겹치지 않는다 — Global Constraints의 충돌 회피):

```yaml

toss:
  secret-key: ${TOSS_SECRET_KEY:}
  base-url: ${TOSS_BASE_URL:https://api.tosspayments.com}
```

`BeautyboyApplication.java`에 `@ConfigurationPropertiesScan`이 없으면 붙인다(있으면 그대로 둔다).
없고 붙이기 애매하면 `TossProperties`에 `@Component` 대신 설정 클래스에서 `@EnableConfigurationProperties(TossProperties.class)`를
쓴다 — **`BeautyboyApplication.java`가 공유 계약이 아니면** 여기에 `@ConfigurationPropertiesScan`을 붙이는 쪽이 단순하다.
(이 파일은 동결 목록에 없다. 애노테이션 1개 추가는 안전하다.)

- [ ] **Step 2: 승인 결과 DTO + 게이트웨이 인터페이스**

`payment/dto/PaymentApproval.java`:

```java
package com.beautyboy.payment.dto;

/**
 * 토스 승인 응답에서 우리가 쓰는 값만 추린 것.
 *
 * @param approvedAmount 토스가 "이만큼 승인했다"고 알려준 금액. 우리 주문의 payableAmount와 대조할 대상이다.
 * @param rawJson        응답 원문. payment.raw_response에 그대로 저장한다 — 분쟁 시 근거는 우리 해석이 아니라 원문이다.
 */
public record PaymentApproval(String paymentKey, int approvedAmount, String status, String rawJson) {
}
```

`payment/PaymentGateway.java`:

```java
package com.beautyboy.payment;

import com.beautyboy.payment.dto.PaymentApproval;

/**
 * 결제사 호출 경계.
 *
 * <p>외부 호출을 인터페이스로 가른 이유가 둘이다.
 * (1) 유닛테스트가 네트워크·시크릿 없이 돈다(로드맵 §5 터미널 병렬 안전).
 * (2) 금액 불일치 → 취소 호출 같은 실패 경로를 가짜 구현으로 결정적으로 재현할 수 있다.
 *
 * <p>구현이 던지는 {@link PaymentGatewayException}은 "토스와 통신 자체가 실패"를 뜻한다 —
 * 금액 불일치는 여기서 판단하지 않는다(그건 우리 도메인 규칙이라 PaymentService의 몫이다).
 */
public interface PaymentGateway {

    /**
     * 승인 요청. 토스가 성공을 반환하면 그 시점에 실제로 돈이 움직인다.
     *
     * @param amount 우리가 승인을 요청하는 금액. 토스는 결제창에서 확정된 금액과 이 값이 다르면 거부한다.
     * @return 승인 결과. 금액 검증은 호출자가 한다.
     * @throws PaymentGatewayException 토스 호출이 4xx/5xx이거나 네트워크가 끊긴 경우
     */
    PaymentApproval confirm(String paymentKey, String orderNo, int amount);

    /**
     * 승인 취소. 우리 검증(금액 대조)이 실패했을 때, 이미 승인된 결제를 되돌린다.
     * 취소마저 실패하면 예외가 오르지만, 그때는 이미 승인 취소가 필요하다는 사실이 로그에 남아야 한다.
     */
    void cancel(String paymentKey, String reason);
}
```

`payment/PaymentGatewayException.java`:

```java
package com.beautyboy.payment;

/** 토스 호출 자체의 실패(통신·4xx·5xx). 도메인 규칙 위반(금액 불일치)과 구분한다. */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 3: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/payment/TossPaymentGatewayTest.java`

`RestClient`를 `MockRestServiceServer`로 감싸 실제 HTTP 없이 토스 계약을 검증한다.

```java
package com.beautyboy.payment;

import com.beautyboy.config.TossProperties;
import com.beautyboy.payment.dto.PaymentApproval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * 토스 게이트웨이 단위 테스트. 실제 토스를 부르지 않는다 —
 * MockRestServiceServer가 RestClient의 요청을 가로채 우리가 정한 응답을 돌려준다.
 * 검증 대상은 "우리가 토스에 올바른 형식으로 요청하고, 응답을 올바르게 해석하는가"다.
 */
class TossPaymentGatewayTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private TossPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new TossPaymentGateway(
                builder, new TossProperties("test_sk_secret", "https://api.tosspayments.com"));
    }

    @Test
    void 승인_요청은_Basic_인증과_paymentKey_orderId_amount를_담는다() {
        // 토스는 시크릿 키를 "키:" 형태로 Base64 인코딩한 Basic 인증을 요구한다(콜론 뒤 비밀번호 없음).
        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString("test_sk_secret:".getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", expectedAuth))
                .andExpect(jsonPath("$.paymentKey").value("pk_123"))
                .andExpect(jsonPath("$.orderId").value("ORD-1"))
                .andExpect(jsonPath("$.amount").value(16000))
                .andRespond(withSuccess("""
                        {"paymentKey":"pk_123","orderId":"ORD-1","totalAmount":16000,"status":"DONE"}
                        """, MediaType.APPLICATION_JSON));

        PaymentApproval approval = gateway.confirm("pk_123", "ORD-1", 16000);

        assertThat(approval.paymentKey()).isEqualTo("pk_123");
        assertThat(approval.approvedAmount()).isEqualTo(16000);
        assertThat(approval.status()).isEqualTo("DONE");
        // 원문을 그대로 보관하는지 — 분쟁 대비.
        assertThat(approval.rawJson()).contains("\"totalAmount\":16000");
        server.verify();
    }

    @Test
    void 토스가_4xx면_PaymentGatewayException() {
        // 이미 취소된 결제·잘못된 키 등. 우리 도메인 예외가 아니라 게이트웨이 예외로 올린다.
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("""
                                {"code":"ALREADY_PROCESSED_PAYMENT","message":"이미 처리된 결제 입니다."}
                                """)
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.confirm("pk_x", "ORD-1", 16000))
                .isInstanceOf(PaymentGatewayException.class);
        server.verify();
    }

    @Test
    void 취소_요청은_cancelReason을_담아_취소_엔드포인트로_간다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/pk_123/cancel"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.cancelReason").value("금액 불일치"))
                .andRespond(withSuccess("{\"status\":\"CANCELED\"}", MediaType.APPLICATION_JSON));

        gateway.cancel("pk_123", "금액 불일치");

        server.verify();
    }
}
```

- [ ] **Step 4: 실패 확인**

Run: `./gradlew test --tests '*TossPaymentGatewayTest*'`
Expected: FAIL — `TossPaymentGateway`가 없어 컴파일 에러.

- [ ] **Step 5: 토스 구현** — `payment/TossPaymentGateway.java`

```java
package com.beautyboy.payment;

import com.beautyboy.config.TossProperties;
import com.beautyboy.payment.dto.PaymentApproval;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 토스페이먼츠 승인·취소 구현.
 *
 * <p>{@code RestClient}는 spring-boot-starter-web에 이미 들어 있어 새 의존성이 없다(빌드 파일 동결 준수).
 *
 * <p>승인 응답의 {@code totalAmount}를 우리가 요청한 amount와 대조하지 않는다 —
 * 그 검증은 PaymentService가 우리 주문의 payableAmount로 한다. 게이트웨이는 통신만 책임진다.
 */
@Component
public class TossPaymentGateway implements PaymentGateway {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TossPaymentGateway(RestClient.Builder builder, TossProperties properties) {
        // 토스 Basic 인증: "시크릿키:"를 Base64로. 콜론 뒤 비밀번호는 비운다.
        String basic = Base64.getEncoder()
                .encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Basic " + basic)
                .build();
    }

    @Override
    public PaymentApproval confirm(String paymentKey, String orderNo, int amount) {
        try {
            String body = restClient.post()
                    .uri("/v1/payments/confirm")
                    .body(Map.of("paymentKey", paymentKey, "orderId", orderNo, "amount", amount))
                    .retrieve()
                    .body(String.class);

            JsonNode json = objectMapper.readTree(body);
            return new PaymentApproval(
                    json.path("paymentKey").asText(),
                    json.path("totalAmount").asInt(),
                    json.path("status").asText(),
                    body);
        } catch (RestClientResponseException e) {
            // 토스가 4xx/5xx를 준 경우. 원문 메시지를 담아 올린다.
            throw new PaymentGatewayException(
                    "토스 승인 실패: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new PaymentGatewayException("토스 승인 응답 처리 실패", e);
        }
    }

    @Override
    public void cancel(String paymentKey, String reason) {
        try {
            restClient.post()
                    .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                    .body(Map.of("cancelReason", reason))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // 취소 실패는 심각하다(승인은 됐는데 되돌리지 못함). 예외를 삼키지 않고 올려 로그·후속 처리로 남긴다.
            throw new PaymentGatewayException("토스 승인 취소 실패: paymentKey=" + paymentKey, e);
        }
    }
}
```

- [ ] **Step 6: green 확인**

Run: `./gradlew test --tests '*TossPaymentGatewayTest*'`
Expected: PASS (3 tests)

`승인_요청은_Basic_인증...`이 인증 헤더 불일치로 실패하면, 토스 Basic 인증이 "시크릿키 뒤에 콜론"인지
다시 확인한다(콜론을 빼먹으면 실 결제에서 401이 난다).

- [ ] **Step 7: 전체 회귀 + 커밋**

Run: `./gradlew test`
Expected: PASS (118 tests)

```bash
git add src/main/java/com/beautyboy/payment src/main/java/com/beautyboy/config/TossProperties.java \
        src/main/resources/application.yml src/main/java/com/beautyboy/BeautyboyApplication.java \
        src/test/java/com/beautyboy/payment/TossPaymentGatewayTest.java
git commit -m "feat(payment): 결제사 호출 경계 — PaymentGateway 인터페이스 + 토스 구현

외부 호출을 인터페이스 뒤에 둬 유닛테스트가 네트워크 없이 돌고,
금액 불일치 → 취소 같은 실패 경로를 가짜로 재현할 수 있다.
시크릿 키는 환경변수로만 주입한다 — 소스에 기본값을 적지 않는다."
```

---

## Task 6: 결제 승인 2단계 검증 — `POST /payments/confirm`

> **모델: opus** (CLAUDE.md 결제 2단계 예외 — 판단이 갈리는 핵심 로직)

**근거:** 설계 7장 결제 2단계 3항 — "서버가 토스 승인 API 호출, **금액 일치 검증** 후 결제완료 전환.
검증 실패 시 토스 취소 API 호출 후 주문 실패 처리." 이 계획 전체에서 가장 중요한 태스크다.

**Files:**
- Create: `backend/src/main/java/com/beautyboy/payment/Payment.java`
- Create: `backend/src/main/java/com/beautyboy/payment/PaymentRepository.java`
- Create: `backend/src/main/java/com/beautyboy/payment/PaymentService.java`
- Create: `backend/src/main/java/com/beautyboy/payment/PaymentController.java`
- Create: `backend/src/main/java/com/beautyboy/payment/dto/PaymentConfirmRequest.java`
- Create: `backend/src/main/java/com/beautyboy/payment/dto/PaymentConfirmResponse.java`
- Test: `backend/src/test/java/com/beautyboy/payment/PaymentConfirmTest.java`

**Interfaces:**
- Consumes: `PaymentGateway`(T2-5) · `order.OrderRepository.findByOrderNoForUpdate(...)` ·
  `Order.markPaid(...)` · `Order.getPayableAmount()`(T2-4).
- Produces: `POST /api/v1/payments/confirm` → `ApiResponse<PaymentConfirmResponse>`.

- [ ] **Step 1: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/payment/PaymentConfirmTest.java`

가짜 게이트웨이를 주입해 승인/불일치/취소 분기를 전부 검증한다.

```java
package com.beautyboy.payment;

import com.beautyboy.order.Order;
import com.beautyboy.order.OrderRepository;
import com.beautyboy.payment.dto.PaymentApproval;
import com.beautyboy.support.TestPersistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentConfirmTest {

    private static final Long 회원 = 1L;

    /**
     * 가짜 게이트웨이. 토스를 부르지 않고, 우리가 지정한 승인 금액을 돌려주고 취소 호출을 기록한다.
     * approvedAmount를 테스트마다 바꿔 "토스가 알려준 금액"을 조작한다 —
     * 이것이 금액 불일치 검증의 대상이다.
     */
    @TestConfiguration
    static class 가짜_게이트웨이_설정 {
        static int approvedAmount;
        static final List<String> canceledKeys = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        PaymentGateway fakeGateway() {
            return new PaymentGateway() {
                @Override
                public PaymentApproval confirm(String paymentKey, String orderNo, int amount) {
                    return new PaymentApproval(paymentKey, approvedAmount, "DONE", "{\"raw\":true}");
                }

                @Override
                public void cancel(String paymentKey, String reason) {
                    canceledKeys.add(paymentKey);
                }
            };
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    PaymentRepository paymentRepository;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    void 금액이_일치하면_결제완료로_전이하고_payment를_남긴다() throws Exception {
        가짜_게이트웨이_설정.canceledKeys.clear();
        가짜_게이트웨이_설정.approvedAmount = 16000;
        Order order = 결제대기_주문_저장(16000);

        승인요청(order.getOrderNo(), "pk_ok", 16000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(orderRepository.findByOrderNo(order.getOrderNo()).orElseThrow().getStatus())
                .isEqualTo(Order.STATUS_PAID);
        assertThat(paymentRepository.findAll()).hasSize(1);
        assertThat(가짜_게이트웨이_설정.canceledKeys).isEmpty();
    }

    @Test
    void 토스_승인액이_주문액과_다르면_취소를_부르고_409다() throws Exception {
        // 핵심 시나리오. 결제창에서 금액을 조작해 싸게 결제한 경우, 토스가 그 조작된 금액을 승인해 돌려준다.
        // 우리 주문의 payableAmount(16000)와 다르므로 승인을 취소하고 주문을 실패시킨다.
        가짜_게이트웨이_설정.canceledKeys.clear();
        가짜_게이트웨이_설정.approvedAmount = 10;          // 토스가 알려온 승인액(조작됨)
        Order order = 결제대기_주문_저장(16000);            // 우리가 계산한 진짜 금액

        승인요청(order.getOrderNo(), "pk_tampered", 10)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));

        TestPersistence.DB_왕복_강제(entityManager);

        // 승인을 반드시 취소했어야 한다 — 안 하면 돈은 빠져나갔는데 주문은 실패로 남는다.
        assertThat(가짜_게이트웨이_설정.canceledKeys).containsExactly("pk_tampered");
        // 주문은 결제대기로 남고, payment는 저장하지 않는다.
        assertThat(orderRepository.findByOrderNo(order.getOrderNo()).orElseThrow().getStatus())
                .isEqualTo(Order.STATUS_PENDING);
        assertThat(paymentRepository.findAll()).isEmpty();
    }

    @Test
    void 이미_결제된_주문에_다시_승인하면_409고_두_번_청구되지_않는다() throws Exception {
        가짜_게이트웨이_설정.canceledKeys.clear();
        가짜_게이트웨이_설정.approvedAmount = 16000;
        Order order = 결제대기_주문_저장(16000);

        승인요청(order.getOrderNo(), "pk_1", 16000).andExpect(status().isOk());

        TestPersistence.DB_왕복_강제(entityManager);

        // 두 번째 승인. 상태가 이미 PAID라 거부해야 한다.
        승인요청(order.getOrderNo(), "pk_2", 16000)
                .andExpect(status().isConflict());

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(paymentRepository.findAll()).hasSize(1);
    }

    @Test
    void 없는_주문번호면_404다() throws Exception {
        가짜_게이트웨이_설정.approvedAmount = 16000;

        승인요청("ORD-NONE", "pk_x", 16000)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void 남의_주문을_결제하려_하면_404다() throws Exception {
        // 주문번호만 알면 남의 결제를 확정시킬 수 있으면 안 된다.
        가짜_게이트웨이_설정.approvedAmount = 16000;
        Order order = 결제대기_주문_저장(16000);   // 회원(1)의 주문

        String body = objectMapper.writeValueAsString(Map.of(
                "orderNo", order.getOrderNo(), "paymentKey", "pk_x", "amount", 16000));
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .with(authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                999L, null,
                                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions 승인요청(
            String orderNo, String paymentKey, int amount) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "orderNo", orderNo, "paymentKey", paymentKey, "amount", amount));
        return mockMvc.perform(post("/api/v1/payments/confirm")
                .with(authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        회원, null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** payableAmount를 원하는 값으로 만들기 위해 단가=payable, 수량 1짜리 주문을 직접 만든다. */
    private Order 결제대기_주문_저장(int payableAmount) {
        Order order = new Order("ORD-" + System.nanoTime(), 회원, "홍길동", "010-0000-0000",
                "06234", "서울시 강남구", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new com.beautyboy.order.OrderItem(
                1L, null, "토너", null, payableAmount, 1));
        return orderRepository.saveAndFlush(order);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*PaymentConfirmTest*'`
Expected: FAIL — `Payment` 등이 없어 컴파일 에러.

- [ ] **Step 3: 결제 엔티티 구현** — `payment/Payment.java`

```java
package com.beautyboy.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 결제 기록. 검증을 통과해 확정된 승인만 저장한다 —
 * 금액 불일치로 취소한 건은 payment 행을 남기지 않는다(주문은 결제대기로 되돌아간다).
 *
 * <p>order_id 유니크 제약(V32)이 이중 승인의 DB 차원 마지막 방어선이다.
 */
@Entity
@Table(name = "payment")
public class Payment {

    public static final String STATUS_APPROVED = "APPROVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_key", nullable = false, length = 200)
    private String paymentKey;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 20)
    private String status;

    @Lob
    @Column(name = "raw_response", nullable = false)
    private String rawResponse;

    @Column(name = "approved_at", nullable = false)
    private LocalDateTime approvedAt;

    protected Payment() {
    }

    public Payment(Long orderId, String paymentKey, int amount, String rawResponse, LocalDateTime approvedAt) {
        this.orderId = orderId;
        this.paymentKey = paymentKey;
        this.amount = amount;
        this.status = STATUS_APPROVED;
        this.rawResponse = rawResponse;
        this.approvedAt = approvedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getPaymentKey() {
        return paymentKey;
    }

    public int getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }
}
```

- [ ] **Step 4: 리포지토리 + 요청·응답 DTO**

`payment/PaymentRepository.java`:

```java
package com.beautyboy.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByOrderId(Long orderId);
}
```

`payment/dto/PaymentConfirmRequest.java`:

```java
package com.beautyboy.payment.dto;

/**
 * 결제 승인 요청.
 *
 * <p>{@code amount}가 있지만 이것은 <b>토스에 전달할 값</b>이지 우리가 신뢰하는 값이 아니다.
 * 최종 판정은 이 amount가 아니라 우리 주문의 payableAmount로 한다 —
 * amount와 payableAmount가 다르면 그 자체가 조작 신호다.
 */
public record PaymentConfirmRequest(String orderNo, String paymentKey, int amount) {
}
```

`payment/dto/PaymentConfirmResponse.java`:

```java
package com.beautyboy.payment.dto;

public record PaymentConfirmResponse(String orderNo, String status, int paidAmount) {
}
```

- [ ] **Step 5: 서비스 구현 (핵심)** — `payment/PaymentService.java`

```java
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
```

> **취소가 실패하면?** `paymentGateway.cancel`이 `PaymentGatewayException`을 던지면 `@Transactional`이
> 롤백돼 주문은 결제대기로 남는다. 승인은 됐는데 취소가 안 된 상태이므로 로그에 남고 수동 대사가 필요하다 —
> 이 한계는 Task 9 보고서에 명시한다(1차 범위에서 재시도 큐는 만들지 않는다).

- [ ] **Step 6: 컨트롤러 구현** — `payment/PaymentController.java`

```java
package com.beautyboy.payment;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.payment.dto.PaymentConfirmRequest;
import com.beautyboy.payment.dto.PaymentConfirmResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/v1/payments/confirm")
    public ResponseEntity<ApiResponse<PaymentConfirmResponse>> confirm(
            @AuthenticationPrincipal Long memberId,
            @RequestBody PaymentConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.confirm(memberId, request)));
    }
}
```

- [ ] **Step 7: green 확인**

Run: `./gradlew test --tests '*PaymentConfirmTest*'`
Expected: PASS (6 tests)

**`토스_승인액이_주문액과_다르면_취소를_부르고_409다`가 실패하면 즉시 멈춰라** — 결제 검증의 핵심이다.
취소가 안 불렸으면 (4)의 순서를, 주문이 PAID로 남으면 예외 후 롤백을 확인한다.
`이미_결제된_주문에...`에서 payment가 2건이면 상태 검사(2)나 락(1)이 빠진 것이다.

- [ ] **Step 8: 전체 회귀 + 커밋**

Run: `./gradlew test`
Expected: PASS (124 tests)

```bash
git add src/main/java/com/beautyboy/payment src/test/java/com/beautyboy/payment/PaymentConfirmTest.java
git commit -m "feat(payment): 결제 승인 2단계 검증 — 금액 불일치 시 승인 취소 후 실패

우리가 계산한 payableAmount가 유일한 진실이다. 토스가 알려온 승인액이
그와 다르면 조작이므로 승인을 취소하고 주문을 결제대기로 되돌린다.
락 + 상태 검사 + payment 유니크로 이중 청구를 3중 방어한다."
```

---

## Task 7: 주문 조회 — `GET /orders` · `GET /orders/{orderNo}`

**근거:** 설계 7장 인증 목록 — `GET /orders, /orders/{orderNo}`.

**Files:**
- Modify: `backend/src/main/java/com/beautyboy/order/OrderController.java`
- Modify: `backend/src/main/java/com/beautyboy/order/OrderService.java`
- Create: `backend/src/main/java/com/beautyboy/order/dto/OrderSummaryResponse.java`
- Create: `backend/src/main/java/com/beautyboy/order/dto/OrderDetailResponse.java`
- Test: `backend/src/test/java/com/beautyboy/order/OrderQueryTest.java`

**Interfaces:**
- Consumes: `OrderRepository.findByMemberIdOrderByOrderedAtDesc(...)` · `findByOrderNo(...)`(T2-4).
- Produces: `GET /api/v1/orders` → `ApiResponse<List<OrderSummaryResponse>>` ·
  `GET /api/v1/orders/{orderNo}` → `ApiResponse<OrderDetailResponse>`.

- [ ] **Step 1: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/order/OrderQueryTest.java`

```java
package com.beautyboy.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderQueryTest {

    private static final Long 회원 = 1L;
    private static final Long 다른회원 = 2L;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    OrderRepository orderRepository;

    @Test
    void 내_주문_목록을_최신순으로_준다() throws Exception {
        주문_저장(회원, "토너");
        주문_저장(회원, "크림");

        mockMvc.perform(get("/api/v1/orders").with(로그인(회원)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void 남의_주문은_목록에_섞이지_않는다() throws Exception {
        주문_저장(회원, "내 토너");
        주문_저장(다른회원, "남의 크림");

        mockMvc.perform(get("/api/v1/orders").with(로그인(회원)))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void 주문_상세를_상품_목록과_함께_준다() throws Exception {
        String orderNo = 주문_저장(회원, "그린티 토너").getOrderNo();

        mockMvc.perform(get("/api/v1/orders/" + orderNo).with(로그인(회원)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.items[0].goodsName").value("그린티 토너"));
    }

    @Test
    void 남의_주문_상세는_404다() throws Exception {
        String orderNo = 주문_저장(회원, "토너").getOrderNo();

        mockMvc.perform(get("/api/v1/orders/" + orderNo).with(로그인(다른회원)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor 로그인(Long memberId) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                memberId, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Order 주문_저장(Long memberId, String goodsName) {
        Order order = new Order("ORD-" + System.nanoTime(), memberId, "홍길동", "010-0000-0000",
                "06234", "서울시", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(1L, null, goodsName, null, 16000, 1));
        return orderRepository.saveAndFlush(order);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*OrderQueryTest*'`
Expected: FAIL — 404 (핸들러 없음)

- [ ] **Step 3: 응답 DTO 구현**

`order/dto/OrderSummaryResponse.java`:

```java
package com.beautyboy.order.dto;

import java.time.LocalDateTime;

/** 주문 목록 1행. 대표 상품명 + 나머지 개수로 "그린티 토너 외 2건"을 프론트가 조립한다. */
public record OrderSummaryResponse(
        String orderNo,
        String status,
        String representativeGoodsName,
        int itemCount,
        int payableAmount,
        LocalDateTime orderedAt) {
}
```

`order/dto/OrderDetailResponse.java`:

```java
package com.beautyboy.order.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 주문 상세. 배송지·금액·상품이 전부 주문 시점 스냅샷이다(현재 상품/회원 정보를 조인하지 않는다). */
public record OrderDetailResponse(
        String orderNo,
        String status,
        int totalAmount,
        int discountAmount,
        int payableAmount,
        String receiverName,
        String receiverPhone,
        String zipcode,
        String address1,
        String address2,
        String deliveryType,
        LocalDateTime orderedAt,
        LocalDateTime paidAt,
        List<OrderItemResponse> items) {

    public record OrderItemResponse(
            String goodsName,
            String optionName,
            int unitPrice,
            int quantity,
            int lineAmount) {
    }
}
```

- [ ] **Step 4: 서비스에 조회 메서드 추가** — `OrderService`의 `create(...)` 아래에 삽입

```java
    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> ordersOf(Long memberId) {
        return orderRepository.findByMemberIdOrderByOrderedAtDesc(memberId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse orderDetail(Long memberId, String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .filter(o -> o.ownedBy(memberId))   // 남의 주문은 존재를 숨긴다.
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return toDetail(order);
    }

    private OrderSummaryResponse toSummary(Order order) {
        List<OrderItem> items = order.getItems();
        String representative = items.isEmpty() ? "" : items.get(0).getGoodsName();
        return new OrderSummaryResponse(
                order.getOrderNo(),
                order.getStatus(),
                representative,
                items.size(),
                order.getPayableAmount(),
                order.getOrderedAt());
    }

    private OrderDetailResponse toDetail(Order order) {
        List<OrderDetailResponse.OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderDetailResponse.OrderItemResponse(
                        item.getGoodsName(),
                        item.getOptionName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getLineAmount()))
                .toList();

        return new OrderDetailResponse(
                order.getOrderNo(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getPayableAmount(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getZipcode(),
                order.getAddress1(),
                order.getAddress2(),
                order.getDeliveryType(),
                order.getOrderedAt(),
                order.getPaidAt(),
                items);
    }
```

상단에 import를 추가한다: `com.beautyboy.order.dto.OrderSummaryResponse`, `OrderDetailResponse`, `java.util.List`.

- [ ] **Step 5: 컨트롤러에 핸들러 추가** — `OrderController`의 `create(...)` 아래에 삽입

```java
    @GetMapping("/api/v1/orders")
    public ResponseEntity<ApiResponse<List<OrderSummaryResponse>>> orders(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.ordersOf(memberId)));
    }

    @GetMapping("/api/v1/orders/{orderNo}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> orderDetail(
            @AuthenticationPrincipal Long memberId, @PathVariable String orderNo) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.orderDetail(memberId, orderNo)));
    }
```

상단에 import를 추가한다: `GetMapping`, `PathVariable`, `List`, 두 응답 DTO.

- [ ] **Step 6: green 확인 + 커밋**

Run: `./gradlew test --tests '*OrderQueryTest*'`
Expected: PASS (5 tests)

Run: `./gradlew test`
Expected: PASS (129 tests)

```bash
git add src/main/java/com/beautyboy/order src/test/java/com/beautyboy/order/OrderQueryTest.java
git commit -m "feat(order): GET /orders · /orders/{orderNo} — 주문 목록·상세 조회

상세는 전부 주문 시점 스냅샷을 낸다. 현재 상품·회원을 조인하지 않는다."
```

---

## Task 8: `SalesStatProvider` 구현 — 랭킹에 판매 수치 공급

**근거:** Wave 2 사전 정리 — `ranking.SalesStatProvider`는 ranking(T1)이 정의하고 order(T2)가 구현한다(의존성 역전).
이 구현이 머지되면 T1의 폴백(빈 맵)이 자동으로 밀려나 랭킹이 실제 판매를 반영한다.

**Files:**
- Create: `backend/src/main/java/com/beautyboy/order/OrderSalesStatProvider.java`
- Test: `backend/src/test/java/com/beautyboy/order/OrderSalesStatProviderTest.java`

**Interfaces:**
- Consumes: `ranking.SalesStatProvider`(main에 이미 있는 인터페이스 — **수정 금지, implements만**).
- Produces: 이 `@Component`가 존재하면 `RankingStatFallbackAutoConfiguration`의 빈 맵 폴백이 물러난다.

- [ ] **Step 1: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/order/OrderSalesStatProviderTest.java`

```java
package com.beautyboy.order;

import com.beautyboy.ranking.SalesStatProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderSalesStatProviderTest {

    @Autowired
    SalesStatProvider salesStatProvider;
    @Autowired
    OrderRepository orderRepository;

    @Test
    void 폴백이_아니라_주문_도메인_구현이_주입된다() {
        // 이 구현이 있으면 ranking의 빈 맵 폴백이 물러나야 한다.
        // 여기가 깨지면 랭킹이 조용히 조회수 랭킹으로 남는다.
        assertThat(salesStatProvider).isInstanceOf(OrderSalesStatProvider.class);
    }

    @Test
    void 그_날_결제완료된_주문의_상품별_판매수량을_센다() {
        결제완료_주문(1L, 2, LocalDateTime.now());
        결제완료_주문(1L, 3, LocalDateTime.now());
        결제완료_주문(2L, 1, LocalDateTime.now());

        Map<Long, Integer> result = salesStatProvider.salesQuantityByGoods(LocalDate.now());

        assertThat(result).containsEntry(1L, 5).containsEntry(2L, 1);
    }

    @Test
    void 결제대기_주문은_세지_않는다() {
        // 담아두기만 해도 랭킹이 오르면 조작이 쉬워진다. 결제완료만 센다.
        결제대기_주문(1L, 10, LocalDateTime.now());

        assertThat(salesStatProvider.salesQuantityByGoods(LocalDate.now())).isEmpty();
    }

    @Test
    void 다른_날_결제는_세지_않는다() {
        결제완료_주문(1L, 5, LocalDateTime.now().minusDays(1));

        assertThat(salesStatProvider.salesQuantityByGoods(LocalDate.now())).isEmpty();
    }

    private void 결제완료_주문(Long goodsId, int quantity, LocalDateTime paidAt) {
        Order order = 주문(goodsId, quantity);
        order.markPaid(paidAt);
        orderRepository.saveAndFlush(order);
    }

    private void 결제대기_주문(Long goodsId, int quantity, LocalDateTime orderedAt) {
        orderRepository.saveAndFlush(주문(goodsId, quantity));
    }

    private Order 주문(Long goodsId, int quantity) {
        Order order = new Order("ORD-" + System.nanoTime(), 1L, "홍길동", "010-0000-0000",
                "06234", "서울시", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(goodsId, null, "상품", null, 10000, quantity));
        return order;
    }
}
```

> **주의:** `결제완료_주문`이 `markPaid(paidAt)`로 `paid_at`을 과거로 세팅하는데, `markPaid`가
> `LocalDateTime.now()`를 강제한다면 이 테스트의 `다른_날_결제는_세지_않는다`를 만들 수 없다.
> T2-4에서 `markPaid(LocalDateTime paidAt)`가 **인자를 그대로 저장**하도록 설계했으므로 문제없다 —
> 만약 구현이 인자를 무시하고 now()를 쓰면 그것부터 고친다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*OrderSalesStatProviderTest*'`
Expected: FAIL — `OrderSalesStatProvider`가 없어 컴파일 에러.

- [ ] **Step 3: 구현** — `order/OrderSalesStatProvider.java`

```java
package com.beautyboy.order;

import com.beautyboy.ranking.SalesStatProvider;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 랭킹이 요구하는 판매 통계를 order가 공급한다(의존성 역전).
 *
 * <p>ranking은 order 테이블을 직접 읽을 수 없다(패키지 = 서비스 경계). 그래서 ranking이 인터페이스를
 * 정의하고 데이터를 가진 order가 구현한다. 이 {@code @Component}가 존재하면
 * ranking의 빈 맵 폴백({@code RankingStatFallbackAutoConfiguration})이 자동으로 물러난다.
 *
 * <p>결제완료(PAID) 주문만 센다 — 장바구니에 담아두기만 해도 랭킹이 오르면 조작이 쉬워진다.
 */
@Component
public class OrderSalesStatProvider implements SalesStatProvider {

    private final EntityManager em;

    public OrderSalesStatProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> salesQuantityByGoods(LocalDate date) {
        // 그 날 결제완료된 주문의 상품별 수량 합. paid_at 기준으로 하루 경계를 잡는다.
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        List<Object[]> rows = em.createQuery(
                        "select i.goodsId, sum(i.quantity) from Order o join o.items i "
                                + "where o.status = :paid and o.paidAt >= :from and o.paidAt < :to "
                                + "group by i.goodsId", Object[].class)
                .setParameter("paid", Order.STATUS_PAID)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return result;
    }
}
```

> **확인:** `Order.items`가 `@OneToMany`로 매핑돼 있어 JPQL `join o.items i`가 되는지 T2-4의 엔티티를 본다.
> `OrderItem.goodsId` 필드명이 그대로인지도 확인한다.

- [ ] **Step 4: green 확인**

Run: `./gradlew test --tests '*OrderSalesStatProviderTest*'`
Expected: PASS (4 tests)

`폴백이_아니라...`가 실패하면 `@Component`가 빠졌거나 컴포넌트 스캔 밖이다.
이 실패는 T1과 통합할 때 랭킹이 죽는다는 뜻이므로 반드시 통과시킨다.

- [ ] **Step 5: 전체 회귀 + 커밋**

Run: `./gradlew test`
Expected: PASS (133 tests)

```bash
git add src/main/java/com/beautyboy/order/OrderSalesStatProvider.java \
        src/test/java/com/beautyboy/order/OrderSalesStatProviderTest.java
git commit -m "feat(order): SalesStatProvider 구현 — 랭킹에 결제완료 판매 수량 공급

ranking이 정의한 인터페이스를 order가 구현한다(의존성 역전).
이 빈이 있으면 ranking의 빈 맵 폴백이 자동으로 물러난다."
```

---

## Task 9: 최종 검증 + 보고

**Files:** 없음(검증만). 필요 시 앞 태스크의 코드 수정.

- [ ] **Step 1: 전체 유닛테스트**

Run: `./gradlew test`
Expected: PASS (133 tests). 하나라도 빨간색이면 그 태스크로 돌아간다.

- [ ] **Step 2: 실 MySQL 통합 테스트**

Run: `./gradlew integrationTest`
Expected: PASS (4 tests — 스모크). V30·V31·V32의 FK 제약이 실제로 걸리는지 여기서 확인된다.
Docker가 없으면 이 스텝에서 중단하고 보고한다 — 유닛테스트 통과로 대체하지 않는다.

- [ ] **Step 3: 결제 흐름을 실제로 한 번 돌려보기 (curl)**

유닛테스트는 가짜 게이트웨이라, 컨트롤러~서비스 배선이 실제로 이어지는지 curl로 한 번 확인한다.
토스 실 호출은 하지 않는다(가짜가 주입되지 않는 실행 환경에서는 이 스텝을 건너뛰고 보고에 남긴다).

로컬 MySQL이 필요하다. 메모리 [[local-mysql-port-conflict]]대로 13306 임시 컨테이너를 쓰거나
docker-compose의 3306을 쓴다. 자세한 절차는 이 스텝에서 환경에 맞춰 판단한다 —
**이 curl 확인이 어려우면 생략하되, 생략 사실과 이유를 보고서에 명시한다**(테스트 통과가 배선 확인을 대체하지 못한다).

- [ ] **Step 4: 보고**

오케스트레이터 리뷰용 보고서에 아래를 남긴다:

- `./gradlew test`·`./gradlew integrationTest` 출력(건수 포함)
- 만든 Flyway 버전(V30·V31·V32)과 각 테이블
- **알려진 미완 사항 명시:**
  1. **재고 차감 없음** — 주문 시 검증만 한다. 차감은 Wave 3 오늘드림(`store_stock` 조건부 차감)의 몫.
  2. **결제 취소 실패 시 수동 대사** — 금액 불일치로 `cancel`을 부르는데 그마저 실패하면
     승인은 됐고 취소는 안 된 상태로 남는다(로그로만 추적). 재시도 큐는 1차 범위 밖.
  3. **`point_history`·쿠폰 없음** — `discount_amount`는 항상 0.
  4. **비회원 장바구니 병합 없음** — 프론트 몫.
  5. **`SalesStatProvider`만 구현** — 랭킹의 `WishStatProvider`는 T3(wishlist)가 구현한다.
     T2·T3 머지 전까지 랭킹의 찜 항은 0이다.
- **공유 계약 준수 확인:** `common/ErrorCode.java`·`config/SecurityConfig.java`·`build.gradle.kts`를
  건드리지 않았음을 `git diff --stat main`으로 보이기.

---

## 통합 마무리 (오케스트레이터)

- [ ] T2-1 ~ T2-9 전 태스크 리뷰 통과 후 `feat/order-payment`를 main에 머지.
- [ ] worktree 정리: `git worktree remove ../BeautyBoy-w2-order`
- [ ] 로드맵의 Flyway 대역 표에 실제 사용 버전(V30·V31·V32) 기록.
- [ ] **T1(search-ranking) 머지 후**: `application.yml`이 두 브랜치에서 각각 수정됐으므로 충돌을 확인한다.
      T1은 `spring.profiles.include`, T2는 최상위 `toss:` 블록이라 라인이 겹치지 않아야 한다 — 겹치면 양쪽을 합친다.
- [ ] **T1·T3 머지 후**: `salesStatProvider`가 폴백이 아니라 `OrderSalesStatProvider`로 주입되는지
      통합 테스트로 확인한다(`RankingStatFallbackTest`가 T1에 있다).

---

## 실행 프롬프트 (터미널에 그대로 붙여넣기)

프로젝트 루트에서 새 터미널을 열고 Claude Code를 실행한 뒤, 아래를 **그대로** 붙여넣는다.

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.

  git worktree add ../BeautyBoy-w2-order -b feat/order-payment

를 실행한 뒤 EnterWorktree 도구에 path로 그 경로(../BeautyBoy-w2-order)를 넘겨 세션을 그 안으로 옮겨라.
EnterWorktree를 name으로 새로 만들지 마라 — origin에서 브랜치를 따서 계획서도 참조 문서도 없는 worktree가 생긴다.

진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 .../BeautyBoy-w2-order 인지
  - git log --oneline -1 이 0e990a7 (chore(backend): Wave 2 병렬 분기 전 사전 정리) 이후인지
  - ls docs/superpowers/plans/2026-07-24-wave2-order-payment.md CLAUDE.md 가 성공하는지
  - ls backend/src/main/java/com/beautyboy/ranking/SalesStatProvider.java 가 성공하는지
    (없으면 기점이 틀린 것이다 — 반드시 중단하고 보고해라. 이 파일을 T2-8이 implements 한다)
  - git status 가 깨끗한지
  - cd backend && ./gradlew test 가 91개 통과로 green인지

[2단계 — 실행]

CLAUDE.md와 docs/superpowers/plans/2026-07-24-wave2-order-payment.md를 읽고, 그 계획서의
Task 1부터 Task 9까지를 순서대로 실행해라.

너는 오케스트레이터다. 직접 구현하지 말고, 태스크마다 서브에이전트를 스폰해 TDD로 구현시켜라.
모델 배분: Task 5·6(결제 2단계 검증)은 model=opus, 나머지는 model=sonnet.
태스크 사이마다 아래를 리뷰한 뒤 다음으로 넘어가라:
  - 해당 태스크의 테스트가 실제로 통과하는가 (출력을 눈으로 확인)
  - 그 태스크의 Files 목록 밖 파일을 건드리지 않았는가
  - 특히 common/ErrorCode.java, config/SecurityConfig.java, build.gradle.kts, ranking/**, search/**를
    건드렸다면 즉시 되돌려라 — 전부 다른 터미널과 공유하는 계약이다
    (예외: ranking/SalesStatProvider.java는 T2-8이 implements만 하되 파일 자체는 수정하지 않는다)

계획서의 Global Constraints를 모든 서브에이전트 프롬프트에 그대로 포함시켜라.
특히 "클라이언트가 보낸 금액을 어떤 경로로도 신뢰하지 않는다", "시크릿을 코드에 넣지 않는다",
"재고는 검증만 하고 차감하지 않는다(Wave 3 몫)"는 핵심 불변식이다.

Task 5·6은 결제 검증이라 가장 조심스럽다. '토스 승인액이 주문액과 다르면 취소를 부르고 실패시킨다'와
'이미 결제된 주문에 다시 승인하면 이중 청구되지 않는다' 두 테스트가 통과하는지 반드시 눈으로 확인해라.

Task 1·9의 통합 테스트는 Docker가 필요하다(./gradlew integrationTest).
Docker가 없으면 그 스텝에서 중단하고 보고해라 — 유닛테스트 통과로 대체하지 마라.

전 태스크 완료 후 ./gradlew test 와 ./gradlew integrationTest 결과, 그리고 계획서 Task 9 Step 4의
"알려진 미완 사항" 5가지를 보고해라.
```

---

## Self-Review (계획 대 spec)

**1. Spec 커버리지** — 설계 7장 인증 목록 중 이 계획의 몫과 결제 2단계 요구를 매핑:

| 사양 항목 | 태스크 |
|---|---|
| `GET|POST|PATCH|DELETE /cart/items` | T2-3 |
| `POST /cart/items/bulk` (루틴 담기) | T2-3 |
| `POST /orders` — 서버 가격·재고 재검증 → 결제대기 | T2-4 |
| `POST /payments/confirm` — 토스 승인 검증 → 결제완료 | T2-6 |
| 검증 실패 시 토스 취소 후 주문 실패 | T2-6 (`토스_승인액이_주문액과_다르면...`) |
| `GET /orders`, `/orders/{orderNo}` | T2-7 |
| 주문 시점 스냅샷(가격·상품명·배송지) | T2-1(스키마) · T2-4(저장) |
| `orders` 상태 전이 PENDING→PAID | T2-4(`markPaid`) · T2-6 |
| `payment`(paymentKey·금액·상태·원문 JSON) | T2-1 · T2-6 |
| 랭킹 판매 수치 공급(사전 정리 계약) | T2-8 |
| `ApiResponse` 봉투 | 전 태스크 |
| 재고 차감(오늘드림) | **의도적 제외** — Wave 3 T1 몫. 여기서는 검증만. |
| `point_history`·쿠폰 | **의도적 제외** — 소비처 없는 테이블을 미리 만들지 않는다. |

**사양에 없었으나 추가한 것:** T2-2(catalog에 `findOrderSnapshot` 추가). 스코프 확장이 아니라
**"돈은 서버가 재계산"을 성립시키는 전제**다 — order가 가격을 다시 읽을 통로가 없으면 재계산 자체가 불가능하다.
catalog 소유권이 이번 웨이브에서 T2에 있으므로 여기서 추가하는 것이 경계에 맞다.

**2. 플레이스홀더 스캔** — "적절히 처리"·"위와 유사"·TBD 없음. 엔티티 생성자·옵션 시그니처처럼
실제 파일을 봐야 하는 지점은 **"실제에 맞춰라, 엔티티를 테스트 편의로 바꾸지 마라"**라고 방향을 명시했다.
curl 스텝(T2-9 Step 3)은 환경 의존이라 "어려우면 생략하되 이유를 보고"로 처리 규칙을 못 박았다.

**3. 타입 일관성** — 교차 확인:
- `GoodsQueryService.OrderGoodsSnapshot`(6필드)를 T2-2가 정의하고 T2-3(장바구니 표시)·T2-4(주문 생성)가 같은 접근자로 읽는다.
- `Order.STATUS_PENDING`/`STATUS_PAID`·`markPaid(LocalDateTime)`·`getPayableAmount()`·`ownedBy(Long)`을
  T2-4가 정의하고 T2-6(결제)·T2-7(조회)·T2-8(판매집계)이 소비한다.
- `OrderRepository.findByOrderNoForUpdate`(락)·`findByOrderNo`·`existsByOrderNo`를 T2-4가 정의, T2-6·T2-7이 사용.
- `PaymentGateway.confirm(String,String,int)`/`cancel(String,String)`·`PaymentApproval`(4필드)을 T2-5가 정의, T2-6이 소비.
- `ranking.SalesStatProvider.salesQuantityByGoods(LocalDate)` — **main의 기존 시그니처**를 T2-8이 그대로 implements.
- `CartService.clear(Long)`을 T2-3이 정의, T2-4가 주문 성립 후 호출.
- 주문번호 문자열은 `OrderService.주문번호_생성()`이 유일하게 만들고, 나머지는 전부 `getOrderNo()`로 읽는다 —
  형식 문자열을 따로 적어둔 곳이 없다.

**4. 태스크 경계** — 각 태스크가 독립적으로 테스트 가능하다. T2-7·T2-8이 T2-4의 파일(`OrderService`·`OrderController`)을
수정하지만 같은 터미널 순차 작업이라 충돌 대상이 아니며 각자 자기 테스트로 회귀를 잡는다.
결제(T2-5·T2-6)만 opus로 올린 것은 CLAUDE.md 모델 배분 예외를 정확히 따른 것이다.
