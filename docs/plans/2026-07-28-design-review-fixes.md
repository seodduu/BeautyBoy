# 구현 계획 — 디자인 리뷰 반영 (2026-07-28)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development 또는
> superpowers:executing-plans로 태스크 단위 실행. 스텝은 체크박스로 추적한다.
>
> 근거: `~/Downloads/beautyboy-design-review.md` (2026-07-28 localhost:3000 전체 페이지 리뷰).
> 리뷰 항목을 코드베이스와 대조·검증한 결과가 §1이고, **§2 "설계 결정"이 설계의 진실을 겸한다.**
> DESIGN.md에는 이 계획과 같은 커밋으로 `compat-banner` / `list-toolbar` / `footer-beautyboy` /
> 평점 줄·대비 최저선 / 영문·한글 혼용 규칙 사양이 추가돼 있다 — 프론트 태스크는 그 절을 따른다.
> **마이그레이션 없음** (Flyway 현재 V83, 이 계획은 DDL을 만들지 않는다).

**Goal:** 외부 디자인 리뷰에서 채택한 10개 항목(궁합 배너 분리, 성분 한글명, 스크롤 초기화,
목록 정렬·필터, 장바구니 상세화·재고 상한, 옵션 힌트, 푸터, 별점, 저대비, 워딩 규칙)을 반영한다.

**Architecture:** 백엔드는 응답 조립 계층만 만진다(궁합 라벨 매핑, 장바구니 응답 확장·재고 검증).
프론트는 DESIGN.md 신규 사양을 소비한다. 도메인 규칙·스키마 변경 없음.

**Tech Stack:** React SPA(Vite+TS, vitest+MSW) / Spring Boot(JUnit) / 기존 토큰 시스템.

## Global Constraints (CLAUDE.md 재확인)

- 자기 터미널의 Files 목록 밖 파일 수정 금지. `common` 패키지(ApiResponse, ErrorCode 등)는 열지 않는다.
- CSS는 hex를 손으로 쓰지 않는다 — `var(--color-*)` 토큰만. 문서에 없는 값이 필요하면 중단·보고.
- 한글 본문: `word-break: keep-all`, 영문 라벨은 DESIGN.md "영문/한글 혼용 규칙" 두 자리만.
- 유닛테스트는 인메모리/MSW만. 실 MySQL·실 API를 만지는 확인은 W2(직렬)에서만 한다.
- 프론트 전체 판정은 `npm test`(vitest 단독 실행은 e2e 수집으로 거짓 적신호), 타입은 `npx tsc -b`.
- 화면을 바꾸는 태스크는 `VITE_USE_MOCK=true npm run dev`로 담당 화면을 띄워 **스크린샷을 찍고
  파일 경로를 보고서에 남겨야 완료**다. 목 데이터 한계(실서버 라벨 등)는 W2에서 재확인한다.

---

## 0. 사람이 할 일 (사전 조건 2줄)

터미널을 열기 전에 프로젝트 루트에서:

```
git log --oneline -1     # 이 계획 커밋(docs(plan): 디자인 리뷰 반영 계획)인지 확인
git status               # 깨끗한지 확인
```

---

## 1. 리뷰 항목 판정표 (코드 대조 결과)

