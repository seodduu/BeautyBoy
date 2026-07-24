# Wave 2 T3 — 리뷰(review) + Q&A(qna) + 찜(wishlist) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: `superpowers:subagent-driven-development`(권장) 또는
> `superpowers:executing-plans`로 태스크 단위 실행. 스텝은 체크박스(`- [ ]`)로 추적한다.

**Goal:** 상품 리뷰(구매인증·평점 집계·도움됐어요), Q&A(비밀글), 찜을 만들어 설계 7장의
`GET /reviews` · `POST /reviews` · `POST /reviews/{id}/helpful` · `GET /qna` · `POST /qna` ·
`POST|DELETE /wishlist/{goodsNo}`를 채우고, 찜을 `WishStatProvider`로 랭킹에 공급해 랭킹 점수를 완성한다.

**Architecture:** 세 도메인 모두 자기 테이블만 접근한다. 리뷰의 **구매인증**은 order 테이블을 직접 읽지
않고 order가 내주는 `OrderQueryService.hasPurchased(memberId, goodsNo)`를 경유한다(Wave 2의
`SalesStatProvider`와 같은 의존성 역전). 찜은 `WishStatProvider`를 구현해, 이미 main에 있는 빈 맵 폴백을
밀어내고 랭킹의 찜 항을 실제 값으로 채운다. 평점 통계(`goods_review_stat`)는 리뷰 작성·삭제 때
**해당 상품의 리뷰를 통째로 재집계**해 upsert한다 — 증분 갱신은 동시성에서 값이 어긋나기 쉽다.

**Tech Stack:** Spring Boot 3.5, Java 21, JPA + Spring Data, Flyway, MySQL 8.4,
테스트는 H2(MySQL 모드) + Testcontainers(통합).

**근거 문서:** 설계 `docs/superpowers/specs/2026-07-23-beautyboy-design.md` 5장(review·qna)·7장(인증 API).
로드맵 `docs/plans/2026-07-23-roadmap.md`(Flyway 대역, 도메인 간 인터페이스 §3).

---

## Global Constraints

모든 태스크에 암묵적으로 포함된다.

- **Flyway 대역은 V40~V49뿐이다.** 이 밖의 번호를 쓰지 않는다.
- **아래는 이미 확정된 공유 계약이다. 확인만 하고 수정하지 않는다:**
  - `common/ErrorCode.java` — `REVIEW_*` `QNA_*` `WISHLIST_*` 상수가 **이미 있다**(사전 정리에서 선점).
    추가가 필요하면 중단하고 보고.
  - `config/SecurityConfig.java` — `GET /reviews`·`/reviews/stats`·`/qna`는 **이미 공개**, 쓰기(POST)와
    wishlist 전부는 `anyRequest().authenticated()`로 자동으로 걸린다. 수정 불필요.
  - `common/ApiResponse.java`·`common/PageResponse.java`·`build.gradle.kts` — 동결. 새 의존성이 필요하면 중단·보고.
  - `ranking/WishStatProvider.java` — main에 이미 있는 인터페이스. **implements만 하고 파일은 수정하지 않는다.**
- **`order/OrderQueryService`는 이 계획이 order 패키지에 신설한다(T3-2).** Wave 2 T2가 이미 머지됐고 order는
  지금 이 터미널 단독 작업이라 충돌 없다. 단 **기존 `OrderService`의 public 메서드 시그니처는 바꾸지 않는다** —
  `create`/`ordersOf`/`orderDetail`은 컨트롤러가 쓰고 있다. 메서드 **추가**만 한다.
- **패키지 = 서비스 경계.** `review`·`qna`·`wishlist`는 자기 테이블만 접근한다. 상품 존재는
  `catalog.GoodsQueryService.exists(Long)`로, 구매 여부는 `order.OrderQueryService`로만 확인한다.
  타 도메인 엔티티/리포지토리를 직접 import하지 않는다.
- **취향은 클라이언트, 검증은 서버.** 구매인증·비밀글 접근권·중복 방지는 전부 서버에서 재검증한다.
- **주문 시점 스냅샷.** 리뷰의 피부타입은 작성 시점 값을 복사해 저장한다(설계 5장) — 회원이 나중에
  프로필을 바꿔도 과거 리뷰의 피부타입이 따라 바뀌면 안 된다.
- **상태 변경을 검증할 때는 재조회 전에 `com.beautyboy.support.TestPersistence.DB_왕복_강제(em)`를 호출한다.**
  평점 통계 갱신·찜 추가가 이 영역이다.
- **응답은 `ApiResponse` 봉투, 목록은 `PageResponse<T>`.**
- **테스트는 H2 + 픽스처만.** 실 MySQL이 필요한 것은 `@Tag("integration")`으로 갈라 `./gradlew integrationTest`로만 돈다.
- **커밋 메시지·주석·문서는 한국어.** 태스크 단위 원자적 커밋.
- **모델 배분: 전 태스크 sonnet.** (CLAUDE.md 예외 3종 해당 없음)
- 명령은 모두 `backend/`에서 실행한다.

### 코드 게재 범위 (CLAUDE.md 계획 작성 규칙 4)

이 계획은 **판단이 있는 곳만 전량 코드**로 적는다. 아래는 **완전한 코드**로 적혔다:
Flyway DDL, `OrderQueryService` 시그니처(공유 계약), 구매인증 로직, 평점 통계 재집계,
`WishStatProvider` 구현, 비밀글 접근 판정, 테스트 케이스 전량.
아래는 **시그니처 + 사양 문장**으로만 적는다(구현은 서브에이전트가 관례대로 채운다):
엔티티의 getter·생성자, 순수 DTO/record, 위임만 하는 컨트롤러 핸들러, Spring Data 메서드명 리포지토리.
"적절히 처리"·TBD는 금지 — 사양 문장은 "무엇이 참이면 통과인지"가 적혀 검증 가능해야 한다.

---

## 착수 전 확인 (사람 몫)

```bash
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" log --oneline -1   # b6d1f6f (Wave 2 T1·T2 머지 완료) 이후여야 함
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" status --short     # 비어 있어야 한다
```

---

## 파일 구조 (이 계획이 만들거나 고치는 것)

| 파일 | 책임 | 태스크 | 코드 게재 |
|---|---|---|---|
| `db/migration/V40__review.sql` | `review` · `review_helpful` · `goods_review_stat` | T3-1 | 전량(DDL) |
| `db/migration/V41__qna.sql` | `qna` | T3-1 | 전량(DDL) |
| `db/migration/V42__wishlist.sql` | `wishlist` | T3-1 | 전량(DDL) |
| `test/.../common/FlywayMigrationSmokeTest.java` | 적용 버전 단언에 40·41·42 추가 | T3-1 | 전량(1줄) |
| `order/OrderQueryService.java` (신규) | 구매인증 통로 (order 소유 인터페이스) | T3-2 | 전량(시그니처) |
| `order/OrderService.java` | 위 인터페이스 구현 | T3-2 | 전량(판정 로직) |
| `wishlist/Wishlist.java` 외 | 찜 CRUD | T3-3 | 시그니처+사양 |
| `wishlist/WishlistWishStatProvider.java` (신규) | `WishStatProvider` 구현 | T3-3 | 전량 |
| `review/Review.java` 외 · `GoodsReviewStat` | 리뷰 작성·조회 + 평점 재집계 | T3-4 | 재집계·구매인증 전량, 나머지 사양 |
| `review/ReviewHelpful.java` 외 | 도움됐어요 | T3-5 | 시그니처+사양 |
| `qna/Qna.java` 외 | Q&A(비밀글) | T3-6 | 비밀글 판정 전량, 나머지 사양 |
| — | 최종 검증·보고 | T3-7 | — |

**범위 밖(YAGNI):**
- **오늘드림(delivery)·routine·compat** — Wave 3 T1. 이 계획에 넣지 않는다.
- **Q&A 관리자 답변 작성** — admin 기능은 Wave 4. `qna.answer`는 컬럼만 두고 답변 등록 API는 만들지 않는다.
- **리뷰 사진(`review_photo`)** — 이미지 업로드 인프라가 없다. 스키마에 테이블만 만들지 않는다(소비처 없는 테이블 금지).
  리뷰 본문·평점·피부타입까지만. 사진은 업로드가 생기는 웨이브의 몫.
