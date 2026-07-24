# Wave 4: 통합 — 주문·결제·루틴 프론트 + 마이페이지 + admin + E2E 구현 계획

> **For agentic workers:** 이 계획서는 오케스트레이터가 태스크마다 서브에이전트를 스폰해 TDD로
> 실행한다. 스텝은 체크박스(`- [ ]`)로 추적한다. 상위 문서 — 웨이브 구조·공유 계약의 진실은
> `docs/plans/2026-07-23-roadmap.md`, 설계 근거는 `docs/superpowers/specs/2026-07-23-beautyboy-design.md`,
> 시각 토큰의 진실은 루트 `DESIGN.md`.

**목표:** Wave 0~3이 만든 API를 화면으로 잇고 남은 빚을 갚아, 로드맵 W4 DoD
("탐색→루틴 담기→주문→결제 E2E 녹색, admin에서 상품/루틴 CRUD 가능, README에 실행법")를 충족한다.

**아키텍처:** **터미널 1개, 직렬.** Wave 2·3이 병렬이었던 이유(파일 소유권 분리)가 여기서는 사라진다 —
Wave 4는 성격상 여러 도메인을 동시에 만지고(카탈로그가 리뷰·찜을 조인, admin이 catalog·routine·qna에
컨트롤러를 얹음), 공유 자원(실 MySQL·토스 테스트 API·Redis)을 쓰는 통합/E2E가 포함된다. 로드맵 §5
"공유 MySQL·토스 API를 만지는 통합/E2E는 Wave 4 직렬 웨이브에서만"이 그 규정이다.
작업은 **A(백엔드 보정) → B(프론트 통합) → C(마감)** 순서로 흐르고, B는 A가 채운 값에 의존한다.

**Tech Stack:** Spring Boot 3.5 · JPA · Flyway · Spring Data Redis(신규) · JUnit5(H2 MySQL 모드) ·
Testcontainers(MySQL 8.4) / React 19 · React Router 7 · TanStack Query 5 · Zustand 5 · MSW ·
Vitest · Testing Library · **Playwright(신규)** · **@tosspayments/tosspayments-sdk(신규)**.

---

## Global Constraints (모든 태스크에 적용)

- **패키지 = 서비스 경계.** 타 도메인 엔티티/리포지토리를 직접 import하지 않는다. 접근은
  `*QueryService`/`*CommandService`/`*Provider` 인터페이스 경유(CLAUDE.md). **Wave 4에서 이 규칙이
  가장 자주 시험받는다** — 카탈로그가 별점·찜을 채우는 것도, admin이 상품을 고치는 것도 전부
  "남의 테이블을 만지고 싶어지는" 지점이다. 예외 없이 인터페이스로 간다(결정 1·4).
- **Flyway 자기 대역만.** Wave 4 = **V60~V69**. 대역 밖 번호 금지. 이 웨이브는 마이그레이션을
  추가하므로 `./gradlew integrationTest`(실 MySQL `validate` 스모크)가 DoD고, `FlywayMigrationSmokeTest`의
  적용 버전 목록 단언도 함께 늘린다(로드맵 §Wave 2 테스트 전략).
- **동결 해제 범위는 이 계획서가 명시한 것뿐.** `common`(ApiResponse·PageResponse·ErrorResponse·
  전역 핸들러)은 **여전히 동결**이다. `common/ErrorCode.java`는 자기 접두사 상수 추가만.
  `build.gradle.kts`·`docker-compose.yml`·`frontend/package.json`은 **이 웨이브에 한해 해제**
  (Redis·Playwright·토스 SDK 도입 — 결정 6·7). `config/SecurityConfig.java`는 **열지 않는다**(결정 4).
- **돈과 재고는 서버.** 프론트는 금액을 계산해 보내지 않는다. `/order`가 화면에 표시하는 합계는
  **서버가 `POST /orders`로 돌려준 `payableAmount`가 최종 진실**이고, 그 전 화면 합계는 안내용이다.
  결제창에 넘기는 금액도 `payableAmount` 그대로다.
- **시각 토큰은 `DESIGN.md` 이름을 직접 참조**하고 hex를 손으로 옮기지 않는다. 문서에 없는 값이
  필요하면 만들지 말고 `DESIGN.md`에 먼저 추가하고 보고한다.
- **화면 태스크는 스크린샷 없이 완료가 아니다.** 개발서버를 띄우고 담당 화면을 390/768/1024/1440/1920
  에서 찍어 **직접 본 뒤** 가로 스크롤 0을 확인하고 파일 경로를 보고서에 남긴다(CLAUDE.md).
- **상태 변경 검증 시 `TestPersistence.DB_왕복_강제(em)`.** 재조회 전에 호출한다(1차 캐시가 왕복을
  가림). 조회 전용 테스트에는 쓰지 않는다. — 이 웨이브는 주문·재고·조회수 카운터가 여기 해당한다.
- **시크릿은 코드·커밋에 넣지 않는다.** 토스 클라이언트 키/시크릿 키, JWT 시크릿, DB 비밀번호는
  환경변수(`VITE_TOSS_CLIENT_KEY`·`TOSS_SECRET_KEY`·`JWT_SECRET`)로만 주입한다.

---

## 착수 전 확정 사항 (이 계획서에서 결정 — 서브에이전트는 그대로 따른다)

### 결정 1: `GoodsListItem`의 별점·찜은 **의존성 역전**으로 채운다 (catalog가 인터페이스를 정의)

로드맵이 남긴 빚: `GoodsService.toItem`이 `rating=0.0 · reviewCount=0 · wished=false`를 하드코딩해
목록·검색·랭킹·루틴 카드가 전부 별점 0·찜 해제로 보인다. review(`goods_review_stat`)와
wishlist 테이블은 이미 있는데 catalog가 조인하지 않는 것이 원인이다.

**catalog가 `goods_review_stat`·`wishlist`를 직접 조인하면 안 된다** — 그 테이블은 review·wishlist
소유다. ranking의 `SalesStatProvider`·`WishStatProvider`와 **같은 패턴**으로 간다:
**필요한 쪽(catalog)이 인터페이스를 정의하고, 가진 쪽(review·wishlist)이 구현한다.**

이름은 `ranking.WishStatProvider`와 구분되게 짓는다(축이 다르다 — 랭킹은 "그 날 증가분", 카탈로그는
"이 회원이 찜했는가"). 확정 시그니처는 Task 4-1에 코드로 적었다.

### 결정 2: 상세 페이지의 미충족 3건은 **장바구니 화면과 같은 태스크로 묶지 않고 먼저 닫는다**

로드맵이 넘긴 3건(옵션 선택 UI 없음 · 설명 탭이 `/description` 미사용 · 추천 섹션 없음) 중
**옵션 선택은 장바구니의 전제**다. 지금 `Detail.tsx`가 `options[0]`을 말없이 담기 때문에,
옵션을 고치지 않고 `/cart`를 만들면 "사용자가 고르지 않은 옵션이 담긴 장바구니"를 화면으로
확정하게 된다. 따라서 **Task 4-8(상세 보정)이 Task 4-9(장바구니)보다 앞선다.**

### 결정 3: 루틴 가이드의 비회원 퀴즈는 **localStorage, 가입 시 승격**(설계 8장 1항 그대로)

`/routine`은 회원이면 `member_profile.skin_type`을, 비회원이면 3문항 퀴즈 결과를 쓴다.
퀴즈 결과는 **localStorage**에 두고(설계 8장), 로그인/가입 후 프로필이 비어 있으면
`PUT /members/me/profile`로 한 번 승격한다. **새 API를 만들지 않는다** — 기존 프로필 API로 충분하다.

### 결정 4: admin API는 `admin` 패키지가 아니라 **소유 도메인 안에** 둔다. 인가는 메서드 보안으로

`admin` 패키지를 새로 파면 그 패키지가 catalog·routine·qna 테이블을 전부 만지게 되어
"패키지 = 서비스 경계"가 정면으로 깨진다. 그래서 admin 엔드포인트는 **소유 도메인 안에**
별도 컨트롤러로 둔다: `catalog/AdminGoodsController`, `routine/AdminRoutineController`,
`qna/AdminQnaController`. 프론트의 `/admin/*` 화면은 하나지만 서버는 각 도메인이 자기 것을 쓴다.

**인가는 `SecurityConfig`를 열지 않고 메서드 보안으로 건다.** `JwtAuthenticationFilter`가 이미
`ROLE_" + claims.role()` 권한을 심고 있고 `member.role` 컬럼도 V1에 있다. 새 파일
`config/MethodSecurityConfig.java`(`@EnableMethodSecurity`) 한 장을 추가하고 각 admin 핸들러에
`@PreAuthorize("hasRole('ADMIN')")`를 단다. **`SecurityConfig.java`는 끝까지 열지 않는다** —
`anyRequest().authenticated()`가 이미 admin 경로를 인증 뒤로 보내고, 역할 판정만 메서드에서 한다.

**설계 문서를 먼저 고친다.** 설계 7장에는 admin API 목록이 없다(6장에 `/admin/*` 화면만 있다).
"이 목록에 없는 경로가 필요해지면 임의로 열지 말고 이 문서를 먼저 고친다"가 7장의 규정이므로,
Task 4-5 Step 1이 설계 7장에 "관리자" 절을 추가하는 것으로 시작한다.

### 결정 5: 결제 E2E는 **토스 결제창을 자동화하지 않는다.** 리다이렉트 계약을 재현한다

토스 결제창은 외부 도메인의 iframe/팝업이라 Playwright로 안정적으로 몰 수 없고, 몰 수 있어도
CI가 결제사 상태에 묶인다. 그래서 E2E는 이렇게 자른다:

- E2E 대상: 탐색 → 루틴 전체 담기 → 장바구니 → 주문서 → `POST /orders` → **토스가 성공 시 보내는
  리다이렉트 URL을 그대로 방문**(`/order/complete?orderId=…&paymentKey=…&amount=…`) →
  `POST /payments/confirm` → 완료 화면.
- 백엔드는 **`e2e` 프로필**로 뜬다. 이 프로필에서만 `PaymentGateway` 구현이 `FakePaymentGateway`로
  바뀌어(같은 금액을 승인) 실제 돈/외부 호출 없이 승인 경로가 돈다.
- **프로덕션 코드에 테스트 분기를 넣지 않는다.** 리다이렉트 URL은 토스가 실제로 쓰는 계약이고,
  게이트웨이 교체는 Spring 프로필이다. 화면 코드에는 `if (E2E)`가 한 줄도 들어가지 않는다.
- 실제 토스 테스트 결제창 승인은 **사람이 한 번 수동으로** 확인하고 결과를 보고서에 남긴다
  (Wave 2 DoD에서 이미 통과한 경로다 — 여기서는 화면 연결만 새로 확인한다).

### 결정 6: 조회수는 Redis INCR로 옮기되 **Redis가 없어도 앱이 뜬다**

로드맵이 Wave 2에서 미룬 항목이다. 조회수는 상품 상세 조회마다 UPDATE가 나가는 곳이라
쓰기 경합이 가장 심한 카운터고, 여기가 Redis를 쓸 **정당한 이유가 있는 유일한 자리**다.

단 Redis를 필수로 만들면 "docker compose 없이 백엔드만 띄우기"가 깨진다. 그래서
`catalog.ViewCountRecorder` 인터페이스 하나에 구현 둘을 두고 **설정으로 고른다**:
Redis 구현은 `@ConditionalOnProperty`, DB 구현은 `@ConditionalOnMissingBean` 폴백.
기본값(프로퍼티 없음) = 지금과 같은 DB 즉시 증가. **테스트는 전부 DB 구현으로 돈다.**

### 결정 7: 프론트 신규 의존성은 **2개뿐** — 토스 SDK와 Playwright

`@tosspayments/tosspayments-sdk`(결제창)와 `@playwright/test`(E2E). **그 외 라이브러리를 추가하지
않는다** — 폼·상태·라우팅은 이미 있는 것(react-hook-form 없이 제어 컴포넌트, Zustand, React Router)으로
충분하다. 새 UI 라이브러리를 넣는 순간 `DESIGN.md` 토큰 체계가 두 개가 된다.

### 결정 8: 시드 150개는 **가상 브랜드·가상 제품명**으로 만든다

설계 11장이 실제 브랜드 제품 사진·상표 사용을 금지한다. 시드는 기존 V12의 가상 브랜드 체계를
그대로 확장하고, 썸네일은 무료 스톡(Unsplash) 또는 플레이스홀더 URL을 쓴다.
**개수보다 분포가 중요하다** — 카테고리·가격대·할인율·재고·성분이 고르게 퍼져야 목록 필터·정렬·
검색·랭킹·궁합이 전부 실데이터로 보인다. 불변식은 Task 4-15에 검증 SQL로 못 박았다.

---

## Flyway V60~V69 사용 계획 (이 웨이브가 쓰는 번호 — 대역 밖 금지)

| 버전 | 파일 | 내용 |
|---|---|---|
| V60 | `V60__address_default_unique.sql` | 기본배송지 유니크 제약(생성 컬럼 + UNIQUE) |
| V61 | `V61__seed_member.sql` | 시드 회원 3명(일반 2 + **ADMIN 1**) + 프로필 + 배송지 |
| V62 | `V62__seed_goods_bulk.sql` | 상품 150개 + 옵션 + 성분 연결 + 프로모션 |
| V63 | `V63__seed_review.sql` | 리뷰 + `goods_review_stat` 재계산 |

V64~V69는 남겨둔다(마감 중 보정이 필요해질 자리).

---

# A. 백엔드 보정 (Task 4-1 ~ 4-7)

## Task 4-1: `GoodsListItem`의 별점·리뷰수·찜 채우기 (의존성 역전)

로드맵이 넘긴 빚. 목록·검색·랭킹·루틴 카드가 전부 별점 0·찜 해제로 보이는 것을 닫는다.

**Files:**
- Create: `backend/src/main/java/com/beautyboy/catalog/GoodsRatingProvider.java`
- Create: `backend/src/main/java/com/beautyboy/catalog/WishedGoodsProvider.java`
- Create: `backend/src/main/java/com/beautyboy/catalog/CatalogStatFallbackAutoConfiguration.java`
- Create: `backend/src/main/java/com/beautyboy/review/ReviewGoodsRatingProvider.java`
- Create: `backend/src/main/java/com/beautyboy/wishlist/WishlistWishedGoodsProvider.java`
- Modify: `backend/src/main/java/com/beautyboy/catalog/GoodsService.java` (toItem 주변 + 조회 메서드 시그니처)
- Modify: `backend/src/main/java/com/beautyboy/catalog/GoodsQueryService.java` (`findListItems`에 viewerId 추가)
- Modify: `backend/src/main/java/com/beautyboy/catalog/GoodsController.java` (`@AuthenticationPrincipal` 수용)
- Modify: `backend/src/main/java/com/beautyboy/routine/RoutineService.java`, `.../search/SearchService.java`,
  `.../ranking/RankingService.java` (`findListItems` 호출부에 viewerId 전달)
