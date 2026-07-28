# 구현 계획 — 목록 성숙: 리뷰 정렬 · 페이지네이션 · compose 배선 (2026-07-28)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development 또는
> superpowers:executing-plans로 태스크 단위 실행. 스텝은 체크박스로 추적한다.
>
> 근거: `docs/plans/2026-07-26-다음-작업.md` §0-2 잔여 백로그("리뷰 많은 순") + §5(compose 배선)
> + GoodsList의 페이지네이션 보류 주석. **이 문서의 §2 "설계 결정"이 설계의 진실을 겸한다.**
> DESIGN.md에는 이 계획과 같은 커밋으로 `pager` 사양 신설 + `list-toolbar` 정렬 6종 갱신이
> 들어 있다 — 프론트 태스크는 그 절을 따른다.
> **마이그레이션 1건: V84**(goods.review_count). 현재 최신은 V83 — [[flyway-number-at-merge-time]]
> 규칙대로 **머지 직전 main의 최신 번호를 재확인**하고 역전이면 번호를 올린다(이번 웨이브에서
> 마이그레이션을 만드는 터미널은 T1 하나뿐이라 웨이브 내 경쟁은 없다).

**Goal:** "리뷰 많은 순" 정렬(백엔드 비정규화 + 프론트 노출), 목록 페이지네이션 + 뒤로가기
스크롤 복원, 그리고 **지난 웨이브 잔여 결함인 가격대 필터 미배선**(`FetchGoodsListParams`에
minPrice/maxPrice 부재 — UI만 있고 요청에 안 실림)을 마감한다. compose의 Redis 미배선도 정리한다.

**Architecture:** 리뷰수는 `view_count`·`sales_count` 선례 그대로 goods의 비정규화 컬럼으로
두고(카탈로그 소유), review가 재집계 시점에 카탈로그 커맨드 경계로 동기화한다. 페이지·정렬·필터
상태는 전부 URL이 진실. 스크롤 복원은 react-router 데이터 라우터의 `<ScrollRestoration>`으로
기존 수제 effect를 대체한다.

**Tech Stack:** Spring Boot(JPQL orderBy 확장, Flyway V84) / React(react-router-dom 7.18,
`<ScrollRestoration>`, TanStack Query) / docker compose.

## Global Constraints (CLAUDE.md 재확인)

- 자기 터미널의 Files 목록 밖 파일 수정 금지. `common` 패키지는 열지 않는다.
- `ddl-auto=validate` — 엔티티 필드 추가는 반드시 마이그레이션과 같은 커밋으로.
- CSS는 `var(--color-*)` 토큰만. DESIGN.md `pager`·`list-toolbar` 사양을 CSS 작성 전에 읽는다.
- 프론트 전체 판정 `npm test` + `npx tsc -b`. 백엔드 `./gradlew test`(+ T1은 `integrationTest`).
- 화면을 바꾸는 태스크는 `VITE_USE_MOCK=true npm run dev` 스크린샷 DoD.
- 커밋 메시지·주석 한국어, 태스크 단위 원자 커밋.

---

## 0. 사람이 할 일 (사전 조건 2줄)

터미널을 열기 전에 프로젝트 루트에서:

```
git log --oneline -1     # 이 계획 커밋(docs(plan): 목록 성숙 계획)인지 확인
git status               # 깨끗한지 확인
```

---

## 1. 웨이브·터미널 분할

| 웨이브 | 터미널 | 브랜치 | 범위 | 모델 |
|---|---|---|---|---|
| W1 (병렬 2) | T1 | `feature/review-sort-backend` | V84 + 리뷰수 동기화 + GoodsSort 확장 (백엔드 전용) | sonnet |
| W1 | T2 | `feature/list-pagination` | 페이저 + 스크롤 복원 + 가격대 필터 배선 + 정렬 6종 노출 (프론트 전용) | sonnet |
| W2 (직렬) | 오케스트레이터 | — | compose Redis 배선 + 머지 게이트 + 실스택 검증 + E2E | opus |

