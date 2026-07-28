# 구현 계획 — 결함 마감 5종 (2026-07-28)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development 또는
> superpowers:executing-plans로 태스크 단위 실행. 스텝은 체크박스로 추적한다.
>
> 근거: 2026-07-28 전체 코드 리뷰(백엔드·프론트 각 1회)에서 **"리뷰어가 5분 안에 찾는" 등급으로
> 판정된 5건**. 별도 설계 문서 없음 — **이 문서의 §2 "설계 결정"이 설계의 진실을 겸한다.**
> DESIGN.md에는 이 계획과 같은 커밋으로 `route-error`(라우트 오류·없는 페이지 화면) 사양이
> 추가돼 있다 — T2-1은 그 절을 따른다.
> **마이그레이션 없음**(Flyway 현재 V84). 새 도메인·새 테이블 없음.

**Goal:** 장바구니 N+1, 주문 목록 N+1 + 페이징 부재, 손님 경로 입력 검증 부재, 코드 스플리팅
0건, 라우트 오류 처리 부재 — 다섯 결함을 닫는다.

**Architecture:** 백엔드는 **소유 도메인이 배치 통로를 내주는** 기존 관례를 그대로 따른다
(`GoodsQueryService.findListItems(Collection)`가 선례 — 이번에 `findOrderSnapshots`가 짝을
이룬다). 프론트는 라우트 트리에 **오류 경계 한 겹**과 **지연 로딩 경계 한 겹**을 추가할 뿐,
화면 컴포넌트의 내부는 건드리지 않는다.

**Tech Stack:** Spring Boot(JPA fetch join, `default_batch_fetch_size`, Bean Validation,
`PageResponse`) / React(`lazy` + `Suspense`, React Router 7 `errorElement`, Vite `manualChunks`).

## Global Constraints (CLAUDE.md 재확인)

- **자기 태스크의 Files 목록 밖 파일 수정 금지.** `common` 패키지는 열지 않는다
  (`ApiResponse`·`ErrorCode`·`PageResponse`·`GlobalExceptionHandler`는 읽기만 한다).
- **기존 테스트의 단언은 원칙적으로 수정 금지.** 이번 계획에서 단언을 고쳐도 되는 곳은
  **T1-3이 명시한 2건뿐**이다(주문 목록 응답 형태가 계약상 바뀌므로). 그 밖에 단언을 바꿔야
  통과한다면 **중단하고 보고**한다.
- CSS는 `var(--color-*)`·`var(--space-*)` 토큰만. hex를 손으로 옮겨 적지 않는다.
- 프론트 전체 판정은 `npm test` + `npx tsc -p tsconfig.app.json --noEmit`
  ([[tsc-noemit-needs-project-flag]] — `-p` 없이 돌리면 0파일 검사로 거짓 녹색이 된다.
  [[vitest-picks-up-e2e-spec]] — `npx vitest run`은 항상 1 failed가 나오므로 판정에 쓰지 않는다).
- 백엔드 판정은 `./gradlew test`. **T1은 마지막에 `./gradlew integrationTest`도 돌린다**
  ([[h2-createdrop-hides-validate]] — H2는 실 MySQL 불일치를 가린다. 이번 T1-3은
  `application.yml`의 JPA 프로퍼티를 건드리므로 실 MySQL 기동 확인이 특히 중요하다).
- 화면이 바뀌는 태스크(T2-1, T2-3)는 **스크린샷 DoD**. 개발서버를 띄우고 담당 화면을 직접
  찍어 본 뒤 경로를 보고서에 남긴다.
- 커밋 메시지·주석·문서는 한국어. 커밋은 태스크 단위로 원자적으로.

### 지금 돌고 있는 다른 터미널 (충돌 회피)

`feature/concept-sets` 브랜치가 **동시에 진행 중**이다(worktree `.claude/worktrees/뷰티보이-컨셉세트`).
그 터미널이 여는 파일은 다음과 같다 — **이 계획의 어떤 태스크도 아래를 열지 않는다**:

```
frontend/src/features/affinity/*        frontend/src/components/routine/SetTabs.*
frontend/src/pages/Main.tsx             frontend/src/pages/Main.css
frontend/src/pages/Main.test.tsx        DESIGN.md
```

DESIGN.md는 이 계획 커밋에서 **이미** `route-error` 절이 추가돼 있다(커머스 컴포넌트 절 끝,
`compat-banner` 다음). 컨셉세트 터미널은 `list-toolbar` 뒤에 `set-tabs`를 넣으므로 삽입 지점이
25줄 이상 떨어져 있어 머지가 자동으로 붙는다. **T2는 DESIGN.md를 수정하지 않고 읽기만 한다.**

---

## 0. 사람이 할 일 (사전 조건 2줄)

터미널을 열기 전에 프로젝트 루트에서:

```
git log --oneline -1     # 이 계획 커밋(docs(plan): 결함 마감 5종 구현 계획)인지 확인
git status               # 깨끗한지 확인
```

---

## 1. 판정표 — 무엇을 고치고 무엇을 남기나

| 리뷰 지적 | 판정 | 배치 |
|---|---|---|
| 장바구니 `itemsOf` 루프 안 단건 조회 (N+1) | 수정 | T1-1(통로) · T1-2(호출부) |
| 주문 목록 LAZY `getItems()` (N+1) | 수정 (`default_batch_fetch_size`) | T1-3 |
| 주문 목록 페이징 없음 (전량 반환) | 수정 (`PageResponse`) | T1-3(서버) · T2-3(화면) |
| 결제·주문·장바구니·리뷰·문의 요청 DTO 검증 부재 | 수정 (**구조적 결손만**) | T1-4 |
| 코드 스플리팅 0건 | 수정 (관리자·마이페이지·주문 계열만) | T2-2 |
| ErrorBoundary / `errorElement` 0건 | 수정 | T2-1 |
| admin 요청 DTO 검증 부재 (`AdminGoodsSaveRequest` 등) | **이번 범위 밖** | §2 결정 3 |
| 페이지 크기 clamp 중복(3곳 복붙) | **이번 범위 밖** | §2 결정 2 |
| queryKey 팩토리·QueryClient 기본값·`ErrorState` 공용화 | **이번 범위 밖** | 다음 웨이브 |

---

## 2. 설계 결정

### 결정 1 — 배치 스냅샷의 키는 "요청한 키"다 (반환 타입이 `Map`인 이유)

`findOrderSnapshot(goodsNo, optionNo)`는 `optionNo=null`이면 서버가 **대표 옵션을 골라** 스냅샷을
채운다. 따라서 **응답의 `optionId`로는 요청을 되찾을 수 없다** — 요청은 `null`이었는데 응답에는
해석된 id가 실려 있다. 배치 조회가 `List`를 돌려주면 호출자가 자기 줄과 결과를 짝지을 방법이
사라진다(장바구니의 레거시 행이 정확히 이 경우다: `option_id=NULL`로 저장된 행).

그래서 반환은 `Map<OrderSnapshotKey, OrderGoodsSnapshot>`이고 **키는 호출자가 넘긴 키 그대로**다.
숨김 상품·상품-옵션 불일치는 **맵에 키가 없는 것**으로 답한다 — 단건이 `Optional.empty()`를 주는
것과 같은 계약이라, 호출자의 "조용히 목록에서 제외한다" 정책이 그대로 유지된다.

**단건 경로는 배치와 같은 해석 함수를 쓰도록 재작성한다.** 해석 로직이 두 벌이 되면 대표 옵션
선택 규칙이 한쪽에서만 바뀌는 날이 오고, 그때 장바구니와 주문이 서로 다른 옵션을 고른다.

### 결정 2 — 주문 목록 페이징은 `PageResponse`, clamp 상한은 100

`common/PageResponse`는 Wave 0에 고정된 공용 계약이고 `/goods`·`/reviews`·`/search`·`/rankings`가
이미 쓴다. 주문만 배열로 남기면 프론트가 파싱 로직을 두 벌 갖는다.

- 기본 크기 **10** — 마이페이지 주문내역 한 화면 분량. admin 문의 목록의 기본값과 같다.
- 상한 **100** — `/goods`(GoodsController)·`/qna`(QnaService)와 **같은 값**이다. 엔드포인트마다
  상한이 다르면 그건 방어가 아니라 우연이다.
- **clamp 로직의 공용화는 이번에 하지 않는다.** 공용화하려면 `common`을 열어야 하는데 `common`은
  동결 계약이다. 지금은 `/orders`가 기존 두 곳과 **같은 값**을 쓰게 맞추는 것까지만 하고,
  "세 곳의 복붙"은 `common` 해동을 결정하는 별도 판단으로 남긴다.