- Modify: `backend/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `backend/src/test/java/com/beautyboy/catalog/GoodsServiceTest.java` (케이스 추가)
- Test: `backend/src/test/java/com/beautyboy/review/ReviewGoodsRatingProviderTest.java`
- Test: `backend/src/test/java/com/beautyboy/wishlist/WishlistWishedGoodsProviderTest.java`

**Interfaces (Produces) — 확정 계약. 한 글자도 바꾸지 않는다:**

```java
package com.beautyboy.catalog;

import java.util.Collection;
import java.util.Map;

/**
 * 카탈로그 카드가 필요로 하는 "상품별 별점 집계" 공급자.
 *
 * <p>왜 catalog 패키지에 있는가: GoodsListItem은 rating·reviewCount를 담는데 그 값은
 * review 도메인 소유(goods_review_stat)다. 필요한 쪽(catalog)이 정의하고 가진 쪽(review)이
 * 구현한다(의존성 역전) — ranking.SalesStatProvider와 같은 패턴이다.
 */
public interface GoodsRatingProvider {

    /** 평균 별점과 리뷰 수. 평균은 rating_sum/review_count로 계산하며, 리뷰가 없으면 맵에 키가 없다. */
    record RatingStat(double rating, int reviewCount) {
    }

    /**
     * @param goodsIds 조회 대상. 비어 있으면 빈 맵.
     * @return {@code goods.id → RatingStat}. 리뷰가 없는 상품은 키를 넣지 않는다(널 반환 금지).
     */
    Map<Long, RatingStat> ratingsByGoods(Collection<Long> goodsIds);
}
```

```java
package com.beautyboy.catalog;

import java.util.Collection;
import java.util.Set;

/** 카탈로그 카드의 wished 플래그 공급자. wishlist 도메인이 구현한다(의존성 역전). */
public interface WishedGoodsProvider {

    /**
     * @param viewerId 보는 사람. <b>비로그인이면 null</b>이며 이때는 항상 빈 집합을 반환한다
     *                 (공개 엔드포인트에서도 카드가 그려져야 하므로 널을 예외로 만들지 않는다).
     * @return 이 회원이 찜한 goods.id 집합. 없으면 빈 집합(널 반환 금지).
     */
    Set<Long> wishedGoodsIds(Long viewerId, Collection<Long> goodsIds);
}
```

`GoodsQueryService.findListItems`는 **시그니처가 바뀐다**(호출자 3곳을 함께 고친다):

```java
/** goods_no 목록 → 카드 아이템. HIDDEN 제외. 입력 순서를 보존하지 않는다.
 *  viewerId는 wished 판정에만 쓰이며 비로그인이면 null이다. */
List<GoodsListItem> findListItems(Collection<Long> goodsNos, Long viewerId);
```

- [ ] **Step 1: 실패 테스트 — catalog가 별점·찜을 카드에 싣는다**

```java
// GoodsServiceTest
@Test
void 목록_카드에_별점과_리뷰수가_공급자_값으로_채워진다() {
    given(goodsRatingProvider.ratingsByGoods(any()))
            .willReturn(Map.of(상품A, new GoodsRatingProvider.RatingStat(4.5, 12)));

    List<GoodsListItem> items = goodsService.findListItems(List.of(상품A, 상품B), null);

    assertThat(items).filteredOn(i -> i.goodsNo().equals(상품A)).singleElement()
            .satisfies(i -> {
                assertThat(i.rating()).isEqualTo(4.5);
                assertThat(i.reviewCount()).isEqualTo(12);
            });
    // 리뷰가 없는 상품은 0.0/0 — 공급자가 키를 안 주면 기본값이다
    assertThat(items).filteredOn(i -> i.goodsNo().equals(상품B)).singleElement()
            .satisfies(i -> {
                assertThat(i.rating()).isEqualTo(0.0);
                assertThat(i.reviewCount()).isEqualTo(0);
            });
}

@Test
void 로그인한_회원이_찜한_상품만_wished가_true다() {
    given(wishedGoodsProvider.wishedGoodsIds(회원1, List.of(상품A, 상품B)))
            .willReturn(Set.of(상품A));

    List<GoodsListItem> items = goodsService.findListItems(List.of(상품A, 상품B), 회원1);

    assertThat(items).filteredOn(GoodsListItem::wished)
            .extracting(GoodsListItem::goodsNo).containsExactly(상품A);
}

@Test
void 비로그인이면_wished는_전부_false이고_공급자에_null이_전달된다() {
    given(wishedGoodsProvider.wishedGoodsIds(isNull(), any())).willReturn(Set.of());

    List<GoodsListItem> items = goodsService.findListItems(List.of(상품A), null);

    assertThat(items).allMatch(i -> !i.wished());
}
```

- [ ] **Step 2: 실패 테스트 — 공급자 구현 (review·wishlist 쪽)**

```java
// ReviewGoodsRatingProviderTest
@Test
void 별점은_ratingSum을_reviewCount로_나눈_값이다() {
    // goods_review_stat: (상품A, review_count=4, rating_sum=18) 을 심는다
    Map<Long, GoodsRatingProvider.RatingStat> stats = provider.ratingsByGoods(List.of(상품A));
    assertThat(stats.get(상품A).rating()).isEqualTo(4.5);   // 18/4
    assertThat(stats.get(상품A).reviewCount()).isEqualTo(4);
}

@Test
void 리뷰수가_0인_행은_0으로_나누지_않고_키를_넣지_않는다() {
    // (상품B, review_count=0, rating_sum=0) 을 심는다 — 삭제로 0이 된 행이 실제로 존재한다
    assertThat(provider.ratingsByGoods(List.of(상품B))).doesNotContainKey(상품B);
}

@Test
void 빈_입력은_리포지토리를_부르지_않고_빈_맵이다() {
    assertThat(provider.ratingsByGoods(List.of())).isEmpty();
    verifyNoInteractions(goodsReviewStatRepository);
}
```

```java
// WishlistWishedGoodsProviderTest
@Test
void viewerId가_null이면_리포지토리를_부르지_않고_빈_집합이다() {
    assertThat(provider.wishedGoodsIds(null, List.of(상품A))).isEmpty();
    verifyNoInteractions(wishlistRepository);
}

@Test
void 찜한_상품만_집합에_담긴다() {
    // 회원1이 상품A만 찜한 상태
    assertThat(provider.wishedGoodsIds(회원1, List.of(상품A, 상품B))).containsExactly(상품A);
}
```

- [ ] **Step 3: 실패 확인** — `./gradlew test --tests '*GoodsServiceTest*' --tests '*ProviderTest*'` → 컴파일 실패

- [ ] **Step 4: 구현**
  - 두 인터페이스를 위 코드 그대로 만든다.
  - `ReviewGoodsRatingProvider`: `GoodsReviewStatRepository.findAllById(goodsIds)` 한 번으로 조회하고
    `review_count > 0`인 행만 `rating_sum / (double) review_count`로 환산한다. **상품별 반복 조회 금지.**
  - `WishlistWishedGoodsProvider`: `wishlistRepository.findGoodsIdsByMemberIdAndGoodsIdIn(memberId, goodsIds)`
    한 번으로 조회. 리포지토리 메서드는 Spring Data 파생 쿼리로 충분하다.
  - `GoodsService`: 목록/검색용 배치 조회 3곳(`findList`·`findListItems`·기존 배치 경로)이 모두
    **행 조회 후 goodsIds를 모아 두 공급자를 각각 한 번씩** 부르고 `toItem`에 넘긴다.
    `toItem` 시그니처를 `toItem(row, badges, ratingStat, wished)`로 바꾼다. **N+1을 만들지 않는다.**
  - `GoodsController`: 목록/상세 핸들러에 `@AuthenticationPrincipal Long memberId`를 추가한다.
    **공개 경로여도 인증 헤더가 있으면 principal이 채워지고 없으면 null**이므로 분기가 필요 없다.
  - `GoodsService.detail`도 같은 방식으로 `rating`·`reviewCount`·`wished`를 채운다(상세 응답도 같은 빚이었다).
  - `CatalogStatFallbackAutoConfiguration`: `ranking.RankingStatFallbackAutoConfiguration`과 **같은 이유로**
    일반 `@Configuration`이 아니라 자동 설정으로 둔다(사용자 설정끼리는 처리 순서가 보장되지 않아
    `@ConditionalOnMissingBean`이 제대로 안 먹는다). 폴백은 빈 맵/빈 집합을 반환한다.
    `AutoConfiguration.imports`에 FQCN 한 줄을 추가한다.

- [ ] **Step 5: 통과 확인** — `./gradlew test` → PASS
- [ ] **Step 6: 커밋** — `git commit -am "feat(catalog): 카드 별점·리뷰수·찜 채우기 — review·wishlist 의존성 역전"`

## Task 4-2: V60 — 기본배송지 유니크 제약 (구조적 해결)

로드맵 §"Wave 3 착수 전"이 남긴 것. 500은 이미 막았으나 **기본배송지가 2개가 되는 것 자체**는
막지 못한 상태다. `UNIQUE(member_id, is_default)`는 `is_default=0`이 여러 개라 그대로는 못 건다.
MySQL 8은 부분 인덱스가 없으므로 **생성 컬럼 + UNIQUE**로 간다.

**Files:**
- Create: `backend/src/main/resources/db/migration/V60__address_default_unique.sql`
- Test: `backend/src/test/java/com/beautyboy/member/AddressServiceTest.java` (케이스 추가)

- [ ] **Step 1: 마이그레이션 작성**

```sql
-- V60__address_default_unique.sql
-- 기본배송지는 회원당 하나여야 한다. UNIQUE(member_id, is_default)는 is_default=0이 여럿이라 못 건다.
-- MySQL 8에는 부분 인덱스가 없으므로 "기본일 때만 member_id, 아니면 NULL"인 생성 컬럼에 UNIQUE를 건다.
-- UNIQUE 인덱스는 NULL 중복을 허용하므로 비기본 주소는 몇 개든 공존한다.

-- 제약을 걸기 전에 기존 위반 데이터를 정리한다. 가장 최근(id 최대) 한 건만 기본으로 남긴다 —
-- AddressRepository.findFirstByMemberIdAndIsDefaultTrueOrderByIdDesc 가 이미 쓰는 기준과 같다.
UPDATE address a
JOIN (
  SELECT member_id, MAX(id) AS keep_id
  FROM address WHERE is_default = 1 GROUP BY member_id
) k ON a.member_id = k.member_id
SET a.is_default = 0
WHERE a.is_default = 1 AND a.id <> k.keep_id;

ALTER TABLE address
  ADD COLUMN default_member_id BIGINT
    GENERATED ALWAYS AS (CASE WHEN is_default = 1 THEN member_id ELSE NULL END) STORED,
  ADD CONSTRAINT uq_address_default UNIQUE (default_member_id);
```

**엔티티에 `default_member_id`를 매핑하지 않는다.** `ddl-auto=validate`는 매핑된 컬럼만 검사하므로
DB에만 있는 생성 컬럼은 문제가 없고, 매핑하면 JPA가 INSERT에 끼워 넣으려다 실패한다.

- [ ] **Step 2: 실패 테스트 — 두 번째 기본배송지 등록이 기존 것을 내린다**

```java
@Test
void 새_기본배송지를_등록하면_기존_기본이_해제된다() {
    Long 첫번째 = addressService.create(회원1, 기본배송지_요청());
    Long 두번째 = addressService.create(회원1, 기본배송지_요청());

    TestPersistence.DB_왕복_강제(em);   // 1차 캐시가 UPDATE 반영을 가린다

    assertThat(addressRepository.findById(첫번째)).get()
            .satisfies(a -> assertThat(a.isDefault()).isFalse());
    assertThat(addressRepository.findById(두번째)).get()
            .satisfies(a -> assertThat(a.isDefault()).isTrue());
}
```

- [ ] **Step 3: 실패 확인** — `./gradlew test --tests '*AddressServiceTest*'`
  (기존 `unmarkExistingDefault`가 이미 있다면 PASS일 수 있다. **그러면 그 사실을 확인하고
  테스트를 회귀 방어로 남긴 뒤 다음으로 간다** — 제약의 목적은 코드가 아니라 DB에서 막는 것이다.)
- [ ] **Step 4: 실 MySQL 확인** — `./gradlew integrationTest`로 V60이 적용되고 `validate`가 통과하는지.
  **여기서만 잡히는 것**: 생성 컬럼 문법 오류, 기존 위반 데이터로 인한 제약 생성 실패.
- [ ] **Step 5: 커밋** — `git commit -am "fix(member): V60 기본배송지 유니크 제약 — 생성 컬럼 + UNIQUE"`

## Task 4-3: `GET /reviews/me` — 마이페이지 "내 리뷰"

설계 6장 `/mypage/*`가 "내 리뷰"를 요구하는데 조회 API가 없다(현재는 `?goodsNo=`로만 조회 가능).

**Files:**
- Modify: `backend/src/main/java/com/beautyboy/review/ReviewController.java`
- Modify: `backend/src/main/java/com/beautyboy/review/ReviewService.java`
- Modify: `backend/src/main/java/com/beautyboy/review/ReviewRepository.java`
- Modify: `docs/superpowers/specs/2026-07-23-beautyboy-design.md` (7장 인증 목록에 한 줄)
- Test: `backend/src/test/java/com/beautyboy/review/ReviewServiceTest.java` (케이스 추가)

**Interfaces (Produces):** `GET /api/v1/reviews/me?page=&size=` → `ApiResponse<PageResponse<MyReviewItem>>`

```java
/** 마이페이지용. 상품 카드가 아니라 "내가 쓴 글" 관점이므로 상품명·썸네일만 곁들인다. */
public record MyReviewItem(
        Long reviewId, Long goodsNo, String goodsName, String thumbnailUrl,
        int rating, String content, int helpfulCount, LocalDateTime createdAt) {
}
```

상품명·썸네일은 **`GoodsQueryService.findListItems`로 가져온다**(review가 goods 테이블을 직접 읽지
않는다 — 경계 규칙). 리뷰 페이지의 goodsNo를 모아 **한 번** 부르고 맵으로 붙인다.

- [ ] **Step 1: 설계 7장 개정** — "인증" 목록에 `GET /reviews/me` 한 줄을 추가하고 용도를 적는다.
  커밋을 분리한다: `git commit -am "docs(spec): 설계 7장에 GET /reviews/me 추가 — 마이페이지 내 리뷰"`
- [ ] **Step 2: 실패 테스트**

```java
@Test
void 내_리뷰만_최신순으로_상품명과_함께_돌려준다() {
    given(goodsQueryService.findListItems(anyCollection(), isNull()))
            .willReturn(List.of(카드(상품A, "그린티 토너", "https://…/a.jpg")));

    PageResponse<MyReviewItem> page = reviewService.myReviews(회원1, 0, 10);

    assertThat(page.content()).extracting(MyReviewItem::goodsName).containsExactly("그린티 토너");
    assertThat(page.content()).extracting(MyReviewItem::reviewId).doesNotContain(남의리뷰);
}