- **리뷰 수정** — 설계 1차 범위에 없다. 작성·조회·삭제까지.

---

## Task 1: Flyway V40~V42 — 리뷰·Q&A·찜 스키마

**왜 이게 1번인가:** 이후 모든 태스크가 이 스키마에 엔티티를 맞춘다. "스키마가 진실"(CLAUDE.md)이므로 먼저 확정한다.

**Files:**
- Create: `backend/src/main/resources/db/migration/V40__review.sql`
- Create: `backend/src/main/resources/db/migration/V41__qna.sql`
- Create: `backend/src/main/resources/db/migration/V42__wishlist.sql`
- Modify: `backend/src/test/java/com/beautyboy/common/FlywayMigrationSmokeTest.java` (적용 버전 단언 1줄)

**Interfaces (Produces):** 아래 4개 테이블.

- [ ] **Step 1: `V40__review.sql` 작성**

```sql
-- 상품 리뷰. 구매인증은 order_item FK가 아니라 order.OrderQueryService로 코드에서 확인한다
-- (패키지 경계: review는 order 테이블에 직접 접근하지 않는다). 그래서 여기에는 FK를 걸지 않는다.
--
-- UNIQUE(member_id, goods_id): 한 회원은 한 상품에 리뷰 1개. 설계의 "이미 리뷰 작성한 주문"을
-- MVP에서는 상품 단위로 단순화한다 — order_item 단위 중복 방지는 orderItemId를 프론트가 들고
-- 다녀야 해 흐름이 무거워지고, 상품당 1리뷰가 커머스 리뷰의 일반적 기대에 더 맞는다.
--
-- skin_type_snapshot: 작성 시점의 회원 피부타입을 복사한다(설계 5장). 프로필이 바뀌어도
-- "이 리뷰를 쓸 때 이 사람의 피부는 무엇이었나"는 그 시점의 사실이라 변하면 안 된다. NULL 허용
-- (피부타입 미입력 회원). member 도메인의 값이지만 스냅샷이라 FK로 참조하지 않는다.
CREATE TABLE review (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL,
  goods_id BIGINT NOT NULL,
  rating TINYINT NOT NULL,               -- 1~5. 범위 검증은 애플리케이션이 한다.
  content VARCHAR(2000) NOT NULL,
  skin_type_snapshot VARCHAR(20) NULL,
  helpful_count INT NOT NULL DEFAULT 0,  -- review_helpful 집계 비정규화(정렬용). 눌림/취소 시 갱신.
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_review_member_goods (member_id, goods_id),
  -- 상품 상세의 리뷰 목록은 "이 상품의 리뷰 최신순/도움순"이라 goods_id 선행 인덱스가 필요하다.
  INDEX idx_review_goods_created (goods_id, created_at),
  CONSTRAINT fk_review_member FOREIGN KEY (member_id) REFERENCES member(id)
);

-- 도움됐어요. member×review 유니크(설계 5장) — 한 사람이 같은 리뷰에 여러 번 못 누른다.
CREATE TABLE review_helpful (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  review_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_review_helpful (review_id, member_id),
  CONSTRAINT fk_review_helpful_review FOREIGN KEY (review_id) REFERENCES review(id)
);

-- 상품별 평점 평균·개수 비정규화(설계 5장). 리뷰 작성·삭제 때 재집계해 upsert한다.
-- 상품 목록/상세가 이 테이블만 읽어 매번 AVG를 돌리지 않게 하는 것이 목적이다.
-- goods_id를 PK로 두어 상품당 1행을 upsert로 유지한다.
CREATE TABLE goods_review_stat (
  goods_id BIGINT PRIMARY KEY,
  review_count INT NOT NULL DEFAULT 0,
  rating_sum INT NOT NULL DEFAULT 0,      -- 평균을 저장하지 않고 합/개수를 저장한다 — 부동소수 누적오차가 없고 재집계가 정확하다.
  updated_at DATETIME NOT NULL
);
```

- [ ] **Step 2: `V41__qna.sql` 작성**

```sql
-- 상품 Q&A. 답변은 관리자만 다는데 admin 기능은 Wave 4라, 이 웨이브는 질문 등록·조회까지다.
-- answer/answered_at 컬럼은 미리 두되(스키마가 진실), 답변 등록 API는 만들지 않는다.
--
-- is_secret: 비밀글이면 작성자와 관리자만 본문을 볼 수 있다. 목록에는 "비밀글입니다"로 표시되고
-- 본문은 내려가지 않는다 — 접근 판정은 애플리케이션이 한다(T3-6).
CREATE TABLE qna (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL,
  goods_id BIGINT NOT NULL,
  question VARCHAR(1000) NOT NULL,
  answer VARCHAR(2000) NULL,
  is_secret BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(20) NOT NULL DEFAULT 'WAITING',  -- WAITING|ANSWERED
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  answered_at DATETIME NULL,
  INDEX idx_qna_goods_created (goods_id, created_at),
  CONSTRAINT fk_qna_member FOREIGN KEY (member_id) REFERENCES member(id)
);
```

- [ ] **Step 3: `V42__wishlist.sql` 작성**

```sql
-- 찜. member×goods 유니크 — 같은 상품을 두 번 찜할 수 없다.
-- created_at을 날짜로 집계해 WishStatProvider가 "그 날 새로 추가된 찜 수"를 랭킹에 공급한다(T3-3).
CREATE TABLE wishlist (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL,
  goods_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wishlist_member_goods (member_id, goods_id),
  -- 랭킹 배치가 "그 날 추가분"을 날짜로 집계하므로 created_at 인덱스가 필요하다.
  INDEX idx_wishlist_created (created_at),
  CONSTRAINT fk_wishlist_member FOREIGN KEY (member_id) REFERENCES member(id)
);
```

- [ ] **Step 4: 스모크 테스트의 적용 버전 단언 갱신**

`FlywayMigrationSmokeTest`의 `모든_마이그레이션이_실_MySQL에서_성공한다()`에서 아래로 **교체**한다:

```java
        assertThat(적용된_버전).contains("1", "10", "11", "12", "20", "21", "22", "30", "31", "32", "40", "41", "42");
```

- [ ] **Step 5: 실 MySQL 검증** — Run: `./gradlew integrationTest` · Expected: PASS (스모크 4). FK가 실제로 걸리는지 여기서만 검증된다.
- [ ] **Step 6: 유닛 회귀** — Run: `./gradlew test` · Expected: PASS (164, Flyway가 test에선 꺼져 있어 영향 없음)
- [ ] **Step 7: 커밋**

```bash
git add src/main/resources/db/migration/V40__review.sql src/main/resources/db/migration/V41__qna.sql \
        src/main/resources/db/migration/V42__wishlist.sql \
        src/test/java/com/beautyboy/common/FlywayMigrationSmokeTest.java
git commit -m "feat(review,qna,wishlist): V40~V42 스키마 — 리뷰·평점통계·Q&A·찜"
```

---

## Task 2: order에 구매인증 통로 `OrderQueryService` 추가

**근거:** 리뷰는 "산 사람만 쓴다"(설계 5장 구매인증). 그런데 review는 order 테이블을 직접 볼 수 없다
(패키지 경계). 로드맵 §3이 지정한 대로 order가 인터페이스를 내주고 review가 그것을 호출한다 —
`SalesStatProvider`(ranking이 정의, order가 구현)와 방향만 반대인 같은 원리다(order가 정의·구현, review가 소비).

**Files:**
- Create: `backend/src/main/java/com/beautyboy/order/OrderQueryService.java`
- Modify: `backend/src/main/java/com/beautyboy/order/OrderService.java` (구현 추가 — 기존 메서드 불변)
- Test: `backend/src/test/java/com/beautyboy/order/OrderQueryServiceTest.java`

**Interfaces (Produces):** `OrderQueryService.hasPurchased(Long memberId, Long goodsNo)` → `boolean`.
T3-4(리뷰 작성)가 소비한다.