- 정렬 2차 키로 **`id desc`**를 반드시 둔다. `ordered_at`만으로 정렬하면 같은 초에 만들어진 주문
  두 건의 순서가 비결정적이고, 그러면 **페이지 경계에서 한 건이 사라지거나 두 번 나온다.**
  (`findRecommendedRows`·`findCandidateIds`가 이미 같은 이유로 2차 키를 둔다.)

### 결정 3 — Bean Validation은 "구조적 결손"만 맡는다 (판단이 갈리는 곳)

애노테이션을 얹으면 **400 INVALID_INPUT이 서비스보다 먼저 나간다.** 이미 도메인 ErrorCode로
판정되던 값에 애노테이션을 붙이면 그 코드가 조용히 사라지고, 프론트가 코드로 분기하던 화면이
같이 깨진다. 그래서 경계를 이렇게 자른다.

**Bean Validation이 맡는 것 = 지금 아무도 막지 않아 NPE·DB 예외(500)로 새는 것**
→ `null`, 공백, 컬럼 길이 초과.

**서비스가 계속 판정하는 것 = 이미 도메인 ErrorCode를 가진 값** → 아래 넷에는 **애노테이션을
붙이지 않는다.**

| 값 | 판정 주체 | 코드 |
|---|---|---|
| `CartAddRequest.quantity` | `CartService.add` | `CART_QUANTITY_INVALID` |
| `CartQuantityRequest.quantity` | `CartService.changeQuantity` | `CART_QUANTITY_INVALID` |
| `OrderItemRequest.quantity` | `OrderService.create` | `CART_QUANTITY_INVALID` |
| `OrderCreateRequest.items`의 null·빈 값 | `OrderService.create` | `CART_EMPTY` |
| `ReviewCreateRequest.rating` 범위 | `ReviewService.create` (`MIN_RATING`/`MAX_RATING`) | `INVALID_INPUT` |

마지막 줄은 결과 코드가 같으므로 애노테이션을 붙여도 겉보기는 통과한다. 그래도 붙이지 않는다 —
**상수와 애노테이션 두 벌이 되면 한쪽만 고쳐지는 날이 온다.** 판정 주체는 하나여야 한다.

`PaymentConfirmRequest.amount`에도 붙이지 않는다. 이 값은 **애초에 신뢰하지 않는 값**이고
(`PaymentService`가 서버의 `payableAmount`와 대조한다), 음수가 와도 그 대조에서 걸린다.
여기에 `@Positive`를 붙이면 "amount를 검증한다"는 잘못된 인상을 남긴다.

### 결정 4 — 오류 경계는 셸 **안쪽**에, 404도 같은 화면

React Router에서 루트 라우트(`element: <Layout />`)에 `errorElement`를 걸면 **Layout째로
대체된다** — 오류가 난 손님에게서 헤더·검색·장바구니로 가는 길을 전부 뺏는 셈이다.

그래서 **경로 없는(pathless) 라우트 한 겹**을 Layout의 자식으로 넣고 거기에 `errorElement`를
건다. 오류는 이 경계까지만 올라가고 `<Outlet />` 자리에서 렌더되므로 셸이 살아남는다.

404(매칭 라우트 없음)는 라우터가 루트까지 던지므로 이 경계로는 안 온다. 대신 같은 자식 목록
맨 끝에 **catch-all `{ path: '*' }`** 을 두어 같은 컴포넌트를 렌더한다 — 이때는 예외가 없으므로
`useRouteError()`가 `undefined`이고, 컴포넌트는 **"에러 객체가 없으면 404"** 로 읽는다.

### 결정 5 — 무엇을 지연 로딩하고 무엇을 남기나

**쪼개는 것**: `admin/*`(4) · `mypage/*`(5) · `Order` · `OrderComplete` · `OrderFail` ·
`Search` · `Ranking` · `dev/Showcase`.

**남기는 것**: `Layout` · `Home` · `Login` · `Signup` · `Main` · `GoodsList` · `Detail` ·
`Cart` · `Routine`.

근거: 첫 진입과 탐색 주 경로(홈→메인→목록→상세→장바구니→루틴)를 쪼개면 **첫 클릭마다 네트워크
왕복이 하나 더 붙는다** — 번들을 줄이려다 체감 속도를 깎는다. 떼어내는 것은 (ㄱ) 대부분의 손님이
평생 안 가는 화면(관리자·마이페이지), (ㄴ) 결제 SDK를 물고 있는 화면(`Order`가
`@tosspayments/tosspayments-sdk`의 **유일한** 상용 임포터다), (ㄷ) 상용 라우트가 아닌 dev 화면
(`Showcase`)이다.

**`React.lazy` + `Suspense`를 쓴다** (React Router의 라우트 `lazy` 속성이 아니라). 이 프로젝트는
모든 페이지가 named export라 `lazy`는 `.then()`으로 default를 지어 주면 되고, 폴백을 Layout의
`<main>` 한 곳에서 통일할 수 있다. 라우트 `lazy`는 로딩 동안 **이전 화면에 머무르므로** 직접
URL 진입 시 무엇이 일어나는지 화면에 드러나지 않는다.

**`manualChunks`는 넣지 않는다.** `toss.ts`의 유일한 임포터가 `Order`이므로, `Order`를 lazy로
떼는 순간 Rollup이 SDK를 그 청크로 자연히 밀어 넣는다. 수동 청크는 그 자동 배치를 덮어써서
오히려 공유 청크를 하나 더 만든다 — **빌드 산출물로 확인하고, 실제로 분리되지 않았을 때만
다시 판단한다**(T2-2 Step 4가 이 확인이다).

---

## 3. 터미널 분할

| 터미널 | 브랜치 | 태스크 | 근거 |
|---|---|---|---|
| **T1** | `feature/defect-closeout-backend` | 1-1 · 1-2 · 1-3 · 1-4 | 전부 백엔드. 1-3과 1-4가 `order/OrderController.java`를 함께 열므로 **같은 터미널에서 순차** 실행이어야 한다. |
| **T2** | `feature/defect-closeout-frontend` | 2-1 · 2-2 · 2-3 | 전부 프론트. 2-1과 2-2가 `router.tsx`를 함께 열므로 순차. |

**T1과 T2는 병렬로 돌린다.** T2-3(주문 목록 화면)이 T1-3의 응답 형태에 의존하지만, 그 계약은
아래 §4에 **완전한 형태로 못 박혀 있고** 프론트 테스트는 MSW로 그 형태를 직접 만든다. 서로를
기다릴 필요가 없다.

**머지 순서는 T1 먼저, T2 나중.** 반대로 하면 `main`에 "프론트는 `PageResponse`를 기대하는데
서버는 배열을 주는" 구간이 생긴다.

### T1 실행 프롬프트 (그대로 붙여넣기)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add .claude/worktrees/뷰티보이-결함마감-백엔드 -b feature/defect-closeout-backend
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 .claude/worktrees/뷰티보이-결함마감-백엔드 인지
  - git log --oneline -1 이 루트에서 본 기점 커밋과 같은지
  - docs/plans/2026-07-28-defect-closeout.md 와 CLAUDE.md 가 실제로 존재하는지
  - git status가 깨끗한지

[2단계 — 실행]
너는 오케스트레이터다. docs/plans/2026-07-28-defect-closeout.md 의 T1 태스크
(Task 1-1 → 1-2 → 1-3 → 1-4)를 **번호 순서대로** 실행해라.
- 태스크마다 서브에이전트를 스폰한다. 모델은 전부 sonnet이다
  (결제·재고 동시성·궁합 규칙 엔진이 아니므로 CLAUDE.md의 opus 예외에 해당하지 않는다).
- 서브에이전트에게는 계획서의 해당 태스크 절 전체를 그대로 준다. Files 목록 밖 파일은
  절대 열지 말라고 명시해라.
- 태스크가 끝날 때마다 네가 직접 리뷰한다: (ㄱ) `./gradlew test` 통과, (ㄴ) Files 목록 준수,
  (ㄷ) 계획서에 적힌 테스트 이름과 단언이 실제로 그대로 있는지.
- 리뷰를 통과하면 태스크 단위로 원자 커밋한다(한국어 메시지).
- Task 1-3은 `order/OrderController.java`를 열고 Task 1-4도 같은 파일을 연다.
  **반드시 1-3을 먼저 끝내고 커밋한 뒤** 1-4를 스폰해라.
- 네 태스크가 전부 끝나면 마지막에 `./gradlew test` 와 `./gradlew integrationTest`를 둘 다
  돌려 실측 건수를 보고서에 적어라. integrationTest는 Docker가 필요하다.
- 기존 테스트의 단언을 고쳐야 통과한다면, 계획서 T1-3이 명시한 2건을 제외하고는
  중단하고 보고해라.