@Test
void 리뷰가_없으면_빈_페이지다() {
    assertThat(reviewService.myReviews(회원2, 0, 10).content()).isEmpty();
}
```

- [ ] **Step 3: 실패 확인** → 컴파일 실패
- [ ] **Step 4: 구현** — 리포지토리는 `findByMemberIdOrderByIdDesc(Long, Pageable)` 파생 쿼리.
  응답은 `PageResponse.of(...)`(동결 계약 재사용 — 새 페이징 타입 금지).
- [ ] **Step 5: 통과 확인** → PASS
- [ ] **Step 6: 커밋** — `git commit -am "feat(review): GET /reviews/me — 마이페이지 내 리뷰 목록"`

## Task 4-4: 메서드 보안 켜기 + `e2e` 프로필 결제 게이트웨이

admin 태스크(4-5)와 E2E 태스크(4-16)가 공통으로 필요로 하는 인프라를 먼저 깐다.

**Files:**
- Create: `backend/src/main/java/com/beautyboy/config/MethodSecurityConfig.java`
- Create: `backend/src/main/java/com/beautyboy/payment/FakePaymentGateway.java`
- Modify: `backend/src/main/java/com/beautyboy/payment/TossPaymentGateway.java` (`@Profile("!e2e")` 한 줄)
- Test: `backend/src/test/java/com/beautyboy/payment/FakePaymentGatewayTest.java`

**Interfaces (Produces):**

```java
package com.beautyboy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * @PreAuthorize를 켠다. SecurityConfig를 열지 않기 위해 별도 파일로 둔다 —
 * SecurityConfig는 Wave 0 이후 동결이고, 인가 규칙을 두 곳에서 고치면 어느 쪽이 진실인지 모르게 된다.
 * 경로 단위 인가는 SecurityConfig가, 역할 단위 인가는 메서드 애노테이션이 담당한다.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
```

```java
package com.beautyboy.payment;

/**
 * e2e 프로필 전용 결제 게이트웨이. 요청한 금액을 그대로 승인한 것처럼 답한다.
 *
 * <p>왜 필요한가: Playwright E2E가 토스 결제창을 자동화할 수 없다(결정 5). 대신 토스가 성공 시
 * 보내는 리다이렉트를 재현하고, 그 뒤의 승인 검증 경로는 실제 PaymentService 코드로 돌린다.
 * <b>금액 대조 로직은 가짜가 아니다</b> — 가짜인 것은 네트워크 호출뿐이다.
 */
@Component
@Profile("e2e")
public class FakePaymentGateway implements PaymentGateway {
    // confirm: PaymentApproval(paymentKey, amount, "DONE", "{\"e2e\":true,…}") 반환
    // cancel : 아무것도 하지 않는다(호출됐다는 사실만 로그로 남긴다)
}
```

- [ ] **Step 1: 실패 테스트**

```java
@Test
void 가짜_게이트웨이는_요청한_금액을_그대로_승인한다() {
    PaymentApproval approval = gateway.confirm("pk_e2e", "ORD-1", 29000);
    assertThat(approval.approvedAmount()).isEqualTo(29000);
    assertThat(approval.status()).isEqualTo("DONE");
    assertThat(approval.rawJson()).contains("e2e");
}
```

- [ ] **Step 2: 실패 확인** → 컴파일 실패
- [ ] **Step 3: 구현** — 위 두 파일 + `TossPaymentGateway`에 `@Profile("!e2e")` 추가.
  **`@Profile("!e2e")`를 빠뜨리면 e2e 프로필에서 빈이 둘이 되어 컨텍스트가 안 뜬다.**
- [ ] **Step 4: 통과 확인** — `./gradlew test` → PASS (기존 결제 테스트가 여전히 녹색인지 함께 확인)
- [ ] **Step 5: 커밋** — `git commit -am "feat(config): 메서드 보안 활성화 + e2e 프로필 가짜 결제 게이트웨이"`

## Task 4-5: admin API — 상품 CRUD · 루틴 단계 편집 · 문의 답변

**Files:**
- Modify: `docs/superpowers/specs/2026-07-23-beautyboy-design.md` (7장 "관리자" 절 신설)
- Create: `backend/src/main/java/com/beautyboy/catalog/AdminGoodsController.java`
- Create: `backend/src/main/java/com/beautyboy/catalog/AdminGoodsService.java`
- Create: `backend/src/main/java/com/beautyboy/catalog/dto/AdminGoodsSaveRequest.java`
- Create: `backend/src/main/java/com/beautyboy/routine/AdminRoutineController.java`
- Create: `backend/src/main/java/com/beautyboy/routine/AdminRoutineService.java`
- Create: `backend/src/main/java/com/beautyboy/routine/dto/RoutineStepGoodsRequest.java`
- Create: `backend/src/main/java/com/beautyboy/qna/AdminQnaController.java`
- Modify: `backend/src/main/java/com/beautyboy/qna/QnaService.java` (답변 등록)
- Modify: `backend/src/main/java/com/beautyboy/common/ErrorCode.java` (`GOODS_*`·`ROUTINE_*`·`QNA_*` 접두사 추가만)
- Test: `backend/src/test/java/com/beautyboy/catalog/AdminGoodsServiceTest.java`,
  `.../catalog/AdminGoodsControllerTest.java`, `.../routine/AdminRoutineServiceTest.java`,
  `.../qna/AdminQnaControllerTest.java`

**Interfaces (Produces) — 설계 7장에 그대로 옮겨 적을 목록:**

```
[관리자] 전부 ROLE_ADMIN 필요. @PreAuthorize("hasRole('ADMIN')")로 판정한다.
GET    /admin/goods?page=&size=&q=      # PageResponse<GoodsListItem> (HIDDEN 포함이 일반 목록과 다르다)
POST   /admin/goods                     # 등록 → 201 + goodsNo
PUT    /admin/goods/{goodsNo}           # 수정(이름·요약·가격·상태·카테고리·썸네일)
DELETE /admin/goods/{goodsNo}           # 소프트 삭제(status=HIDDEN). 물리 삭제하지 않는다.
GET    /admin/routines                  # 템플릿 + 단계 목록(추천 상품 goodsNo만)
PUT    /admin/routines/{templateId}/steps/{stepOrder}/goods   # 단계 추천 상품 전체 교체
POST   /admin/qna/{qnaId}/answer        # 문의 답변(설계 7장 "답변은 admin")
```

**판단이 있는 지점 — 코드로 못 박는다:**

```java
// AdminGoodsService.delete — 물리 삭제하지 않는 이유:
//   goods_no는 order_item·review·wishlist·routine_step_goods가 논리 참조(물리 FK 없음)로 붙들고 있다.
//   행을 지우면 그 참조들이 조용히 유령이 된다 — 이미 결제된 주문의 상품명이 사라지는 식이다.
//   상태를 HIDDEN으로 내리면 목록·상세·검색·랭킹·루틴이 전부 이미 그것을 제외하도록 돼 있다.
@Transactional
public void delete(Long goodsNo) {
    Goods goods = goodsRepository.findById(goodsNo)
            .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
    goods.hide();   // status = HIDDEN
}
```

```java
// AdminRoutineService.replaceStepGoods — "추가"가 아니라 "전체 교체"인 이유:
//   부분 추가/삭제 API를 두면 정렬 순서(sort_order)를 클라이언트가 관리하게 되고, 중간 삭제 시
//   순서에 구멍이 난다. 화면도 어차피 "이 단계의 추천 상품 목록"을 통째로 편집한다.
@Transactional
public void replaceStepGoods(Long templateId, int stepOrder, List<Long> goodsNos) {
    RoutineStep step = routineStepRepository
            .findByTemplateIdAndStepOrder(templateId, stepOrder)
            .orElseThrow(() -> new BusinessException(ErrorCode.ROUTINE_STEP_NOT_FOUND));
    // 존재하지 않거나 숨겨진 상품을 단계에 꽂으면 /routines 추천이 조용히 빈다.
    // catalog에 물어본다(경계 규칙 — goods 테이블을 직접 보지 않는다).
    List<GoodsListItem> valid = goodsQueryService.findListItems(goodsNos, null);
    if (valid.size() != goodsNos.size()) {
        throw new BusinessException(ErrorCode.ROUTINE_STEP_GOODS_INVALID);
    }
    routineStepGoodsRepository.deleteByStepId(step.getId());
    for (int i = 0; i < goodsNos.size(); i++) {
        routineStepGoodsRepository.save(new RoutineStepGoods(step.getId(), goodsNos.get(i), i + 1));
    }
}
```

추가할 `ErrorCode` 상수(자기 접두사만):

```java
GOODS_CATEGORY_INVALID(HttpStatus.BAD_REQUEST, "존재하지 않는 카테고리입니다"),
GOODS_PRICE_INVALID(HttpStatus.BAD_REQUEST, "판매가는 정가보다 클 수 없습니다"),
ROUTINE_STEP_NOT_FOUND(HttpStatus.NOT_FOUND, "루틴 단계를 찾을 수 없습니다"),
ROUTINE_STEP_GOODS_INVALID(HttpStatus.BAD_REQUEST, "노출되지 않는 상품은 루틴에 넣을 수 없습니다"),
QNA_ALREADY_ANSWERED(HttpStatus.CONFLICT, "이미 답변된 문의입니다"),
```

- [ ] **Step 1: 설계 7장 개정 + 커밋** — 위 "[관리자]" 목록을 7장에 절로 추가한다.
  `git commit -am "docs(spec): 설계 7장에 관리자 API 목록 추가"`
- [ ] **Step 2: 실패 테스트 (서비스)**

```java
// AdminGoodsServiceTest
@Test
void 삭제는_행을_지우지_않고_상태를_HIDDEN으로_내린다() {
    adminGoodsService.delete(상품A);
    TestPersistence.DB_왕복_강제(em);
    assertThat(goodsRepository.findById(상품A)).get()
            .satisfies(g -> assertThat(g.getStatus()).isEqualTo(Goods.STATUS_HIDDEN));
}

@Test
void 판매가가_정가보다_크면_GOODS_PRICE_INVALID다() {
    assertThatThrownBy(() -> adminGoodsService.create(요청(정가(10000), 판매가(12000))))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GOODS_PRICE_INVALID);
}

@Test
void 없는_카테고리코드로_등록하면_GOODS_CATEGORY_INVALID다() { /* … */ }

@Test
void 관리자_목록은_HIDDEN_상품도_포함한다() {
    // 일반 목록과 다른 유일한 지점. 숨긴 상품을 관리자가 못 보면 되살릴 방법이 없다.
    assertThat(adminGoodsService.list(0, 20, null).content())
            .extracting(GoodsListItem::goodsNo).contains(숨김상품);
}
```

```java
// AdminRoutineServiceTest
@Test
void 단계_추천상품을_통째로_교체하고_순서를_1부터_다시_매긴다() {
    adminRoutineService.replaceStepGoods(템플릿1, 2, List.of(상품C, 상품A));
    TestPersistence.DB_왕복_강제(em);
    assertThat(routineStepGoodsRepository.findByStepIdOrderBySortOrder(단계2))
            .extracting(RoutineStepGoods::getGoodsNo).containsExactly(상품C, 상품A);
}

@Test
void 숨겨진_상품을_단계에_넣으면_ROUTINE_STEP_GOODS_INVALID다() { /* … */ }
```

- [ ] **Step 3: 실패 테스트 (인가 — 슬라이스)**

```java
// AdminGoodsControllerTest (@WebMvcTest + MethodSecurityConfig import)
@Test
@WithMockUser(roles = "USER")
void 일반_회원이_관리자_API를_부르면_403이다() throws Exception {
    mockMvc.perform(delete("/api/v1/admin/goods/1")).andExpect(status().isForbidden());
}

@Test
@WithMockUser(roles = "ADMIN")
void 관리자는_상품을_삭제할_수_있다() throws Exception {
    mockMvc.perform(delete("/api/v1/admin/goods/1")).andExpect(status().isOk());
}
```

- [ ] **Step 4: 실패 확인** → 컴파일/실패
- [ ] **Step 5: 구현** — 위 코드 + 위임만 하는 컨트롤러. 모든 admin 핸들러에
  `@PreAuthorize("hasRole('ADMIN')")`. QnA 답변은 이미 답변이 있으면 `QNA_ALREADY_ANSWERED`.
- [ ] **Step 6: 통과 확인** — `./gradlew test` → PASS
- [ ] **Step 7: 커밋** — `git commit -am "feat(admin): 상품 CRUD·루틴 단계 편집·문의 답변 — 도메인별 관리자 컨트롤러"`

## Task 4-6: 조회수 Redis INCR (Redis 없이도 뜬다)

**Files:**
- Modify: `backend/build.gradle.kts` (`spring-boot-starter-data-redis` — 이 웨이브 한정 해제)
- Modify: `docker-compose.yml` (redis 7 서비스 — 이 웨이브 한정 해제)
- Create: `backend/src/main/java/com/beautyboy/catalog/ViewCountRecorder.java`
- Create: `backend/src/main/java/com/beautyboy/catalog/DbViewCountRecorder.java`
- Create: `backend/src/main/java/com/beautyboy/catalog/RedisViewCountRecorder.java`
- Create: `backend/src/main/java/com/beautyboy/catalog/ViewCountFlushScheduler.java`
- Modify: `backend/src/main/java/com/beautyboy/catalog/GoodsService.java` (상세 조회 시 recorder 호출)
- Modify: `backend/src/main/java/com/beautyboy/BeautyboyApplication.java` (`@EnableScheduling`)
- Modify: `backend/src/main/resources/application.yml` (redis 설정 + 토글 프로퍼티)
- Test: `backend/src/test/java/com/beautyboy/catalog/DbViewCountRecorderTest.java`,
  `.../catalog/ViewCountFlushSchedulerTest.java`

**Interfaces (Produces):**

```java
package com.beautyboy.catalog;