- **프론트 정렬 노출을 T1이 아니라 T2에 몰았다**: 정렬 1종 추가와 페이지네이션이 같은 파일들
  (`api/goods.ts`·`GoodsList.tsx`·`ListToolbar.tsx`·`handlers.ts`)을 만지므로, 나누면 충돌한다.
  T1·T2의 계약은 "정렬 파라미터 문자열 `review`" 하나뿐이라 병렬에 문제 없다(T2는 목 정렬로 개발).
- **compose 배선은 터미널을 열지 않는다**: 코드가 아니라 인프라 설정 3줄이고, 검증이 실 자원
  (컨테이너 재기동·Redis 관찰)이라 직렬 웨이브 성격이다 — W2에서 오케스트레이터가 직접 한다.

### 파일 소유권

| 터미널 | 소유 파일 |
|---|---|
| T1 | `backend/src/main/resources/db/migration/V84__goods_review_count.sql`(신규), `backend/…/catalog/Goods.java`, `backend/…/catalog/GoodsSort.java`, `backend/…/catalog/GoodsReviewCountCommand.java`(신규), `backend/…/catalog/GoodsReviewCountService.java`(신규), `backend/…/review/ReviewService.java`, `backend/…/catalog/GoodsReviewCountServiceTest.java`(신규), `backend/…/catalog/GoodsSortTest.java`(있으면 수정·없으면 신규), `backend/…/review/ReviewServiceTest.java`(동기화 케이스 추가) |
| T2 | `frontend/src/api/goods.ts`, `frontend/src/api/goods.test.ts`, `frontend/src/pages/GoodsList.tsx`, `GoodsList.css`, `GoodsList.test.tsx`, `frontend/src/components/goods/ListToolbar.tsx`, `ListToolbar.test.tsx`, `frontend/src/components/goods/Pager.tsx`(신규), `Pager.css`(신규), `Pager.test.tsx`(신규), `frontend/src/components/layout/Layout.tsx`, `Layout.test.tsx`, `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/fixtures/goods.ts` |
| W2(오케스트레이터) | `docker-compose.yml` |

- T1은 프론트를 만지지 않는다. T2는 백엔드를 만지지 않는다.
- `Goods.java` 수정은 **필드 1개 추가**(§3-1)뿐 — 다른 필드·메서드 변경 금지.
- `ReviewService.java` 수정은 `recalculateStat` 안의 **동기화 호출 1곳**(§3-3)뿐.

---

## 2. 설계 결정

### 결정 1 — 리뷰수는 goods의 비정규화 컬럼, 동기화는 "재계산 값 그대로 set"

- **왜 비정규화인가**: `GoodsSort.orderByClause`는 `g.` 별칭(Goods 엔티티)만 참조할 수 있고,
  `goods_review_stat`은 review 소유라 catalog JPQL이 조인할 수 없다(패키지 = 서비스 경계).
  정렬 키는 Goods 안에 있어야 하고, `view_count`·`sales_count`가 정확히 같은 이유로 이미
  비정규화돼 있다(V10 주석 "sort=popular (결정 2)").
- **왜 증감(+1)이 아니라 set인가**: review의 집계는 `recalculateStat`이 매번 `COUNT(*)`로
  전량 재계산하는 방식이다(작성·삭제 어느 경로든 이 한 메서드). 증감 누적을 따로 두면 두 값이
  드리프트할 수 있는 반면, **재계산된 count를 그대로 UPDATE**하면 goods.review_count는 항상
  goods_review_stat과 같은 시점 값이고 멱등이다. 삭제 API가 아직 없어도 자동으로 대비된다.
- 백필: V84가 `goods_review_stat`에서 UPDATE ... JOIN으로 채운다(마이그레이션 SQL은 DB 레벨
  작업이라 도메인 경계 무관). 리뷰가 있는 상품은 45개 — 나머지 145개는 0으로 남고,
  2차 정렬키 `g.id desc`가 동률을 안정화한다(기존 모든 정렬과 동일 규약).
- 인덱스는 만들지 않는다 — 목록은 항상 `(category_code, status)` 인덱스로 좁힌 뒤 정렬하고,
  전체 190행 규모에서 정렬 컬럼 인덱스는 이득이 없다. 필요해지는 규모면 그때 추가한다.

### 결정 2 — review→catalog 방향은 카탈로그 소유의 커맨드 인터페이스