```

### T2 실행 프롬프트 (그대로 붙여넣기)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add .claude/worktrees/뷰티보이-결함마감-프론트 -b feature/defect-closeout-frontend
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 .claude/worktrees/뷰티보이-결함마감-프론트 인지
  - git log --oneline -1 이 루트에서 본 기점 커밋과 같은지
  - docs/plans/2026-07-28-defect-closeout.md 와 DESIGN.md 가 실제로 존재하고,
    DESIGN.md에 `route-error` 절이 있는지 (없으면 기점이 틀린 것이다 — 중단하고 보고)
  - git status가 깨끗한지
진입 직후 `cd frontend && npm install` 을 먼저 돌려라 — 새 worktree에는 node_modules가 없다.

[2단계 — 실행]
너는 오케스트레이터다. docs/plans/2026-07-28-defect-closeout.md 의 T2 태스크
(Task 2-1 → 2-2 → 2-3)를 **번호 순서대로** 실행해라. 모델은 전부 sonnet이다.
- 태스크마다 서브에이전트를 스폰하고, 계획서의 해당 태스크 절 전체를 그대로 준다.
  Files 목록 밖 파일은 절대 열지 말라고 명시해라.
- Task 2-1과 2-2는 둘 다 `src/router.tsx`를 연다. **2-1을 끝내고 커밋한 뒤** 2-2를 스폰해라.
- **DESIGN.md는 읽기만 한다. 수정하면 안 된다** — 다른 터미널(feature/concept-sets)이
  같은 파일을 동시에 고치고 있다. 문서에 없는 색·간격·컴포넌트가 필요하면 만들지 말고 보고해라.
- Task 2-1과 2-3은 화면이 바뀌므로 스크린샷 DoD다. 서브에이전트가 `npm run dev`로 개발서버를
  띄우고 담당 화면을 직접 찍어 본 뒤 파일 경로를 보고하게 하고, 너도 그 스크린샷을 열어보고
  판정해라. 테스트 통과는 이 요구를 대체하지 못한다.
- 태스크마다 리뷰: (ㄱ) `npm test` 통과, (ㄴ) `npx tsc -p tsconfig.app.json --noEmit` 0에러
  (`-p` 없이 돌리면 거짓 녹색이다), (ㄷ) Files 목록 준수, (ㄹ) CSS에 hex 하드코딩이 없는지.
- 리뷰를 통과하면 태스크 단위로 원자 커밋한다(한국어 메시지).
- 전부 끝나면 `npm run build`를 돌려 산출물 크기를 보고서에 적어라(T2-2의 DoD).
```

---

## T1 — 백엔드

### Task 1-1: 상품 스냅샷 배치 조회 통로 (catalog)

**Files:**
- Modify: `backend/src/main/java/com/beautyboy/catalog/GoodsQueryService.java` (메서드·record 추가만)
- Modify: `backend/src/main/java/com/beautyboy/catalog/GoodsService.java` (`findOrderSnapshot` 재작성 + 배치 구현 추가)
- Modify: `backend/src/main/java/com/beautyboy/catalog/GoodsRepository.java` (fetch join 조회 추가)
- Create/Test: `backend/src/test/java/com/beautyboy/catalog/GoodsSnapshotBatchTest.java`

**Interfaces:**
- Produces: `GoodsQueryService.findOrderSnapshots(Collection<OrderSnapshotKey>)` + `OrderSnapshotKey` — **Task 1-2가 이 시그니처를 그대로 쓴다.**
- Consumes: 기존 `Goods`·`GoodsOption`·`대표_옵션_순서`

- [ ] **Step 1: 실패하는 테스트 작성**