/**
 * 상품 상세 조회수 기록 경계.
 *
 * <p>왜 인터페이스인가: 조회수는 상세를 볼 때마다 쓰기가 나가는, 이 서비스에서 쓰기 경합이 가장
 * 심한 카운터다. 그래서 Redis INCR로 옮길 값어치가 있다. 그런데 Redis를 필수로 만들면
 * "docker compose 없이 백엔드만 띄우기"가 깨진다 — 구현을 갈라 설정으로 고른다.
 *
 * <p>기본값은 DB 즉시 증가({@link DbViewCountRecorder})다. Redis 구현은
 * {@code beautyboy.view-count.redis=true}일 때만 뜬다.
 */
public interface ViewCountRecorder {

    /** 조회 1회 기록. <b>실패해도 예외를 밖으로 던지지 않는다</b> — 조회수 때문에 상세가 500이 되면 안 된다. */
    void record(Long goodsNo);
}
```

**매직넘버와 근거:**
- 플러시 주기 `60_000ms`(1분). 근거: 랭킹 집계가 일 단위라 1분 지연은 랭킹 정확도에 영향이 없고,
  1분이면 서버가 죽어도 잃는 조회수가 한 상품당 한 자릿수다. 더 짧게 하면 Redis를 쓴 의미가 줄고,
  더 길게 하면 재기동 시 손실이 눈에 띈다.
- Redis 키 `bb:viewcount` (해시, field=goodsNo, value=증가분). 해시 하나로 모으는 이유:
  플러시가 `HGETALL` + `DEL` **한 쌍**으로 원자에 가깝게 끝난다. 키를 상품마다 나누면
  `SCAN`이 필요해지고 그 사이 들어온 증가분을 잃는다.

- [ ] **Step 1: 의존성·인프라 추가**

```kotlin
// backend/build.gradle.kts — dependencies 블록
implementation("org.springframework.boot:spring-boot-starter-data-redis")
```

```yaml
# docker-compose.yml — services 블록에 추가
  redis:
    image: redis:7-alpine
    container_name: beautyboy-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5
```

```yaml
# application.yml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

beautyboy:
  view-count:
    # 기본은 DB 즉시 증가. Redis 컨테이너가 떠 있을 때만 true로 켠다.
    redis: ${VIEW_COUNT_REDIS:false}
```

- [ ] **Step 2: 실패 테스트**

```java
// DbViewCountRecorderTest
@Test
void 조회수를_1_증가시킨다() {
    recorder.record(상품A);
    TestPersistence.DB_왕복_강제(em);
    assertThat(goodsRepository.findById(상품A)).get()
            .satisfies(g -> assertThat(g.getViewCount()).isEqualTo(기존 + 1));
}

@Test
void 없는_상품을_기록해도_예외를_던지지_않는다() {
    assertThatCode(() -> recorder.record(999999L)).doesNotThrowAnyException();
}
```

```java
// ViewCountFlushSchedulerTest — Redis는 mock RedisTemplate으로 대체한다(테스트에 Redis를 요구하지 않는다)
@Test
void 플러시는_버퍼를_읽어_DB에_더하고_버퍼를_비운다() {
    given(hashOps.entries("bb:viewcount")).willReturn(Map.of("1", 5L, "2", 3L));

    scheduler.flush();

    verify(goodsRepository).addViewCount(1L, 5);
    verify(goodsRepository).addViewCount(2L, 3);
    verify(redisTemplate).delete("bb:viewcount");
}

@Test
void 버퍼가_비어_있으면_DB를_건드리지_않는다() {
    given(hashOps.entries("bb:viewcount")).willReturn(Map.of());
    scheduler.flush();
    verifyNoInteractions(goodsRepository);
}

@Test
void Redis가_죽어_있으면_로그만_남기고_다음_주기를_기다린다() {
    given(hashOps.entries("bb:viewcount")).willThrow(new RedisConnectionFailureException("down"));
    assertThatCode(() -> scheduler.flush()).doesNotThrowAnyException();
}
```

- [ ] **Step 3: 실패 확인** → 컴파일 실패
- [ ] **Step 4: 구현**
  - `DbViewCountRecorder`: 기존 상세 조회의 조회수 증가 로직을 그대로 옮긴다(**새 쿼리를 만들지 않는다**).
    `@ConditionalOnMissingBean(ViewCountRecorder.class)`.
  - `RedisViewCountRecorder`: `@ConditionalOnProperty(name="beautyboy.view-count.redis", havingValue="true")`.
    `redisTemplate.opsForHash().increment("bb:viewcount", goodsNo.toString(), 1)`.
    **전체를 try-catch로 감싸고 실패 시 로그만 남긴다**(인터페이스 계약).
  - `ViewCountFlushScheduler`: 같은 `@ConditionalOnProperty`. `@Scheduled(fixedDelay = 60_000)`.
    `HGETALL` → 상품별 `UPDATE goods SET view_count = view_count + :delta WHERE id = :id` → `DEL`.
    **DEL을 HGETALL 뒤에 두는 순서가 중요하다** — 먼저 지우면 그 사이 조회를 통째로 잃는다.
    (그래도 HGETALL~DEL 사이 증가분은 잃는다. 조회수는 근사값이어도 되는 데이터라 감수한다 —
    돈·재고였다면 이 설계를 쓰지 않는다.)
  - `GoodsRepository`에 `@Modifying @Query("update Goods g set g.viewCount = g.viewCount + :delta where g.id = :id")
    void addViewCount(Long id, int delta)` 추가.
  - `BeautyboyApplication`에 `@EnableScheduling`.
- [ ] **Step 5: 통과 확인** — `./gradlew test` → PASS (기본값 false라 기존 테스트가 전부 DB 경로로 돈다)
- [ ] **Step 6: 수동 확인** — `docker compose up -d redis` 후
  `VIEW_COUNT_REDIS=true ./gradlew bootRun --args='--spring.profiles.active=local'`,
  상세를 3번 조회하고 `redis-cli HGETALL bb:viewcount`로 3이 쌓이는지 → 1분 뒤 DB `view_count`
  반영 + 키 소멸 확인. **결과를 보고서에 남긴다.**
- [ ] **Step 7: 커밋** — `git commit -am "feat(catalog): 조회수 Redis INCR 버퍼 + 1분 플러시(폴백은 DB 즉시 증가)"`

## Task 4-7: A그룹 마감 — Flyway 스모크 갱신 + 백엔드 전체 녹색

**Files:**
- Modify: `backend/src/test/java/com/beautyboy/**/FlywayMigrationSmokeTest.java` (적용 버전 단언에 `60` 추가)

- [ ] **Step 1: 스모크 단언에 V60 추가** (V61~V63은 Task 4-15에서 함께 추가한다)
- [ ] **Step 2:** `./gradlew test` → PASS
- [ ] **Step 3:** `./gradlew integrationTest` → PASS (실 MySQL `validate` + V60 적용)
- [ ] **Step 4: curl 재확인** — 앱 기동 후 `GET /api/v1/goods?page=0&size=5`에
  **`rating`·`reviewCount`가 0이 아닌 상품이 있는지**(4-1 결과). 리뷰 시드는 아직 없으므로 여기서는
  0이 정상이며, **Task 4-15 이후 다시 확인한다.** 대신 `Authorization` 헤더를 붙였을 때 찜한 상품의
  `wished`가 `true`로 오는지는 **지금 확인한다**(찜 API로 하나 찜하고 목록 재조회).
- [ ] **Step 5: 커밋** — `git commit -am "test(flyway): V60 스모크 버전 단언 — Wave 4 A그룹 마감"`

---

# B. 프론트 통합 (Task 4-8 ~ 4-14)

**공통 규칙(B그룹 전체):**
- `DESIGN.md` 토큰 이름만 쓴다. 기존 UI 프리미티브(`Button`·`Field`·`Price`·`Rating`·`Badge`·
  `Skeleton`·`EmptyState`·`Toast`/`useToast`·`GoodsCard`·`GoodsGrid`)를 **재사용하고 새로 만들지 않는다.**
- 각 화면 태스크는 **MSW 핸들러를 함께 추가**한다(`mocks/handlers.ts`) — 오프라인에서도 화면이 뜬다.
- 폼은 `DESIGN.md` UX 계약을 따른다: 에러 `role="alert"`, `inputMode`, `autoComplete`,
  아이콘 `aria-label`, 이미지 `alt`, `prefers-reduced-motion` 존중.
- 라우트는 `router.tsx`의 기존 중첩 구조 + `RequireAuth` 패턴을 그대로 쓴다.

## Task 4-8: 상세 페이지 미충족 3건 — 옵션 선택 · 설명 지연로딩 · 추천 섹션

로드맵이 "Wave 4에서 반드시 함께 닫는다"고 못 박은 것. **결정 2에 따라 장바구니보다 먼저 한다.**

**Files:**
- Modify: `frontend/src/pages/Detail.tsx`, `frontend/src/pages/Detail.css`
- Create: `frontend/src/components/goods/OptionSelector.tsx` (+ `.css`)
- Create: `frontend/src/components/goods/QuantityStepper.tsx` (+ `.css`)
- Create: `frontend/src/components/goods/RecommendedSection.tsx` (+ `.css`)
- Modify: `frontend/src/api/goods.ts` (`fetchGoodsDescription`·`fetchRecommended` 추가)
- Modify: `frontend/src/mocks/handlers.ts`
- Test: `frontend/src/pages/Detail.test.tsx` (케이스 추가),
  `frontend/src/components/goods/OptionSelector.test.tsx`

**Interfaces (Produces):**

```ts
// api/goods.ts 에 추가
export function fetchGoodsDescription(goodsNo: number): Promise<{ description: string }>;  // GET /goods/{n}/description
export function fetchRecommended(goodsNo: number): Promise<GoodsListItem[]>;               // GET /goods/{n}/recommended
```

**표시 가격 규칙(현재 결함의 핵심):** 상세 상단 가격은 `salePrice + selectedOption.addPrice`다.
지금은 `addPrice`가 반영되지 않아 `300ml(+3000)`을 골라도 같은 가격이 보인다.
**금액의 최종 진실은 여전히 서버**이며(주문 시 재계산), 이 값은 화면 안내용이다.

- [ ] **Step 1: 실패 테스트 — 옵션 선택**

```tsx
it('옵션이 2개 이상이면 선택 UI를 보여주고, 처음에는 아무것도 선택되지 않는다', async () => {
  // options: [{optionNo:1,name:'200ml',addPrice:0,stock:50},{optionNo:2,name:'300ml',addPrice:3000,stock:80}]
  render(<Detail />);
  expect(await screen.findByRole('radiogroup', { name: /옵션/ })).toBeInTheDocument();
  expect(screen.getByRole('radio', { name: /300ml/ })).not.toBeChecked();
  expect(screen.getByRole('button', { name: '장바구니 담기' })).toBeDisabled();
});

it('옵션을 고르면 표시 가격에 addPrice가 더해진다', async () => {
  render(<Detail />);
  await userEvent.click(await screen.findByRole('radio', { name: /300ml/ }));
  expect(screen.getByTestId('detail-price')).toHaveTextContent('23,000');  // salePrice 20000 + 3000
});

it('품절 옵션은 선택할 수 없다', async () => {
  // stock: 0 인 옵션
  expect(await screen.findByRole('radio', { name: /품절/ })).toBeDisabled();
});

it('옵션이 하나뿐이면 자동 선택하고 선택 UI를 그리지 않는다', async () => {
  // 옵션 하나짜리 상품은 고를 것이 없다 — 선택을 강요하면 클릭만 늘어난다
  render(<Detail />);
  expect(await screen.findByRole('button', { name: '장바구니 담기' })).toBeEnabled();
  expect(screen.queryByRole('radiogroup')).not.toBeInTheDocument();
});

it('선택한 옵션과 수량으로 담는다', async () => {
  render(<Detail />);
  await userEvent.click(await screen.findByRole('radio', { name: /300ml/ }));
  await userEvent.click(screen.getByRole('button', { name: '수량 늘리기' }));
  await userEvent.click(screen.getByRole('button', { name: '장바구니 담기' }));
  expect(addCartItemSpy).toHaveBeenCalledWith(1, 2, 2);   // goodsNo, optionNo, quantity
});
```

- [ ] **Step 2: 실패 테스트 — 설명 탭 지연 로딩 / 추천 섹션**

```tsx
it('설명 탭을 열기 전에는 /description을 부르지 않는다', async () => {
  render(<Detail />);
  await screen.findByRole('tab', { name: '설명' });
  expect(descriptionSpy).not.toHaveBeenCalled();
});

it('설명 탭을 열면 summary가 아니라 /description 본문을 보여준다', async () => {
  render(<Detail />);
  await userEvent.click(await screen.findByRole('tab', { name: '설명' }));
  expect(await screen.findByText(/전문 본문/)).toBeInTheDocument();
});