- 조회(카탈로그가 리뷰 통계를 당겨오는 것)는 `GoodsRatingProvider`(catalog 정의, review 구현)가
  이미 있다. 이번엔 반대 방향의 **쓰기 통지**라 새 경계가 필요하다:
  `GoodsReviewCountCommand`(catalog 소유·구현, review가 호출) — `StockCommandService`를
  order/payment가 호출하는 것과 같은 방향·같은 관례다.
- 구현은 파생 UPDATE 쿼리 1개(§3-2). 리뷰 트랜잭션 안에서 불리므로 `MANDATORY`로 계약을
  강제한다(리뷰 저장과 카운트 갱신이 원자적으로 함께 커밋/롤백).

### 결정 3 — 페이지네이션: 번호 페이저 + `?page=` URL (1-based)

- **더보기/무한스크롤이 아니라 번호 페이저**: 이 시스템의 편집디자인 언어(명시적 구조, 그림자·
  플로팅 없음)에 맞고, URL 공유·뒤로가기 복원이 공짜로 따라온다. 시드 190개(전체)·C001 67개가
  1페이지(40)를 넘어 실제로 필요하다.
- URL은 `?page=2`부터(1-based 표시, 1이면 파라미터 생략 — sort=popular 생략과 같은 규약).
  API 호출 시 0-based로 변환. 쿼리키에 page 포함.
- `keepPreviousData`(TanStack Query `placeholderData: keepPreviousData`)로 페이지 전환 중
  이전 목록을 유지해 그리드 높이 붕괴를 막는다.
- 페이지 변경 시 **목록 상단(툴바)으로 스크롤** — 문서 최상단이 아니다(DESIGN.md `pager`).
  Layout의 스크롤 처리는 pathname 기준이라 쿼리 변경에 개입하지 않으므로 GoodsList가 직접 한다.
- 검색·랭킹은 이번 범위 밖(페이저 컴포넌트는 공용으로 만들되 배선은 GoodsList만). 근거:
  랭킹은 배열 응답이라 계약 변경이 필요하고, 검색은 트래픽 진입점이 아니다 — 필요해지면
  Pager 재사용으로 각각 1태스크다.

### 결정 4 — 뒤로가기 스크롤 복원: `<ScrollRestoration>`으로 수제 effect 대체

- react-router-dom 7(데이터 라우터)의 `<ScrollRestoration/>`이 정확히 이 문제를 푼다:
  **뒤로/앞으로는 세션 히스토리 키 기준으로 위치 복원, 새 이동(push)은 최상단**. 지난 웨이브의
  수제 `useEffect`(pathname 기준 scrollTo)는 이 표준 컴포넌트로 대체한다 — 바퀴를 유지보수할
  이유가 없다.
- **의도된 동작 변화 2가지**(기존 Layout 테스트 2건은 이 사양 변화로 교체된다):
  ① 뒤로가기가 이제 최상단이 아니라 **이전 위치로 복원**된다(이번 계획의 목적 그 자체).
  ② 쿼리스트링만 바뀌는 push(정렬·필터 변경)도 새 히스토리 엔트리라 최상단으로 간다 —
  결과 집합이 바뀌었으니 목록 처음부터 보는 게 맞고, 페이지 변경의 스크롤은 결정 3이
  목록 상단으로 따로 처리한다.
- smooth 개입 차단은 유지된다 — `ScrollRestoration`은 브라우저 네이티브 복원처럼 즉시 점프한다.
  jsdom에서 세션스토리지 기반 복원을 단위테스트로 증명하기 어려우므로, 이 동작의 완료 판정은
  **W2 실스택 시나리오**(목록 2페이지 스크롤 → 상세 → 뒤로가기 → 페이지·위치 유지)로 한다.

### 결정 5 — 가격대 필터 배선 마감 (지난 웨이브 잔여 결함)

- `FetchGoodsListParams`에 `minPrice`/`maxPrice`를 추가하고 GoodsList의 NOTE 지점에서 매핑을
  배선한다. 매핑 값은 원계획(2026-07-28-design-review-fixes §T2-A) 그대로:
  `UNDER_10K → maxPrice=9999` / `FROM_10K_TO_30K → minPrice=10000&maxPrice=29999` /
  `OVER_30K → minPrice=30000`.