`GoodsSnapshotBatchTest.java` — 케이스 이름과 단언 전량(이것이 사양이다):

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GoodsSnapshotBatchTest {

    @Test
    @DisplayName("배치 조회는 요청한 키 그대로 돌려준다 — 응답의 optionId로는 요청을 되찾을 수 없다")
    void 요청한_키로_돌려준다() {
        // 옵션 2개짜리 상품, 키는 (goodsNo, null) 하나
        // assertThat(result).containsOnlyKeys(new OrderSnapshotKey(goodsNo, null));
        // assertThat(result.get(key).optionId()).isEqualTo(대표옵션_id);   // 값은 해석된 id
    }

    @Test
    @DisplayName("optionNo가 null이면 배치도 대표 옵션(sortOrder 최소, 동률이면 id 최소)으로 채운다")
    void null_옵션은_대표_옵션으로() {
        // assertThat(snapshot.optionName()).isEqualTo("200ml");
        // assertThat(snapshot.unitPrice()).isEqualTo(상품가 + 대표옵션_추가금);
        // assertThat(snapshot.stock()).isEqualTo(대표옵션_재고);
    }

    @Test
    @DisplayName("숨김 상품은 맵에 키가 없다 — 예외를 던지지 않는다(단건의 Optional.empty와 같은 계약)")
    void 숨김_상품은_키가_없다() {
        // assertThat(result).doesNotContainKey(new OrderSnapshotKey(숨김상품, null));
        // assertThat(result).containsKey(new OrderSnapshotKey(정상상품, null));  // 나머지는 살아남는다
    }

    @Test
    @DisplayName("그 상품의 것이 아닌 옵션을 붙인 키는 맵에 없다")
    void 남의_옵션은_키가_없다() {
        // 상품A + 상품B의 옵션id로 키를 만든다
        // assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("같은 상품의 서로 다른 옵션 두 키가 각각 자기 옵션으로 해석된다")
    void 같은_상품_다른_옵션() {
        // assertThat(result).hasSize(2);
        // assertThat(result.get(키_200ml).unitPrice()).isNotEqualTo(result.get(키_300ml).unitPrice());
    }

    @Test
    @DisplayName("빈 입력은 빈 맵 — 쿼리를 내지 않는다")
    void 빈_입력() {
        // assertThat(goodsQueryService.findOrderSnapshots(List.of())).isEmpty();
    }

    @Test
    @DisplayName("단건 조회와 배치 조회의 결과가 같다 — 해석 로직이 두 벌로 갈라지지 않았다는 증거")
    void 단건과_배치가_같다() {
        // 옵션 있는 상품 / 옵션 없는 상품 / optionNo 지정 / optionNo null — 네 경우 모두
        // assertThat(배치결과.get(key)).isEqualTo(goodsQueryService.findOrderSnapshot(g, o).orElseThrow());
    }
}
```

- [ ] **Step 2: 인터페이스에 배치 통로 추가** (`GoodsQueryService.java`)

공유 계약이므로 전량으로 적는다. `findOrderSnapshot` 선언 **바로 아래**에 넣는다:

```java
    /**
     * 여러 (상품, 옵션) 쌍의 스냅샷을 한 번에 조회한다.
     *
     * <p>왜 필요한가: 장바구니·주문처럼 여러 줄을 한 화면에 그리는 호출자가 줄마다
     * {@link #findOrderSnapshot}을 부르면 줄 수만큼 쿼리가 나간다(N+1). {@code findListItems}가
     * 목록 카드에 대해 하는 일을 주문 스냅샷에 대해 하는 짝이다.
     *
     * <p><b>반환 맵의 키는 호출자가 넘긴 키 그대로다.</b> 스냅샷의 {@code optionId}는 서버가
     * 해석한 대표 옵션일 수 있어({@code optionNo=null} 요청) 응답 값으로는 요청을 되찾을 수
     * 없다 — 호출자가 자기 줄과 결과를 짝지으려면 요청 키가 살아 있어야 한다.
     *
     * <p>숨김 상품과 상품-옵션 불일치는 <b>맵에 키가 없는 것</b>으로 답한다. 단건 조회가 빈 값을
     * 주는 것과 같은 계약이라, 호출자의 "조용히 목록에서 제외한다" 정책이 그대로 유지된다.
     *
     * @param keys 조회할 키 목록. 비어 있으면 쿼리 없이 빈 맵.
     * @return 조회에 성공한 키만 담긴 맵. 입력 순서를 보존하지 않는다(호출자가 자기 순서로 읽는다).
     */
    Map<OrderSnapshotKey, OrderGoodsSnapshot> findOrderSnapshots(Collection<OrderSnapshotKey> keys);

    /**
     * {@link #findOrderSnapshots}의 조회 키. {@code optionNo}의 null 의미는
     * {@link #findOrderSnapshot}과 같다 — "옵션을 특정하지 않았다"이지 "옵션이 없다"가 아니다.
     */
    record OrderSnapshotKey(Long goodsNo, Long optionNo) {
    }
```

`import java.util.Map;` 추가. `Collection`은 이미 임포트돼 있다.

- [ ] **Step 3: 리포지토리에 fetch join 조회 추가** (`GoodsRepository.java`)

성능 의도가 있는 쿼리이므로 전량으로 적는다:

```java
    /**
     * 상품과 옵션을 한 번에 끌어오는 배치 조회. {@code options}는 LAZY라 이 fetch join이 없으면
     * 스냅샷을 만드는 쪽에서 <b>상품 수만큼 옵션 쿼리가 추가로</b> 나간다(N+1을 옮겨 놓는 꼴).
     *
     * <p>{@code distinct}가 필요한 이유: {@code left join fetch}는 상품 행을 그 상품의 옵션 수만큼
     * 복제해 돌려준다. 옵션이 3개면 같은 상품이 3번 나온다.
     *
     * <p>{@code left}인 이유: 옵션이 하나도 없는 상품도 스냅샷 대상이다(재고 무제한 경로).
     * inner join이면 그 상품이 통째로 사라진다.
     */
    @Query("select distinct g from Goods g left join fetch g.options where g.id in :ids")
    List<Goods> findAllWithOptionsByIdIn(@Param("ids") Collection<Long> ids);
```

- [ ] **Step 4: `GoodsService` — 해석 로직을 한 벌로 모으고 배치 구현 추가**

기존 `findOrderSnapshot`의 본문(옵션 해석 부분)을 `해석` private 메서드로 뽑고, 단건·배치가
**둘 다 그것만 부르게** 한다. 판단이 갈리는 곳이므로 전량으로 적는다:

```java
    @Override
    @Transactional(readOnly = true)
    public Optional<OrderGoodsSnapshot> findOrderSnapshot(Long goodsNo, Long optionNo) {
        return goodsRepository.findById(goodsNo)
                .filter(goods -> !Goods.STATUS_HIDDEN.equals(goods.getStatus()))
                .flatMap(goods -> 해석(goods, optionNo));
    }

    /**
     * 여러 키를 한 번에 해석한다. 상품+옵션을 fetch join 한 방으로 읽고, 해석은 단건과 <b>같은</b>
     * {@link #해석} 하나를 쓴다 — 해석이 두 벌이 되면 대표 옵션 규칙이 한쪽에서만 바뀌는 날이 오고,
     * 그때 장바구니와 주문이 서로 다른 옵션을 고른다.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<OrderSnapshotKey, OrderGoodsSnapshot> findOrderSnapshots(Collection<OrderSnapshotKey> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        Set<Long> goodsIds = keys.stream()
                .map(OrderSnapshotKey::goodsNo)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (goodsIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Goods> 노출_상품 = goodsRepository.findAllWithOptionsByIdIn(goodsIds).stream()
                .filter(goods -> !Goods.STATUS_HIDDEN.equals(goods.getStatus()))
                .collect(java.util.stream.Collectors.toMap(Goods::getId, goods -> goods));

        Map<OrderSnapshotKey, OrderGoodsSnapshot> result = new java.util.LinkedHashMap<>();
        for (OrderSnapshotKey key : keys) {
            Goods goods = 노출_상품.get(key.goodsNo());
            if (goods == null) {
                continue;   // 미존재·숨김은 키를 넣지 않는다(단건의 Optional.empty와 같은 계약)
            }
            해석(goods, key.optionNo()).ifPresent(snapshot -> result.put(key, snapshot));
        }
        return result;
    }

    /**
     * 상품 하나에 대해 optionNo를 스냅샷으로 해석한다. 노출 여부(HIDDEN) 판정은 호출자가 이미 끝냈다.
     *
     * <p>optionNo가 null인 것은 "옵션을 특정하지 않았다"는 뜻일 뿐 "상품에 옵션이 없다"는 뜻이 아니다.
     * 예전에는 이 둘을 같게 보고 무조건 재고 MAX_VALUE로 답했고, 그래서 루틴 전체담기
     * (항상 optionNo=null을 보낸다)로 담은 품절 상품이 재고 게이트를 통째로 통과했다.
     */
    private Optional<OrderGoodsSnapshot> 해석(Goods goods, Long optionNo) {
        if (optionNo == null) {
            return goods.getOptions().stream()
                    .min(대표_옵션_순서)
                    .map(option -> 스냅샷(goods, option))
                    // 옵션이 진짜 하나도 없는 상품만 여기로 온다. 재고 관리 단위가 옵션이므로
                    // 이 경우에만 상품 단위 재고를 무제한으로 본다.
                    .or(() -> Optional.of(new OrderGoodsSnapshot(
                            goods.getId(), null, goods.getName(), null,
                            goods.getSalePrice(), Integer.MAX_VALUE, goods.getThumbnailUrl())));
        }
        // 옵션은 반드시 그 상품의 것이어야 한다. 남의 옵션을 붙이는 조작을 여기서 끊는다.
        return goods.getOptions().stream()
                .filter(option -> option.getId().equals(optionNo))
                .findFirst()
                .map(option -> 스냅샷(goods, option));
    }
```

기존 `findOrderSnapshot`에 달려 있던 Javadoc(장바구니 옵션 삭제 정책 설명)은 **지우지 말고
그대로 둔다** — 그 판단의 근거가 사라지면 안 된다.

- [ ] **Step 5: `./gradlew test` 통과 확인.** 기존 `CartServiceTest`·`OrderCreateTest`가 여전히
      녹색이어야 한다(단건 경로의 동작이 1비트도 바뀌지 않았다는 증거다).

---

### Task 1-2: 장바구니가 배치 통로를 쓴다

**Files:**
- Modify: `backend/src/main/java/com/beautyboy/cart/CartService.java` (**`itemsOf` 메서드만**)
- Test: `backend/src/test/java/com/beautyboy/cart/CartServiceTest.java` (**케이스 추가만 — 기존 단언 수정 금지**)

**Interfaces:**
- Consumes: Task 1-1의 `findOrderSnapshots` / `OrderSnapshotKey`
- Produces: 없음 (`CartItemResponse` 형태는 **1비트도 바뀌지 않는다**)

- [ ] **Step 1: 실패하는 테스트 작성** — `CartServiceTest.java` 말미에 추가

`@MockitoSpyBean`으로 호출 횟수를 센다. 스텁하지 않고 **검증만** 한다(스파이는 기본으로 실
빈에 위임하므로 동작이 바뀌지 않는다).

```java
    /**
     * 이 테스트가 이 계획의 존재 이유다 — 줄 수가 늘어도 상품 조회는 한 번이어야 한다.
     * 응답 내용만 단언하면 N+1이 되살아나도 녹색이라 잡히지 않는다.
     */
    @MockitoSpyBean
    GoodsQueryService goodsQueryService;

    @Test
    @DisplayName("장바구니 조회: 줄이 3개여도 상품 조회는 배치 1회 — 단건 조회는 부르지 않는다")
    void 조회는_배치_한_번() {
        // 서로 다른 상품 3개를 담는다(담기 과정의 호출을 세지 않도록 clearInvocations 후 조회)
        // org.mockito.Mockito.clearInvocations(goodsQueryService);
        // cartService.itemsOf(회원);
        // verify(goodsQueryService, times(1)).findOrderSnapshots(any());
        // verify(goodsQueryService, never()).findOrderSnapshot(any(), any());
    }

    @Test
    @DisplayName("숨김 상품 줄은 목록에서 조용히 빠지고 나머지 줄은 그대로 남는다")
    void 숨김_줄만_빠진다() {
        // 상품 2개를 담은 뒤 하나를 HIDDEN으로 바꾼다
        // assertThat(cartService.itemsOf(회원)).hasSize(1);
        // assertThat(cartItemRepository.findByMemberIdOrderByIdAsc(회원)).hasSize(2);  // 지우지는 않는다
    }

    @Test
    @DisplayName("optionNo=null로 담긴 레거시 행도 대표 옵션 이름·번호로 채워진다(배치 경로에서도)")
    void 레거시_행도_대표_옵션으로() {
        // option_id=NULL인 CartItem을 직접 save한 뒤
        // assertThat(response.optionNo()).isEqualTo(대표옵션_id);
        // assertThat(response.optionName()).isEqualTo("200ml");
    }
```

- [ ] **Step 2: `itemsOf` 재작성** — 응답 조립부는 기존과 **완전히 동일**하게 두고 조회만 바꾼다:

```java
    @Transactional(readOnly = true)
    public List<CartItemResponse> itemsOf(Long memberId) {
        List<CartItem> items = cartItemRepository.findByMemberIdOrderByIdAsc(memberId);
        if (items.isEmpty()) {
            return List.of();
        }

        // 줄마다 findOrderSnapshot을 부르면 줄 수만큼 쿼리가 나간다(N+1).
        // 키를 모아 한 번만 묻는다 — search/ranking/review가 이미 쓰는 규칙이다.
        List<GoodsQueryService.OrderSnapshotKey> keys = items.stream()
                .map(item -> new GoodsQueryService.OrderSnapshotKey(item.getGoodsId(), item.getOptionId()))
                .toList();
        Map<GoodsQueryService.OrderSnapshotKey, GoodsQueryService.OrderGoodsSnapshot> snapshots =
                goodsQueryService.findOrderSnapshots(keys);

        List<CartItemResponse> responses = new ArrayList<>();
        for (CartItem item : items) {
            // 담은 뒤 숨겨진 상품(과 삭제된 옵션)은 목록에서 제외한다. 지우지는 않는다 —
            // 다시 판매되면 그대로 살아나는 편이 손님에게 자연스럽다.
            // 맵에 키가 없는 것이 곧 그 경우다(배치 계약).
            GoodsQueryService.OrderGoodsSnapshot snapshot = snapshots.get(
                    new GoodsQueryService.OrderSnapshotKey(item.getGoodsId(), item.getOptionId()));
            if (snapshot == null) {
                continue;
            }
            responses.add(new CartItemResponse(
                    item.getId(),
                    item.getGoodsId(),
                    // item.getOptionId()가 아니라 snapshot.optionId()를 내려준다. 4-18 이전에
                    // 담긴 레거시 행(옵션 있는 상품인데 option_id=NULL로 저장된 행)은
                    // item.getOptionId()가 여전히 null이라, 그걸 그대로 내리면 optionNo=null인데
                    // optionName은 대표 옵션 이름이 나오는 자기모순 응답이 된다.
                    snapshot.optionId(),
                    snapshot.goodsName(),
                    snapshot.optionName(),
                    snapshot.unitPrice(),
                    item.getQuantity(),
                    snapshot.unitPrice() * item.getQuantity(),
                    snapshot.thumbnailUrl(),
                    snapshot.stock()));
        }
        return responses;
    }
```

**`add`·`changeQuantity`는 건드리지 않는다.** 그 둘은 원래 한 건만 보므로 단건 조회가 맞다.

- [ ] **Step 3: `./gradlew test` 통과.** `CartApiTest`의 기존 단언이 그대로 녹색이어야 한다
      (응답 형태 불변의 증거).

---

### Task 1-3: 주문 목록 — 페이징 + 배치 페치

**Files:**
- Modify: `backend/src/main/resources/application.yml` (**JPA 프로퍼티 3줄만**)
- Modify: `backend/src/main/java/com/beautyboy/order/OrderRepository.java` (조회 메서드 추가)
- Modify: `backend/src/main/java/com/beautyboy/order/OrderService.java` (**`ordersOf`만**)
- Modify: `backend/src/main/java/com/beautyboy/order/OrderController.java` (**`orders` 핸들러만**)
- Modify: `backend/src/test/java/com/beautyboy/order/OrderQueryTest.java` (**아래 명시한 2건의 단언만 수정** + 케이스 추가)
- Create/Test: `backend/src/test/java/com/beautyboy/order/OrderListQueryCountTest.java`

**Interfaces:**
- Produces: `GET /api/v1/orders?page&size` → `ApiResponse<PageResponse<OrderSummaryResponse>>` — **T2-3이 이 형태를 그대로 쓴다.**

- [ ] **Step 1: 깨지는 기존 단언 2건을 먼저 확인하고 고친다**

응답 형태가 배열 → `PageResponse`로 바뀌므로 아래 **2건만** 수정한다. 그 외 단언을 고쳐야
통과한다면 중단·보고:

| 파일 | 테스트 | 기존 | 변경 후 |
|---|---|---|---|
| `OrderQueryTest.java` | `내_주문_목록을_최신순으로_준다` | `jsonPath("$.data.length()").value(2)` | `jsonPath("$.data.content.length()").value(2)` |
| `OrderQueryTest.java` | `남의_주문은_목록에_섞이지_않는다` | `jsonPath("$.data.length()").value(1)` | `jsonPath("$.data.content.length()").value(1)` |

- [ ] **Step 2: 실패하는 테스트 작성** — `OrderQueryTest.java`에 추가:

```java
    @Test
    @DisplayName("주문 목록은 기본 10건씩 페이지로 준다 — 12건이면 1페이지에 10건, hasNext=true")
    void 기본_페이지_크기는_10() {
        // 12건 저장
        // .andExpect(jsonPath("$.data.content.length()").value(10))
        // .andExpect(jsonPath("$.data.page").value(0))
        // .andExpect(jsonPath("$.data.size").value(10))
        // .andExpect(jsonPath("$.data.totalElements").value(12))
        // .andExpect(jsonPath("$.data.totalPages").value(2))
        // .andExpect(jsonPath("$.data.hasNext").value(true))
    }

    @Test
    @DisplayName("size가 상한을 넘으면 100으로 깎는다 — /goods·/qna와 같은 상한")
    void size_상한은_100() {
        // get("/api/v1/orders?size=100000")
        // .andExpect(jsonPath("$.data.size").value(100))
    }

    @Test
    @DisplayName("size가 0 이하면 1로 올린다 — PageRequest가 예외를 던져 500이 되지 않게")
    void size_하한은_1() {
        // get("/api/v1/orders?size=0")
        // .andExpect(status().isOk())
        // .andExpect(jsonPath("$.data.size").value(1))
    }

    @Test
    @DisplayName("같은 시각 주문 두 건도 페이지 경계에서 중복·누락되지 않는다 — id desc 2차 정렬 키")
    void 동시각_주문의_페이지_경계() {
        // orderedAt이 똑같은 주문 2건 저장, size=1로 0페이지와 1페이지를 각각 조회
        // assertThat(0페이지_orderNo).isNotEqualTo(1페이지_orderNo);
    }
```

- [ ] **Step 3: 쿼리 수 회귀 테스트** — `OrderListQueryCountTest.java` (신규)

`@OneToMany`의 N+1은 서비스 경계 바깥(Hibernate 안)에서 일어나므로 스파이로는 안 보인다.
통계를 켠 **별도 컨텍스트**가 필요하다:

```java
/**
 * 주문 목록의 N+1 회귀 방어. 주문 건수가 늘어도 쿼리 수가 일정해야 한다.
 * 응답만 단언하는 테스트는 N+1이 되살아나도 녹색이라 이 클래스가 유일한 증거다.
 * 통계를 켜야 해서 별도 프로퍼티 → 별도 컨텍스트다(그 비용을 알고 감수한다).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
class OrderListQueryCountTest {

    @Test
    @DisplayName("주문 5건 목록과 1건 목록의 쿼리 수가 같다 — default_batch_fetch_size가 항목을 IN으로 모은다")
    void 건수가_늘어도_쿼리_수는_같다() {
        // statistics.clear() 후 1건짜리 회원 조회 → n1
        // statistics.clear() 후 5건짜리 회원 조회 → n5
        // assertThat(n5).isEqualTo(n1);
    }
}
```

- [ ] **Step 4: `application.yml`에 배치 페치 크기 추가**

`spring.jpa` 아래에 `properties`를 더한다(`hibernate.ddl-auto: validate`는 그대로 둔다):

```yaml
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        # LAZY 컬렉션(@OneToMany)을 부모 100건씩 IN 절로 모아 읽는다. 없으면 주문 목록이
        # "주문 1건 = 항목 쿼리 1건"으로 늘어난다(N+1). 100을 고른 이유는 목록 size 상한과
        # 같은 값이어서다 — 한 페이지를 한 번의 IN으로 덮는 가장 작은 수다.
        default_batch_fetch_size: 100
```

> **주의**: 이 파일은 공유 설정이다. 위 3줄 외에 아무것도 바꾸지 않는다.

- [ ] **Step 5: 리포지토리·서비스·컨트롤러**

```java
    /**
     * 내 주문 한 페이지. 2차 정렬 키로 id desc를 두는 이유: ordered_at만으로 정렬하면 같은 초에
     * 만들어진 주문 두 건의 순서가 비결정적이고, 그러면 페이지 경계에서 한 건이 사라지거나
     * 두 번 나온다. (findRecommendedRows·findCandidateIds가 이미 같은 이유로 2차 키를 둔다.)
     */
    Page<Order> findByMemberIdOrderByOrderedAtDescIdDesc(Long memberId, Pageable pageable);
```

```java
    /** 목록 기본 크기. 마이페이지 주문내역 한 화면 분량이며 admin 문의 목록과 같은 값이다. */
    private static final int DEFAULT_PAGE_SIZE = 10;
    /** 상한. /goods(GoodsController)·/qna(QnaService)와 <b>같은 값</b>이다 — 엔드포인트마다 다르면 방어가 아니다. */
    private static final int MAX_PAGE_SIZE = 100;

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> ordersOf(Long memberId, int page, int size) {
        // 손으로 친 파라미터를 그대로 PageRequest에 넣으면 음수·0에서 IllegalArgumentException(500)이 난다.
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<Order> found = orderRepository.findByMemberIdOrderByOrderedAtDescIdDesc(
                memberId, PageRequest.of(safePage, safeSize));

        return PageResponse.of(
                found.getContent().stream().map(this::toSummary).toList(),
                safePage, safeSize, found.getTotalElements());
    }
```

```java
    @GetMapping("/api/v1/orders")
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> orders(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.ordersOf(memberId, page, size)));
    }