- [ ] **Step 1: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/order/OrderQueryServiceTest.java`

```java
package com.beautyboy.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 구매인증 통로 테스트. 리뷰가 "산 사람만 쓴다"를 판정하는 유일한 근거다.
 * order 테이블을 review가 직접 못 보므로 이 인터페이스로만 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderQueryServiceTest {

    private static final Long 회원 = 1L;
    private static final Long 상품 = 100L;

    @Autowired
    OrderQueryService orderQueryService;
    @Autowired
    OrderRepository orderRepository;

    @Test
    void 결제완료_주문에_그_상품이_있으면_구매로_인정한다() {
        결제완료_주문(회원, 상품);

        assertThat(orderQueryService.hasPurchased(회원, 상품)).isTrue();
    }

    @Test
    void 결제대기_주문만_있으면_구매가_아니다() {
        // 담아두고 결제 안 한 것으로 리뷰를 쓸 수 있으면 인증이 무의미하다.
        결제대기_주문(회원, 상품);

        assertThat(orderQueryService.hasPurchased(회원, 상품)).isFalse();
    }

    @Test
    void 다른_회원의_구매는_내_구매가_아니다() {
        결제완료_주문(999L, 상품);

        assertThat(orderQueryService.hasPurchased(회원, 상품)).isFalse();
    }

    @Test
    void 산_적_없는_상품이면_구매가_아니다() {
        결제완료_주문(회원, 상품);

        assertThat(orderQueryService.hasPurchased(회원, 200L)).isFalse();
    }

    private void 결제완료_주문(Long memberId, Long goodsId) {
        Order order = 주문(memberId, goodsId);
        order.markPaid(LocalDateTime.now());
        orderRepository.saveAndFlush(order);
    }

    private void 결제대기_주문(Long memberId, Long goodsId) {
        orderRepository.saveAndFlush(주문(memberId, goodsId));
    }

    private Order 주문(Long memberId, Long goodsId) {
        Order order = new Order("ORD-" + System.nanoTime(), memberId, "홍길동", "010-0000-0000",
                "06234", "서울시", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(goodsId, null, "상품", null, 10000, 1));
        return order;
    }
}
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew test --tests '*OrderQueryServiceTest*'` · Expected: FAIL(`OrderQueryService` 없음)

- [ ] **Step 3: 인터페이스 구현** — `order/OrderQueryService.java`

```java
package com.beautyboy.order;

/**
 * order가 타 도메인에 내주는 조회 통로.
 *
 * <p>review는 "구매한 사람만 리뷰를 쓴다"를 판정해야 하는데 order 테이블을 직접 볼 수 없다
 * (패키지 = 서비스 경계). 그래서 order가 이 인터페이스를 내주고 review가 호출한다.
 * order 엔티티/리포지토리를 review가 import하지 않게 하는 유일한 통로다.
 */
public interface OrderQueryService {

    /**
     * 이 회원이 이 상품을 <b>결제 완료</b> 상태로 구매한 적이 있는가.
     *
     * <p>결제대기(PENDING)는 세지 않는다 — 담아두기만 한 것으로 리뷰를 쓰면 인증이 무의미하다.
     * 배송완료(DONE) 조건을 쓰지 않는 이유: 배송 상태 전이는 Wave 3 스케줄러 몫이라 아직 PAID가 최종 상태다.
     * 배송 개념이 생기면 이 판정 기준을 DONE 이상으로 좁힌다.
     *
     * @return 결제완료 주문에 그 상품이 하나라도 있으면 true
     */
    boolean hasPurchased(Long memberId, Long goodsNo);
}
```

- [ ] **Step 4: 구현** — `order/OrderService.java`가 `implements OrderQueryService`하도록 하고, `orderDetail(...)` 아래에 추가

```java
    @Override
    @Transactional(readOnly = true)
    public boolean hasPurchased(Long memberId, Long goodsNo) {
        // 이 회원의 결제완료 주문 중 그 상품을 포함한 것이 하나라도 있으면 true.
        // exists 쿼리라 건수를 세지 않고 첫 매칭에서 멈춘다.
        return orderRepository.existsPaidItem(memberId, goodsNo, Order.STATUS_PAID);
    }