### 결정 6 — compose Redis 배선 (W2, 오케스트레이터)

- backend 서비스에 `REDIS_HOST: redis`, `VIEW_COUNT_REDIS: "true"`, `depends_on.redis.condition:
  service_healthy` 3줄 추가. 현재 redis 컨테이너는 아무도 안 쓰는 상태다(기본 토글 false +
  호스트 미지정) — 붙이거나, 안 붙일 거면 서비스 정의를 지워야 하는데, Redis 조회수 버퍼는
  이미 구현·조건부 배선까지 끝난 코드라 붙이는 쪽이 맞다.
- Redis가 죽어도 앱은 뜬다(레코더가 warn만 남기고 누락 감수 — 코드에 명시된 설계).
  `service_healthy` 조건은 기동 순서만 보장한다.

---

## 3. 공유 계약 — 코드 전량

### 3-1. `V84__goods_review_count.sql` + `Goods.java` 필드

```sql
-- 리뷰 많은 순 정렬용 비정규화 컬럼 (view_count·sales_count와 같은 계열 — V10 "결정 2" 참조).
-- 진실은 review 도메인의 goods_review_stat이고, 이 컬럼은 재집계 시점마다 같은 값으로 동기화된다
-- (GoodsReviewCountCommand). 정렬 키는 Goods 엔티티 안에 있어야 한다 — catalog JPQL은
-- 타 도메인 테이블을 조인할 수 없다(패키지 = 서비스 경계).
ALTER TABLE goods ADD COLUMN review_count INT NOT NULL DEFAULT 0;

-- 백필: 기존 리뷰 통계를 그대로 옮긴다. 리뷰 없는 상품은 DEFAULT 0.
UPDATE goods g
JOIN goods_review_stat s ON s.goods_id = g.id
SET g.review_count = s.review_count;
```

`Goods.java` — `salesCount` 필드 바로 아래에 추가(다른 변경 금지):

```java
/** 리뷰 많은 순 정렬용 비정규화 값. 진실은 review의 goods_review_stat — 재집계가 동기화한다(V84). */
@Column(name = "review_count", nullable = false)
private int reviewCount = 0;
```

(getter는 기존 필드들과 같은 관례로. setter는 만들지 않는다 — 갱신은 §3-2의 벌크 UPDATE만.)

### 3-2. `catalog/GoodsReviewCountCommand.java` + 구현 (신규 — 전량)

```java
package com.beautyboy.catalog;

/**
 * 상품 리뷰수 동기화 커맨드 경계. 값의 진실은 review 도메인(goods_review_stat)이고,
 * catalog는 정렬용 사본(goods.review_count)만 가진다. review의 재집계가 계산한 값을
 * 그대로 받아 쓴다 — 증감 누적이 아니라 set이므로 멱등이고 드리프트가 없다.
 *
 * <p>호출 계약: 호출자(review)의 트랜잭션 안에서만 부른다(구현이 MANDATORY로 강제) —
 * 리뷰 저장과 카운트 갱신이 원자적으로 함께 커밋/롤백된다.
 */
public interface GoodsReviewCountCommand {

    void syncReviewCount(Long goodsId, int reviewCount);
}
```

```java
package com.beautyboy.catalog;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoodsReviewCountService implements GoodsReviewCountCommand {

    private final EntityManager em;

    public GoodsReviewCountService(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void syncReviewCount(Long goodsId, int reviewCount) {
        // 엔티티 로드 없이 벌크 UPDATE — 이 트랜잭션의 영속성 컨텍스트에 Goods가 올라와 있지 않은
        // 경로(리뷰 작성)에서 불리므로 1차 캐시 불일치 우려가 없다.
        em.createQuery("update Goods g set g.reviewCount = :count where g.id = :goodsId")
                .setParameter("count", reviewCount)
                .setParameter("goodsId", goodsId)
                .executeUpdate();
    }
}
```

### 3-3. `ReviewService.recalculateStat` 배선 (수정 지점 — 이 1곳뿐)

`recalculateStat(Long goodsNo)`가 `goods_review_stat`을 upsert한 직후, 같은 재계산 값으로:

```java
goodsReviewCountCommand.syncReviewCount(goodsNo, reviewCount);
```