it('추천 상품이 있으면 카드로 렌더하고, 없으면 섹션 자체를 그리지 않는다', async () => {
  render(<Detail />);
  expect(await screen.findByRole('heading', { name: '함께 보면 좋은 상품' })).toBeInTheDocument();
});
```

- [ ] **Step 3: 실패 확인** — `cd frontend && npm run test -- Detail OptionSelector` → FAIL
- [ ] **Step 4: 구현**
  - `OptionSelector`: `role="radiogroup"` + `role="radio"`. 옵션명 뒤에 `addPrice > 0`이면 `(+3,000원)`,
    `stock === 0`이면 `(품절)` + `disabled`. 천 단위 구분자는 기존 `Price` 포맷터를 재사용한다.
  - `QuantityStepper`: `-`/`+` 버튼에 `aria-label`("수량 줄이기"/"수량 늘리기"), 값은 `role="status"`로
    읽히게. 하한 1, 상한은 선택 옵션의 `stock`.
  - 담기 버튼: 옵션 미선택이면 `disabled`(옵션이 하나면 자동 선택). 클릭 시
    `addCartItem(goodsNo, optionNo, quantity)` → 성공 토스트(기존 `useToast` 재사용).
  - 설명 탭: TanStack Query의 `enabled: activeTab === 'description'`으로 지연 조회.
  - `RecommendedSection`: 응답이 빈 배열이면 **섹션을 렌더하지 않는다**(빈 EmptyState를 넣지 않는다 —
    추천은 없을 수 있는 것이지 사용자가 뭔가 해야 하는 상태가 아니다).
- [ ] **Step 5: 통과 확인** — `npm run test -- Detail OptionSelector` → PASS
- [ ] **Step 6: 스크린샷** — 390/768/1024/1440/1920. 옵션 2개 상품과 1개 상품 **둘 다** 찍는다.
- [ ] **Step 7: 커밋** — `git commit -am "fix(front): 상세 옵션 선택·수량·설명 지연로딩·추천 섹션 — 로드맵 이월 3건 해소"`

## Task 4-9: 장바구니 `/cart` (궁합 경고 배너 포함)

**Files:**
- Create: `frontend/src/pages/Cart.tsx` (+ `.css`)
- Create: `frontend/src/components/cart/CartLine.tsx` (+ `.css`)
- Create: `frontend/src/components/compat/CompatBanner.tsx` (+ `.css`)
- Create: `frontend/src/api/compat.ts`
- Modify: `frontend/src/api/cart.ts` (조회·수량변경·삭제·벌크 추가)
- Modify: `frontend/src/router.tsx`, `frontend/src/mocks/handlers.ts`
- Test: `frontend/src/pages/Cart.test.tsx`, `frontend/src/components/compat/CompatBanner.test.tsx`

**Interfaces (Produces):**

```ts
// api/cart.ts 에 추가
export interface CartItem {
  cartItemId: number; goodsNo: number; optionNo: number | null;
  goodsName: string; optionName: string;
  unitPrice: number; quantity: number; lineAmount: number;
}
export function fetchCartItems(): Promise<CartItem[]>;                       // GET /cart/items
export function updateCartQuantity(cartItemId: number, quantity: number): Promise<void>;  // PATCH /cart/items/{id}
export function removeCartItem(cartItemId: number): Promise<void>;           // DELETE /cart/items/{id}
export function addCartItemsBulk(
  items: { goodsNo: number; optionNo: number | null; quantity: number }[]): Promise<void>; // POST /cart/items/bulk

// api/compat.ts
export type CompatVerdict = 'CONFLICT' | 'CAUTION' | 'SYNERGY';
export interface CompatFinding {
  verdict: CompatVerdict; categoryA: string; categoryB: string; reason: string; goodsNos: number[];
}
export interface CompatCheckResult { overall: CompatVerdict | 'OK'; findings: CompatFinding[]; }
export function checkCompat(goodsNos: number[]): Promise<CompatCheckResult>;  // POST /compat/check
```

**구성:** 장바구니 라인 목록(수량 스텝퍼·삭제) + **궁합 경고 배너**(설계 8장 "적용 지점 ③") +
합계(안내용) + "주문하기" → `/order`. 비어 있으면 `EmptyState` + "상품 보러 가기".

**궁합 배너 규칙:** 장바구니가 바뀔 때마다 담긴 `goodsNos`로 `checkCompat`를 부른다.
`overall === 'OK'`면 배너를 그리지 않는다. `CONFLICT`는 danger 톤, `CAUTION`은 warning 톤,
`SYNERGY`는 **경고가 아니라 긍정 안내**로 문구를 바꾼다(같은 컴포넌트, 다른 톤).
`DESIGN.md` 규칙대로 **시그널 색을 배경으로 칠하지 않고** 좌측 보더 + 아이콘 + 텍스트로 표현한다.

- [ ] **Step 1: 실패 테스트**

```tsx
it('장바구니 라인과 합계를 보여준다', async () => {
  render(<Cart />);
  expect(await screen.findByText('그린티 토너')).toBeInTheDocument();
  expect(screen.getByTestId('cart-total')).toHaveTextContent('43,000');   // 20000*2 + 3000
});

it('수량을 바꾸면 PATCH를 부르고 합계가 갱신된다', async () => {
  render(<Cart />);
  await userEvent.click(await screen.findByRole('button', { name: '수량 늘리기' }));
  expect(updateCartQuantitySpy).toHaveBeenCalledWith(1, 3);
});

it('삭제하면 라인이 사라지고 토스트가 뜬다', async () => { /* … */ });

it('장바구니가 비면 EmptyState를 보여주고 주문하기 버튼을 감춘다', async () => {
  render(<Cart />);
  expect(await screen.findByRole('status')).toHaveTextContent(/비어 있/);
  expect(screen.queryByRole('button', { name: '주문하기' })).not.toBeInTheDocument();
});

it('CONFLICT면 경고 배너를 보여주고 이유를 읽어준다', async () => {
  // checkCompat → { overall:'CONFLICT', findings:[{verdict:'CONFLICT',categoryA:'AHA',categoryB:'레티노이드',reason:'자극 중첩',goodsNos:[1,2]}] }
  render(<Cart />);
  const banner = await screen.findByRole('alert');
  expect(banner).toHaveTextContent('자극 중첩');
});