```

`toSummary`·`orderDetail`·`create`는 건드리지 않는다. `List` 임포트가 안 쓰이면 정리한다.

- [ ] **Step 6: `./gradlew test` 통과 + Step 1의 2건 외에 고친 단언이 없는지 확인.**

---

### Task 1-4: 손님 경로 입력 검증 (Bean Validation)

**Files:**
- Modify: `backend/src/main/java/com/beautyboy/payment/dto/PaymentConfirmRequest.java`
- Modify: `backend/src/main/java/com/beautyboy/payment/PaymentController.java`
- Modify: `backend/src/main/java/com/beautyboy/order/dto/OrderCreateRequest.java`
- Modify: `backend/src/main/java/com/beautyboy/order/OrderController.java` (**`create` 핸들러의 `@Valid`만** — Task 1-3이 먼저 커밋돼 있어야 한다)
- Modify: `backend/src/main/java/com/beautyboy/cart/dto/CartAddRequest.java`
- Modify: `backend/src/main/java/com/beautyboy/cart/dto/CartBulkAddRequest.java`
- Modify: `backend/src/main/java/com/beautyboy/cart/CartController.java`
- Modify: `backend/src/main/java/com/beautyboy/review/dto/ReviewCreateRequest.java`
- Modify: `backend/src/main/java/com/beautyboy/review/ReviewController.java`
- Modify: `backend/src/main/java/com/beautyboy/qna/dto/QnaCreateRequest.java`
- Modify: `backend/src/main/java/com/beautyboy/qna/QnaController.java`
- Create/Test: `backend/src/test/java/com/beautyboy/common/RequestValidationTest.java`

**§2 결정 3을 먼저 읽어라.** 어디에 붙이고 **어디에 붙이지 않는지**가 이 태스크의 전부다.

- [ ] **Step 1: 실패하는 테스트 작성** — `RequestValidationTest.java`

앞의 8건은 "이제 막힌다", 뒤의 3건은 **"애노테이션이 도메인 코드를 가로채지 않는다"** 를
못 박는다. 뒤의 3건이 이 태스크의 진짜 사양이다.

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RequestValidationTest {

    // --- 구조적 결손: 이제 400 INVALID_INPUT으로 막힌다 (예전에는 NPE·DB 예외로 500) ---

    @Test @DisplayName("결제 승인: orderNo가 비면 400 INVALID_INPUT")
    void 결제_orderNo_공백() {
        // post /api/v1/payments/confirm {"orderNo":"","paymentKey":"pk","amount":1000}
        // .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test @DisplayName("결제 승인: paymentKey가 null이면 400 INVALID_INPUT — 토스에 null을 들고 가지 않는다")
    void 결제_paymentKey_null() { }

    @Test @DisplayName("주문 생성: 수령인이 비면 400 INVALID_INPUT")
    void 주문_수령인_공백() { }

    @Test @DisplayName("주문 생성: address1이 200자를 넘으면 400 INVALID_INPUT — DB 예외(500)로 새지 않는다")
    void 주문_주소_길이초과() { }

    @Test @DisplayName("주문 생성: 항목의 goodsNo가 null이면 400 INVALID_INPUT")
    void 주문_항목_goodsNo_null() { }

    @Test @DisplayName("장바구니 담기: goodsNo가 null이면 400 INVALID_INPUT")
    void 담기_goodsNo_null() { }

    @Test @DisplayName("리뷰 작성: 내용이 공백이면 400 INVALID_INPUT")
    void 리뷰_내용_공백() { }

    @Test @DisplayName("문의 작성: 질문이 공백이면 400 INVALID_INPUT")
    void 문의_질문_공백() { }

    // --- 경계 고정: 도메인 판정은 그대로 서비스가 한다 (§2 결정 3) ---

    @Test @DisplayName("수량 0은 여전히 CART_QUANTITY_INVALID — 애노테이션이 도메인 코드를 가로채지 않는다")
    void 수량_0은_도메인_코드() {
        // post /api/v1/cart/items {"goodsNo":1,"optionNo":null,"quantity":0}
        // .andExpect(status().isBadRequest())
        // .andExpect(jsonPath("$.code").value("CART_QUANTITY_INVALID"));
    }

    @Test @DisplayName("빈 항목 주문은 여전히 CART_EMPTY")
    void 빈_항목은_도메인_코드() {
        // .andExpect(jsonPath("$.code").value("CART_EMPTY"));
    }

    @Test @DisplayName("평점 6은 여전히 ReviewService가 판정한다 — detail(필드 오류)이 없는 INVALID_INPUT")
    void 평점_범위는_서비스가_판정한다() {
        // .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        // .andExpect(jsonPath("$.detail").doesNotExist());
        // ↑ Bean Validation이 잡았다면 GlobalExceptionHandler가 fieldErrors를 detail에 싣는다.
        //   detail이 비어 있다는 것이 "서비스가 판정했다"는 증거다.
    }
}
```