(`reviewCount`는 그 메서드가 이미 계산한 COUNT 값. 생성자 주입 필드 추가 외 다른 변경 금지.
javadoc "작성·삭제 어느 경로에서도 이 한 메서드만 부르면 통계가 항상 리뷰와 일치한다"에
goods.review_count도 함께 간다는 문장을 덧붙인다.)

### 3-4. `GoodsSort.REVIEW` (T1·T2 공유 계약 — 파라미터 문자열이 접점)

```java
REVIEW("review", "g.reviewCount desc, g.id desc"),
```

프론트 대응(`api/goods.ts`, T2 소유):

```ts
export type GoodsSort = 'popular' | 'new' | 'sales' | 'priceAsc' | 'discount' | 'review';
```

### 3-5. `FetchGoodsListParams` 확장 (T2 소유)

```ts
export interface FetchGoodsListParams {
  page?: number;
  size?: number;
  sort?: GoodsSort;
  categoryCode?: string;
  /** 태그 슬러그 필터(예: "uv"). GoodsList가 `/goods?tag=` 진입 시 이 값을 그대로 실어 보낸다. */
  tag?: string;
  /** 가격대 필터 — 서버 GoodsController의 minPrice/maxPrice와 1:1. 경계값은 포함(이상/이하). */
  minPrice?: number;
  maxPrice?: number;
}
```

### 3-6. `Pager` 컴포넌트 인터페이스 (T2 소유 — 공용으로 만들되 배선은 GoodsList만)

```ts
interface PagerProps {
  /** 1-based 현재 페이지 */
  page: number;
  totalPages: number;
  /** 클릭된 1-based 페이지 번호를 넘긴다 — URL 갱신·스크롤은 호출부 책임 */
  onPageChange: (page: number) => void;
}
```

---

## 4. 태스크 상세

### T1-A: V84 + 리뷰수 동기화 (백엔드)

**Files:** §1 소유권 표의 T1 전부
**Interfaces:** Produces §3-2 `GoodsReviewCountCommand`, §3-4 `GoodsSort.REVIEW`

- [ ] **1. V84 작성**(§3-1 그대로) + `Goods.java` 필드 추가. `./gradlew test` 실행 —
  `FlywayMigrationSmokeTest`·validate가 마이그레이션과 엔티티의 정합을 잡는다
- [ ] **2. 동기화 실패 테스트** — `GoodsReviewCountServiceTest.java`(신규):

```java
@Test
@DisplayName("재계산 값을 그대로 set한다 — 멱등: 같은 값을 두 번 보내도 결과 동일")
void 리뷰수_동기화() {
    tx(() -> goodsReviewCountCommand.syncReviewCount(goodsId, 7));
    assertThat(리뷰수(goodsId)).isEqualTo(7);
    tx(() -> goodsReviewCountCommand.syncReviewCount(goodsId, 7));
    assertThat(리뷰수(goodsId)).isEqualTo(7);
    tx(() -> goodsReviewCountCommand.syncReviewCount(goodsId, 3)); // 삭제로 줄어든 재계산 값
    assertThat(리뷰수(goodsId)).isEqualTo(3);
}

@Test
@DisplayName("트랜잭션 밖 호출은 예외 — MANDATORY 계약")
void 트랜잭션_강제() {
    assertThatThrownBy(() -> goodsReviewCountCommand.syncReviewCount(goodsId, 1))
            .isInstanceOf(IllegalTransactionStateException.class);
}
```

  (`tx(...)`·픽스처는 `StockServiceTest`의 관례. `리뷰수()`는 em으로 직접 조회하는 테스트 헬퍼.)
- [ ] **3. RED → §3-2 구현 → GREEN**
- [ ] **4. ReviewService 배선 실패 테스트** — `ReviewServiceTest`에 추가:

```java
@Test
@DisplayName("리뷰를 쓰면 goods.review_count가 stat과 같은 값으로 따라간다")
void 리뷰작성_카운트_동기화() {
    reviewService.create(memberId, 리뷰요청(goodsNo));
    assertThat(리뷰수(goodsNo)).isEqualTo(1);
    reviewService.create(다른회원Id, 리뷰요청(goodsNo));
    assertThat(리뷰수(goodsNo)).isEqualTo(2);
}
```