it('OK면 배너를 그리지 않는다', async () => {
  render(<Cart />);
  await screen.findByText('그린티 토너');
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

it('CONFLICT여도 주문하기는 막지 않는다', async () => {
  // 궁합은 조언이지 금지가 아니다 — 설계 8장은 "경고+대체 제안"이지 차단이 아니다
  render(<Cart />);
  expect(await screen.findByRole('button', { name: '주문하기' })).toBeEnabled();
});
```

- [ ] **Step 2: 실패 확인** → FAIL
- [ ] **Step 3: 구현** — TanStack Query `useMutation` + `invalidateQueries(['cart'])`.
  궁합 조회는 담긴 goodsNos가 바뀔 때만 재조회(`queryKey: ['compat', goodsNos.join(',')]`).
  합계는 `lineAmount` 합(서버가 준 값 — 프론트에서 단가×수량을 다시 곱하지 않는다).
- [ ] **Step 4: 통과 확인** — `npm run test -- Cart CompatBanner` → PASS
- [ ] **Step 5: 스크린샷** — 5개 뷰포트. **CONFLICT 배너가 있는 상태와 없는 상태 둘 다.**
- [ ] **Step 6: 커밋** — `git commit -am "feat(front): 장바구니 화면 — 수량·삭제·궁합 경고 배너"`

## Task 4-10: 주문서 `/order` + 토스 결제창

**Files:**
- Modify: `frontend/package.json` (`@tosspayments/tosspayments-sdk` — 이 웨이브 한정 해제)
- Create: `frontend/src/pages/Order.tsx` (+ `.css`)
- Create: `frontend/src/components/order/AddressSection.tsx` (+ `.css`)
- Create: `frontend/src/api/order.ts`, `frontend/src/api/member.ts`
- Create: `frontend/src/features/payment/toss.ts`
- Modify: `frontend/src/router.tsx`, `frontend/src/mocks/handlers.ts`, `frontend/.env.example`
- Test: `frontend/src/pages/Order.test.tsx`, `frontend/src/features/payment/toss.test.ts`

**Interfaces (Produces):**

```ts
// api/order.ts
export interface OrderCreateRequest {
  items: { goodsNo: number; optionNo: number | null; quantity: number }[];
  receiverName: string; receiverPhone: string;
  zipcode: string; address1: string; address2: string;
  deliveryType: 'NORMAL';        // 오늘드림은 1차 범위 밖 — 값은 하나뿐이다
}
export interface OrderCreateResult { orderNo: string; payableAmount: number; }
export function createOrder(req: OrderCreateRequest): Promise<OrderCreateResult>;   // POST /orders
export function confirmPayment(
  orderNo: string, paymentKey: string, amount: number): Promise<{ orderNo: string; status: string; paidAmount: number }>;
                                                                                    // POST /payments/confirm
export function fetchOrders(): Promise<OrderSummary[]>;                             // GET /orders
export function fetchOrderDetail(orderNo: string): Promise<OrderDetail>;            // GET /orders/{orderNo}

// api/member.ts
export function fetchAddresses(): Promise<Address[]>;         // GET /members/me/addresses
export function createAddress(a: AddressInput): Promise<void>; // POST /members/me/addresses
export function fetchMe(): Promise<Me>;                        // GET /members/me
export function updateProfile(p: ProfileInput): Promise<void>; // PUT /members/me/profile

// features/payment/toss.ts
/** 토스 결제창을 연다. 성공하면 토스가 successUrl로 리다이렉트하므로 이 함수는 정상 경로에서 반환하지 않는다. */
export function requestTossPayment(params: {
  orderNo: string; orderName: string; amount: number; customerKey: string;
}): Promise<void>;
```

**토스 SDK 사용(확정):**

```ts
// features/payment/toss.ts
import { loadTossPayments } from '@tosspayments/tosspayments-sdk';

// 클라이언트 키는 공개 값이지만 환경마다 다르므로 코드에 박지 않는다(CLAUDE.md 시크릿 규칙의 연장).
const CLIENT_KEY = import.meta.env.VITE_TOSS_CLIENT_KEY;

export async function requestTossPayment({ orderNo, orderName, amount, customerKey }) {
  const tossPayments = await loadTossPayments(CLIENT_KEY);
  const payment = tossPayments.payment({ customerKey });
  await payment.requestPayment({
    method: 'CARD',
    amount: { currency: 'KRW', value: amount },   // 서버가 준 payableAmount 그대로. 프론트가 계산하지 않는다.
    orderId: orderNo,
    orderName,
    successUrl: `${window.location.origin}/order/complete`,
    failUrl: `${window.location.origin}/order/fail`,
    card: { useEscrow: false, flowMode: 'DEFAULT', useCardPoint: false, useAppCardOnly: false },
  });
}
```

`customerKey`는 회원 식별자를 그대로 노출하지 않도록 `bb-${memberId}` 형태로 만든다(토스가 요구하는
비-추측성은 이 프로젝트 범위에서 과한 요구라 단순 접두사로 둔다 — 이 판단을 주석으로 남긴다).

`.env.example`에 남길 값:
```
# 토스페이먼츠 공개 테스트 클라이언트 키(문서 공개값). 운영 키를 여기 넣지 않는다.
VITE_TOSS_CLIENT_KEY=test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm
```

**흐름:** 장바구니에서 넘어온 항목 표시(읽기) → 배송지 선택(기본배송지 자동 선택, 없으면 직접 입력) →
배송유형 → "결제하기" → `createOrder` → **서버가 준 `payableAmount`로** `requestTossPayment`.

- [ ] **Step 1: 실패 테스트**

```tsx
it('기본배송지가 있으면 자동으로 선택돼 있다', async () => {
  render(<Order />);
  expect(await screen.findByRole('radio', { name: /집 · 서울/ })).toBeChecked();
});

it('배송지가 없으면 직접 입력 폼을 펼친다', async () => {
  render(<Order />);
  expect(await screen.findByLabelText('받는 분')).toBeInTheDocument();
});

it('받는 분 정보가 비면 결제하기가 막히고 에러가 role=alert로 뜬다', async () => {
  render(<Order />);
  await userEvent.click(await screen.findByRole('button', { name: '결제하기' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(/받는 분/);
  expect(createOrderSpy).not.toHaveBeenCalled();
});

it('결제하기는 주문을 만든 뒤 서버가 준 payableAmount로 결제창을 연다', async () => {
  // createOrder → { orderNo: 'ORD-1', payableAmount: 43000 }  (화면 합계와 일부러 다른 값을 준다)
  render(<Order />);
  await userEvent.click(await screen.findByRole('button', { name: '결제하기' }));
  expect(requestTossPaymentSpy).toHaveBeenCalledWith(
    expect.objectContaining({ orderNo: 'ORD-1', amount: 43000 }),
  );
});

it('주문 생성이 재고 부족으로 실패하면 결제창을 열지 않고 에러를 보여준다', async () => {
  // createOrder가 ORDER_OUT_OF_STOCK로 reject
  render(<Order />);
  await userEvent.click(await screen.findByRole('button', { name: '결제하기' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(/재고/);
  expect(requestTossPaymentSpy).not.toHaveBeenCalled();
});

it('결제하기를 연타해도 주문은 한 번만 생성된다', async () => {
  render(<Order />);
  const btn = await screen.findByRole('button', { name: '결제하기' });
  await userEvent.click(btn);
  await userEvent.click(btn);
  expect(createOrderSpy).toHaveBeenCalledTimes(1);   // 제출 중 disabled
});
```

- [ ] **Step 2: 실패 확인** → FAIL
- [ ] **Step 3: 구현** — 제출 중 버튼 `disabled`(연타 방어). 서버 에러 메시지는 `ApiResponse` 봉투의
  메시지를 그대로 보여준다(프론트가 문구를 새로 지어내면 서버와 갈라진다).
- [ ] **Step 4: 통과 확인** — `npm run test -- Order toss` → PASS
- [ ] **Step 5: 스크린샷** — 5개 뷰포트
- [ ] **Step 6: 커밋** — `git commit -am "feat(front): 주문서 화면 + 토스 결제창 연동"`

## Task 4-11: 결제 완료 `/order/complete` · 실패 `/order/fail`

**Files:**
- Create: `frontend/src/pages/OrderComplete.tsx` (+ `.css`), `frontend/src/pages/OrderFail.tsx` (+ `.css`)
- Modify: `frontend/src/router.tsx`, `frontend/src/mocks/handlers.ts`
- Test: `frontend/src/pages/OrderComplete.test.tsx`

**계약:** 토스는 성공 시 `successUrl?paymentKey=…&orderId=…&amount=…`로, 실패 시
`failUrl?code=…&message=…&orderId=…`로 리다이렉트한다. **이 쿼리 이름은 토스가 정한 것이라 바꿀 수 없다.**
완료 화면은 마운트 시 `confirmPayment(orderId, paymentKey, Number(amount))`를 **정확히 한 번** 부른다.

- [ ] **Step 1: 실패 테스트**

```tsx
it('쿼리의 paymentKey·orderId·amount로 승인을 한 번만 요청한다', async () => {
  renderAt('/order/complete?paymentKey=pk_1&orderId=ORD-1&amount=43000');
  await screen.findByText(/주문이 완료/);
  expect(confirmPaymentSpy).toHaveBeenCalledTimes(1);
  expect(confirmPaymentSpy).toHaveBeenCalledWith('ORD-1', 'pk_1', 43000);
});

it('승인 성공 시 주문번호와 결제 금액을 보여준다', async () => {
  renderAt('/order/complete?paymentKey=pk_1&orderId=ORD-1&amount=43000');
  expect(await screen.findByText('ORD-1')).toBeInTheDocument();
  expect(screen.getByText(/43,000/)).toBeInTheDocument();
});

it('금액 불일치로 승인이 실패하면 실패 안내를 보여주고 완료로 오인시키지 않는다', async () => {
  // confirmPayment가 PAYMENT_AMOUNT_MISMATCH로 reject
  renderAt('/order/complete?paymentKey=pk_1&orderId=ORD-1&amount=1');
  expect(await screen.findByRole('alert')).toHaveTextContent(/일치하지 않/);
  expect(screen.queryByText(/주문이 완료/)).not.toBeInTheDocument();
});

it('쿼리 파라미터가 없으면 승인을 부르지 않고 잘못된 접근으로 안내한다', async () => {
  renderAt('/order/complete');
  expect(await screen.findByRole('alert')).toBeInTheDocument();
  expect(confirmPaymentSpy).not.toHaveBeenCalled();
});
```

- [ ] **Step 2: 실패 확인** → FAIL
- [ ] **Step 3: 구현** — 승인은 `useMutation` + `useRef` 가드로 **StrictMode 이중 마운트에서도 한 번만**
  나가게 한다(개발 모드에서 승인이 두 번 나가면 두 번째가 `PAYMENT_ALREADY_CONFIRMED`로 실패해
  성공한 결제를 실패로 표시하게 된다 — 실제로 걸리기 쉬운 함정이다).
  승인 성공 후 `queryClient.invalidateQueries(['cart'])`(주문이 끝났으니 장바구니를 다시 읽는다).
- [ ] **Step 4: 통과 확인** — `npm run test -- OrderComplete` → PASS
- [ ] **Step 5: 스크린샷** — 성공·실패 각각 5개 뷰포트
- [ ] **Step 6: 커밋** — `git commit -am "feat(front): 결제 완료·실패 화면 — 토스 리다이렉트 승인 처리"`

## Task 4-12: 루틴 가이드 `/routine`

설계 8장 그대로: 프로필/퀴즈 → 단계 카드 → 궁합 검사 → "루틴 전체 담기".

**Files:**
- Create: `frontend/src/pages/Routine.tsx` (+ `.css`)
- Create: `frontend/src/components/routine/SkinTypeQuiz.tsx` (+ `.css`)
- Create: `frontend/src/components/routine/RoutineStepCard.tsx` (+ `.css`)
- Create: `frontend/src/api/routine.ts`
- Create: `frontend/src/features/routine/skinProfile.ts` (localStorage + 승격)
- Modify: `frontend/src/router.tsx`, `frontend/src/mocks/handlers.ts`
- Test: `frontend/src/pages/Routine.test.tsx`,
  `frontend/src/components/routine/SkinTypeQuiz.test.tsx`,
  `frontend/src/features/routine/skinProfile.test.ts`

**Interfaces (Produces):**

```ts
// api/routine.ts
export type SkinType = 'DRY' | 'OILY' | 'COMBINATION' | 'SENSITIVE';
export interface RoutineStepResponse {
  stepOrder: number; stepName: string; beginnerTip: string; recommendations: GoodsListItem[];
}
export interface RoutineResponse {
  templateId: number; name: string; skinType: SkinType; time: string;
  description: string; steps: RoutineStepResponse[];
}
export function fetchRoutine(skinType?: SkinType, time?: string): Promise<RoutineResponse>;  // GET /routines

// features/routine/skinProfile.ts
export function readLocalSkinType(): SkinType | null;      // localStorage 'bb.skinType'
export function writeLocalSkinType(t: SkinType): void;
export function clearLocalSkinType(): void;
/** 로그인했고 서버 프로필이 비어 있으면 로컬 퀴즈 결과를 PUT /members/me/profile 로 한 번 승격하고 로컬을 비운다. */
export async function promoteLocalSkinTypeIfNeeded(me: Me): Promise<void>;
```

**퀴즈 3문항(확정 — 설계 8장 "3문항"):** 각 문항의 선택지에 피부타입 가중치를 주고 최다 득표를 고른다.
동점이면 `COMBINATION`(가장 무난한 기본값)으로 떨어뜨린다. 문항·가중치는 상수 하나로 모은다:

```ts
// 세수 후 30분 뒤 / 오후 T존 / 자극 반응 — 이 셋이 피부타입을 가르는 최소 질문이다.
const QUIZ: { question: string; options: { label: string; weight: Partial<Record<SkinType, number>> }[] }[] = [
  { question: '세수하고 30분 뒤 얼굴은 어떤가요?',
    options: [
      { label: '당기고 각질이 인다',       weight: { DRY: 2 } },
      { label: '번들거린다',               weight: { OILY: 2 } },
      { label: 'T존만 번들거린다',         weight: { COMBINATION: 2 } },
      { label: '붉어지거나 따갑다',        weight: { SENSITIVE: 2 } },
    ] },
  { question: '오후가 되면 이마·코가…',
    options: [
      { label: '거의 그대로다',            weight: { DRY: 1, SENSITIVE: 1 } },
      { label: '기름종이가 필요하다',      weight: { OILY: 2 } },
      { label: 'T존만 필요하다',           weight: { COMBINATION: 2 } },
    ] },
  { question: '새 화장품을 쓰면?',
    options: [
      { label: '별 반응 없다',             weight: { OILY: 1, COMBINATION: 1 } },
      { label: '가끔 뒤집어진다',          weight: { SENSITIVE: 1 } },
      { label: '자주 따갑고 붉어진다',     weight: { SENSITIVE: 2 } },
    ] },
];
```

- [ ] **Step 1: 실패 테스트 — 퀴즈·프로필**

```ts
it('최다 득표 피부타입을 고른다', () => {
  expect(scoreQuiz([0, 1, 0])).toBe('DRY');       // DRY 2 / OILY 2+1 → OILY… 실제 기대값은 구현 시 표로 검증
});

it('동점이면 COMBINATION으로 떨어진다', () => {
  expect(scoreQuiz([2, 2, 0])).toBe('COMBINATION');
});
```

```tsx
it('로그인 회원이고 서버 프로필에 skinType이 있으면 퀴즈를 보여주지 않는다', async () => {
  render(<Routine />);
  expect(await screen.findByRole('heading', { name: /루틴/ })).toBeInTheDocument();
  expect(screen.queryByText(/세수하고 30분/)).not.toBeInTheDocument();
});

it('프로필도 로컬 결과도 없으면 퀴즈부터 보여준다', async () => {
  render(<Routine />);
  expect(await screen.findByText(/세수하고 30분/)).toBeInTheDocument();
});

it('퀴즈를 마치면 결과를 localStorage에 저장하고 그 타입으로 루틴을 조회한다', async () => {
  render(<Routine />);
  /* 3문항 응답 */
  expect(fetchRoutineSpy).toHaveBeenCalledWith('DRY', 'BASIC');
  expect(readLocalSkinType()).toBe('DRY');
});

it('로그인했고 서버 프로필이 비어 있으면 로컬 결과를 한 번 승격하고 로컬을 비운다', async () => {
  await promoteLocalSkinTypeIfNeeded({ memberId: 1, skinType: null } as Me);
  expect(updateProfileSpy).toHaveBeenCalledWith(expect.objectContaining({ skinType: 'DRY' }));
  expect(readLocalSkinType()).toBeNull();
});
```

- [ ] **Step 2: 실패 테스트 — 단계 카드·궁합·전체 담기**

```tsx
it('5단계를 순서대로 보여주고 단계마다 추천을 카드로 그린다', async () => {
  render(<Routine />);
  const steps = await screen.findAllByTestId('routine-step');
  expect(steps).toHaveLength(5);
  expect(within(steps[0]).getByText('클렌징')).toBeInTheDocument();
  expect(within(steps[0]).getAllByTestId('goods-card').length).toBeGreaterThan(0);
});

it('단계마다 첫 추천이 기본 선택돼 있다', async () => {
  // 설계 8장 "추천 2~3개, 기본 선택" — 아무것도 안 고른 상태로 전체 담기를 누를 수 없으면 플로우가 끊긴다
  render(<Routine />);
  expect(await screen.findAllByRole('radio', { checked: true })).toHaveLength(5);
});

it('선택을 바꾸면 궁합을 다시 검사한다', async () => {
  render(<Routine />);
  await userEvent.click((await screen.findAllByRole('radio'))[1]);
  expect(checkCompatSpy).toHaveBeenLastCalledWith(expect.arrayContaining([expect.any(Number)]));
});

it('CONFLICT면 담기 전에 경고를 보여준다', async () => {
  render(<Routine />);
  expect(await screen.findByRole('alert')).toHaveTextContent(/자극 중첩/);
});

it('루틴 전체 담기는 선택된 5개를 한 번에 담고 장바구니로 보낸다', async () => {
  render(<Routine />);
  await userEvent.click(await screen.findByRole('button', { name: '루틴 전체 담기' }));
  expect(addCartItemsBulkSpy).toHaveBeenCalledWith(
    expect.arrayContaining([expect.objectContaining({ quantity: 1 })]),
  );
  expect(addCartItemsBulkSpy.mock.calls[0][0]).toHaveLength(5);
});
```

- [ ] **Step 3: 실패 확인** → FAIL
- [ ] **Step 4: 구현** — 단계별 선택은 `role="radiogroup"`. 궁합은 선택 조합이 바뀔 때만 재조회
  (`queryKey: ['compat', selected.join(',')]`). 전체 담기는 `addCartItemsBulk` 한 번 →
  성공 토스트 → `navigate('/cart')`. **CONFLICT여도 담기를 막지 않는다**(장바구니와 같은 판단).
  `optionNo`는 카드에 옵션 정보가 없으므로 `null`로 보낸다 — 서버가 기본 옵션을 고른다.
  **`null`을 서버가 어떻게 다루는지 `CartService`에서 확인하고, 기본 옵션 선택이 없으면 보고한다.**
- [ ] **Step 5: 통과 확인** — `npm run test -- Routine SkinTypeQuiz skinProfile` → PASS
- [ ] **Step 6: 스크린샷** — 퀴즈 화면과 결과 화면 각각 5개 뷰포트
- [ ] **Step 7: 커밋** — `git commit -am "feat(front): 루틴 가이드 — 퀴즈·단계 카드·궁합 검사·전체 담기"`

## Task 4-13: 마이페이지 `/mypage/*`

**Files:**
- Create: `frontend/src/pages/mypage/MyPageLayout.tsx` (+ `.css`)
- Create: `frontend/src/pages/mypage/MyOrders.tsx`, `MyWishlist.tsx`, `MyReviews.tsx`, `MyProfile.tsx` (+ 각 `.css`)
- Create: `frontend/src/api/wishlist.ts`
- Modify: `frontend/src/api/review.ts` (`fetchMyReviews`), `frontend/src/router.tsx`,
  `frontend/src/mocks/handlers.ts`, `frontend/src/components/layout/Header.tsx` (마이페이지 진입 링크)
- Test: `frontend/src/pages/mypage/MyOrders.test.tsx`, `MyWishlist.test.tsx`, `MyProfile.test.tsx`

**Interfaces (Produces):**

```ts
// api/wishlist.ts
export function fetchWishlist(): Promise<GoodsListItem[]>;       // GET /wishlist
export function addWish(goodsNo: number): Promise<void>;         // POST /wishlist/{goodsNo}
export function removeWish(goodsNo: number): Promise<void>;      // DELETE /wishlist/{goodsNo}

// api/review.ts 에 추가
export function fetchMyReviews(page?: number): Promise<PageResponse<MyReviewItem>>;   // GET /reviews/me
```

**라우팅:** `/mypage`(index → orders 리다이렉트)·`/mypage/orders`·`/mypage/orders/:orderNo`·
`/mypage/wishlist`·`/mypage/reviews`·`/mypage/profile`. 전부 `RequireAuth` 아래.
`MyPageLayout`은 좌측(모바일은 상단 가로 스크롤) 탭 네비 + `<Outlet />`.

- [ ] **Step 1: 실패 테스트**

```tsx
it('주문 목록은 "대표상품 외 N건" 형태로 보여준다', async () => {
  // { representativeGoodsName:'그린티 토너', itemCount:3 }
  render(<MyOrders />);
  expect(await screen.findByText('그린티 토너 외 2건')).toBeInTheDocument();
});

it('주문이 없으면 EmptyState를 보여준다', async () => { /* role=status */ });

it('주문을 누르면 상세로 이동해 스냅샷 배송지와 금액을 보여준다', async () => { /* … */ });

it('찜 목록에서 하트를 끄면 카드가 사라진다', async () => {
  render(<MyWishlist />);
  await userEvent.click(await screen.findByRole('button', { name: /찜 해제/ }));
  expect(removeWishSpy).toHaveBeenCalledWith(1);
});

it('프로필 저장은 PUT /members/me/profile을 부르고 성공 토스트를 띄운다', async () => {
  render(<MyProfile />);
  await userEvent.click(await screen.findByRole('radio', { name: '건성' }));
  await userEvent.click(screen.getByRole('button', { name: '저장' }));
  expect(updateProfileSpy).toHaveBeenCalledWith(expect.objectContaining({ skinType: 'DRY' }));
});
```

- [ ] **Step 2: 실패 확인** → FAIL
- [ ] **Step 3: 구현** — 배송지 관리(추가·수정·삭제·기본 지정)는 `MyProfile` 안 섹션으로 둔다.
  **기본배송지 지정은 4-2의 DB 제약이 지켜주므로 프론트가 다중 기본을 걱정하지 않는다.**
- [ ] **Step 4: 통과 확인** — `npm run test -- mypage` → PASS
- [ ] **Step 5: 스크린샷** — 4개 탭 각각 5개 뷰포트
- [ ] **Step 6: 커밋** — `git commit -am "feat(front): 마이페이지 — 주문내역·찜·내 리뷰·프로필/배송지"`

## Task 4-14: 리뷰·문의 작성 + admin 화면 `/admin/*`

로드맵이 "Wave 3 상세는 읽기 전용"으로 남긴 것과 admin 최소 CRUD를 함께 닫는다.

**Files:**
- Create: `frontend/src/components/goods/ReviewForm.tsx` (+ `.css`), `QnaForm.tsx` (+ `.css`)
- Modify: `frontend/src/components/goods/DetailTabs.tsx`, `ReviewList.tsx`, `QnaList.tsx`
- Modify: `frontend/src/api/review.ts` (`createReview`·`markHelpful`), `frontend/src/api/qna.ts` (`createQna`)
- Create: `frontend/src/pages/admin/AdminLayout.tsx`, `AdminGoods.tsx`, `AdminRoutine.tsx`, `AdminQna.tsx` (+ 각 `.css`)
- Create: `frontend/src/api/admin.ts`
- Create: `frontend/src/components/auth/RequireAdmin.tsx`
- Modify: `frontend/src/router.tsx`, `frontend/src/mocks/handlers.ts`, `frontend/src/stores/authStore.ts` (role 보관)
- Test: `frontend/src/components/goods/ReviewForm.test.tsx`,
  `frontend/src/pages/admin/AdminGoods.test.tsx`,
  `frontend/src/components/auth/RequireAdmin.test.tsx`

- [ ] **Step 1: 실패 테스트 — 작성 폼**

```tsx
it('비로그인이면 리뷰 작성 폼 대신 로그인 안내를 보여준다', async () => { /* … */ });

it('별점을 고르지 않으면 제출되지 않고 에러가 role=alert로 뜬다', async () => {
  render(<ReviewForm goodsNo={1} />);
  await userEvent.click(screen.getByRole('button', { name: '등록' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(/별점/);
  expect(createReviewSpy).not.toHaveBeenCalled();
});

it('구매하지 않은 상품이면 서버 메시지를 그대로 보여준다', async () => {
  // createReview가 REVIEW_NOT_PURCHASED로 reject
  expect(await screen.findByRole('alert')).toHaveTextContent(/구매한 상품에만/);
});

it('등록에 성공하면 목록을 다시 읽고 폼을 비운다', async () => { /* invalidateQueries 확인 */ });
```

- [ ] **Step 2: 실패 테스트 — admin 가드·화면**

```tsx
it('ADMIN이 아니면 /admin은 메인으로 돌려보낸다', async () => {
  renderAt('/admin/goods', { role: 'USER' });
  expect(await screen.findByText(/뷰티보이/)).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: '상품 관리' })).not.toBeInTheDocument();
});

it('ADMIN은 상품 목록에서 숨김 상품도 본다', async () => {
  renderAt('/admin/goods', { role: 'ADMIN' });
  expect(await screen.findByText('숨김')).toBeInTheDocument();
});

it('삭제를 누르면 확인을 거친 뒤 DELETE를 부르고 목록을 다시 읽는다', async () => {
  renderAt('/admin/goods', { role: 'ADMIN' });
  await userEvent.click(await screen.findByRole('button', { name: /삭제/ }));
  await userEvent.click(screen.getByRole('button', { name: '확인' }));
  expect(deleteGoodsSpy).toHaveBeenCalledWith(1);
});
```

- [ ] **Step 3: 실패 확인** → FAIL
- [ ] **Step 4: 구현**
  - `RequireAdmin`: `authStore`의 role이 `ADMIN`이 아니면 `<Navigate to="/main" replace />`.
    **이것은 편의 가드일 뿐 보안이 아니다** — 진짜 판정은 서버의 `@PreAuthorize`다. 주석으로 남긴다.
  - `authStore`에 `role`을 보관한다. 출처는 `GET /members/me` 응답이다(**JWT를 프론트에서 디코드해
    role을 꺼내지 않는다** — 토큰 파싱을 클라이언트가 시작하면 신뢰 경계가 흐려진다).
  - admin 화면은 **테이블 + 인라인 편집**의 최소 형태. 화려할 이유가 없고, `DESIGN.md`의
    편집디자인 톤(흰 캔버스 + 검정 잉크 + 무채색 사다리)을 그대로 쓴다.
- [ ] **Step 5: 통과 확인** — `npm run test` 전체 → PASS
- [ ] **Step 6: 스크린샷** — 상세 작성 폼 + admin 3화면, 각 5개 뷰포트
- [ ] **Step 7: 커밋** — `git commit -am "feat(front): 리뷰·문의 작성 + 관리자 화면(상품·루틴·문의)"`

---

# C. 마감 (Task 4-15 ~ 4-17)

## Task 4-15: 시드 확충 — 회원 · 상품 150개 · 리뷰 (V61~V63)

**Files:**
- Create: `backend/src/main/resources/db/migration/V61__seed_member.sql`
- Create: `backend/src/main/resources/db/migration/V62__seed_goods_bulk.sql`
- Create: `backend/src/main/resources/db/migration/V63__seed_review.sql`
- Modify: `backend/src/test/java/com/beautyboy/**/FlywayMigrationSmokeTest.java` (61·62·63 단언 추가)

**V61 — 시드 회원.** admin 계정이 없으면 Task 4-14의 admin 화면을 확인할 방법이 없다.

- [ ] **Step 1: bcrypt 해시 생성** — 비밀번호는 셋 다 `seed1234!`로 통일한다(README에 적을 것이므로
  운영 비밀이 아니다). 해시는 손으로 짓지 말고 **프로젝트의 인코더로 생성**한다:

```bash
cd backend && ./gradlew -q test --tests '*PasswordHashPrinter*'
# 없으면 임시 테스트를 하나 만들어 new BCryptPasswordEncoder().encode("seed1234!") 를 출력시키고,
# 값을 V61에 붙여넣은 뒤 그 임시 테스트는 지운다.
```

시드 회원 3명: `admin@beautyboy.dev`(role=`ADMIN`), `dry@beautyboy.dev`(skin_type=`DRY`),
`oily@beautyboy.dev`(skin_type=`OILY`). 일반 회원 둘에는 `member_profile`과 **기본배송지 1건씩**을 심는다
(주문서 화면의 "기본배송지 자동 선택"을 시드만으로 확인할 수 있게 된다).

**V62 — 상품 150개.** 개수보다 분포가 중요하다(결정 8). 생성 규칙과 불변식:

| 축 | 규칙 |
|---|---|
| 카테고리 | 루틴 5단계 카테고리(`C002`·`C001001`·`C001002`·`C001003`·`C004001`)에 **각 20개 이상**, 나머지 50개는 `C003`·`C005`·`C006`·`C004002`·`C004003`에 분산 |
| 브랜드 | 가상 브랜드 12~15개. 한 브랜드에 상품이 5개 이상 붙어 브랜드 필터가 의미를 갖게 |
| 가격 | `list_price` 8,000~120,000. `sale_price ≤ list_price`, **할인율 0%인 상품이 30% 이상** (할인 정렬이 전부 세일 상품이면 정렬 검증이 안 된다) |
| 상태 | `NORMAL` 145개 + **`HIDDEN` 5개** (admin 목록과 일반 목록의 차이를 시드로 확인할 수 있게) |
| 옵션 | 상품당 1~3개. **`add_price > 0`인 옵션이 있는 상품 30개 이상**, **`stock = 0`인 품절 옵션 5개 이상** |
| 성분 | 상품당 3~6개를 `goods_ingredient`로 연결. **`ingredient_rule`의 CONFLICT 분류쌍(AHA×레티노이드 등)을 동시에 갖는 상품 조합이 실제로 존재**해야 `/compat/check`가 시드만으로 CONFLICT를 낸다 |
| 조회/판매 | `view_count`·`sales_count`를 0이 아닌 값으로 흩뿌린다(랭킹 정렬이 전부 동점이면 검증이 안 된다) |
| 썸네일 | 무료 스톡 또는 플레이스홀더 URL. **실제 브랜드 제품 사진 금지**(설계 11장) |

- [ ] **Step 2: V62 작성** — 위 표를 만족하는 `INSERT`. **기존 V12의 브랜드·카테고리 코드를 재사용**하고
  goods id는 V12가 쓴 번호와 겹치지 않게 이어 붙인다(V12를 먼저 열어 마지막 id를 확인한다).
- [ ] **Step 3: 불변식 검증 쿼리** — 시드가 의도대로 들어갔는지 SQL로 확인하고 결과를 보고서에 남긴다.
  **눈으로 훑지 말고 이 쿼리를 돌린다** — 150행은 눈으로 못 센다.

```sql
-- 1) 총 개수와 상태 분포
SELECT status, COUNT(*) FROM goods GROUP BY status;              -- NORMAL≥145, HIDDEN≥5
-- 2) 루틴 5단계 카테고리 커버리지
SELECT category_code, COUNT(*) FROM goods
 WHERE category_code IN ('C002','C001001','C001002','C001003','C004001')
 GROUP BY category_code;                                          -- 각 ≥20
-- 3) 할인 없는 상품 비율
SELECT SUM(list_price = sale_price) / COUNT(*) FROM goods;        -- ≥0.30
-- 4) 브랜드당 상품 수
SELECT brand_id, COUNT(*) c FROM goods GROUP BY brand_id ORDER BY c;  -- 최소값 ≥5
-- 5) 유료 옵션 / 품절 옵션
SELECT SUM(add_price > 0), SUM(stock = 0) FROM goods_option;      -- 각각 ≥30, ≥5
-- 6) 궁합 CONFLICT가 실제로 잡히는 상품 쌍이 있는가
SELECT COUNT(*) FROM goods_ingredient gi1
  JOIN ingredient i1 ON i1.id = gi1.ingredient_id
  JOIN goods_ingredient gi2 ON gi2.goods_id <> gi1.goods_id
  JOIN ingredient i2 ON i2.id = gi2.ingredient_id
  JOIN ingredient_rule r ON r.verdict = 'CONFLICT'
   AND ((r.category_a = i1.category AND r.category_b = i2.category)
     OR (r.category_a = i2.category AND r.category_b = i1.category));  -- >0