| 리뷰 | 판정 | 근거 · 배치 |
|---|---|---|
| 1-1 장바구니 뱃지 | **재현 확인만 (W2)** | 이미 구현됨 — `Header.tsx:38-46` 레이아웃 레벨 `['cart']` 쿼리 + 모든 담기 경로가 invalidate. 리뷰 증상은 수정 이전 빌드로 추정 |
| 1-2 스크롤 유지 | **수정 (T3)** | `scrollTo`/`ScrollRestoration` 사용처 0건. 단 `Main.css`의 전역 `scroll-behavior: smooth` 때문에 `behavior:'instant'` 필수 |
| 1-3 랜딩 오버플로우 | **재현 확인만 (W2)** | `WaveCanvas.tsx:114`에 resize 핸들러 존재, `.bb-hero`는 `overflow:hidden`. 재현되면 별도 보고 후 후속 |
| 1-4 스티키 가림 | **재현 확인만 (W2)** | 이미 구현됨 — `RoutineSection.css:10` `scroll-margin-top: 118px` |
| 1-5 궁합 배너 혼재 | **수정 (T1)** | 실재 버그 — `CompatBanner.tsx`가 overall 하나로 제목을 정하고 findings를 verdict 필터 없이 전부 나열 |
| 2-1 옵션 비활성 이유 | **수정 (T4)** | 절반 구현됨(1개면 자동선택, `+가격` 표기 존재). 힌트 문구만 추가 |
| 2-2 푸터 부재 | **수정 (T3)** | Footer 컴포넌트 0건, `--color-footer` 토큰은 예약만 돼 있음 |
| 2-3 정렬·필터 없음 | **수정 (T2)** | 서버 `GoodsSort` 5종 + `minPrice`/`maxPrice` 이미 지원, 프론트 UI만 없음. **"리뷰 많은 순"은 제외** — 리뷰수가 JPQL 밖 사후 주입이라 백엔드 구조 변경 필요(범위 밖). 피부타입 필터도 서버 미지원으로 제외 |
| 2-4 장바구니 상세 부족 | **수정 (T4)** | `CartItemResponse`에 썸네일·재고 없음(계약 확장 필요), `unitPrice`는 있는데 미표시, 수량 검증은 `<=0`뿐 |
| 2-5 성분 코드 노출 | **수정 (T1)** | 한글명이 백엔드·DB·프론트 어디에도 없음. 카테고리 14종의 유일한 정의는 `V11__ingredient.sql:4-6` 주석 |
| 3-1 랜딩 내비 | **기각 (후속 웨이브)** | `Header.tsx:71` 주석에 명시된 의도적 자리표시 — 결함 아님 |
| 3-2 별점 표현 | **수정 (T4)** | `Rating.tsx`가 텍스트만, 리뷰없음/평점 동일 색 |
| 3-3 저대비 | **수정 (T4)** | 실측: ash(#999999)는 surface 위 2.6:1, slate도 4.2:1로 AA 미달 → 카드 위 최저선 graphite(8.7:1). DESIGN.md에 명문화됨 |
| 3-4 언어 혼용 | **규칙 명문화만 (완료)** | TODAY'S PICK 등은 아이브로우 자리라 규칙 부합. **"헤어" placeholder는 리뷰어 오판** — C003 헤어·바디가 실제 카테고리(랭킹 탭 노출). DESIGN.md에 혼용 규칙 추가로 종결, 코드 수정 없음 |

---

## 2. 설계 결정

### 결정 1 — 궁합 배너: verdict별 섹션 분리, SYNERGY는 `role="status"`

- 표시 순서 **CONFLICT → CAUTION → SYNERGY** 고정 — 나쁜 소식부터. 긍정 안내가 먼저 보이면
  경고의 무게가 죽는다.
- 톤은 기존 좌측 4px 보더 + 아이콘 + 텍스트 패턴 그대로(배경 채움 금지). CSS 클래스 재사용,
  **CompatBanner.css는 수정하지 않는다.**
- CONFLICT·CAUTION은 `role="alert"`, SYNERGY는 `role="status"` — 경고가 아닌 것을 assertive로
  낭독시키지 않는다. 스크린리더 전달은 유지된다.
- 컴포넌트 인터페이스(`result: CompatCheckResult`) 불변 — 호출부(Cart.tsx:91, Routine.tsx:161) 무수정.

### 결정 2 — 성분 한글명: 백엔드가 `categoryA/B` 값 자체를 라벨로 내려준다

- **필드 추가가 아니라 값 교체.** `CompatFinding` 계약 모양이 안 바뀌므로 프론트 무수정,
  목·테스트 픽스처(이미 한글)와도 일치하게 된다. 프론트 매핑 테이블(리뷰 처방)은 새 카테고리
  추가 때마다 백엔드와 갈라지므로 채택하지 않는다.
- 매핑의 자리는 **ingredient 패키지**(카테고리의 소유 도메인)의 순수 상수 클래스.
  compat이 이를 import하는 것은 엔티티/리포지토리 직접 접근이 아니므로 경계 규칙 위반이 아니다.
- **미지 코드는 코드 그대로 반환** — 라벨 누락 시 정보가 사라지는 것(CautionPanel의 조용한 누락
  전례)보다 코드가 보이는 게 낫다. 로그도 남기지 않는다(조회 경로 소음).
- AHA·BHA는 통용 약어라 그대로 둔다. 14종 매핑은 §3-1 코드가 전량이다.

### 결정 3 — 장바구니: 응답 확장 + 재고 상한, 에러는 `ORDER_OUT_OF_STOCK` 재사용

- `CartItemResponse`에 `thumbnailUrl`·`stock` 추가(§3-2). 재고 소스는 이미 CartService 안에
  들어와 있는 `OrderGoodsSnapshot` — 여기에 `thumbnailUrl`을 추가한다(§3-3). record라
  컴파일이 모든 사용처를 잡아준다.
- `add`/`changeQuantity`에서 **결과 수량 > 재고면 409** — 코드는 기존 `ORDER_OUT_OF_STOCK`
  ("재고가 부족한 상품이 있습니다") 재사용. 시나리오·메시지가 동일하고, 신규 코드를 만들면
  `ErrorCode`(공유 계약, 수정 금지)를 열어야 한다. 옵션 없는 상품은 stock이
  `Integer.MAX_VALUE`라 자연 통과(기존 정의 유지).
- 프론트 스텝퍼 상한은 `min(stock, 99)` — 99는 기존 `MAX_QUANTITY`(오입력 방지 캡) 유지.
  서버 검증이 진실이고 스텝퍼는 UX 게이트다(돈과 재고는 서버).
- 합계 라벨 "합계(안내용)" → **"결제 예상 금액"** + 보조 문구 "배송비는 주문서에서 계산됩니다".

### 결정 4 — 목록 툴바: 서버가 이미 지원하는 것만 노출

- 정렬 5종(popular/new/sales/priceAsc/discount)을 `?sort=` URL 파라미터로. URL이 상태의
  진실(공유·새로고침 생존). 기본값 popular은 파라미터 생략.
- 카테고리 탭은 루틴 5단계(`ROUTINE_STEPS`)만 — 목록 화면의 진입 맥락이 루틴 STEP이다.
  랭킹의 대분류 탭(`RANKING_CATEGORY_TABS`)을 재사용하지 않는다(랭킹 전용 컴포넌트,
  일반화는 YAGNI).
- 가격대 필터 3 프리셋(1만 미만 / 1~3만 / 3만 이상)은 서버 `minPrice`/`maxPrice`로만 거른다.
- **"리뷰 많은 순" 미지원 사유를 코드 주석이 아니라 이 계획서에 남긴다**: 리뷰수는
  `GoodsQueryRepository.findList` JPQL 밖에서 provider로 사후 주입되므로 order by에 쓸 수 없다.
  필요해지면 비정규화 컬럼 또는 조인 확장이 별도 계획으로 필요하다.

### 결정 5 — ScrollToTop: Layout 내부, `behavior: 'instant'`

- `Layout.tsx`(전 라우트 공용, 인스턴스 1개)에서 `useLocation().pathname` 변경 시
  `window.scrollTo({ top: 0, left: 0, behavior: 'instant' })`. `Main.css`의 전역
  `scroll-behavior: smooth`가 라우트 전환에 개입하지 못하게 instant를 명시한다.
- pathname만 본다 — 같은 화면의 쿼리스트링 변경(정렬·탭)과 해시 앵커(`#step`)는 리셋하지 않는다.
- 뒤로가기도 최상단으로 간다(트레이드오프 수용 — 목록 위치 복원은 페이지네이션이 붙는
  웨이브에서 상태 복원과 함께 다룰 일이지, 지금 절반만 만들 일이 아니다).

### 결정 6 — 푸터·별점·대비·옵션 힌트

- 푸터는 DESIGN.md `footer-beautyboy` 사양 그대로. 랜딩(`/`) 제외. **가짜 사업자 정보를 실존처럼
  쓰지 않는다** — 데모 명시 + 자리표시 값. `.bb-layout`에 `display:flex; flex-direction:column;
  min-height:100dvh`를 추가해 짧은 화면에서 푸터를 하단에 붙인다(`.bb-layout__main`의
  `flex:1 1 auto`가 이미 이를 전제하고 있다).
- 별점·대비는 DESIGN.md "평점 줄"·"카드 위 보조 텍스트 최저선" 사양 그대로 —
  `★ 4.0 (3)` graphite, 빈 상태는 "첫 리뷰를 기다려요", 브랜드명 slate → graphite.
- 옵션 힌트: `optionRequired`일 때 CTA 버튼 아래 `옵션을 선택해주세요` meta 텍스트
  (`--color-graphite`). 자동 선택 확대(대표 옵션 미리 선택)는 하지 않는다 — 가격이 옵션에
  따라 달라지므로 사용자가 고르지 않은 옵션의 가격을 기본 노출하는 쪽이 더 큰 혼란이다.

---

## 3. 공유 계약 — 코드 전량

### 3-1. `ingredient/IngredientCategoryLabels.java` (신규)

```java
package com.beautyboy.ingredient;

import java.util.Map;

/**
 * 성분 카테고리 코드 → 한글 표시명. 카테고리의 소유 도메인(ingredient)이 표시명도 소유한다.
 * 코드 목록의 원천은 V11__ingredient.sql 주석의 14종 — 여기와 어긋나면 이 클래스가 아니라
 * 데이터가 먼저다(스키마·픽스처가 진실).
 *
 * <p>미지 코드는 코드를 그대로 반환한다 — 라벨 누락으로 정보가 사라지는 것보다
 * 코드가 노출되는 편이 낫고, 노출되면 눈에 띄어 고쳐진다.
 */
public final class IngredientCategoryLabels {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("RETINOID", "레티노이드"),
            Map.entry("AHA", "AHA"),
            Map.entry("BHA", "BHA"),
            Map.entry("VITAMIN_C", "비타민C"),
            Map.entry("NIACINAMIDE", "나이아신아마이드"),
            Map.entry("HYALURONIC", "히알루론산"),
            Map.entry("CERAMIDE", "세라마이드"),
            Map.entry("PEPTIDE", "펩타이드"),
            Map.entry("CENTELLA", "센텔라"),
            Map.entry("SALICYLIC", "살리실산"),
            Map.entry("FRAGRANCE", "향료"),
            Map.entry("ALCOHOL", "알코올"),
            Map.entry("SPF_FILTER", "자외선 차단 성분"),
            Map.entry("OTHER", "기타"));

    private IngredientCategoryLabels() {
    }

    public static String labelOf(String categoryCode) {
        return LABELS.getOrDefault(categoryCode, categoryCode);
    }
}
```

적용 지점은 `CompatService.java:88-90`의 finding 조립 한 곳:

```java
findings.add(new CompatFinding(
        rule.get().getVerdict(),
        IngredientCategoryLabels.labelOf(ca),
        IngredientCategoryLabels.labelOf(cb),
        rule.get().getReason(),
        List.copyOf(contributors)));
```

`CompatFinding` record 자체는 무수정(필드 추가 없음 — 결정 2).

### 3-2. `cart/dto/CartItemResponse.java` (필드 2개 추가)

```java
public record CartItemResponse(
        Long cartItemId,
        Long goodsNo,
        Long optionNo,
        String goodsName,
        String optionName,
        int unitPrice,
        int quantity,
        int lineAmount,
        String thumbnailUrl,   // 스냅샷 경유. 없으면 null — 프론트가 플레이스홀더 처리
        int stock) {}          // 남은 재고. 옵션 없는 상품은 Integer.MAX_VALUE
```

프론트 대응 타입 `frontend/src/api/cart.ts`의 `CartItem`:

```ts
export interface CartItem {
  cartItemId: number;
  goodsNo: number;
  optionNo: number | null;
  goodsName: string;
  optionName: string;
  unitPrice: number;
  quantity: number;
  lineAmount: number;
  thumbnailUrl: string | null;
  stock: number; // 옵션 없는 상품은 2147483647 — min(stock, 99) 캡이 흡수한다
}
```

### 3-3. `catalog/GoodsQueryService.OrderGoodsSnapshot` (필드 1개 추가 — 맨 뒤)

```java
record OrderGoodsSnapshot(Long goodsId, Long optionId, String goodsName,
                          String optionName, int unitPrice, int stock,
                          String thumbnailUrl) {}
```

- 값 채우기는 `GoodsService.findOrderSnapshot`에서 Goods의 썸네일 필드로 —
  필드명은 `GoodsListItem.thumbnailUrl` 조립부(`GoodsService.java` 목록 경로)가 쓰는 것과
  같은 소스를 쓴다(로컬 에셋 경로, V76 이후).
- record라 기존 생성자 호출부(OrderService·PaymentService·CartService·테스트 픽스처)는
  **컴파일 에러로 전부 드러난다** — T4가 자기 Files 안에서 인자만 추가한다. Files 밖
  파일(payment 등)이 걸리면 수정하지 말고 보고.

### 3-4. 장바구니 재고 검증 (CartService — 판단이 갈리는 로직이라 전량)

```java
// add(): 기존 항목 누적을 포함한 "결과 수량"으로 검증한다.
// 줄 단위 검증이면 2개 담고 또 2개 담아 재고 3을 넘는 경우를 못 잡는다.
int stock = snapshot.stock();
int resultingQuantity = cartItemRepository.findByMemberIdAndGoodsNoAndOptionId(...)
        .map(existing -> existing.getQuantity() + request.quantity())
        .orElse(request.quantity());
if (resultingQuantity > stock) {
    throw new BusinessException(ErrorCode.ORDER_OUT_OF_STOCK);
}
```

(기존 항목 조회 메서드명은 현재 `CartService.add`가 upsert에 쓰는 것을 그대로 쓴다 —
새 리포지토리 메서드를 만들지 않는다.)

```java
// changeQuantity(): 스냅샷을 새로 조회해 검증한다. 항목의 goodsNo/optionNo로
// findOrderSnapshot을 부르고, 상품이 사라졌으면 기존 관례대로 GOODS_NOT_FOUND.
if (quantity > snapshot.stock()) {
    throw new BusinessException(ErrorCode.ORDER_OUT_OF_STOCK);
}
```

---

## 4. 웨이브·터미널 분할

| 웨이브 | 터미널 | 브랜치 | 범위 | 모델 |
|---|---|---|---|---|
| W1 (병렬 4) | T1 | `feature/review-compat-labels` | 1-5 배너 분리 + 2-5 한글 라벨 | sonnet |
| W1 | T2 | `feature/review-list-toolbar` | 2-3 정렬·카테고리 탭·가격대 필터 | sonnet |
| W1 | T3 | `feature/review-shell` | 1-2 ScrollToTop + 2-2 푸터 | sonnet |
| W1 | T4 | `feature/review-card-cart` | 2-4 장바구니 + 2-1 옵션 힌트 + 3-2/3-3 카드 | sonnet |
| W2 (직렬) | 오케스트레이터 | — | 머지 게이트 + 실스택 검증 + 1-1/1-3/1-4 재현 확인 | opus |

전부 sonnet인 이유: 판단이 필요한 지점(§2, §3)은 이 계획서가 코드로 못 박았고, 남은 것은
사양 이행이다. 모델 배분 예외 3종(결제·재고 차감·궁합 규칙 엔진) 영역을 만지지 않는다 —
T1은 규칙 엔진이 아니라 표시 라벨, T4는 재고 차감이 아니라 읽기 검증이다.

### 파일 소유권

| 터미널 | 소유 파일 |
|---|---|
| T1 | `backend/…/ingredient/IngredientCategoryLabels.java`(신규), `backend/…/ingredient/IngredientCategoryLabelsTest.java`(신규), `backend/…/compat/CompatService.java`, `backend/…/compat/CompatServiceTest.java`(있으면 수정·없으면 신규), `frontend/src/components/compat/CompatBanner.tsx`, `frontend/src/components/compat/CompatBanner.test.tsx` |
| T2 | `frontend/src/pages/GoodsList.tsx`, `GoodsList.css`, `GoodsList.test.tsx`, `frontend/src/components/goods/ListToolbar.tsx`(신규), `ListToolbar.css`(신규), `ListToolbar.test.tsx`(신규) |
| T3 | `frontend/src/components/layout/Layout.tsx`, `Layout.css`, `Layout.test.tsx`, `frontend/src/components/layout/Footer.tsx`(신규), `Footer.css`(신규), `Footer.test.tsx`(신규), `frontend/src/router.test.tsx` |
| T4 | `backend/…/cart/CartService.java`, `backend/…/cart/dto/CartItemResponse.java`, `backend/…/cart/CartServiceTest.java`, `backend/…/catalog/GoodsQueryService.java`, `backend/…/catalog/GoodsService.java`, `backend/…/catalog/GoodsServiceTest.java`(스냅샷 관련 케이스만), `frontend/src/api/cart.ts`, `frontend/src/components/cart/CartLine.tsx`, `CartLine.css`, `CartLine.test.tsx`(있으면), `frontend/src/pages/Cart.tsx`, `Cart.test.tsx`, `frontend/src/pages/Detail.tsx`, `Detail.css`, `Detail.test.tsx`, `frontend/src/components/ui/Rating.tsx`, `Rating.css`, `Rating.test.tsx`(있으면 수정·없으면 신규), `frontend/src/components/goods/GoodsCard.css`, `frontend/src/mocks/handlers.ts`(cart 픽스처에 thumbnailUrl·stock 추가만) |

- **`frontend/src/mocks/handlers.ts`는 T4 전용.** T1·T2는 기존 목이 이미 요구를 충족한다
  (compat 픽스처는 이미 한글, `sortGoods`는 이미 5종). 부족하면 고치지 말고 보고.
- `OrderGoodsSnapshot` 확장의 컴파일 파급이 T4 Files 밖(order·payment·그 테스트)에 닿으면
  **생성자 인자 추가만** 허용한다 — 로직 변경은 금지, 어느 파일을 왜 만졌는지 보고서에 명시.
- CompatBanner.css는 아무도 수정하지 않는다(결정 1).

---

## 5. 태스크 상세

### T1-A: 성분 카테고리 한글 라벨 (백엔드)

**Files:** `IngredientCategoryLabels.java`(신규), `IngredientCategoryLabelsTest.java`(신규), `CompatService.java`
**Interfaces:** Produces `IngredientCategoryLabels.labelOf(String): String` (§3-1 전량)

- [x] **1. 실패 테스트** — `backend/src/test/java/com/beautyboy/ingredient/IngredientCategoryLabelsTest.java`

```java
package com.beautyboy.ingredient;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IngredientCategoryLabelsTest {

    @Test
    @DisplayName("14종 코드 전부가 표시명으로 변환된다 — 코드가 그대로 남는 카테고리가 없다")
    void 전체_코드_매핑() {
        assertThat(IngredientCategoryLabels.labelOf("RETINOID")).isEqualTo("레티노이드");
        assertThat(IngredientCategoryLabels.labelOf("VITAMIN_C")).isEqualTo("비타민C");
        assertThat(IngredientCategoryLabels.labelOf("NIACINAMIDE")).isEqualTo("나이아신아마이드");
        assertThat(IngredientCategoryLabels.labelOf("HYALURONIC")).isEqualTo("히알루론산");
        assertThat(IngredientCategoryLabels.labelOf("CERAMIDE")).isEqualTo("세라마이드");
        assertThat(IngredientCategoryLabels.labelOf("PEPTIDE")).isEqualTo("펩타이드");
        assertThat(IngredientCategoryLabels.labelOf("CENTELLA")).isEqualTo("센텔라");
        assertThat(IngredientCategoryLabels.labelOf("SALICYLIC")).isEqualTo("살리실산");
        assertThat(IngredientCategoryLabels.labelOf("FRAGRANCE")).isEqualTo("향료");
        assertThat(IngredientCategoryLabels.labelOf("ALCOHOL")).isEqualTo("알코올");
        assertThat(IngredientCategoryLabels.labelOf("SPF_FILTER")).isEqualTo("자외선 차단 성분");
        assertThat(IngredientCategoryLabels.labelOf("OTHER")).isEqualTo("기타");
        // AHA·BHA는 통용 약어 — 변환하지 않는 것이 사양이다
        assertThat(IngredientCategoryLabels.labelOf("AHA")).isEqualTo("AHA");
        assertThat(IngredientCategoryLabels.labelOf("BHA")).isEqualTo("BHA");
    }

    @Test
    @DisplayName("미지 코드는 코드를 그대로 반환한다 — 조용한 누락 금지")
    void 미지_코드_통과() {
        assertThat(IngredientCategoryLabels.labelOf("SQUALANE")).isEqualTo("SQUALANE");
    }
}
```

- [x] **2. RED 확인**: `./gradlew test --tests IngredientCategoryLabelsTest` — 클래스 부재로 컴파일 실패
- [x] **3. §3-1 코드 그대로 구현 → GREEN 확인**
- [x] **4. CompatService 적용 테스트** — 기존 `CompatServiceTest`(없으면 신규)에 추가.
  기존 테스트가 세우는 픽스처·목 관례를 그대로 따르되 케이스는 이것이어야 한다:

```java
@Test
@DisplayName("finding의 categoryA/B는 코드가 아니라 한글 표시명으로 내려간다")
void 카테고리_표시명_변환() {
    // VITAMIN_C × NIACINAMIDE 규칙이 걸리는 카트를 구성하고 check()를 부른다
    // (규칙·성분 세팅은 기존 CompatServiceTest의 conflict/caution 케이스와 같은 방식)
    CompatCheckResponse response = compatService.check(goodsNos);
    CompatFinding finding = response.findings().get(0);
    assertThat(finding.categoryA()).isEqualTo("비타민C");        // "VITAMIN_C"면 실패
    assertThat(finding.categoryB()).isEqualTo("나이아신아마이드");
}
```

- [x] **5. RED 확인(코드 그대로 내려와 실패) → §3-1 적용 지점 수정 → GREEN**
- [x] **6. `./gradlew test` 전체 녹색 확인.** e2e 스펙이 카테고리 코드 원문을 단언하는지
  `grep -rn "VITAMIN_C\|HYALURONIC" frontend/e2e/`로 확인 — 걸리면 수정하지 말고 보고
- [x] **7. 커밋** `feat(compat): 궁합 카테고리를 한글 표시명으로 응답`

### T1-B: 궁합 배너 verdict별 분리 (프론트)

**Files:** `CompatBanner.tsx`, `CompatBanner.test.tsx`
**Interfaces:** Consumes `CompatCheckResult`(무변경). Props 인터페이스 불변.

- [x] **1. 실패 테스트** — `CompatBanner.test.tsx`에 추가/수정. 픽스처 `MIXED_RESULT` 추가:

```tsx
/* 리뷰 1-5 재현: 주의(CAUTION)와 시너지(SYNERGY)가 한 응답에 섞여 내려오는 실제 케이스.
   overall은 최악 verdict(CAUTION)이므로, 분리 없이는 시너지 항목이 주의 박스 안에 나열된다. */
const MIXED_RESULT: CompatCheckResult = {
  overall: 'CAUTION',
  findings: [
    {
      verdict: 'CAUTION',
      categoryA: '비타민C',
      categoryB: '나이아신아마이드',
      reason: '민감 피부는 동시 사용 시 붉어질 수 있어요',
      goodsNos: [3, 4],
    },
    {
      verdict: 'SYNERGY',
      categoryA: '히알루론산',
      categoryB: '세라마이드',
      reason: '보습 효과를 서로 보완해요',
      goodsNos: [5, 6],
    },
  ],
};
```

케이스 2건 — 신규 분리 케이스와, 기존 SYNERGY 케이스의 role 변경:

```tsx
it('SYNERGY면 경고가 아니라 role=status 긍정 안내로 보여준다', () => {
  render(<CompatBanner result={SYNERGY_RESULT} />);

  const banner = screen.getByRole('status');
  expect(banner.className).toContain('bb-compat-banner--success');
  expect(banner).toHaveTextContent('함께 쓰면 좋은 조합이에요');
  expect(banner).toHaveTextContent('보습 효과를 서로 보완해요');
});

it('주의와 시너지가 섞이면 verdict별 섹션으로 분리해 보여준다', () => {
  render(<CompatBanner result={MIXED_RESULT} />);

  // 주의 섹션(alert)에는 CAUTION 항목만 — 시너지 문구가 경고 박스 안에 섞이면 안 된다
  const cautionBanner = screen.getByRole('alert');
  expect(cautionBanner.className).toContain('bb-compat-banner--caution');
  expect(cautionBanner).toHaveTextContent('민감 피부는 동시 사용 시 붉어질 수 있어요');
  expect(cautionBanner).not.toHaveTextContent('보습 효과를 서로 보완해요');

  // 시너지 섹션(status)은 별도 박스로, 긍정 제목과 함께 나온다
  const synergyBanner = screen.getByRole('status');
  expect(synergyBanner.className).toContain('bb-compat-banner--success');
  expect(synergyBanner).toHaveTextContent('함께 쓰면 좋은 조합이에요');
  expect(synergyBanner).toHaveTextContent('보습 효과를 서로 보완해요');
  expect(synergyBanner).not.toHaveTextContent('민감 피부는 동시 사용 시 붉어질 수 있어요');
});
```

(기존 CONFLICT·CAUTION·OK 케이스는 그대로 통과해야 한다 — 단언 수정 금지.)

- [x] **2. RED 확인**: `npx vitest run src/components/compat/CompatBanner.test.tsx` —
  분리 케이스가 "시너지 문구가 alert 안에 있음"으로, SYNERGY 케이스가 "status 없음"으로 실패
- [x] **3. 구현**: `VERDICT_ORDER = ['CONFLICT','CAUTION','SYNERGY']`로 findings를 verdict별
  그룹핑, 빈 그룹은 렌더하지 않고, 그룹마다 기존 `bb-compat-banner` 마크업(TONE_CLASS/HEADING/
  ICON 유지)을 반복. SYNERGY만 `role="status"`. 컴포넌트 주석의 "SYNERGY도 alert 재사용"
  문구를 새 결정(§2 결정 1)에 맞게 고친다.
- [x] **4. GREEN + `npm test` + `npx tsc -b`**
- [x] **5. 스크린샷**: `VITE_USE_MOCK=true npm run dev` → 장바구니 궁합 배너 상태를 캡처.
  목 핸들러의 compat 응답이 혼합 케이스가 아니면 **핸들러를 고치지 말고**(T4 소유) 목이
  주는 단일 verdict 화면을 찍는다 — 혼합 분리의 시각 검증은 테스트(스텝 1)와 W2 실스택
  확인이 담당한다 → 파일 경로 보고
- [x] **6. 커밋** `fix(cart): 궁합 배너를 verdict별 섹션으로 분리`

### T2-A: 목록 정렬 셀렉트

**Files:** `ListToolbar.tsx`(신규), `ListToolbar.css`(신규), `ListToolbar.test.tsx`(신규), `GoodsList.tsx`, `GoodsList.css`, `GoodsList.test.tsx`
**Interfaces:** Produces `<ListToolbar category={string|null} sort={GoodsSort} priceBand={PriceBand|null} onSortChange onPriceBandChange />` — 카테고리 탭은 `<Link>`로 자체 내비게이션. `type PriceBand = 'UNDER_10K' | 'FROM_10K_TO_30K' | 'OVER_30K'`

- [ ] **1. 실패 테스트** — `ListToolbar.test.tsx` (신규):

```tsx
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ListToolbar } from './ListToolbar';

function renderToolbar(props: Partial<Parameters<typeof ListToolbar>[0]> = {}) {
  const onSortChange = vi.fn();
  const onPriceBandChange = vi.fn();
  render(
    <MemoryRouter>
      <ListToolbar
        category="C002"
        sort="popular"
        priceBand={null}
        onSortChange={onSortChange}
        onPriceBandChange={onPriceBandChange}
        {...props}
      />
    </MemoryRouter>,
  );
  return { onSortChange, onPriceBandChange };
}

describe('ListToolbar', () => {
  it('정렬 5종을 서버 GoodsSort 값으로 노출한다', () => {
    renderToolbar();
    const select = screen.getByRole('combobox', { name: '정렬' });
    const values = Array.from(select.querySelectorAll('option')).map((o) => o.getAttribute('value'));
    expect(values).toEqual(['popular', 'new', 'sales', 'priceAsc', 'discount']);
  });

  it('정렬을 바꾸면 onSortChange가 서버 값으로 불린다', async () => {
    const { onSortChange } = renderToolbar();
    await userEvent.selectOptions(screen.getByRole('combobox', { name: '정렬' }), 'priceAsc');
    expect(onSortChange).toHaveBeenCalledWith('priceAsc');
  });

  it('루틴 5단계 탭을 렌더하고 현재 카테고리에 aria-current를 단다', () => {
    renderToolbar({ category: 'C002' });
    const current = screen.getByRole('link', { name: '클렌징' });
    expect(current).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: '선크림' })).toHaveAttribute('href', '/goods?category=C004001');
  });

  it('루틴 단계 밖 카테고리면 탭을 렌더하지 않는다', () => {
    renderToolbar({ category: 'C003' });
    expect(screen.queryByRole('link', { name: '클렌징' })).not.toBeInTheDocument();
  });

  it('가격대 pill을 토글하면 onPriceBandChange가 불리고, 선택된 pill을 다시 누르면 해제(null)된다', async () => {
    const { onPriceBandChange } = renderToolbar({ priceBand: 'UNDER_10K' });
    await userEvent.click(screen.getByRole('button', { name: '1만원 미만' }));
    expect(onPriceBandChange).toHaveBeenCalledWith(null);
    await userEvent.click(screen.getByRole('button', { name: '3만원 이상' }));
    expect(onPriceBandChange).toHaveBeenCalledWith('OVER_30K');
  });
});
```

- [ ] **2. RED 확인** → **3. 구현**: DESIGN.md `list-toolbar` 사양. 탭은 `ROUTINE_STEPS`
  (`features/routine/steps.ts`) import, `to={'/goods?category=' + step.categoryCode}`.
  정렬 라벨: 인기순/최신순/판매량순/낮은 가격순/높은 할인율순. 가격대 pill은
  `aria-pressed`로 선택 상태. 색 반전은 DESIGN.md "선택 상태 — 색 반전" 토큰.
- [ ] **4. GoodsList 배선 실패 테스트** — `GoodsList.test.tsx`에 추가 (기존 렌더 헬퍼 관례 사용):

```tsx
it('sort 쿼리 파라미터가 fetch에 그대로 전달되고 URL이 상태의 진실이다', async () => {
  // MSW 핸들러가 받은 요청의 searchParams를 검사한다 — 기존 GoodsList.test의 서버 스파이 관례를 따른다
  renderGoodsList('/goods?category=C002&sort=priceAsc');
  await screen.findByRole('combobox', { name: '정렬' });
  expect(capturedSearchParams?.get('sort')).toBe('priceAsc');
});

it('정렬 변경은 setSearchParams로 URL을 바꾼다 — 기본값 popular은 파라미터를 지운다', async () => {
  renderGoodsList('/goods?category=C002&sort=priceAsc');
  await userEvent.selectOptions(await screen.findByRole('combobox', { name: '정렬' }), 'popular');
  expect(currentLocation().search).not.toContain('sort=');
});

it('가격대 pill 선택 시 minPrice/maxPrice가 요청에 실린다', async () => {
  renderGoodsList('/goods?category=C002&price=FROM_10K_TO_30K');
  await screen.findByRole('combobox', { name: '정렬' });
  expect(capturedSearchParams?.get('minPrice')).toBe('10000');
  expect(capturedSearchParams?.get('maxPrice')).toBe('29999');
});
```

  가격대 → 파라미터 매핑(경계값이 사양이다): `UNDER_10K` → `maxPrice=9999` /
  `FROM_10K_TO_30K` → `minPrice=10000&maxPrice=29999` / `OVER_30K` → `minPrice=30000`.
  URL 파라미터 이름은 `price`.
- [ ] **5. RED → 구현 → GREEN**: `useSearchParams`에서 `sort`(미지값이면 popular로 정규화 —
  서버 400 방지)·`price` 읽기, `queryKey: ['goods-list', category, tag, sort, priceBand]`,
  `fetchGoodsList`에 전달. **`api/goods.ts`는 무수정**(파라미터 타입이 이미 있다).
- [ ] **6. `npm test` + `npx tsc -b`**
- [ ] **7. 스크린샷**: 목 dev 서버로 `/goods?category=C002` — 탭·정렬·가격대가 보이는 상태,
  정렬을 "낮은 가격순"으로 바꾼 상태 2장 → 경로 보고
- [ ] **8. 커밋** `feat(goods): 카테고리 목록에 정렬·카테고리 탭·가격대 필터`

### T3-A: 라우트 전환 스크롤 초기화

**Files:** `Layout.tsx`, `Layout.test.tsx`, `router.test.tsx`(구조 복제 갱신이 필요할 때만)
**Interfaces:** 없음 (Layout 내부 effect)

- [ ] **1. 실패 테스트** — `Layout.test.tsx`에 추가 (기존 렌더 관례를 따르되, 핵심 단언은 이것):

```tsx
it('pathname이 바뀌면 스크롤을 즉시(top:0, instant) 리셋한다', async () => {
  const scrollTo = vi.fn();
  vi.stubGlobal('scrollTo', scrollTo); // jsdom은 scrollTo 미구현 — 스텁이 곧 관측 지점
  renderLayoutAt('/main'); // createMemoryRouter로 Layout + 더미 자식 2개를 세우는 헬퍼
  scrollTo.mockClear(); // 첫 렌더 호출은 관심 밖 — 전환만 검증한다
  await navigateTo('/cart');
  expect(scrollTo).toHaveBeenCalledWith({ top: 0, left: 0, behavior: 'instant' });
});

it('같은 pathname에서 쿼리스트링만 바뀌면 리셋하지 않는다', async () => {
  const scrollTo = vi.fn();
  vi.stubGlobal('scrollTo', scrollTo);
  renderLayoutAt('/goods?category=C002');
  scrollTo.mockClear();
  await navigateTo('/goods?category=C002&sort=priceAsc');
  expect(scrollTo).not.toHaveBeenCalled();
});
```

- [ ] **2. RED → 구현**: Layout 안 `useLocation().pathname` 의존 `useEffect`에서
  `window.scrollTo({ top: 0, left: 0, behavior: 'instant' })`. (§2 결정 5 — smooth 개입 차단.)
- [ ] **3. GREEN + `npm test`** — `router.test.tsx`가 Layout 구조 변경으로 깨지면 같은 구조로 갱신
- [ ] **4. 수동 확인**: 목 dev 서버에서 목록 중간 스크롤 → 카드 클릭 → 상세가 최상단인 것 스크린샷
- [ ] **5. 커밋** `fix(shell): 라우트 전환 시 스크롤 최상단 초기화`

### T3-B: 푸터

**Files:** `Footer.tsx`(신규), `Footer.css`(신규), `Footer.test.tsx`(신규), `Layout.tsx`, `Layout.css`
**Interfaces:** Produces `<Footer />` (props 없음, 랜딩 판별은 Footer 내부 `useLocation`)

- [ ] **1. 실패 테스트** — `Footer.test.tsx` (신규):

```tsx
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Footer } from './Footer';

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Footer />
    </MemoryRouter>,
  );
}

describe('Footer', () => {
  it('contentinfo 랜드마크로 렌더되고 데모 고지가 있다', () => {
    renderAt('/main');
    const footer = screen.getByRole('contentinfo');
    expect(footer).toHaveTextContent('본 사이트는 취업 포트폴리오용 데모입니다');
    expect(footer).toHaveTextContent('© 2026 BeautyBoy — Portfolio Demo');
  });

  it('전자상거래 표기 항목이 형식 예시 값으로 존재한다', () => {
    renderAt('/main');
    const footer = screen.getByRole('contentinfo');
    expect(footer).toHaveTextContent('사업자등록번호 000-00-00000');
    expect(footer).toHaveTextContent('통신판매업 신고번호');
  });

  it('약관·개인정보처리방침은 대상 화면이 없으므로 링크가 아니다', () => {
    renderAt('/main');
    expect(screen.queryByRole('link', { name: '이용약관' })).not.toBeInTheDocument();
    expect(screen.getByText('이용약관')).toBeInTheDocument();
  });

  it('랜딩(/)에서는 렌더하지 않는다', () => {
    renderAt('/');
    expect(screen.queryByRole('contentinfo')).not.toBeInTheDocument();
  });
});
```

- [ ] **2. RED → 구현**: DESIGN.md `footer-beautyboy` 사양 그대로(표기 항목·문구 포함).
  `Layout.tsx`의 `<main>` 아래에 `<Footer />`, `Layout.css`의 `.bb-layout`에
  `display: flex; flex-direction: column; min-height: 100dvh;` 추가.
- [ ] **3. GREEN + `npm test` + `npx tsc -b`**
- [ ] **4. 스크린샷**: 메인 최하단(푸터 보이게) + 콘텐츠 짧은 화면(마이페이지 등)에서 푸터가
  하단에 붙는 것 2장 → 경로 보고
- [ ] **5. 커밋** `feat(shell): 전자상거래 표기 푸터 (데모 고지 포함)`

### T4-A: 장바구니 응답 확장 + 재고 상한 (백엔드)

**Files:** `CartItemResponse.java`, `CartService.java`, `CartServiceTest.java`, `GoodsQueryService.java`, `GoodsService.java`, `GoodsServiceTest.java`
**Interfaces:** Produces §3-2 `CartItemResponse`(+thumbnailUrl, stock), §3-3 `OrderGoodsSnapshot`(+thumbnailUrl)

- [ ] **1. 스냅샷 확장 실패 테스트** — `GoodsServiceTest`의 기존 findOrderSnapshot 케이스 옆에:

```java
@Test
@DisplayName("주문 스냅샷에 썸네일 URL이 실려 온다 — 장바구니 표시용")
void 스냅샷_썸네일() {
    // 기존 findOrderSnapshot 픽스처 관례(goods+option 세팅)를 그대로 쓴다
    OrderGoodsSnapshot snapshot = goodsQueryService.findOrderSnapshot(goodsNo, optionNo).orElseThrow();
    assertThat(snapshot.thumbnailUrl()).isEqualTo(fixtureThumbnailUrl); // 픽스처 goods의 썸네일 값
}
```

- [ ] **2. RED(record에 필드 없음 — 컴파일 실패) → §3-3 구현 → GREEN.** 컴파일이 잡아주는
  Files 밖 생성자 호출부는 인자 추가만(§4 소유권 예외 규칙), 목록 보고
- [ ] **3. 재고 상한 실패 테스트** — `CartServiceTest`에 추가 (기존 픽스처 관례 사용):

```java
@Test
@DisplayName("담기: 기존 수량과 합쳐 재고를 넘으면 409 ORDER_OUT_OF_STOCK")
void 담기_재고_초과() {
    // 재고 3인 옵션에 2개가 이미 담긴 상태에서 2개를 더 담는다 — 결과 4 > 3
    cartService.add(memberId, new CartAddRequest(goodsNo, optionNo, 2));
    assertThatThrownBy(() -> cartService.add(memberId, new CartAddRequest(goodsNo, optionNo, 2)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_OUT_OF_STOCK);
}

@Test
@DisplayName("수량 변경: 재고를 넘는 값이면 409, 재고 이내면 성공")
void 수량변경_재고_초과() {
    cartService.add(memberId, new CartAddRequest(goodsNo, optionNo, 1));
    Long cartItemId = 단일_항목_id(memberId);
    assertThatThrownBy(() -> cartService.changeQuantity(memberId, cartItemId, 4)) // 재고 3
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_OUT_OF_STOCK);
    cartService.changeQuantity(memberId, cartItemId, 3); // 경계값 — 재고와 같으면 성공
}

@Test
@DisplayName("옵션 없는 상품(stock=MAX_VALUE)은 상한 검증을 자연 통과한다")
void 옵션없는_상품_통과() {
    cartService.add(memberId, new CartAddRequest(optionlessGoodsNo, null, 50));
    // 예외 없이 통과하면 성공 — 별도 단언 불필요
}

@Test
@DisplayName("응답에 thumbnailUrl과 stock이 실려 온다")
void 응답_확장_필드() {
    cartService.add(memberId, new CartAddRequest(goodsNo, optionNo, 1));
    CartItemResponse item = cartService.items(memberId).get(0);
    assertThat(item.thumbnailUrl()).isEqualTo(fixtureThumbnailUrl);
    assertThat(item.stock()).isEqualTo(3);
}
```

- [ ] **4. RED → §3-2·§3-4 구현 → GREEN.** `items()` 조립부는 스냅샷의 thumbnailUrl·stock을
  그대로 옮긴다(별도 조회 금지 — 이미 스냅샷을 부르고 있다)
- [ ] **5. `./gradlew test` 전체 녹색**
- [ ] **6. 커밋** `feat(cart): 응답에 썸네일·재고 추가, 담기·수량변경에 재고 상한 검증`

### T4-B: 장바구니 화면 상세화 (프론트)

**Files:** `api/cart.ts`, `CartLine.tsx`, `CartLine.css`, `Cart.tsx`, `Cart.test.tsx`, `mocks/handlers.ts`(cart 픽스처만)
**Interfaces:** Consumes §3-2 `CartItem`(+thumbnailUrl, stock)

- [ ] **1. `api/cart.ts` 타입에 §3-2 필드 추가 + 목 cart 픽스처에 `thumbnailUrl`(기존 goods
  썸네일 경로 재사용)·`stock`(3처럼 작은 값 하나 포함 — 상한 UI 검증용) 추가.** `npx tsc -b`로
  픽스처 누락 전부 잡기
- [ ] **2. 실패 테스트** — `Cart.test.tsx`에 추가 (기존 렌더·MSW 관례):

```tsx
it('장바구니 줄에 썸네일과 개당 가격이 보인다', async () => {
  renderCart();
  const line = await screen.findByTestId(`cart-line-${fixtureCartItemId}`);
  expect(within(line).getByRole('img')).toHaveAttribute('src', fixtureThumbnailUrl);
  expect(line).toHaveTextContent('개당 12,000원'); // unitPrice 표기 형식이 사양이다
});

it('수량 스텝퍼 상한은 min(재고, 99)다 — 재고 3이면 3에서 + 버튼이 비활성', async () => {
  renderCart(); // stock=3 픽스처 항목 기준
  const line = await screen.findByTestId(`cart-line-${lowStockCartItemId}`);
  // 수량을 3까지 올리면 증가 버튼이 disabled — QuantityStepper 기존 경계 동작을 재고값으로 검증
  expect(within(line).getByRole('button', { name: '수량 증가' })).toBeDisabled();
});

it('합계 라벨은 "결제 예상 금액"이고 배송비 안내가 붙는다', async () => {
  renderCart();
  expect(await screen.findByText('결제 예상 금액')).toBeInTheDocument();
  expect(screen.getByText('배송비는 주문서에서 계산됩니다')).toBeInTheDocument();
});
```

  (버튼 접근명·testid는 기존 CartLine/QuantityStepper 마크업 관례를 먼저 읽고 그에 맞춘다 —
  없으면 `data-testid="cart-line-{cartItemId}"`를 이 사양대로 추가한다.)
- [ ] **3. RED → 구현**: 썸네일은 `goods-thumbnail` 축소형(56px, `{rounded.md}`,
  `--color-surface-cool` 플레이스홀더, `alt=""` — 상품명 텍스트가 바로 옆에 있다),
  개당 가격 `개당 {formatWon(unitPrice)}` meta 톤(`--color-graphite` — 카드 대비 최저선 준수),
  스텝퍼 `max={Math.min(item.stock, 99)}`
- [x] **4. GREEN + `npm test` + `npx tsc -b`**
- [ ] **5. 스크린샷**: 목 dev 서버 장바구니 — 썸네일·개당가·재고 상한(+ 비활성) 보이는 상태 → 경로 보고
- [ ] **6. 커밋** `feat(cart): 장바구니 줄 썸네일·개당가·재고 상한, 합계 문구 명확화`

### T4-C: 별점 표기 + 카드 대비

**Files:** `Rating.tsx`, `Rating.css`, `Rating.test.tsx`(신규), `GoodsCard.css`
**Interfaces:** `<Rating rating reviewCount />` props 불변

- [ ] **1. 실패 테스트** — `Rating.test.tsx` (신규):

```tsx
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Rating } from './Rating';

describe('Rating', () => {
  it('리뷰가 있으면 별 글리프 1개 + 소수 1자리 점수 + 리뷰수를 보여준다', () => {
    render(<Rating rating={4} reviewCount={3} />);
    const rating = screen.getByText((_, el) => el?.classList.contains('bb-rating') ?? false);
    expect(rating).toHaveTextContent('★ 4.0 (3)');
  });

  it('별 글리프는 장식이므로 스크린리더에서 숨긴다', () => {
    render(<Rating rating={4} reviewCount={3} />);
    expect(screen.getByText('★')).toHaveAttribute('aria-hidden', 'true');
  });

  it('리뷰가 없으면 "첫 리뷰를 기다려요"를 보여준다 — "리뷰 없음"은 쓰지 않는다', () => {
    render(<Rating rating={0} reviewCount={0} />);
    expect(screen.getByText('첫 리뷰를 기다려요')).toBeInTheDocument();
    expect(screen.queryByText('리뷰 없음')).not.toBeInTheDocument();
  });
});
```

- [ ] **2. RED → 구현**: DESIGN.md "평점 줄" 사양. `Rating.css`의 두 상태 색을
  `var(--color-ash)` → `var(--color-graphite)`
- [ ] **3. 대비 수정**: `GoodsCard.css:85-91` 브랜드명 `var(--color-slate)` →
  `var(--color-graphite)` (DESIGN.md "카드 위 보조 텍스트 최저선" — surface 위 slate는 4.2:1로 미달)
- [ ] **4. GREEN + `npm test`.** Rating 문구 변화로 깨지는 기존 테스트(GoodsCard.test 등)는
  새 사양 문구로 갱신 — 단 "자리 유지(높이 안정성)" 단언은 지우지 않는다
- [ ] **5. 스크린샷**: 목 dev 서버 목록 화면 — 별점 표기·빈 상태 문구·대비가 보이는 카드 그리드 → 경로 보고
- [ ] **6. 커밋** `fix(goods): 별점 글리프 표기와 카드 메타 대비 AA 충족`

### T4-D: 옵션 미선택 힌트

**Files:** `Detail.tsx`, `Detail.css`, `Detail.test.tsx`
**Interfaces:** 없음

- [ ] **1. 실패 테스트** — `Detail.test.tsx`에 추가 (기존 옵션 2개 픽스처·렌더 관례 사용):

```tsx
it('옵션 미선택이면 담기 버튼 아래에 힌트가 보이고, 선택하면 사라진다', async () => {
  renderDetail(goodsWithTwoOptions);
  expect(await screen.findByText('옵션을 선택해주세요')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('radio', { name: /40ml/ }));
  expect(screen.queryByText('옵션을 선택해주세요')).not.toBeInTheDocument();
});

it('옵션이 1개라 자동 선택되면 힌트를 보여주지 않는다', async () => {
  renderDetail(goodsWithSingleOption);
  await screen.findByRole('button', { name: '장바구니 담기' });
  expect(screen.queryByText('옵션을 선택해주세요')).not.toBeInTheDocument();
});
```

- [ ] **2. RED → 구현**: `optionRequired && <p className="bb-detail__option-hint">옵션을
  선택해주세요</p>`를 CTA 버튼 바로 아래에. 스타일은 `{typography.meta}` /
  `var(--color-graphite)`. 버튼과의 연결은 `aria-describedby`
- [ ] **3. GREEN + `npm test` + `npx tsc -b`**
- [ ] **4. 스크린샷**: 목 dev 서버 옵션 2개 상품 상세 — 힌트 노출 상태 → 경로 보고
- [ ] **5. 커밋** `fix(goods): 옵션 미선택 시 담기 버튼 힌트 표시`

---

## 6. W2 — 직렬 검증 웨이브 (오케스트레이터, 머지 후)

- [ ] 4개 브랜치 리뷰(테스트 통과 + 파일 소유권 준수 + 스크린샷 열어보기) 후 main 머지.
  충돌 예상 지점 없음(소유권 분리) — `OrderGoodsSnapshot` 파급 보고가 있으면 그 파일 먼저 확인
- [ ] 실스택 기동(compose 전체 스택) 후 확인:
  - [ ] **2-5**: 실서버 장바구니에서 궁합 카테고리가 한글로 나오는지 (목이 아니라 실 DB 값 —
    T1의 진짜 완료 조건. `curl-smoke-recipe`의 CONFLICT 시드 goods 사용)
  - [ ] **2-4**: 실서버 장바구니 썸네일 경로가 로컬 에셋으로 렌더되는지
  - [ ] **1-1 재현 확인**: 로그인 → 담기 → 메인 이동 → 뱃지 숫자 일치 여부. 불일치 재현 시
    별도 이슈로 보고(이 계획 범위 밖 — §1 판정 유지)
  - [ ] **1-3 재현 확인**: 랜딩에서 스크롤·리사이즈 시 흰 여백/빈 스크롤 영역. 재현 시 증상
    스크린샷과 함께 보고 후 후속 계획
  - [ ] **1-4 재현 확인**: 메인 스텝 내비로 이동 시 섹션 제목 가림 여부
- [ ] `./gradlew test` + `./gradlew integrationTest` + `npm test` + `npx tsc -b` +
  `npm run test:e2e` 전체 녹색 확인 (e2e가 "리뷰 없음"·"합계(안내용)" 등 옛 문구를 단언하면 갱신)
- [ ] `docs/plans/2026-07-26-다음-작업.md`에 결과 기록

---

## 7. 터미널 실행 프롬프트

> 사람은 프로젝트 루트에서 터미널을 열고 아래를 통째로 붙여넣는다. git 명령을 손으로 치지 않는다.

### T1 — 궁합·성분

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add ../뷰티보이-궁합 -b feature/review-compat-labels
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 해당 worktree인지
  - git log --oneline -1 이 루트에서 본 기점 커밋(docs(plan): 디자인 리뷰 반영 계획)과 같은지
  - docs/plans/2026-07-28-design-review-fixes.md 와 DESIGN.md 의 compat-banner 절이 실제로 존재하는지
  - git status가 깨끗한지

[2단계 — 실행] docs/plans/2026-07-28-design-review-fixes.md 의 T1-A, T1-B를 순서대로 실행해라.
너는 이 계획의 T1 실행 서브에이전트다(model: sonnet). CLAUDE.md 공통 규칙과 계획서의
Global Constraints·파일 소유권 표를 지켜라. T1 소유 파일 밖은 수정 금지 — 어긋나면 중단·보고.
각 태스크는 계획서의 스텝(실패 테스트 → RED 확인 → 구현 → GREEN → 스크린샷 → 커밋)을
그대로 밟고, 스텝마다 체크박스를 갱신해라. 완료 보고에는 테스트 결과 요약과 스크린샷
파일 경로를 포함해라.
```

### T2 — 목록·정렬

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add ../뷰티보이-목록 -b feature/review-list-toolbar
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 해당 worktree인지
  - git log --oneline -1 이 루트에서 본 기점 커밋(docs(plan): 디자인 리뷰 반영 계획)과 같은지
  - docs/plans/2026-07-28-design-review-fixes.md 와 DESIGN.md 의 list-toolbar 절이 실제로 존재하는지
  - git status가 깨끗한지

[2단계 — 실행] docs/plans/2026-07-28-design-review-fixes.md 의 T2-A를 실행해라.
너는 이 계획의 T2 실행 서브에이전트다(model: sonnet). CLAUDE.md 공통 규칙과 계획서의
Global Constraints·파일 소유권 표를 지켜라. 특히 api/goods.ts 와 mocks/handlers.ts 는
소유가 아니다 — 부족하면 고치지 말고 보고. DESIGN.md list-toolbar 사양과 "선택 상태 —
색 반전" 규칙을 CSS 작성 전에 읽어라. 스텝별 TDD와 스크린샷 DoD는 계획서 그대로.
```

### T3 — 셸 (스크롤·푸터)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add ../뷰티보이-셸 -b feature/review-shell
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 해당 worktree인지
  - git log --oneline -1 이 루트에서 본 기점 커밋(docs(plan): 디자인 리뷰 반영 계획)과 같은지
  - docs/plans/2026-07-28-design-review-fixes.md 와 DESIGN.md 의 footer-beautyboy 절이 실제로 존재하는지
  - git status가 깨끗한지

[2단계 — 실행] docs/plans/2026-07-28-design-review-fixes.md 의 T3-A, T3-B를 순서대로 실행해라.
너는 이 계획의 T3 실행 서브에이전트다(model: sonnet). CLAUDE.md 공통 규칙과 계획서의
Global Constraints·파일 소유권 표를 지켜라. 푸터의 사업자 표기는 계획서 §2 결정 6 그대로 —
실존 정보처럼 쓰지 말고 데모 고지를 반드시 포함해라. 스텝별 TDD와 스크린샷 DoD는 계획서 그대로.
```

### T4 — 카드·상세·장바구니

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add ../뷰티보이-카드 -b feature/review-card-cart
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 해당 worktree인지
  - git log --oneline -1 이 루트에서 본 기점 커밋(docs(plan): 디자인 리뷰 반영 계획)과 같은지
  - docs/plans/2026-07-28-design-review-fixes.md 가 실제로 존재하는지
  - git status가 깨끗한지

[2단계 — 실행] docs/plans/2026-07-28-design-review-fixes.md 의 T4-A → T4-B → T4-C → T4-D를
순서대로 실행해라. 너는 이 계획의 T4 실행 서브에이전트다(model: sonnet). CLAUDE.md 공통
규칙과 계획서의 Global Constraints·파일 소유권 표를 지켜라. §3-3 OrderGoodsSnapshot 확장의
컴파일 파급이 소유 파일 밖(order·payment 등)에 닿으면 생성자 인자 추가만 하고, 만진 파일
전부를 보고서에 명시해라. ErrorCode.java는 열지 않는다(기존 ORDER_OUT_OF_STOCK 재사용).
스텝별 TDD와 스크린샷 DoD는 계획서 그대로.
```