- [ ] **Step 2: DTO에 애노테이션을 붙인다** (전량 — 어디에 안 붙었는지가 사양이다)

```java
// payment/dto/PaymentConfirmRequest.java — 기존 Javadoc은 그대로 두고 필드만 바꾼다
public record PaymentConfirmRequest(
        // order.order_no VARCHAR(30)
        @NotBlank @Size(max = 30) String orderNo,
        // payment.payment_key VARCHAR(200)
        @NotBlank @Size(max = 200) String paymentKey,
        // amount에는 제약을 붙이지 않는다 — 애초에 신뢰하지 않는 값이고 PaymentService가
        // 서버의 payableAmount와 대조한다. @Positive를 붙이면 "검증한다"는 잘못된 인상만 남는다.
        int amount) {
}
```

```java
// order/dto/OrderCreateRequest.java — 기존 Javadoc(금액 필드 없음의 근거)은 그대로 둔다
public record OrderCreateRequest(
        // @Valid만 붙인다(@NotEmpty 아님) — null·빈 목록은 OrderService가 CART_EMPTY로 판정한다.
        // 여기서 400으로 가로채면 그 코드가 사라진다.
        @Valid List<OrderItemRequest> items,
        @NotBlank @Size(max = 50) String receiverName,     // order.receiver_name VARCHAR(50)
        @NotBlank @Size(max = 20) String receiverPhone,    // order.receiver_phone VARCHAR(20)
        @NotBlank @Size(max = 10) String zipcode,          // order.zipcode VARCHAR(10)
        @NotBlank @Size(max = 200) String address1,        // order.address1 VARCHAR(200)
        @Size(max = 200) String address2,                  // NULL 허용 컬럼이라 @NotBlank 없음
        // 값 집합(NORMAL만) 검증은 하지 않는다 — 오늘드림을 도입하면 여기부터 고쳐야 하는데,
        // 허용 값 목록이 DTO에 박히면 도메인 결정이 DTO로 새어 나간다.
        @NotBlank @Size(max = 20) String deliveryType) {

    /** 무엇을 몇 개. 가격은 서버가 정한다. quantity는 OrderService가 CART_QUANTITY_INVALID로 판정한다. */
    public record OrderItemRequest(@NotNull Long goodsNo, Long optionNo, int quantity) {
    }
}
```

```java
// cart/dto/CartAddRequest.java — quantity는 CartService가 판정한다(애노테이션 없음)
public record CartAddRequest(@NotNull Long goodsNo, Long optionNo, int quantity) {
}

// cart/dto/CartBulkAddRequest.java
// @NotNull만 — 빈 목록은 지금도 "아무것도 담지 않음"(201)이고 그 동작을 바꾸지 않는다.
public record CartBulkAddRequest(@NotNull @Valid List<CartAddRequest> items) {
}
```

`cart/dto/CartQuantityRequest.java`는 **수정하지 않는다** — 유일한 필드인 `quantity`의 판정
주체가 `CartService`이므로 붙일 것이 없다.

```java
// review/dto/ReviewCreateRequest.java
// rating에는 붙이지 않는다 — ReviewService의 MIN_RATING/MAX_RATING이 판정 주체다(§2 결정 3).
public record ReviewCreateRequest(
        @NotNull Long goodsNo,
        int rating,
        @NotBlank @Size(max = 2000) String content) {   // review.content VARCHAR(2000)
}

// qna/dto/QnaCreateRequest.java
public record QnaCreateRequest(
        @NotNull Long goodsNo,
        @NotBlank @Size(max = 1000) String question,    // qna.question VARCHAR(1000)
        boolean isSecret) {
}
```