- [ ] **5. RED → §3-3 배선 → GREEN**
- [ ] **6. 정렬 실패 테스트** — 기존 GoodsService/GoodsSort 테스트 관례에 추가:

```java
@Test
@DisplayName("sort=review는 review_count 내림차순, 동률은 id 내림차순")
void 리뷰많은순_정렬() {
    // review_count를 5/0/2로 세팅한 goods 3개 픽스처
    List<GoodsListItem> items = 목록조회(sort = "review");
    assertThat(items).extracting("goodsNo").containsExactly(리뷰5_id, 리뷰2_id, 리뷰0_id);
}
```

- [ ] **7. RED → §3-4 `REVIEW` 추가 → GREEN**
- [ ] **8. `./gradlew test` + `./gradlew integrationTest` 전체 녹색** (IT가 실 MySQL에서 V84
  적용·validate까지 증명한다)
- [ ] **9. 커밋** `feat(catalog): 리뷰 많은 순 정렬 — goods.review_count 비정규화(V84) + 재집계 동기화 경계`

### T2-A: 정렬 6종 노출 + 가격대 필터 배선

**Files:** `api/goods.ts`, `api/goods.test.ts`, `ListToolbar.tsx`, `ListToolbar.test.tsx`, `GoodsList.tsx`, `GoodsList.test.tsx`, `handlers.ts`, `fixtures/goods.ts`

- [ ] **1. 실패 테스트** — `ListToolbar.test.tsx`의 정렬 5종 단언을 6종으로(사양 변화 — DESIGN.md
  갱신됨), `GoodsList.test.tsx`에 배선 케이스:

```tsx
it('정렬 6종을 서버 GoodsSort 값으로 노출한다', () => {
  renderToolbar();
  const values = Array.from(
    screen.getByRole('combobox', { name: '정렬' }).querySelectorAll('option'),
  ).map((o) => o.getAttribute('value'));
  expect(values).toEqual(['popular', 'new', 'sales', 'priceAsc', 'discount', 'review']);
});

it('sort=review가 fetch에 전달되고 리뷰 많은 순으로 내려온다', async () => {
  renderGoodsList('/goods?category=C002&sort=review');
  await screen.findByRole('combobox', { name: '정렬' });
  expect(capturedSearchParams?.get('sort')).toBe('review');
});

it('가격대 pill 선택 시 minPrice/maxPrice가 실제 요청에 실린다', async () => {
  renderGoodsList('/goods?category=C002&price=FROM_10K_TO_30K');
  await screen.findByRole('combobox', { name: '정렬' });
  expect(capturedSearchParams?.get('minPrice')).toBe('10000');
  expect(capturedSearchParams?.get('maxPrice')).toBe('29999');
});
```

- [ ] **2. RED 확인** (가격대 케이스는 현재 NOTE 상태라 반드시 실패해야 한다 — 통과하면
  이미 배선된 것이니 중단·보고)
- [ ] **3. 구현**: §3-4 타입 + §3-5 파라미터 + ListToolbar 옵션(`리뷰 많은 순`) +
  GoodsList `SORT_VALUES`·NOTE 지점 배선. 목: `sortGoods`에 `case 'review'`
  (`b.reviewCount - a.reviewCount`), `buildGoodsFixtures`에 reviewCount 분산 값
  (기존 `salesCount` 주입과 같은 방식 — 전부 0이면 정렬이 눈에 안 보인다). 목 필터도
  minPrice/maxPrice를 실제로 거른다(핸들러에 없으면 추가)
- [ ] **4. GREEN + `npm test` + `npx tsc -b`**
- [ ] **5. 커밋** `feat(goods): 리뷰 많은 순 노출 + 가격대 필터 실배선(잔여 결함 마감)`

### T2-B: 페이저 + 스크롤 복원

**Files:** `Pager.tsx`(신규), `Pager.css`(신규), `Pager.test.tsx`(신규), `GoodsList.tsx`, `GoodsList.css`, `GoodsList.test.tsx`, `Layout.tsx`, `Layout.test.tsx`

- [ ] **1. Pager 실패 테스트** — `Pager.test.tsx`(신규):