```

**V63 — 리뷰 + 통계.** 별점이 0으로 보이는 것(Task 4-1의 소비처)을 시드로 실제로 확인하려면
리뷰 행이 있어야 한다. 통계는 **손으로 적지 말고 리뷰에서 계산해 넣는다** — 손으로 적으면
언젠가 리뷰와 통계가 어긋난다.

```sql
-- V63__seed_review.sql
INSERT INTO review (goods_id, member_id, rating, content, skin_type, created_at) VALUES
  -- 상품 40개 × 2~3건. rating은 3~5를 섞어 평균이 4.x로 갈리게 한다.
  -- (구매 인증은 시드에서 검증하지 않는다 — order 없이 심는다. 화면 표시가 목적이다.)
  ...;

-- 통계는 리뷰에서 계산한다. 이 INSERT...SELECT가 곧 "통계의 정의"다.
INSERT INTO goods_review_stat (goods_id, review_count, rating_sum, updated_at)
SELECT goods_id, COUNT(*), SUM(rating), NOW()
  FROM review GROUP BY goods_id
ON DUPLICATE KEY UPDATE
  review_count = VALUES(review_count),
  rating_sum   = VALUES(rating_sum),
  updated_at   = VALUES(updated_at);
```

- [ ] **Step 4: V63 작성 + 스모크 단언 갱신** — `FlywayMigrationSmokeTest`의 적용 버전 목록에 61·62·63 추가.
- [ ] **Step 5:** `./gradlew integrationTest` → PASS (실 MySQL에 V60~V63 전부 적용 + `validate`)
- [ ] **Step 6: 화면 확인** — 앱+프론트 기동 후 목록·검색·랭킹에서 **별점이 0이 아닌 카드가 보이는지**
  눈으로 확인한다(Task 4-1이 실제로 살아 있는지의 최종 증거). 스크린샷 1장을 보고서에 남긴다.
- [ ] **Step 7: 커밋** — `git commit -am "feat(seed): V61~V63 — 시드 회원(admin 포함)·상품 150개·리뷰"`

## Task 4-16: Playwright E2E — 탐색→루틴 담기→주문→결제

**Files:**
- Modify: `frontend/package.json` (`@playwright/test` + `test:e2e` 스크립트 — 이 웨이브 한정 해제)
- Create: `frontend/playwright.config.ts`
- Create: `frontend/e2e/checkout.spec.ts`
- Create: `frontend/e2e/fixtures/auth.ts` (시드 계정 로그인 헬퍼)
- Create: `docs/plans/e2e-실행법.md` **아니라** → README에 절로 추가(Task 4-17)

**전제(결정 5):** 백엔드는 `e2e` 프로필로, 프론트는 `vite preview`(또는 `dev`)로 뜬다.
DB는 실 MySQL이며 V61~V63 시드가 들어 있어야 한다. **테스트가 인프라를 띄우지 않는다** —
`playwright.config.ts`의 `webServer`는 프론트만 띄우고, 백엔드·MySQL은 사람이 띄운다(README에 적는다).
이유: 백엔드 기동에 JWT/토스 환경변수가 필요해 Playwright가 대신 관리하면 실패 원인이 흐려진다.

- [ ] **Step 1: 설정**

```ts
// frontend/playwright.config.ts
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  retries: 0,                               // 재시도로 플래키를 가리지 않는다. 깨지면 원인을 본다.
  use: { baseURL: 'http://localhost:5173', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
  },
});
```

```json
// package.json scripts 에 추가
"test:e2e": "playwright test"
```

- [ ] **Step 2: 실패 테스트 — 전 구간 플로우**

```ts
// e2e/checkout.spec.ts
test('탐색 → 루틴 전체 담기 → 장바구니 → 주문 → 결제 승인 → 완료', async ({ page }) => {
  await loginAsSeedUser(page, 'dry@beautyboy.dev', 'seed1234!');

  // 1) 루틴 가이드에서 전체 담기
  await page.goto('/routine');
  await expect(page.getByTestId('routine-step')).toHaveCount(5);
  await page.getByRole('button', { name: '루틴 전체 담기' }).click();

  // 2) 장바구니에 5줄
  await expect(page).toHaveURL(/\/cart/);
  await expect(page.getByTestId('cart-line')).toHaveCount(5);

  // 3) 주문서 → 주문 생성. 서버가 준 금액을 화면에서 읽어 결제 단계로 넘긴다.
  await page.getByRole('button', { name: '주문하기' }).click();
  await expect(page).toHaveURL(/\/order/);
  await page.getByRole('button', { name: '결제하기' }).click();

  // 4) 토스 결제창은 자동화하지 않는다(결정 5). 토스가 보내는 성공 리다이렉트를 그대로 재현한다.
  const { orderNo, payableAmount } = await readCreatedOrder(page);   // 네트워크 응답에서 캡처
  await page.goto(`/order/complete?paymentKey=pk_e2e_${orderNo}&orderId=${orderNo}&amount=${payableAmount}`);

  // 5) 승인 검증은 진짜 PaymentService가 한다 — 가짜인 것은 게이트웨이 네트워크 호출뿐이다.
  await expect(page.getByText('주문이 완료되었습니다')).toBeVisible();
  await expect(page.getByText(orderNo)).toBeVisible();

  // 6) 마이페이지 주문내역에 남는다
  await page.goto('/mypage/orders');
  await expect(page.getByText(orderNo)).toBeVisible();
});