- [ ] **Step 3: 컨트롤러 6곳에 `@Valid` 추가.** `@RequestBody` 앞에 붙인다. 그 외 시그니처·본문은
      건드리지 않는다:

| 파일 | 핸들러 |
|---|---|
| `payment/PaymentController.java` | `confirm` |
| `order/OrderController.java` | `create` |
| `cart/CartController.java` | `add`, `addAll` |
| `review/ReviewController.java` | `create` |
| `qna/QnaController.java` | `create` |

`spring-boot-starter-validation`은 이미 의존성에 있고(`build.gradle.kts`),
`MethodArgumentNotValidException` 핸들러도 이미 있다(`GlobalExceptionHandler:36`).
**`build.gradle.kts`와 `common`은 열지 않는다.**

- [ ] **Step 4: `./gradlew test` 통과.** 기존 결제·주문·장바구니·리뷰·문의 테스트가 전부 녹색이어야
      한다. 하나라도 깨지면 그것은 **애노테이션이 도메인 코드를 가로챈 것**이므로, 애노테이션을
      떼고 §2 결정 3 표에 그 항목을 추가한 뒤 보고한다.

- [ ] **Step 5 (T1 마감): `./gradlew test` + `./gradlew integrationTest` 실측 건수를 보고한다.**

---

## T2 — 프론트

### Task 2-1: 라우트 오류 화면 (`route-error`)

**Files:**
- Create: `frontend/src/components/common/RouteError.tsx`
- Create: `frontend/src/components/common/RouteError.css`
- Create/Test: `frontend/src/components/common/RouteError.test.tsx`
- Modify: `frontend/src/router.tsx` (**라우트 트리에 경계 한 겹 + catch-all만**)

**Interfaces:**
- Consumes: `DESIGN.md`의 `route-error` 절(**읽기 전용**), 기존 `components/ui/Button`
- Produces: `RouteError` — Task 2-2가 이 컴포넌트를 다시 건드리지 않는다

- [ ] **Step 1: 실패하는 테스트 작성** — `RouteError.test.tsx`

```tsx
describe('RouteError', () => {
  it('렌더 중 예외가 나도 헤더·푸터는 남는다', () => {
    // 예외를 던지는 자식 라우트를 memory router로 구성하고 Layout 안에서 렌더
    // expect(screen.getByRole('banner')).toBeInTheDocument();       // Header
    // expect(screen.getByText('화면을 불러오지 못했어요')).toBeInTheDocument();
  });

  it('없는 주소는 404 문구를 낸다 — 예외 화면과 같은 컴포넌트다', () => {
    // initialEntries={['/이런경로는없다']}
    // expect(screen.getByText('요청하신 페이지를 찾을 수 없어요')).toBeInTheDocument();
  });

  it('예외 메시지를 화면에 내지 않는다', () => {
    // throw new Error('DB 커넥션 풀 고갈: jdbc:mysql://...')
    // expect(screen.queryByText(/jdbc/)).not.toBeInTheDocument();
  });

  it('복구 경로를 둘 다 준다 — 다시 시도 + 홈으로', () => {
    // expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
    // expect(screen.getByRole('button', { name: '홈으로' })).toBeInTheDocument();
  });

  it('오류 컨테이너에 role="alert"과 h1이 있다', () => {
    // expect(screen.getByRole('alert')).toBeInTheDocument();
    // expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: `RouteError.tsx` 작성**

DESIGN.md `route-error` 절을 그대로 따른다. 판단이 갈리는 부분(에러 없음 = 404, 새로고침을
쓰는 이유)만 코드로 못 박는다:

```tsx
/**
 * 라우트 오류·없는 페이지 화면 (DESIGN.md `route-error`).
 *
 * **에러 객체가 없으면 404다.** 이 컴포넌트는 두 자리에서 쓰인다 — 예외가 올라오는
 * errorElement 자리와, 매칭 라우트가 없을 때 도는 catch-all(`path: '*'`) 자리. 후자는 예외가
 * 없으므로 useRouteError()가 undefined다. 그것을 그대로 404 신호로 읽는다.
 *
 * **"다시 시도"가 navigate가 아니라 reload인 이유**: 이 화면에 도달했다는 것은 라우터가 이미
 * 오류 상태를 들고 있다는 뜻이라, 같은 주소로 navigate하면 그 상태 그대로 다시 오류 화면이 뜬다.
 * 문서를 새로 받아야 복구된다. (404 경로에서는 오류 상태가 없지만, 문구가 "다시 시도"인 이상
 * 두 경로에서 같은 일을 해야 손님이 예측할 수 있다.)
 */
```

문구는 DESIGN.md가 정한 두 갈래만 쓴다. 원인 문자열은 화면에 내지 않는다(개발 편의가 필요하면
`console.error`로만).

- [ ] **Step 3: `RouteError.css`** — `empty-state`와 같은 골격. `var(--color-*)`·`var(--space-*)`
      토큰만 쓰고 hex를 옮겨 적지 않는다. 배경 채움·`signal-danger` 테두리 금지.

- [ ] **Step 4: `router.tsx` — 경계 한 겹 추가** (판단이 갈리는 곳이라 구조를 전량으로)

기존 children을 **경로 없는 라우트 한 겹으로 감싸고**, 그 안 맨 끝에 catch-all을 둔다.
자식 라우트들의 내용은 **하나도 바꾸지 않는다** — 들여쓰기만 한 칸 들어간다:

```tsx
export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      {
        // 경로 없는 경계. errorElement를 루트(<Layout/>)에 걸면 오류 시 헤더·푸터째 사라져
        // 손님이 다른 곳으로 갈 길을 잃는다. 한 겹 안쪽에 걸어야 <Outlet/> 자리에서만 대체된다.
        errorElement: <RouteError />,
        children: [
          { index: true, element: <Home /> },
          /* ... 기존 자식 라우트 전부 그대로 ... */
          // 매칭되는 주소가 없을 때. 예외가 아니므로 errorElement로는 오지 않는다 —
          // 같은 화면을 여기서 직접 렌더한다(404와 오류는 손님에게 같은 사건이다).
          { path: '*', element: <RouteError /> },
        ],
      },
    ],
  },
  { path: 'dev/components', element: <Showcase /> },
]);
```

- [ ] **Step 5: `npm test` + `npx tsc -p tsconfig.app.json --noEmit` 통과.**
- [ ] **Step 6 (스크린샷 DoD): `npm run dev`로 띄우고 없는 주소(예: `/없는페이지`)로 들어가
      화면을 직접 찍는다.** 헤더·푸터가 살아 있고 버튼 2개가 보이는지 눈으로 확인한 뒤 경로를
      보고서에 남긴다.

---

### Task 2-2: 라우트 코드 스플리팅

**Files:**
- Modify: `frontend/src/router.tsx` (임포트 → `lazy` 전환. **Task 2-1이 먼저 커밋돼 있어야 한다**)
- Modify: `frontend/src/components/layout/Layout.tsx` (`Suspense` 경계 + 폴백)
- Modify: `frontend/src/components/layout/Layout.css` (폴백 블록 여백만)
- Modify: `README.md` (번들 산출물 표 추가)

**§2 결정 5를 먼저 읽어라.** 무엇을 쪼개고 무엇을 남기는지가 정해져 있다.

- [ ] **Step 1: `router.tsx`의 임포트를 전환한다**

named export이므로 `.then()`으로 default를 지어 준다:

```tsx
// 쪼개는 화면: 대부분의 손님이 안 가거나(관리자·마이페이지), 결제 SDK를 물고 있거나(주문 계열),
// 상용 라우트가 아니다(Showcase). 첫 진입·탐색 주 경로는 정적 임포트로 남긴다 — 거기까지
// 쪼개면 첫 클릭마다 네트워크 왕복이 하나 더 붙어 번들을 줄이려다 체감 속도를 깎는다.
const Order = lazy(() => import('./pages/Order').then((m) => ({ default: m.Order })));
```

전환 대상(§2 결정 5): `AdminLayout` `AdminGoods` `AdminRoutine` `AdminQna` `MyPageLayout`
`MyOrders` `MyWishlist` `MyReviews` `MyProfile` `Order` `OrderComplete` `OrderFail` `Search`
`Ranking` `Showcase`.

정적 유지: `Layout` `Home` `Login` `Signup` `Main` `GoodsList` `Detail` `Cart` `Routine`
`RequireAuth` `RequireAdmin` `RouteError`.

`Showcase`는 Layout 밖 라우트이므로 **자기 자리에서** `<Suspense fallback={null}>`로 감싼다.

- [ ] **Step 2: `Layout.tsx`에 Suspense 경계**

```tsx
      <main id="main-content" className="bb-layout__main">
        {/* 지연 로딩 라우트의 폴백. 스켈레톤 한 블록으로 통일한다 — 화면마다 다른 폴백을 두면
            "로딩 중"이 화면마다 다르게 생기고, 그 자체가 레이아웃 점프의 원인이 된다.
            (DESIGN.md "상태: 로딩·빈 상태·진행" — 300ms 넘는 로딩은 스켈레톤) */}
        <Suspense fallback={<Skeleton ratio="16 / 6" className="bb-layout__fallback" />}>
          <Outlet />
        </Suspense>
      </main>