```tsx
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Pager } from './Pager';

describe('Pager', () => {
  it('totalPages가 1이면 아무것도 렌더하지 않는다', () => {
    const { container } = render(<Pager page={1} totalPages={1} onPageChange={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('현재 페이지에 aria-current를 달고, 번호 클릭이 1-based로 전달된다', async () => {
    const onPageChange = vi.fn();
    render(<Pager page={2} totalPages={3} onPageChange={onPageChange} />);
    expect(screen.getByRole('button', { name: '2' })).toHaveAttribute('aria-current', 'page');
    await userEvent.click(screen.getByRole('button', { name: '3' }));
    expect(onPageChange).toHaveBeenCalledWith(3);
  });

  it('첫 페이지에서 이전이, 끝 페이지에서 다음이 비활성이다', () => {
    render(<Pager page={1} totalPages={3} onPageChange={vi.fn()} />);
    expect(screen.getByRole('button', { name: '이전' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '다음' })).toBeEnabled();
  });

  it('페이지가 많으면 현재 ±2 윈도로 최대 5개 번호만 보인다', () => {
    render(<Pager page={7} totalPages={20} onPageChange={vi.fn()} />);
    const numbers = screen.getAllByRole('button').map((b) => b.textContent)
      .filter((t) => /^\d+$/.test(t ?? ''));
    expect(numbers).toEqual(['5', '6', '7', '8', '9']);
  });
});
```

- [ ] **2. RED → 구현**(DESIGN.md `pager` 사양: 색 반전 pill, nav 랜드마크, stone 비활성) → GREEN
- [ ] **3. GoodsList 배선 실패 테스트**:

```tsx
it('?page=2는 0-based page=1로 요청되고, 페이저가 렌더된다', async () => {
  renderGoodsList('/goods?category=C001&page=2'); // C001은 67개 — 2페이지 존재
  await screen.findByRole('navigation', { name: '페이지 이동' });
  expect(capturedSearchParams?.get('page')).toBe('1');
});

it('페이지를 넘기면 URL이 바뀌고, 1페이지로 돌아오면 page 파라미터가 지워진다', async () => {
  renderGoodsList('/goods?category=C001&page=2');
  await userEvent.click(await screen.findByRole('button', { name: '1' }));
  expect(currentLocation().search).not.toContain('page=');
});

it('정렬·필터가 바뀌면 page는 1로 리셋된다 — 결과 집합이 달라졌는데 3페이지에 남는 것은 거짓 화면이다', async () => {
  renderGoodsList('/goods?category=C001&page=2');
  await userEvent.selectOptions(await screen.findByRole('combobox', { name: '정렬' }), 'priceAsc');
  expect(currentLocation().search).not.toContain('page=');
});
```

- [ ] **4. RED → 구현**: `?page` 읽기(1-based, 미지값·0 이하는 1로 정규화), 쿼리키에 page,
  `placeholderData: keepPreviousData`, 페이지 변경 시 툴바 ref로 `scrollIntoView`
  (`behavior:'instant'` — 전역 smooth 차단), 정렬·가격대·카테고리 변경 시 page 삭제 → GREEN
- [ ] **5. 스크롤 복원 교체**: `Layout.tsx`의 scrollTo effect 삭제, `<ScrollRestoration />`을
  Layout(Outlet 형제)에 추가. **기존 Layout 스크롤 테스트 2건은 결정 4의 사양 변화로 삭제**하고,
  대체 단언(Layout이 ScrollRestoration을 렌더)을 남긴다. `router.test.tsx`가 깨지면 같은
  구조로 갱신(데이터 라우터 밖에서 ScrollRestoration은 no-op/경고 — createMemoryRouter로 감싸는
  기존 관례면 문제없다)
- [ ] **6. `npm test` + `npx tsc -b` 전체 녹색**
- [ ] **7. 스크린샷 2장**: 목 dev 서버 `/goods`(전체 190개 — 페이저 보임) 1페이지 하단 +
  2페이지 상단(툴바 위치로 스크롤된 상태) → 경로 보고
- [ ] **8. 커밋** `feat(goods): 목록 번호 페이저(+URL page) 및 ScrollRestoration 스크롤 복원`

---

## 5. W2 — 직렬 검증 웨이브 (오케스트레이터, 머지 후)