```

클래스 선언을 `public class OrderService implements OrderQueryService {`로 바꾼다.
`OrderRepository`에 아래 쿼리 메서드를 추가한다:

```java
    /** 이 회원의 status 상태 주문 중 goodsNo를 담은 것이 존재하는가. 구매인증용. */
    @Query("select count(i) > 0 from Order o join o.items i "
            + "where o.memberId = :memberId and i.goodsId = :goodsNo and o.status = :status")
    boolean existsPaidItem(@Param("memberId") Long memberId,
                           @Param("goodsNo") Long goodsNo,
                           @Param("status") String status);
```

`OrderRepository` 상단에 `import org.springframework.data.jpa.repository.Query;`·`import org.springframework.data.repository.query.Param;`가 없으면 추가한다.

- [ ] **Step 5: green 확인** — Run: `./gradlew test --tests '*OrderQueryServiceTest*'` · Expected: PASS (4)
- [ ] **Step 6: 회귀 확인** — Run: `./gradlew test` · Expected: PASS. 기존 order 테스트가 그대로 통과해야 한다(메서드 추가만 했다).
- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/beautyboy/order/OrderQueryService.java \
        src/main/java/com/beautyboy/order/OrderService.java \
        src/main/java/com/beautyboy/order/OrderRepository.java \
        src/test/java/com/beautyboy/order/OrderQueryServiceTest.java
git commit -m "feat(order): 구매인증 통로 OrderQueryService.hasPurchased 추가

review가 order 테이블을 직접 보지 않고 '산 사람만 리뷰'를 판정하는 유일한 통로.
결제완료(PAID) 주문만 구매로 인정한다."
```

---

## Task 3: 찜(wishlist) CRUD + `WishStatProvider` 구현

**근거:** 설계 7장 `POST|DELETE /wishlist/{goodsNo}`. 그리고 Wave 2 사전 정리 계약 — wishlist가
`WishStatProvider`를 구현하면 ranking의 빈 맵 폴백이 물러나 랭킹 점수의 찜 항이 완성된다.

**Files:**
- Create: `backend/src/main/java/com/beautyboy/wishlist/Wishlist.java` (엔티티 — 시그니처+사양)
- Create: `backend/src/main/java/com/beautyboy/wishlist/WishlistRepository.java`
- Create: `backend/src/main/java/com/beautyboy/wishlist/WishlistService.java`
- Create: `backend/src/main/java/com/beautyboy/wishlist/WishlistController.java`
- Create: `backend/src/main/java/com/beautyboy/wishlist/WishlistWishStatProvider.java` (전량)
- Create: `backend/src/main/java/com/beautyboy/wishlist/dto/WishlistItemResponse.java`
- Test: `backend/src/test/java/com/beautyboy/wishlist/WishlistApiTest.java`
- Test: `backend/src/test/java/com/beautyboy/wishlist/WishlistWishStatProviderTest.java`

**엔티티·리포지토리·컨트롤러 사양 (시그니처+사양, 구현은 관례대로):**
- `Wishlist(Long memberId, Long goodsId)` 엔티티. 필드 `id·memberId·goodsId·createdAt(@CreationTimestamp)`,
  각 getter. `memberId`/`goodsId`는 스칼라(타 도메인 참조 금지). 소유 검사 `ownedBy(Long)`.
- `WishlistRepository extends JpaRepository<Wishlist, Long>`:
  `Optional<Wishlist> findByMemberIdAndGoodsId(Long, Long)` · `List<Wishlist> findByMemberIdOrderByIdDesc(Long)` ·
  `boolean existsByMemberIdAndGoodsId(Long, Long)` · `void deleteByMemberIdAndGoodsId(Long, Long)`.
- `WishlistController`: `POST /api/v1/wishlist/{goodsNo}`(201) · `DELETE /api/v1/wishlist/{goodsNo}`(204) ·
  `GET /api/v1/wishlist`(내 찜 목록). 전부 `@AuthenticationPrincipal Long memberId` 위임. 전부 인증 대상.
- `WishlistItemResponse(Long goodsNo, String goodsName, int salePrice, ...)` — 표시용. 상품 정보는
  `catalog.GoodsQueryService`로 조회 시점에 읽는다(장바구니와 같은 규칙). **간단히 goodsNo만 담아도 되며,
  카드 표시는 프론트가 goodsNo로 상세를 부르는 방식으로 축소해도 된다** — 이 결정은 서브에이전트가
  `GoodsQueryService`에 목록 표시용 조회가 있는지 보고 정한다(없으면 goodsNo 목록만 반환하고 T3-7 보고에 남긴다).

**구매인증과 달리 찜은 검증이 가볍다:** 존재하지 않는 상품을 찜하면 `catalog.GoodsQueryService.exists`로
막고(`GOODS_NOT_FOUND`), 이미 찜한 상품은 `WISHLIST_ALREADY_ADDED`. 그 외 판단은 없다.

- [ ] **Step 1: 찜 CRUD 실패 테스트** — `backend/src/test/java/com/beautyboy/wishlist/WishlistApiTest.java`

```java
package com.beautyboy.wishlist;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WishlistApiTest {

    private static final Long 회원 = 1L;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    @Test
    void 찜하고_목록에서_확인한다() throws Exception {
        Long goodsId = 상품_저장();

        mockMvc.perform(post("/api/v1/wishlist/" + goodsId).with(로그인(회원)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/wishlist").with(로그인(회원)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void 찜_해제가_동작한다() throws Exception {
        Long goodsId = 상품_저장();
        mockMvc.perform(post("/api/v1/wishlist/" + goodsId).with(로그인(회원)));

        mockMvc.perform(delete("/api/v1/wishlist/" + goodsId).with(로그인(회원)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/wishlist").with(로그인(회원)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 같은_상품을_두_번_찜하면_409와_WISHLIST_ALREADY_ADDED() throws Exception {
        Long goodsId = 상품_저장();
        mockMvc.perform(post("/api/v1/wishlist/" + goodsId).with(로그인(회원)));

        mockMvc.perform(post("/api/v1/wishlist/" + goodsId).with(로그인(회원)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WISHLIST_ALREADY_ADDED"));
    }

    @Test
    void 없는_상품을_찜하면_404와_GOODS_NOT_FOUND() throws Exception {
        mockMvc.perform(post("/api/v1/wishlist/999999").with(로그인(회원)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOODS_NOT_FOUND"));
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/wishlist")).andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor 로그인(Long memberId) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                memberId, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Long 상품_저장() {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        return goodsRepository.save(
                new Goods(brand, "C001001001", "토너", null, "https://img/x.jpg", 16000, 16000)).getId();
    }
}
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew test --tests '*WishlistApiTest*'` · Expected: FAIL(404/컴파일)
- [ ] **Step 3: 엔티티·리포지토리·서비스·컨트롤러·DTO 구현** — 위 "엔티티·리포지토리·컨트롤러 사양"대로.
  서비스 규칙: `add`는 `GoodsQueryService.exists`로 상품 확인(없으면 `GOODS_NOT_FOUND`) →
  `existsByMemberIdAndGoodsId`면 `WISHLIST_ALREADY_ADDED` → 저장. `remove`는 `deleteByMemberIdAndGoodsId`.
- [ ] **Step 4: 찜 CRUD green** — Run: `./gradlew test --tests '*WishlistApiTest*'` · Expected: PASS (5)

- [ ] **Step 5: `WishStatProvider` 구현 실패 테스트** — `backend/src/test/java/com/beautyboy/wishlist/WishlistWishStatProviderTest.java`

```java
package com.beautyboy.wishlist;

import com.beautyboy.ranking.WishStatProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WishlistWishStatProviderTest {

    @Autowired
    WishStatProvider wishStatProvider;
    @Autowired
    WishlistRepository wishlistRepository;

    @Test
    void 폴백이_아니라_wishlist_구현이_주입된다() {
        // 이 구현이 있으면 ranking의 빈 맵 폴백이 물러나야 한다.
        // 여기가 깨지면 랭킹의 찜 항이 영원히 0으로 남는다.
        assertThat(wishStatProvider).isInstanceOf(WishlistWishStatProvider.class);
    }

    @Test
    void 그_날_추가된_찜을_상품별로_센다() {
        wishlistRepository.saveAndFlush(new Wishlist(1L, 100L));
        wishlistRepository.saveAndFlush(new Wishlist(2L, 100L));
        wishlistRepository.saveAndFlush(new Wishlist(1L, 200L));

        Map<Long, Integer> result = wishStatProvider.wishCountByGoods(LocalDate.now());

        assertThat(result).containsEntry(100L, 2).containsEntry(200L, 1);
    }

    @Test
    void 다른_날_추가분은_세지_않는다() {
        // created_at을 어제로 조작해 저장. 오늘 집계에 들어오면 "어제 인기"가 오늘 순위를 오염시킨다.
        Wishlist old = new Wishlist(1L, 100L);
        wishlistRepository.saveAndFlush(old);
        wishlistRepository.flush();
        // created_at을 어제로 강제(네이티브) — @CreationTimestamp라 엔티티로는 못 바꾼다.
        wishlistRepository.백일_전으로_당긴다(old.getId());

        assertThat(wishStatProvider.wishCountByGoods(LocalDate.now())).isEmpty();
    }
}
```

> `백일_전으로_당긴다`는 테스트 편의를 위한 리포지토리 메서드다. 서브에이전트는 `WishlistRepository`에
> `@Modifying @Query("update Wishlist w set w.createdAt = ... where w.id = :id")` 형태로 추가하거나,
> 더 단순하게 이 테스트만 `EntityManager` 네이티브 update로 처리한다 — **둘 중 어느 쪽이든,
> "오늘이 아닌 찜은 집계에서 빠진다"는 단언이 통과하면 된다.**

- [ ] **Step 6: 실패 확인** — Run: `./gradlew test --tests '*WishlistWishStatProviderTest*'` · Expected: FAIL(`WishlistWishStatProvider` 없음)

- [ ] **Step 7: `WishStatProvider` 구현 (전량)** — `wishlist/WishlistWishStatProvider.java`

```java
package com.beautyboy.wishlist;

import com.beautyboy.ranking.WishStatProvider;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 랭킹이 요구하는 찜 통계를 wishlist가 공급한다(의존성 역전).
 *
 * <p>ranking은 wishlist 테이블을 직접 읽을 수 없다(패키지 = 서비스 경계). 그래서 ranking이 인터페이스를
 * 정의하고 데이터를 가진 wishlist가 구현한다. 이 {@code @Component}가 존재하면
 * ranking의 빈 맵 폴백({@code RankingStatFallbackAutoConfiguration})이 자동으로 물러난다 —
 * 이 웨이브가 머지되면 랭킹 점수의 찜 항(찜×2)이 비로소 실제 값으로 채워진다.
 *
 * <p>"그 날 새로 추가된 찜"만 센다(누적이 아니다). 누적을 쓰면 한 번 오른 상품이 영원히 상위에 남아
 * "최근 3일 가중"이라는 랭킹 설계가 무의미해진다(WishStatProvider 계약).
 */
@Component
public class WishlistWishStatProvider implements WishStatProvider {

    private final EntityManager em;

    public WishlistWishStatProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> wishCountByGoods(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        List<Object[]> rows = em.createQuery(
                        "select w.goodsId, count(w) from Wishlist w "
                                + "where w.createdAt >= :from and w.createdAt < :to group by w.goodsId",
                        Object[].class)
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

- [ ] **Step 8: green 확인** — Run: `./gradlew test --tests '*WishlistWishStatProviderTest*'` · Expected: PASS (3)

  `폴백이_아니라_wishlist_구현이_주입된다`가 실패하면 `@Component`가 빠졌거나 컴포넌트 스캔 밖이다 —
  이건 랭킹 통합이 깨진다는 뜻이라 반드시 통과시킨다.

- [ ] **Step 9: 랭킹 폴백 회귀 확인** — Run: `./gradlew test --tests '*RankingStatFallbackTest*'`
  Expected: PASS. `WishStatProvider`가 이제 실 구현이므로, T1·T2 통합 때 sales에 했던 것처럼
  `미구현_도메인은_폴백으로_뜬다`가 깨질 수 있다. **깨지면**: 그 테스트가 이제 wish까지 실 구현이라
  검증할 폴백이 없어진 것이다 — 해당 클래스를 제거하거나, 두 공급자가 모두 실 구현으로 주입됨을
  확인하는 형태로 갱신한다(계획서 T3-7에서 최종 정리).

- [ ] **Step 10: 전체 회귀 + 커밋** — Run: `./gradlew test` · Expected: PASS

```bash
git add src/main/java/com/beautyboy/wishlist src/test/java/com/beautyboy/wishlist
git commit -m "feat(wishlist): 찜 CRUD + WishStatProvider 구현

wishlist가 ranking의 WishStatProvider를 구현해 빈 맵 폴백을 밀어낸다.
이 웨이브 머지로 랭킹 점수의 찜 항(찜×2)이 실제 값으로 채워진다."
```

---

## Task 4: 리뷰 작성·조회 + 구매인증 + 평점 통계 재집계

**근거:** 설계 7장 `POST /reviews`·`GET /reviews`·`GET /reviews/stats`. 5장 구매인증·`goods_review_stat`.

**Files:**
- Create: `backend/src/main/java/com/beautyboy/review/Review.java` (엔티티 — 시그니처+사양)
- Create: `backend/src/main/java/com/beautyboy/review/ReviewRepository.java`
- Create: `backend/src/main/java/com/beautyboy/review/GoodsReviewStat.java` (엔티티 — 시그니처+사양)
- Create: `backend/src/main/java/com/beautyboy/review/GoodsReviewStatRepository.java`
- Create: `backend/src/main/java/com/beautyboy/review/ReviewService.java` (재집계·구매인증 전량)
- Create: `backend/src/main/java/com/beautyboy/review/ReviewController.java` (위임)
- Create: `backend/src/main/java/com/beautyboy/review/dto/ReviewCreateRequest.java` · `ReviewResponse.java` · `ReviewStatResponse.java`
- Test: `backend/src/test/java/com/beautyboy/review/ReviewApiTest.java`

**Consumes:** `order.OrderQueryService.hasPurchased`(T3-2) · `catalog.GoodsQueryService.exists` ·
`member`의 피부타입 — **member도 타 도메인이다.** 피부타입 스냅샷이 필요하나 member가 조회 인터페이스를
내주지 않았다. **서브에이전트는 `member` 패키지에 조회 통로가 있는지 먼저 확인하고, 없으면 피부타입
스냅샷을 이 웨이브에서 `null`로 저장하고 T3-7 보고에 "member 피부타입 조회 인터페이스 부재 → 스냅샷 미채움"으로
남긴다.** member 인터페이스 신설은 이 계획의 범위를 넘는다(order처럼 여기서 만들지 않는다 — order는 리뷰의
필수 전제라 만들었지만, 피부타입은 없어도 리뷰가 성립한다).

**엔티티·DTO 사양 (시그니처+사양):**
- `Review(Long memberId, Long goodsId, int rating, String content, String skinTypeSnapshot)`.
  필드 getter, `helpfulCount`(기본 0)·`increaseHelpful(int)`·`ownedBy(Long)`.
- `GoodsReviewStat` — PK `goodsId`, 필드 `reviewCount·ratingSum·updatedAt`, `average()`는 계산
  (`reviewCount==0 ? 0.0 : (double) ratingSum / reviewCount`). 생성/갱신 정적 팩토리 또는 setter.
- `ReviewCreateRequest(Long goodsNo, int rating, String content)`. **금액처럼 조작 위험 값이 없다.**
- `ReviewResponse(Long reviewId, Long memberId, int rating, String content, String skinType, int helpfulCount, LocalDateTime createdAt)`.
- `ReviewStatResponse(long reviewCount, double averageRating)`.
- `ReviewRepository`: `List<Review> findByGoodsIdOrderByCreatedAtDesc(Long, Pageable)`(정렬 파라미터로 도움순도) ·
  `long countByGoodsId(Long)` · `boolean existsByMemberIdAndGoodsId(Long, Long)` ·
  집계 쿼리 `@Query("select count(r), coalesce(sum(r.rating),0) from Review r where r.goodsId = :goodsNo")`.

- [ ] **Step 1: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/review/ReviewApiTest.java`

```java
package com.beautyboy.review;

import com.beautyboy.order.Order;
import com.beautyboy.order.OrderItem;
import com.beautyboy.order.OrderRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewApiTest {

    private static final Long 구매자 = 1L;
    private static final Long 비구매자 = 2L;
    private static final Long 상품 = 500L;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    ReviewRepository reviewRepository;
    @Autowired
    GoodsReviewStatRepository goodsReviewStatRepository;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    void 구매자는_리뷰를_쓸_수_있다() throws Exception {
        결제완료_주문(구매자, 상품);

        리뷰작성(구매자, 상품, 5, "좋아요")
                .andExpect(status().isCreated());

        assertThat(reviewRepository.findAll()).hasSize(1);
    }

    @Test
    void 사지_않은_사람은_403과_REVIEW_NOT_PURCHASED() throws Exception {
        // 구매인증의 핵심. 여기가 뚫리면 아무나 리뷰를 쓴다.
        리뷰작성(비구매자, 상품, 5, "안 샀는데 씀")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_PURCHASED"));
    }

    @Test
    void 같은_상품에_두_번_쓰면_409와_REVIEW_ALREADY_WRITTEN() throws Exception {
        결제완료_주문(구매자, 상품);
        리뷰작성(구매자, 상품, 5, "첫 리뷰");

        리뷰작성(구매자, 상품, 4, "또 씀")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_WRITTEN"));
    }

    @Test
    void 평점이_1_5_범위_밖이면_400() throws Exception {
        결제완료_주문(구매자, 상품);

        리뷰작성(구매자, 상품, 6, "범위 밖")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 리뷰_목록을_최신순으로_조회한다() throws Exception {
        결제완료_주문(구매자, 상품);
        리뷰작성(구매자, 상품, 5, "리뷰 본문");

        mockMvc.perform(get("/api/v1/reviews").param("goodsNo", String.valueOf(상품)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].content").value("리뷰 본문"));
    }

    @Test
    void 리뷰_조회는_비로그인도_가능하다() throws Exception {
        // 설계 7장 공개 목록. 토큰 없이 200.
        mockMvc.perform(get("/api/v1/reviews").param("goodsNo", String.valueOf(상품)))
                .andExpect(status().isOk());
    }

    @Test
    void 리뷰_작성시_평점_통계가_재집계된다() throws Exception {
        결제완료_주문(구매자, 상품);
        결제완료_주문(비구매자, 상품);
        리뷰작성(구매자, 상품, 5, "별 다섯");
        리뷰작성(비구매자, 상품, 3, "별 셋");

        TestPersistence.DB_왕복_강제(entityManager);

        GoodsReviewStat stat = goodsReviewStatRepository.findById(상품).orElseThrow();
        assertThat(stat.getReviewCount()).isEqualTo(2);
        // 평균 (5+3)/2 = 4.0
        assertThat(stat.average()).isEqualTo(4.0);
    }

    @Test
    void 통계_조회가_평균과_개수를_준다() throws Exception {
        결제완료_주문(구매자, 상품);
        리뷰작성(구매자, 상품, 4, "리뷰");

        mockMvc.perform(get("/api/v1/reviews/stats").param("goodsNo", String.valueOf(상품)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCount").value(1))
                .andExpect(jsonPath("$.data.averageRating").value(4.0));
    }

    private org.springframework.test.web.servlet.ResultActions 리뷰작성(
            Long memberId, Long goodsNo, int rating, String content) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("goodsNo", goodsNo, "rating", rating, "content", content));
        return mockMvc.perform(post("/api/v1/reviews")
                .with(로그인(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor 로그인(Long memberId) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                memberId, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }

    private void 결제완료_주문(Long memberId, Long goodsId) {
        Order order = new Order("ORD-" + System.nanoTime(), memberId, "홍길동", "010-0000-0000",
                "06234", "서울시", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(goodsId, null, "상품", null, 10000, 1));
        order.markPaid(LocalDateTime.now());
        orderRepository.saveAndFlush(order);
    }
}
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew test --tests '*ReviewApiTest*'` · Expected: FAIL(컴파일)

- [ ] **Step 3: 엔티티·리포지토리·DTO 구현** — 위 사양대로.

- [ ] **Step 4: 서비스 구현 (구매인증·재집계 전량)** — `review/ReviewService.java`

```java
package com.beautyboy.review;

import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.common.PageResponse;
import com.beautyboy.order.OrderQueryService;
import com.beautyboy.review.dto.ReviewCreateRequest;
import com.beautyboy.review.dto.ReviewResponse;
import com.beautyboy.review.dto.ReviewStatResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 리뷰 작성·조회 + 평점 통계 재집계.
 *
 * <p>핵심 판단 둘: (1) 구매인증 — order 테이블을 직접 보지 않고 OrderQueryService로 확인한다.
 * (2) 평점 통계 — 리뷰가 바뀔 때마다 그 상품의 리뷰를 통째로 다시 집계해 upsert한다.
 * 증분(+1, 평균 재계산)을 쓰지 않는 이유는 동시 작성 시 값이 어긋나기 때문이다 —
 * MVP 규모(상품당 리뷰 수십 개)에서 재집계 비용은 무시할 만하다.
 */
@Service
public class ReviewService {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final ReviewRepository reviewRepository;
    private final GoodsReviewStatRepository goodsReviewStatRepository;
    private final OrderQueryService orderQueryService;
    private final GoodsQueryService goodsQueryService;

    public ReviewService(ReviewRepository reviewRepository,
                         GoodsReviewStatRepository goodsReviewStatRepository,
                         OrderQueryService orderQueryService,
                         GoodsQueryService goodsQueryService) {
        this.reviewRepository = reviewRepository;
        this.goodsReviewStatRepository = goodsReviewStatRepository;
        this.orderQueryService = orderQueryService;
        this.goodsQueryService = goodsQueryService;
    }

    @Transactional
    public void create(Long memberId, ReviewCreateRequest request) {
        if (request.rating() < MIN_RATING || request.rating() > MAX_RATING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (!goodsQueryService.exists(request.goodsNo())) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        // 구매인증: 산 사람만 쓴다. order 테이블을 직접 보지 않는 유일한 통로.
        if (!orderQueryService.hasPurchased(memberId, request.goodsNo())) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_PURCHASED);
        }
        // 상품당 1리뷰. DB 유니크 제약이 최종 방어선이지만 여기서 먼저 걸러 409를 명확히 준다.
        if (reviewRepository.existsByMemberIdAndGoodsId(memberId, request.goodsNo())) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_WRITTEN);
        }

        // 피부타입 스냅샷: member 조회 통로가 없으면 null로 둔다(T3-7 보고).
        reviewRepository.save(new Review(memberId, request.goodsNo(), request.rating(), request.content(), null));

        recalculateStat(request.goodsNo());
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> list(Long goodsNo, int page) {
        List<Review> reviews = reviewRepository.findByGoodsIdOrderByCreatedAtDesc(
                goodsNo, PageRequest.of(page, DEFAULT_PAGE_SIZE));
        long total = reviewRepository.countByGoodsId(goodsNo);
        List<ReviewResponse> items = reviews.stream().map(this::toResponse).toList();
        return PageResponse.of(items, page, DEFAULT_PAGE_SIZE, total);
    }

    @Transactional(readOnly = true)
    public ReviewStatResponse stat(Long goodsNo) {
        // 통계 테이블을 읽는다(매번 AVG를 돌리지 않는다). 없으면 0건으로 응답한다.
        return goodsReviewStatRepository.findById(goodsNo)
                .map(s -> new ReviewStatResponse(s.getReviewCount(), s.average()))
                .orElse(new ReviewStatResponse(0, 0.0));
    }

    /**
     * 그 상품의 리뷰를 통째로 재집계해 goods_review_stat을 upsert한다.
     * 작성·삭제 어느 경로에서도 이 한 메서드만 부르면 통계가 항상 리뷰와 일치한다.
     */
    private void recalculateStat(Long goodsNo) {
        // [count, sum]을 한 쿼리로. 리뷰가 0건이면 count=0, sum=0.
        Object[] agg = reviewRepository.aggregate(goodsNo);
        int count = ((Number) agg[0]).intValue();
        int sum = ((Number) agg[1]).intValue();

        GoodsReviewStat stat = goodsReviewStatRepository.findById(goodsNo)
                .orElseGet(() -> new GoodsReviewStat(goodsNo));
        stat.update(count, sum, LocalDateTime.now());
        goodsReviewStatRepository.save(stat);
    }

    private ReviewResponse toResponse(Review r) {
        return new ReviewResponse(r.getId(), r.getMemberId(), r.getRating(), r.getContent(),
                r.getSkinTypeSnapshot(), r.getHelpfulCount(), r.getCreatedAt());
    }
}
```

> `reviewRepository.aggregate(goodsNo)`는 `@Query("select count(r), coalesce(sum(r.rating),0) from Review r where r.goodsId = :goodsNo") Object[] aggregate(Long goodsNo)`.
> `coalesce`가 중요하다 — 리뷰가 0건이면 `sum`이 null이라 NPE가 난다(삭제로 마지막 리뷰가 사라진 경우).

- [ ] **Step 5: 컨트롤러 구현 (위임)** — `POST /api/v1/reviews`(201, `@AuthenticationPrincipal`) ·
  `GET /api/v1/reviews?goodsNo=&page=`(공개) · `GET /api/v1/reviews/stats?goodsNo=`(공개). 전부 서비스에 위임.

- [ ] **Step 6: green 확인** — Run: `./gradlew test --tests '*ReviewApiTest*'` · Expected: PASS (8)

  `사지_않은_사람은...`이 201이면 구매인증이 빠진 것이다 — **즉시 멈춘다.**
  `리뷰_작성시_평점_통계가_재집계된다`가 어긋나면 `recalculateStat`의 `coalesce`와 `average()` 계산을 본다.

- [ ] **Step 7: 전체 회귀 + 커밋** — Run: `./gradlew test` · Expected: PASS

```bash
git add src/main/java/com/beautyboy/review src/test/java/com/beautyboy/review/ReviewApiTest.java
git commit -m "feat(review): 리뷰 작성·조회 + 구매인증 + 평점 통계 재집계

산 사람만 쓴다(OrderQueryService 경유). 평점 통계는 증분이 아니라
그 상품의 리뷰를 통째로 재집계해 upsert한다 — 동시 작성에도 값이 어긋나지 않는다."
```

---

## Task 5: 도움됐어요 `POST /reviews/{id}/helpful`

**근거:** 설계 7장 `POST /reviews/{id}/helpful`. member×review 유니크(설계 5장).

**Files:**
- Create: `backend/src/main/java/com/beautyboy/review/ReviewHelpful.java` (엔티티 — 시그니처+사양)
- Create: `backend/src/main/java/com/beautyboy/review/ReviewHelpfulRepository.java`
- Modify: `backend/src/main/java/com/beautyboy/review/ReviewService.java` (메서드 추가)
- Modify: `backend/src/main/java/com/beautyboy/review/ReviewController.java` (핸들러 추가)
- Test: `backend/src/test/java/com/beautyboy/review/ReviewHelpfulTest.java`

**사양:** `POST /api/v1/reviews/{reviewId}/helpful`(인증). 이미 누른 사람이 또 누르면
`REVIEW_HELPFUL_DUPLICATED`(409). 없는 리뷰면 `REVIEW_NOT_FOUND`(404). 성공 시 `review.helpful_count`를 +1하고
`review_helpful`에 (review, member) 저장. `ReviewHelpfulRepository`: `existsByReviewIdAndMemberId(Long, Long)`.

- [ ] **Step 1: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/review/ReviewHelpfulTest.java`

```java
package com.beautyboy.review;

import com.beautyboy.support.TestPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewHelpfulTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ReviewRepository reviewRepository;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    void 도움됐어요를_누르면_카운트가_오른다() throws Exception {
        Long reviewId = 리뷰_저장(1L, 500L);

        mockMvc.perform(post("/api/v1/reviews/" + reviewId + "/helpful").with(로그인(9L)))
                .andExpect(status().isOk());

        TestPersistence.DB_왕복_강제(entityManager);
        assertThat(reviewRepository.findById(reviewId).orElseThrow().getHelpfulCount()).isEqualTo(1);
    }

    @Test
    void 같은_사람이_두_번_누르면_409() throws Exception {
        Long reviewId = 리뷰_저장(1L, 500L);
        mockMvc.perform(post("/api/v1/reviews/" + reviewId + "/helpful").with(로그인(9L)));

        mockMvc.perform(post("/api/v1/reviews/" + reviewId + "/helpful").with(로그인(9L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_HELPFUL_DUPLICATED"));
    }

    @Test
    void 없는_리뷰면_404() throws Exception {
        mockMvc.perform(post("/api/v1/reviews/999999/helpful").with(로그인(9L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor 로그인(Long memberId) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                memberId, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Long 리뷰_저장(Long memberId, Long goodsId) {
        return reviewRepository.saveAndFlush(new Review(memberId, goodsId, 5, "리뷰", null)).getId();
    }
}
```

- [ ] **Step 2~4:** 실패 확인 → 구현(엔티티·리포지토리·서비스 `markHelpful(reviewId, memberId)`·컨트롤러) → green.
  서비스 규칙: 리뷰 없으면 `REVIEW_NOT_FOUND` → 이미 눌렀으면 `REVIEW_HELPFUL_DUPLICATED` →
  `review_helpful` 저장 + `review.increaseHelpful(1)`.
- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/beautyboy/review src/test/java/com/beautyboy/review/ReviewHelpfulTest.java
git commit -m "feat(review): 도움됐어요 — member×review 유니크로 중복 방지"
```

---

## Task 6: Q&A `GET /qna` · `POST /qna` (비밀글)

**근거:** 설계 7장 `GET /qna?goodsNo=`·`POST /qna`. 5장 비밀글.

**Files:**
- Create: `backend/src/main/java/com/beautyboy/qna/Qna.java` (엔티티 — 시그니처+사양)
- Create: `backend/src/main/java/com/beautyboy/qna/QnaRepository.java`
- Create: `backend/src/main/java/com/beautyboy/qna/QnaService.java` (비밀글 판정 전량)
- Create: `backend/src/main/java/com/beautyboy/qna/QnaController.java` (위임)
- Create: `backend/src/main/java/com/beautyboy/qna/dto/QnaCreateRequest.java` · `QnaResponse.java`
- Test: `backend/src/test/java/com/beautyboy/qna/QnaApiTest.java`

**사양:** `POST /api/v1/qna`(인증, `goodsNo·question·isSecret`) · `GET /api/v1/qna?goodsNo=`(공개, 페이징).
`QnaCreateRequest(Long goodsNo, String question, boolean isSecret)`.

**비밀글 판정(전량으로 명시할 판단):** 목록 응답에서 각 항목의 본문 노출 규칙 —

```java
    /**
     * 비밀글 본문 마스킹.
     *
     * <p>비밀글은 작성자 본인에게만 본문을 보인다. 그 외에게는 질문 내용을 "비밀글입니다"로 가리되
     * 항목 자체(작성일·답변여부·닉네임 자리)는 목록에 남긴다 — 존재를 숨기면 "몇 번째 질문"의 흐름이 깨진다.
     *
     * <p>viewerId가 null이면(비로그인) 작성자일 수 없으므로 무조건 마스킹된다.
     * 관리자 노출은 admin(Wave 4)에서 role 검사로 확장한다 — 지금은 작성자 본인만.
     */
    private String visibleQuestion(Qna qna, Long viewerId) {
        boolean 작성자본인 = viewerId != null && qna.getMemberId().equals(viewerId);
        if (qna.isSecret() && !작성자본인) {
            return "비밀글입니다.";
        }
        return qna.getQuestion();
    }
```

목록 컨트롤러는 `@AuthenticationPrincipal Long viewerId`(공개 엔드포인트라 비로그인은 null)를 서비스에 넘겨
위 마스킹을 적용한다.

- [ ] **Step 1: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/qna/QnaApiTest.java`

```java
package com.beautyboy.qna;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QnaApiTest {

    private static final Long 작성자 = 1L;
    private static final Long 남 = 2L;
    private static final Long 상품 = 700L;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 질문을_등록하고_목록에서_본다() throws Exception {
        질문등록(작성자, 상품, "재고 있나요?", false).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/qna").param("goodsNo", String.valueOf(상품)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].question").value("재고 있나요?"));
    }

    @Test
    void 비밀글은_남에게_본문이_가려진다() throws Exception {
        질문등록(작성자, 상품, "비밀 질문입니다", true);

        // 남이 조회 — 본문 마스킹
        mockMvc.perform(get("/api/v1/qna").param("goodsNo", String.valueOf(상품)).with(로그인(남)))
                .andExpect(jsonPath("$.data.content[0].question").value("비밀글입니다."));
    }

    @Test
    void 비밀글도_작성자_본인에게는_보인다() throws Exception {
        질문등록(작성자, 상품, "비밀 질문입니다", true);

        mockMvc.perform(get("/api/v1/qna").param("goodsNo", String.valueOf(상품)).with(로그인(작성자)))
                .andExpect(jsonPath("$.data.content[0].question").value("비밀 질문입니다"));
    }

    @Test
    void 비밀글은_비로그인에게_가려진다() throws Exception {
        질문등록(작성자, 상품, "비밀 질문입니다", true);

        // 조회는 공개 엔드포인트라 비로그인도 200이지만 본문은 마스킹.
        mockMvc.perform(get("/api/v1/qna").param("goodsNo", String.valueOf(상품)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].question").value("비밀글입니다."));
    }

    @Test
    void 질문_등록은_인증이_필요하다() throws Exception {
        mockMvc.perform(post("/api/v1/qna")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions 질문등록(
            Long memberId, Long goodsNo, String question, boolean secret) throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("goodsNo", goodsNo, "question", question, "isSecret", secret));
        return mockMvc.perform(post("/api/v1/qna")
                .with(로그인(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor 로그인(Long memberId) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                memberId, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }
}
```

- [ ] **Step 2~4:** 실패 확인 → 구현(엔티티·리포지토리·서비스·컨트롤러, 위 `visibleQuestion` 판정 포함) → green.
- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/beautyboy/qna src/test/java/com/beautyboy/qna
git commit -m "feat(qna): 질문 등록·조회 + 비밀글 마스킹(작성자 본인만 본문)"
```

---

## Task 7: 최종 검증 + 랭킹 통합 정리 + 보고

**Files:** 없음(검증). 필요 시 `RankingStatFallbackTest` 정리.

- [ ] **Step 1: 랭킹 폴백 테스트 최종 정리**

  `WishStatProvider`까지 실 구현(T3-3)이 됐으므로, 이제 sales·wish 둘 다 폴백이 아니다.
  `RankingStatFallbackTest`의 `미구현_도메인은_폴백으로_뜬다`(T1·T2 통합 때 wish만 폴백이라 남겨둔 것)는
  검증할 폴백이 사라졌다. **두 공급자가 모두 실 구현으로 주입됨을 확인하는 테스트로 갱신하거나,
  폴백 자동설정이 여전히 "구현이 없을 때만" 동작함을 격리 검증하는 형태로 남긴다.**
  판단 기준: `RankingStatFallbackAutoConfiguration`은 삭제하지 않는다 — 그것이 없으면 다음에 어느
  도메인이 빠졌을 때 앱이 안 뜬다. 폴백 메커니즘은 유지하되, 실제 통합 상태(둘 다 실 구현)를 테스트가 반영한다.

- [ ] **Step 2: 전체 유닛테스트** — Run: `./gradlew test` · Expected: PASS (증가분 포함, 실패 0)
- [ ] **Step 3: 실 MySQL 통합** — Run: `./gradlew integrationTest` · Expected: PASS. V1~V42 전부 실 MySQL에서 적용된다.
- [ ] **Step 4: 랭킹이 실제로 찜을 반영하는지 확인**

  `WishlistWishStatProviderTest.폴백이_아니라_wishlist_구현이_주입된다`와
  `OrderSalesStatProviderTest`(기존)가 함께 통과하면, 랭킹 점수 `판매×3 + 찜×2 + 조회×1`의 세 항이
  모두 실 데이터로 채워진다는 뜻이다. 이 사실을 보고에 명시한다.

- [ ] **Step 5: 보고**

  - `./gradlew test`·`./gradlew integrationTest` 출력(건수 포함)
  - 만든 Flyway 버전(V40·V41·V42)과 테이블
  - **랭킹 완성 확인:** wish 항이 실 구현으로 채워져 랭킹 점수 3항이 전부 실데이터임
  - **알려진 미완 사항:**
    1. **리뷰 사진 없음** — 이미지 업로드 인프라 부재. 리뷰는 본문·평점·피부타입까지.
    2. **피부타입 스냅샷 미채움(가능성)** — member 조회 인터페이스가 없으면 null. 있으면 채웠는지 명시.
    3. **Q&A 관리자 답변 없음** — `answer` 컬럼만 존재. 등록 API는 Wave 4 admin.
    4. **리뷰 수정 없음** — 작성·조회·(삭제) 까지. 설계 1차 범위.
  - **공유 계약 준수:** `ErrorCode`·`SecurityConfig`·`build.gradle.kts` 미변경을 `git diff --stat b6d1f6f`로 보이기.

---

## 통합 마무리 (오케스트레이터)

- [ ] T3-1 ~ T3-7 리뷰 통과 후 `feat/review`를 main에 머지.
- [ ] worktree 정리: `git worktree remove ../BeautyBoy-w2-review`
- [ ] 로드맵 Flyway 대역 표에 실사용 버전(V40·V41·V42) 기록.
- [ ] **랭킹 최종 확인:** 머지 후 `salesStatProvider`·`wishStatProvider`가 둘 다 실 구현으로 주입되는지 —
      Wave 2가 이것으로 완결된다(랭킹 점수 3항 완성).

---

## 실행 프롬프트 (터미널에 그대로 붙여넣기)

프로젝트 루트에서 새 터미널을 열고 Claude Code를 실행한 뒤, 아래를 **그대로** 붙여넣는다.

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.

  git worktree add ../BeautyBoy-w2-review -b feat/review

를 실행한 뒤 EnterWorktree 도구에 path로 그 경로(../BeautyBoy-w2-review)를 넘겨 세션을 그 안으로 옮겨라.
EnterWorktree를 name으로 새로 만들지 마라 — origin에서 브랜치를 따서 계획서도 참조 문서도 없는 worktree가 생긴다.

진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 .../BeautyBoy-w2-review 인지
  - git log --oneline -1 이 b6d1f6f (Wave 2 T1·T2 머지 완료) 이후인지
  - ls docs/superpowers/plans/2026-07-24-wave2-review-qna-wishlist.md CLAUDE.md 가 성공하는지
  - ls backend/src/main/java/com/beautyboy/ranking/WishStatProvider.java 가 성공하는지
    (없으면 기점이 틀린 것이다 — 반드시 중단하고 보고해라. 이 파일을 T3-3이 implements 한다)
  - ls backend/src/main/java/com/beautyboy/order/OrderService.java 가 성공하는지
    (T3-2가 여기에 OrderQueryService를 구현시킨다)
  - git status 가 깨끗한지
  - cd backend && ./gradlew test 가 164개 통과로 green인지

[2단계 — 실행]

CLAUDE.md와 docs/superpowers/plans/2026-07-24-wave2-review-qna-wishlist.md를 읽고, 그 계획서의
Task 1부터 Task 7까지를 순서대로 실행해라.

너는 오케스트레이터다. 직접 구현하지 말고, 태스크마다 서브에이전트(model: sonnet)를 스폰해
TDD로 구현시켜라. 태스크 사이마다 아래를 리뷰한 뒤 다음으로 넘어가라:
  - 해당 태스크의 테스트가 실제로 통과하는가 (출력을 눈으로 확인)
  - 그 태스크의 Files 목록 밖 파일을 건드리지 않았는가
  - 특히 common/ErrorCode.java, config/SecurityConfig.java, build.gradle.kts,
    ranking/WishStatProvider.java를 건드렸다면 즉시 되돌려라 — 전부 공유 계약이다
    (order 패키지는 이 터미널 단독 소유라 OrderQueryService 추가는 허용. 단 기존 메서드 시그니처는 불변)

계획서의 Global Constraints를 모든 서브에이전트 프롬프트에 그대로 포함시켜라.
특히 "구매인증은 OrderQueryService로만", "타 도메인 엔티티 직접 import 금지",
"평점 통계는 증분이 아니라 재집계", "WishStatProvider 시그니처 수정 금지"가 핵심이다.

Task 1·7의 통합 테스트는 Docker가 필요하다(./gradlew integrationTest).
Docker가 없으면 그 스텝에서 중단하고 보고해라 — 유닛테스트 통과로 대체하지 마라.

전 태스크 완료 후 ./gradlew test 와 ./gradlew integrationTest 결과, 그리고 계획서 Task 7 Step 5의
"알려진 미완 사항"과 "랭킹 완성 확인"을 보고해라.
```

---

## Self-Review (계획 대 spec)

**1. Spec 커버리지** — 설계 7장 중 이 계획의 몫:

| 사양 항목 | 태스크 |
|---|---|
| `POST /reviews` (구매인증) | T3-4 |
| `GET /reviews?goodsNo=` | T3-4 |
| `GET /reviews/stats?goodsNo=` | T3-4 |
| `POST /reviews/{id}/helpful` (member×review 유니크) | T3-5 |
| `GET /qna?goodsNo=` · `POST /qna` (비밀글) | T3-6 |
| `POST|DELETE /wishlist/{goodsNo}` | T3-3 |
| `goods_review_stat` 평점 집계 | T3-4 (재집계) |
| 리뷰 피부타입 스냅샷 | T3-4 (member 통로 있으면 채움, 없으면 null+보고) |
| 찜 → 랭킹 `WishStatProvider` | T3-3 (Wave 2 완결) |
| 구매인증 order 인터페이스 | T3-2 (order가 내주는 통로) |
| `review_photo` | **의도적 제외** — 업로드 인프라 부재 |
| qna 관리자 답변 | **의도적 제외** — Wave 4 admin |

**사양에 없었으나 추가한 것:** T3-2(order의 `OrderQueryService`). 스코프 확장이 아니라 **구매인증의
필수 전제**다 — 이 통로가 없으면 review가 order 테이블을 직접 봐야 해 패키지 경계가 무너진다.
`SalesStatProvider`와 같은 의존성 역전 패턴이다.

**2. 코드 게재 범위 (완화 규칙 적용)** — 판단 있는 곳만 전량:
전량으로 적은 것 = Flyway DDL 3개, `OrderQueryService` 시그니처, `hasPurchased` 판정, 평점 재집계
(`recalculateStat`·`coalesce` 근거), `WishlistWishStatProvider` 전체, 비밀글 `visibleQuestion` 판정,
테스트 케이스 전량. 시그니처+사양으로 줄인 것 = 엔티티 getter/생성자, 순수 DTO, 위임 컨트롤러,
Spring Data 리포지토리. "적절히"·TBD 없음 — 줄인 항목도 "무엇이 참이면 통과인지"가 사양 문장에 있다.

**3. 타입 일관성** — `OrderQueryService.hasPurchased(Long,Long)`를 T3-2가 정의, T3-4가 소비.
`WishStatProvider.wishCountByGoods(LocalDate)`(main 기존 시그니처)를 T3-3이 그대로 implements.
`GoodsReviewStat.average()`를 T3-4 서비스와 테스트가 같은 계산으로 본다.
`catalog.GoodsQueryService.exists`(기존)를 wishlist·review가 소비.

**4. 태스크 경계** — 각 태스크가 독립 테스트 가능. T3-5·T3-6이 T3-4의 파일(`ReviewService` 등)을
수정하지만 같은 터미널 순차 작업이라 충돌 대상이 아니다. T3-2(order)만 다른 도메인을 건드리는데,
order가 이 터미널 단독이라 안전하고 "메서드 추가만" 제약으로 기존 기능을 깨지 않는다.