```

- [ ] **Step 3: `npm test` + `npx tsc -p tsconfig.app.json --noEmit` 통과.**
      기존 페이지 테스트는 컴포넌트를 직접 렌더하므로 영향이 없어야 한다. 라우터를 통째로
      렌더하는 테스트가 있다면 `Suspense` 때문에 `findBy*`가 필요해질 수 있다 — **단언은 그대로
      두고 쿼리만 비동기로 바꾼다.** 단언을 바꿔야 한다면 중단·보고.

- [ ] **Step 4: `npm run build`로 산출물을 확인한다 (이 태스크의 진짜 DoD)**

전환 **전후** 청크 목록과 크기를 기록한다. 확인할 것 두 가지:
1. 초기 청크(entry + 정적 임포트)의 합이 줄었는가
2. `@tosspayments/tosspayments-sdk`가 **`Order` 청크로 빠졌는가** — 초기 청크에 남아 있으면
   §2 결정 5의 전제가 틀린 것이므로 **`manualChunks`를 넣지 말고 먼저 보고**한다.

- [ ] **Step 5: README.md에 "번들" 절을 추가한다.** "테스트" 절 뒤에 표 하나로:
      전환 전/후 초기 청크 크기(gzip 포함)와 **무엇을 쪼갰고 무엇을 남겼는지 한 줄 근거**.
      숫자는 Step 4의 실측값만 적는다 — 추정치를 적지 않는다.

---

### Task 2-3: 주문 목록 페이징 (화면)

**Files:**
- Modify: `frontend/src/api/order.ts` (**`fetchOrders`와 그 반환 타입만**)
- Modify: `frontend/src/pages/mypage/MyOrders.tsx` (**`OrderListView`만** — 상세 뷰는 건드리지 않는다)
- Modify: `frontend/src/pages/mypage/MyOrders.css` (페이저 여백만)
- Modify: `frontend/src/pages/mypage/MyOrders.test.tsx` (**MSW 응답 형태 수정 + 케이스 추가. 기존 단언 유지**)
- Modify: `frontend/src/mocks/handlers.ts` (**`GET /api/v1/orders` 핸들러 1개만**)

**Interfaces:**
- Consumes: T1-3의 계약 — `GET /api/v1/orders?page&size` → `data: PageResponse<OrderSummary>`
- Consumes: 기존 `components/ui/Pager`, `types/goods`의 `PageResponse`

- [ ] **Step 1: 응답 형태 변경을 MSW에 먼저 반영하고 테스트를 고친다**

`MyOrders.test.tsx`의 핸들러(현재 `envelope(orders)` — 배열)를 `envelope({ content: orders,
page: 0, size: 10, totalElements: orders.length, totalPages: 1, hasNext: false })`로 바꾼다.
`mocks/handlers.ts`의 `GET /api/v1/orders`도 같은 형태로 바꾼다.

**기존 케이스의 단언은 그대로 둔다** — 화면에 보이는 것은 바뀌지 않는다.

새 케이스:

```tsx
it('주문이 두 페이지 이상이면 페이저가 보인다', async () => {
  // totalPages: 3으로 응답
  // expect(await screen.findByRole('navigation', { name: '페이지 이동' })).toBeInTheDocument();
});

it('한 페이지뿐이면 페이저를 렌더하지 않는다', async () => {
  // totalPages: 1
  // expect(screen.queryByRole('navigation', { name: '페이지 이동' })).not.toBeInTheDocument();
});

it('2페이지를 누르면 URL이 ?page=2가 되고 서버에 page=1을 보낸다 (URL은 1-based, API는 0-based)', async () => {
  // 요청 파라미터를 가로채 page=1 인지 단언
});

it('1페이지로 돌아가면 page 파라미터를 URL에서 지운다', async () => {
  // GoodsList와 같은 규약 — ?page=1과 무파라미터가 같은 화면의 두 주소가 되지 않게
});
```

- [ ] **Step 2: `api/order.ts`**

```ts
/** GET /orders — 페이지 단위. page는 0-based(서버 계약), size 상한은 서버가 100으로 깎는다. */
export async function fetchOrders(page = 0, size = 10): Promise<PageResponse<OrderSummary>> {
  const response = await api.get<ApiEnvelope<PageResponse<OrderSummary>>>('/orders', {
    params: { page, size },
  });
  return response.data.data;
}
```

- [ ] **Step 3: `MyOrders.tsx`의 `OrderListView`만 고친다**

`GoodsList`의 규약을 그대로 따른다 — **URL이 상태의 진실**, 1페이지는 파라미터 생략,
손으로 친 값은 정규화:

```tsx
/** URL의 page는 1-based 표시값이다. 손으로 친 미지값·0 이하는 1로 접어 빈 화면을 막는다. */
function normalizePage(raw: string | null): number {
  const parsed = Number(raw);
  return Number.isInteger(parsed) && parsed >= 1 ? parsed : 1;
}
```

- `useSearchParams`로 `page`를 읽고 `queryKey: ['myOrders', page]`
- `placeholderData: keepPreviousData` — 페이지 전환 때 목록 높이가 무너지지 않게(GoodsList와 같은 이유)
- 목록 아래 `<Pager page={page} totalPages={data.totalPages} onPageChange={...} />`
- `onPageChange`는 1페이지면 파라미터를 지우고, 그 외에는 `?page=N`
- 빈 상태 판정은 `data.content.length === 0`으로 바꾼다 (기존 `EmptyState` 문구·행동은 그대로)

**`OrderDetailView`·`buildOrderLabel`은 건드리지 않는다.**

- [ ] **Step 4: `npm test` + `npx tsc -p tsconfig.app.json --noEmit` 통과.**
- [ ] **Step 5 (스크린샷 DoD): 시드 계정으로 `/mypage/orders`를 열어 페이저가 붙은 목록을 찍는다.**
      주문이 10건을 넘어야 페이저가 보인다 — 부족하면 MSW(`VITE_USE_MOCK=true`)로 늘려 찍고
      그 사실을 보고서에 적는다.

---

## 4. 공유 계약 요약 (T1 ↔ T2)

이 계획에서 두 터미널이 **동시에 의존하는** 것은 하나뿐이다.

```
GET /api/v1/orders?page={0-based}&size={기본 10, 상한 100}
→ 200 {
    "code": "OK",
    "message": "success",
    "data": {
      "content": [
        { "orderNo": "...", "status": "PAID", "representativeGoodsName": "...",
          "itemCount": 3, "payableAmount": 58000, "orderedAt": "2026-07-20T10:00:00" }
      ],
      "page": 0, "size": 10, "totalElements": 12, "totalPages": 2, "hasNext": true
    }
  }
```

`OrderSummary`의 필드는 **하나도 바뀌지 않는다** — 배열이 `content`로 한 겹 들어갈 뿐이다.

---

## 5. 머지 게이트 (사람이 판정)

1. **T1 먼저 머지.** 조건: `./gradlew test` 전량 통과 + `./gradlew integrationTest` 전량 통과
   (실 MySQL·Flyway V1~V84·`ddl-auto=validate`). `application.yml`을 건드렸으므로 이 스위트를
   생략하면 안 된다 — [[h2-createdrop-hides-validate]].
2. **T2 나중.** 조건: `npm test` 전량 + `npx tsc -p tsconfig.app.json --noEmit` 0에러 +
   Task 2-1·2-3의 스크린샷을 **사람이 열어보고** 판정 + Task 2-2의 번들 실측표가 README에 있음.
3. **두 브랜치를 머지한 뒤** `npm run test:e2e`를 한 번 돌린다. 주문 목록 응답 형태가 바뀌었으므로
   결제 플로우 E2E가 이 변경의 최종 통합 증거다. 백엔드는 반드시 **`e2e` 프로필**로 띄운다
   ([[e2e-needs-e2e-profile-backend]] — compose 백엔드로 돌리면 토스 401로 거짓 적신호가 뜨고,
   시드 계정 장바구니도 비워야 한다).
4. 머지 후 `docs/plans/2026-07-26-다음-작업.md`에 이번 5건의 종결을 기록하고, **이번에 범위 밖으로
   남긴 것**(admin DTO 검증, clamp 공용화, queryKey 팩토리, QueryClient 기본값, `ErrorState`
   공용화, Pretendard 로딩)을 다음 웨이브 후보로 옮겨 적는다.