- [ ] 머지 전: main의 Flyway 최신 번호가 여전히 V83인지 확인 — 아니면 T1 브랜치의 V84 번호를
  올린 뒤 머지([[flyway-number-at-merge-time]])
- [ ] 2개 브랜치 리뷰 후 main 머지 (겹치는 파일 없음 — T1 백엔드 전용 / T2 프론트 전용)
- [ ] **compose Redis 배선**(결정 6): backend에 `REDIS_HOST: redis`·`VIEW_COUNT_REDIS: "true"`·
  `depends_on.redis` 추가 → `docker compose up -d --build` → 상세 조회 몇 번 →
  `docker exec beautyboy-redis redis-cli HGETALL` 로 버퍼 적재 확인 → 1분 후 goods.view_count
  증가 확인(플러시 스케줄러 동작 증명)
- [ ] 실스택 검증:
  - [ ] `sort=review` 실서버 — 리뷰 45개 상품군이 앞으로 오는지, 시드 리뷰수와 일치하는지
  - [ ] 리뷰 1건 작성 → 해당 상품이 리뷰 많은 순에서 전진하는지(동기화 실증)
  - [ ] 가격대 pill 클릭 → 네트워크 탭/응답 건수로 실제 필터링 확인(지난 웨이브 잔여 결함 마감 확인)
  - [ ] **뒤로가기 복원 시나리오**: `/goods` 2페이지로 이동 → 중간까지 스크롤 → 상세 진입 →
    뒤로가기 → 2페이지·스크롤 위치 유지 확인. 그리고 목록→상세 **새 이동은 여전히 최상단**인지
    (1-2 회귀 방지)
- [ ] `./gradlew test` + `integrationTest` + `npm test` + `npx tsc -b` + E2E
  ([[e2e-needs-e2e-profile-backend]] 절차)
- [ ] `docs/plans/2026-07-26-다음-작업.md` 기록(리뷰 정렬·페이지네이션·compose 배선·가격대 배선 마감)

---

## 6. 터미널 실행 프롬프트

> 사람은 프로젝트 루트에서 터미널을 열고 아래를 통째로 붙여넣는다. git 명령을 손으로 치지 않는다.

### T1 — 리뷰 정렬 백엔드 (sonnet)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add .claude/worktrees/뷰티보이-리뷰정렬 -b feature/review-sort-backend
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 해당 worktree인지
  - git log --oneline -1 이 루트에서 본 기점 커밋(docs(plan): 목록 성숙 계획)과 같은지
  - docs/plans/2026-07-28-list-maturity.md 가 실제로 존재하는지
  - git status가 깨끗한지

[2단계 — 실행] docs/plans/2026-07-28-list-maturity.md 의 T1-A를 실행해라.
너는 이 계획의 T1 실행 서브에이전트다(model: sonnet). CLAUDE.md 공통 규칙과 계획서의
Global Constraints·파일 소유권 표를 지켜라. 프론트는 한 파일도 만지지 않는다.
Goods.java는 필드 1개 추가만, ReviewService.java는 recalculateStat 배선 1곳만.
마이그레이션은 V84 하나만 만들고, 스텝별 TDD는 계획서 그대로.
```

### T2 — 목록 프론트 (sonnet)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add .claude/worktrees/뷰티보이-페이저 -b feature/list-pagination
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 해당 worktree인지
  - git log --oneline -1 이 루트에서 본 기점 커밋(docs(plan): 목록 성숙 계획)과 같은지
  - docs/plans/2026-07-28-list-maturity.md 와 DESIGN.md 의 pager 절이 실제로 존재하는지
  - git status가 깨끗한지

[2단계 — 실행] docs/plans/2026-07-28-list-maturity.md 의 T2-A → T2-B를 순서대로 실행해라.
너는 이 계획의 T2 실행 서브에이전트다(model: sonnet). CLAUDE.md 공통 규칙과 계획서의
Global Constraints·파일 소유권 표를 지켜라. 백엔드는 한 파일도 만지지 않는다 —
sort=review는 목(handlers.ts)으로 개발하고 실서버 확인은 W2가 한다.
DESIGN.md pager·list-toolbar 절을 CSS 작성 전에 읽어라. 스텝별 TDD와 스크린샷 DoD는
계획서 그대로.
```