test('금액을 위조한 승인 요청은 거부되고 완료로 표시되지 않는다', async ({ page }) => {
  // 결제 2단계 검증이 화면까지 이어지는지 — 이 프로젝트에서 가장 설명할 값어치가 있는 경로다
  await loginAsSeedUser(page, 'dry@beautyboy.dev', 'seed1234!');
  const orderNo = await createOrderViaUi(page);
  await page.goto(`/order/complete?paymentKey=pk_e2e&orderId=${orderNo}&amount=100`);
  await expect(page.getByRole('alert')).toContainText('일치하지 않');
  await expect(page.getByText('주문이 완료되었습니다')).toHaveCount(0);
});

test('비로그인으로 보호 화면에 가면 로그인으로 보낸다', async ({ page }) => {
  await page.goto('/cart');
  await expect(page).toHaveURL(/\/login/);
});
```

- [ ] **Step 3: 실패 확인** — 백엔드를 `e2e` 프로필로 띄우고 `npm run test:e2e` → FAIL
- [ ] **Step 4: 구현** — 위 스펙이 통과할 때까지 화면의 `data-testid`(`routine-step`·`cart-line`·
  `cart-total`·`detail-price`)를 맞춘다. **`data-testid`를 새로 뿌리기 전에 접근성 역할(role/name)로
  잡을 수 있는지 먼저 본다** — 역할로 잡히면 그것이 곧 접근성 검증도 된다.
- [ ] **Step 5: 통과 확인** — `npm run test:e2e` → 3건 PASS. **trace 파일 경로를 보고서에 남긴다.**
- [ ] **Step 6: 실제 토스 결제창 수동 확인** — `e2e` 프로필이 **아닌** local 프로필로 띄우고
  `TOSS_SECRET_KEY`·`VITE_TOSS_CLIENT_KEY` 테스트 키로 주문서→결제하기→토스 테스트 카드 승인까지
  사람이 한 번 통과시킨다. 완료 화면 스크린샷을 보고서에 남긴다. **이것이 결정 5가 자동화에서
  잘라낸 유일한 구간이므로, 수동으로라도 한 번은 반드시 통과해야 W4 DoD가 성립한다.**
- [ ] **Step 7: 커밋** — `git commit -am "test(e2e): Playwright — 루틴 담기부터 결제 완료까지 전 구간"`

## Task 4-17: README · 로드맵 갱신 · 전체 녹색 확인

**Files:**
- Modify: `README.md`
- Modify: `docs/plans/2026-07-23-roadmap.md`
- Modify: `CLAUDE.md` ("현재 상태" 한 줄)

- [ ] **Step 1: README 개정** — 지금 README는 "Wave 0 완료, 나머지 미구현"에 멈춰 있다. 아래를 담는다:
  - **현재 상태**를 "1차 MVP 완성"으로 교체하고 구현된 기능을 한 문단으로.
  - **실행법**: MySQL(+3306 충돌 함정은 유지) · **Redis**(신규, 선택) · `JWT_SECRET` ·
    `TOSS_SECRET_KEY` · 프론트 `.env` (`VITE_TOSS_CLIENT_KEY`).
  - **시드 계정 표**: `admin@beautyboy.dev` / `dry@…` / `oily@…`, 비밀번호 `seed1234!`, 각 역할.
  - **테스트**: `./gradlew test`(H2) · `./gradlew integrationTest`(Docker 필요) ·
    `npm run test` · **`npm run test:e2e`(백엔드를 `e2e` 프로필로 먼저 띄워야 함 — 명령 그대로)**.
  - **주요 화면 스크린샷** 몇 장(메인·루틴·상세·장바구니·주문 완료).
  - **설계 하이라이트 5개** — 결제 2단계 검증, 패키지=서비스 경계와 의존성 역전, 성분 궁합 분류쌍,
    조회수 Redis 버퍼, Flyway 대역 분할. 포트폴리오 프로젝트의 README는 면접에서 먼저 읽히는 문서다.
- [ ] **Step 2: 로드맵 갱신** — `docs/plans/2026-07-23-roadmap.md`에:
  - Flyway 대역표 `V60~` 행에 실사용(V60·V61·V62·V63) 기록.
  - "Wave 3에서 Wave 4로 넘기는 것" 항목 전부에 취소선 + 해소 커밋.
  - W4 DoD에 달성 근거(테스트 건수, E2E 결과, curl/스크린샷 경로).
  - **2차로 남는 것**을 새 절로: 오늘드림(delivery), 포인트, 리뷰 사진 업로드, 검색
    Elasticsearch 교체, 배송 상태 전이 스케줄러.
- [ ] **Step 3: 전체 녹색 확인** — 네 개를 순서대로 돌리고 **각 결과 숫자를 보고서에 적는다**:

```bash
cd backend && ./gradlew test              # 단위/슬라이스 (H2)
cd backend && ./gradlew integrationTest   # 실 MySQL 8.4 + Flyway V1~V63 + validate
cd frontend && npm run test               # Vitest
cd frontend && npm run test:e2e           # Playwright (백엔드 e2e 프로필 기동 후)
```

- [ ] **Step 4: 커밋** — `git commit -am "docs: README 실행법·시드 계정·설계 하이라이트 + 로드맵 Wave 4 마감"`

---

## 웨이브 마감 (오케스트레이터)

- [ ] 태스크별 리뷰 완료(테스트 통과 + Files 목록 준수 + 사양 일치)를 확인한다.
      **특히 확인할 것**: `config/SecurityConfig.java`·`common/ApiResponse`·`PageResponse`·
      `ErrorResponse`·전역 핸들러가 **한 줄도 바뀌지 않았는지**(`git diff main --stat`으로 대조).
- [ ] `feat/wave4-integration`을 main에 머지.
- [ ] 머지 후 4-17 Step 3의 네 명령이 전부 녹색인지 다시 확인(머지 자체가 깨뜨리지 않았는지).
- [ ] **W4 DoD 판정:**
  - 탐색→루틴 담기→주문→결제 E2E 녹색 (Playwright 3건)
  - admin 계정으로 상품 등록·수정·삭제, 루틴 단계 편집, 문의 답변이 화면에서 동작
  - README만 보고 처음 사람이 프로젝트를 띄울 수 있는가 (**직접 따라 해보고 판정한다**)
  - 실제 토스 테스트 결제창 승인 1회 통과 (수동, 스크린샷)
- [ ] 로드맵의 "웨이브별 완료 기준"에 W4 달성 기록 추가.

---

## 터미널 실행 프롬프트

### 0) 사전 조건 (사람이 루트에서 확인 — 2줄)

```bash
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" log --oneline -1   # 이 커밋이 worktree 기점
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" status --short     # 비어 있어야 한다
```

계획서가 커밋돼 있어야 새 worktree에 딸려간다. 아직이면 먼저:
`git add docs/plans && git commit -m "docs(plan): Wave 4 통합 계획"`

### 1) 터미널 A (Wave 4 — 통합, 단일 터미널)

```
[1단계 — 작업 공간] 무엇보다 먼저 이것부터.
  git worktree add ../BeautyBoy-w4 -b feat/wave4-integration
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 옮겨라.
(name으로 새로 만들지 마라 — origin에서 브랜치를 따 계획서 없는 worktree가 생긴다.)
진입 후 확인하고 하나라도 어긋나면 중단·보고:
  - pwd가 BeautyBoy-w4 인지
  - git log --oneline -1 이 루트 기점과 같은지
  - docs/plans/2026-07-24-wave4-integration.md 와 루트 DESIGN.md 가 있는지
  - backend/src/main/java/com/beautyboy/ranking/SalesStatProvider.java 가 있는지
    (Task 4-1이 따라 할 의존성 역전 선례)
  - frontend/src/pages/Detail.tsx 가 있는지(Task 4-8이 고칠 대상)
  - git status가 깨끗한지
확인 뒤 ./gradlew test 와 cd frontend && npm install && npm run test 가 둘 다 녹색인지 먼저 본다.

[2단계 — 실행]
docs/plans/2026-07-24-wave4-integration.md 의 Task 4-1 ~ 4-17을 번호 순서대로 TDD로 실행하라.
너는 오케스트레이터다 — 태스크마다 서브에이전트를 스폰하고, 태스크 사이마다
(1) 테스트 통과 (2) Files 목록 준수 (3) 사양 일치를 리뷰한 뒤 다음으로 넘어가라.

모델 배분(CLAUDE.md):
  - Task 4-6(조회수 Redis 버퍼·플러시)과 Task 4-11(결제 승인 화면) 은 model: opus.
    앞은 동시성·유실 구간 판단, 뒤는 결제 2단계의 마지막 조각이라 조용히 틀리면 돈이 걸린다.
  - 나머지 전부 model: sonnet.

반드시 지킬 것:
- Flyway는 V60~V69 대역만. 이 웨이브가 쓰는 번호는 계획서 §"Flyway V60~V69 사용 계획"에 못 박혀 있다.
- config/SecurityConfig.java 를 열지 마라. 역할 인가는 새 파일 MethodSecurityConfig + @PreAuthorize다.
- common 의 ApiResponse·PageResponse·ErrorResponse·전역 핸들러는 여전히 동결. ErrorCode는
  자기 도메인 접두사 상수 추가만.
- build.gradle.kts · docker-compose.yml · frontend/package.json 은 이 웨이브에 한해 열 수 있으나,
  계획서가 명시한 항목(spring-boot-starter-data-redis, redis 서비스,
  @tosspayments/tosspayments-sdk, @playwright/test)만 추가하라. 그 외 의존성은 추가하지 말고 보고하라.
- 패키지 = 서비스 경계. catalog가 goods_review_stat·wishlist 를 직접 조인하면 안 된다.
  admin 도 별도 패키지를 만들지 말고 소유 도메인 안에 컨트롤러를 둔다(계획서 결정 1·4).
- 화면 태스크는 dev 서버를 띄우고 390/768/1024/1440/1920 에서 스크린샷을 찍어 직접 본 뒤
  가로 스크롤 0을 확인하고 파일 경로를 보고서에 남겨야 완료다.
- CSS는 DESIGN.md 토큰 이름을 직접 참조하고 hex를 손으로 적지 마라. 없는 값이 필요하면 보고하라.
- 마이그레이션을 추가했으므로 마감 전 ./gradlew test · ./gradlew integrationTest ·
  npm run test · npm run test:e2e 를 전부 녹색으로 만들고 각 결과 숫자를 보고서에 남겨라.
- 토스 실제 결제창 승인은 자동화하지 않는다(계획서 결정 5). 대신 Task 4-16 Step 6에서
  사람이 한 번 통과시키고 스크린샷을 남긴다 — 이것 없이는 W4 DoD가 성립하지 않는다.
```

---

## Self-Review 결과

- **스펙 커버리지 (로드맵 W4 DoD "탐색→루틴 담기→주문→결제 E2E 녹색, admin에서 상품/루틴 CRUD 가능,
  README에 실행법"):** E2E = Task 4-16. admin CRUD = 4-5(API)·4-14(화면). README = 4-17.
  로드맵 §"Wave 3에서 Wave 4로 넘기는 것" 7건도 전부 대응된다 — 루틴 가이드 화면(4-12) ·
  장바구니/주문/결제 화면(4-9·4-10·4-11) · 리뷰/문의 작성(4-14) · 상세 미충족 3건(4-8) ·
  기본배송지 유니크 제약(4-2) · `GoodsListItem` 미충족 필드(4-1) · 조회수 Redis(4-6).
  설계 6장 화면 표에서 남는 것은 `/category/:code`(현 `/goods?categoryCode=`가 같은 일을 한다)와
  오늘드림 판정(1차 범위 밖)뿐이다. **미커버 없음.**
- **범위 밖(의도적, 2차로 명시):** 오늘드림(delivery)·포인트·리뷰 사진 업로드·검색 Elasticsearch
  교체·배송 상태 전이 스케줄러. Task 4-17 Step 2가 로드맵에 이 목록을 남긴다.
- **타입 일관성:** `GoodsQueryService.findListItems(Collection<Long>, Long)`의 시그니처 변경이
  호출자 3곳(routine·search·ranking)에 명시돼 있고, 4-3(`MyReviewItem` 조립)과 4-5
  (`replaceStepGoods` 검증)도 같은 시그니처를 쓴다. 프론트의 `CompatCheckResult`·`CompatFinding`은
  백엔드 `CompatCheckResponse`·`CompatFinding`(Wave 3 확정)과 필드명이 일치한다.
  `addCartItemsBulk` 항목 형태는 백엔드 `CartAddRequest(goodsNo, optionNo, quantity)`와 같다.
- **플레이스홀더:** 생성 컬럼 DDL, Redis 키·주기와 그 근거, 플러시 순서(HGETALL→UPDATE→DEL)와
  손실 구간, StrictMode 이중 승인 방어, 퀴즈 가중치표, 시드 불변식 검증 SQL, 토스 SDK 호출 형태 —
  판단이 있는 곳은 전부 코드/규칙으로 적었다. "적절히 처리" 류 없음.
  시드 150행의 **개별 행 내용**은 의도적으로 위임했다(판단이 아니라 콘텐츠). 대신 분포 규칙과
  **기계로 검증 가능한 불변식 6개**를 못 박아 "요약해서 옮기다 한 단어가 사라지는" Wave 3 T2의
  실패 양상을 막았다.
- **직렬 안전:** 터미널 1개라 파일 소유권 다툼이 없다. 대신 **순서 의존이 실질 제약**이다 —
  4-1(카드 값 채우기) → 4-15(시드)에서 비로소 눈에 보이고, 4-4(메서드 보안) → 4-5(admin API) →
  4-14(admin 화면), 4-8(옵션 선택) → 4-9(장바구니) → 4-10·4-11(주문·결제) → 4-16(E2E).
  번호 순서가 곧 의존 순서이므로 건너뛰지 않는다.
