# 루틴 메인 페이지(`/main`) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: `superpowers:subagent-driven-development`(권장) 또는 `superpowers:executing-plans`로 태스크 단위 실행. 스텝은 체크박스(`- [ ]`)로 추적한다.

**Goal:** 로그인 이후 진입하는 신규 페이지 `/main`을 만들어, 스크롤을 내리는 순서가 곧 스킨케어 루틴 순서(클렌징 → 스킨/토너 → 앰플/세럼 → 크림/로션 → 선크림)가 되게 한다.

**Architecture:** 랜딩(`/`)은 그대로 두고 `/main`을 신설한다. `RequireAuth` 가드가 비로그인 접근을 `/login`으로 돌린다. 루틴 5단계는 `features/routine/steps.ts`의 명시적 상수이며(카테고리 트리로 표현하지 않는다), 각 단계는 자기 데이터를 스스로 가져오는 `RoutineSection` 하나로 렌더된다 — 큰 타이포 블록 ↔ 이미지 박스가 좌우 교차하고, 아래에 그 카테고리 상품 4개와 `/goods?category=` 더보기가 붙는다.

**Tech Stack:** React 19, Vite 7, TypeScript, React Router 7, Zustand, TanStack Query 5, MSW, Vitest + @testing-library, oxlint.

**근거 문서:** `docs/plans/2026-07-23-landing-main-composition.md` 2장(구성 사양)·5장(태스크). 시각 토큰의 진실은 루트 `DESIGN.md`.

---

## Global Constraints

모든 태스크에 암묵적으로 포함된다.

- **CSS는 `frontend/src/index.css`에 정의된 토큰만 참조한다. hex를 손으로 적지 않는다.** 문서에 없는 색·간격이 필요하면 만들지 말고 **중단하고 보고**한다.
- 이 계획이 쓰는 토큰은 전부 `index.css`에 실존함이 확인됐다: `--color-canvas`(#f7f7f7) · `--color-surface`(#ebebeb) · `--color-ink` · `--color-graphite` · `--color-ash-soft` · `--color-hairline` · `--rounded-lg`(12px) · `--space-xxs|xs|sm|md|lg|xl|xxl|section|section-lg` · `--fs-display-sm`(72) · `--fs-heading-md`(36) · `--fs-heading-sm`(24) · `--fs-body-lg`(18) · `--fs-body` · `--fs-eyebrow` · `--fs-micro-caps` · `--fs-link-sm` · `--font-body` · `--landing-column`(1360px).
- **시그널 색(`--color-signal-*`)은 배경으로 칠하지 않는다** — 글자·아이콘·1px 테두리로만. 한 뷰포트에 시그널 색 2종까지.
- **색 단독으로 의미를 전달하지 않는다.**
- **한글**: `word-break: keep-all`은 전역 적용됨(건드리지 말 것). 본문 최소 16px.
- **`prefers-reduced-motion: reduce`를 존중한다** — 앵커 스무스 스크롤 포함, 어떤 트랜지션도 즉시 완료로 축약.
- **외부 이미지 URL을 직접 참조하지 않는다.** 이미지는 `frontend/public/` 아래로 받아서 쓴다. 실제 브랜드 제품 사진은 금지.
- **상품 카드는 `GoodsCard`/`GoodsGrid`를 재사용한다. 새로 만들지 않는다.**
- 자기 태스크 **Files 목록 밖 파일 수정 금지.** `index.css` 토큰 정의·`DESIGN.md`·루트 빌드 설정은 공유 계약 — 안 맞으면 수정 말고 보고.
- 커밋 메시지·주석은 한국어, 태스크 단위 원자적 커밋.
- **모델 배분: 전 태스크 sonnet** (`CLAUDE.md` 예외 3종 — 결제·재고차감·궁합엔진 — 해당 없음).
- 명령은 모두 `frontend/`에서 실행한다.

---

## 착수 전 확인 (사람 몫)

```bash
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" log --oneline -1   # 0e19fb3 (docs(plan): 메인 루틴 페이지 사양 확정) 이후여야 함
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" status --short     # 비어 있어야 한다
```

### 작업 공간 만들기 (오케스트레이터 첫 행동)

```bash
git worktree add ../BeautyBoy-w-main-routine -b feat/main-routine
```

실행 뒤 `EnterWorktree` 도구에 **path**로 그 경로를 넘겨 세션을 옮긴다.
(`EnterWorktree`를 `name`으로 새로 만들지 마라 — origin에서 브랜치를 따서 이 계획서도 갱신된 `DESIGN.md`도 없는 worktree가 생긴다.)

진입 후 아래를 확인하고 **하나라도 어긋나면 중단하고 보고**한다:

```bash
pwd                                                    # ...BeautyBoy-w-main-routine
git log --oneline -1                                   # 루트에서 본 기점과 동일
ls docs/superpowers/plans/2026-07-24-main-routine-page.md DESIGN.md
ls frontend/src/components/goods/GoodsGrid.tsx frontend/src/stores/authStore.ts
git status --short                                     # 비어 있어야 한다
cd frontend && npm install && npm run test             # green이어야 착수
```

---

## 파일 구조 (이 계획이 만들거나 고치는 것)

| 파일 | 책임 | 태스크 |
|---|---|---|
| `src/mocks/fixtures/goods.ts` | fixture 카테고리 코드를 실 시드(V12)에 정렬 | T1 |
| `src/mocks/handlers.ts` | 카테고리 트리 실 시드 정렬 + 인증 목 핸들러 5종 | T1 |
| `src/api/goods.test.ts` | 바뀐 fixture 배분에 맞춰 건수 단언 갱신 | T1 |
| `src/features/routine/steps.ts` (신규) | 루틴 5단계 상수 — 매핑의 단일 진실 | T2 |
| `src/components/auth/RequireAuth.tsx` (신규) | 비로그인 차단 가드 (부트스트랩 대기 포함) | T3 |
| `src/router.tsx` | `/main`·`/goods` 라우트 등록 | T3 |
| `src/pages/Login.tsx` | 로그인 성공 후 이동지 `/` → `/main` | T3 |
| `public/images/routine/*` (신규) | 단계별 이미지 5장 | T4 |
| `src/components/routine/RoutineSection.tsx` + `.css` (신규) | 단계 1개의 렌더 + 데이터 페치 | T5 |
| `src/pages/Main.tsx` + `Main.css` (신규) | 인트로 + 앵커 네비(scroll-spy) + 섹션 5개 조립 | T6 |
| `src/pages/GoodsList.tsx` + `GoodsList.css` (신규) | `?category=` 목록 페이지 — 더보기 행선지 | T7 |
| — | 반응형·스크린샷 검증 (5개 폭) | T8 |

**범위 밖(YAGNI):**
- **상품 상세 `/goods/:goodsNo`** — `GoodsCard`가 이미 이 경로로 링크하지만 라우트는 아직 없다. 이 계획은 만들지 않는다(카드 클릭 시 라우터 404). 별도 웨이브 몫이며, **이 사실을 T8 보고서에 명시**한다.
- **헤더 내비에 `/main` 링크 추가** — 헤더는 랜딩 전용 구성이라 로그인 상태별 내비 재설계가 필요하다. 이 계획의 진입 경로는 "로그인 성공 → `/main`" 하나다.
- 장바구니·검색·찜 서버 반영.

---

## Task 1: mock 데이터를 실 시드 카테고리 코드에 정렬 + 인증 목 핸들러

**왜 이게 1번인가:** 루틴 매핑은 실 시드(`V12__seed_catalog.sql`)의 `C002`(클렌징) · `C001001` · `C001002` · `C001003` · `C004001`을 쓴다. 그런데 현재 mock fixture는 `C001/C0011/C0012`, `C002=헤어`, `C004=메이크업`이라 **루틴 5섹션 중 4개가 mock 모드에서 빈 상태로 렌더된다.** 이걸 고치지 않으면 T8의 스크린샷 검증이 무의미하다. 또한 `/main`은 로그인이 필요한데 인증 목 핸들러가 하나도 없어 mock 모드로는 로그인 자체가 불가능하다.

**Files:**
- Modify: `frontend/src/mocks/fixtures/goods.ts` (`CATEGORY_PLAN` 상수)
- Modify: `frontend/src/mocks/handlers.ts` (`categoryTree` 상수, `handlers` 배열)
- Modify: `frontend/src/api/goods.test.ts` (건수 단언 2곳)

**Interfaces:**
- Produces: fixture 총 40건 유지. 접두사 필터 기준 건수 — `C002`→8, `C001001`→6, `C001002`→6, `C001003`→6, `C004001`→5, `C001`→18, `C003`→5.
- Produces: 목 인증 계약 — `POST /api/v1/auth/login`은 임의 자격증명에 `{ accessToken: 'mock-access-token' }` 200, `GET /api/v1/members/me`는 닉네임 `민수`인 회원, `POST /api/v1/auth/refresh`는 **401**(비로그인 시작이 기본), `POST /api/v1/auth/logout`은 204.

- [ ] **Step 1: 실패 테스트 작성** — `frontend/src/api/goods.test.ts`의 카테고리 단언 2개를 새 배분으로 교체

기존 23~35행의 두 `it` 블록을 아래로 **교체**한다:

```ts
  it('categoryCode=C001은 접두사 필터로 동작해 하위 카테고리까지 포함한다', async () => {
    const result = await fetchGoodsList({ page: 0, size: 40, categoryCode: 'C001' });

    // fixture 배분: C001001*(6) + C001002*(6) + C001003*(6) = 18건
    expect(result.totalElements).toBe(18);
    expect(result.content).toHaveLength(18);
  });

  it('categoryCode=C002(클렌징)는 3개 하위 카테고리를 합쳐 반환한다', async () => {
    const result = await fetchGoodsList({ page: 0, size: 40, categoryCode: 'C002' });

    // C002001(3) + C002002(3) + C002003(2) = 8건
    expect(result.totalElements).toBe(8);
  });

  it('루틴 5단계 categoryCode가 모두 4건 이상을 반환한다(섹션이 비지 않는다)', async () => {
    const routineCodes = ['C002', 'C001001', 'C001002', 'C001003', 'C004001'];

    for (const code of routineCodes) {
      const result = await fetchGoodsList({ page: 0, size: 4, categoryCode: code });
      expect(result.totalElements, `${code}의 상품 수`).toBeGreaterThanOrEqual(4);
      expect(result.content, `${code}의 첫 페이지`).toHaveLength(4);
    }
  });
```

- [ ] **Step 2: 실패 확인**

Run: `npm run test -- goods`
Expected: FAIL — `expected 15 to be 18`, `C002`는 5건이라 8이 아님, 루틴 코드 대부분이 0건.

- [ ] **Step 3: fixture 카테고리 배분 교체**

`frontend/src/mocks/fixtures/goods.ts`의 `CATEGORY_PLAN` 상수를 아래로 **교체**한다(총합 40건 유지):

```ts
/**
 * 실 시드(backend V12__seed_catalog.sql)의 카테고리 코드를 그대로 쓴다.
 * mock 전용 코드를 따로 두면 루틴 매핑(features/routine/steps.ts)이 mock에서만 빈 화면이 된다.
 * 루틴 5단계(C002 / C001001 / C001002 / C001003 / C004001)는 각각 4건 이상이 되도록 배분했다.
 */
const CATEGORY_PLAN: Array<{ code: string; label: string; count: number }> = [
  // 루틴 1단계 — 클렌징(C002)
  { code: 'C002001', label: '클렌징폼', count: 3 },
  { code: 'C002002', label: '클렌징오일', count: 3 },
  { code: 'C002003', label: '필링젤', count: 2 },
  // 루틴 2단계 — 토너/스킨(C001001)
  { code: 'C001001001', label: '수분토너', count: 3 },
  { code: 'C001001002', label: '진정토너', count: 3 },
  // 루틴 3단계 — 에센스/세럼(C001002)
  { code: 'C001002001', label: '고보습에센스', count: 3 },
  { code: 'C001002002', label: '미백세럼', count: 3 },
  // 루틴 4단계 — 로션/크림(C001003)
  { code: 'C001003001', label: '데일리로션', count: 3 },
  { code: 'C001003002', label: '고영양크림', count: 3 },
  // 루틴 5단계 — 선크림(C004001)
  { code: 'C004001', label: '선크림', count: 5 },
  // 루틴 축 밖 — 목록/필터 검증용
  { code: 'C003001', label: '샴푸', count: 3 },
  { code: 'C003002', label: '바디워시', count: 2 },
  { code: 'C005001', label: '면도기', count: 2 },
  { code: 'C006001', label: '베이스메이크업', count: 2 },
];
```

- [ ] **Step 4: 카테고리 트리 목을 같은 시드에 맞춤**

`frontend/src/mocks/handlers.ts`의 `categoryTree` 상수를 아래로 **교체**한다:

```ts
/** 실 시드(V12__seed_catalog.sql) 1~2depth를 그대로 옮긴 목 트리. fixture의 categoryCode와 같은 축을 쓴다. */
const categoryTree: CategoryNode[] = [
  {
    code: 'C001',
    name: '스킨케어',
    children: [
      { code: 'C001001', name: '토너/스킨', children: [] },
      { code: 'C001002', name: '에센스/세럼', children: [] },
      { code: 'C001003', name: '로션/크림', children: [] },
    ],
  },
  {
    code: 'C002',
    name: '클렌징',
    children: [
      { code: 'C002001', name: '클렌징폼', children: [] },
      { code: 'C002002', name: '클렌징오일/밤', children: [] },
      { code: 'C002003', name: '필링/스크럽', children: [] },
    ],
  },
  {
    code: 'C003',
    name: '헤어·바디',
    children: [
      { code: 'C003001', name: '샴푸/린스', children: [] },
      { code: 'C003002', name: '바디워시', children: [] },
    ],
  },
  {
    code: 'C004',
    name: '선케어',
    children: [{ code: 'C004001', name: '선크림', children: [] }],
  },
  {
    code: 'C005',
    name: '쉐이빙·그루밍',
    children: [{ code: 'C005001', name: '면도기/날', children: [] }],
  },
  {
    code: 'C006',
    name: '메이크업',
    children: [{ code: 'C006001', name: '베이스메이크업', children: [] }],
  },
];
```

- [ ] **Step 5: 인증 목 핸들러 추가**

`frontend/src/mocks/handlers.ts`의 `handlers` 배열 **맨 앞**에 아래 5개를 추가한다(기존 goods 핸들러는 그대로 뒤에 남긴다):

```ts
export const handlers = [
  /* 인증 목 — mock 모드에서 /main 같은 보호 라우트에 도달하려면 로그인이 성립해야 한다.
     비밀번호를 검증하지 않는다: 목적은 화면 흐름 확인이지 인증 로직 재현이 아니다.
     refresh는 401이 기본값 — 새로고침하면 비로그인으로 시작해 가드가 실제로 동작하는지 보인다.
     세션 복원이 필요한 테스트는 server.use()로 이 핸들러를 덮어쓴다(App.test.tsx가 그렇게 한다). */
  http.post('/api/v1/auth/login', () =>
    HttpResponse.json({
      code: 'OK',
      message: '성공',
      data: { accessToken: 'mock-access-token' },
    }),
  ),

  http.post('/api/v1/auth/signup', () =>
    HttpResponse.json({
      code: 'OK',
      message: '성공',
      data: { id: 1, email: 'mock@beautyboy.dev', nickname: '민수', grade: 'BRONZE' },
    }),
  ),

  http.get('/api/v1/members/me', () =>
    HttpResponse.json({
      code: 'OK',
      message: '성공',
      data: { id: 1, email: 'mock@beautyboy.dev', nickname: '민수', grade: 'BRONZE' },
    }),
  ),

  http.post('/api/v1/auth/refresh', () => new HttpResponse(null, { status: 401 })),

  http.post('/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })),

  http.get('/api/v1/goods', ({ request }) => {
    // ...기존 구현 그대로...
```

- [ ] **Step 6: green 확인**

Run: `npm run test`
Expected: PASS — 전체 green. `goods.test.ts`의 페이지네이션 테스트(총 40건, page=1이 21~40번)는 총합을 유지했으므로 그대로 통과해야 한다. `App.test.tsx`는 두 테스트 모두 `server.use()`로 refresh를 덮어쓰므로 영향받지 않는다.

실패하면 총합이 40이 아닌 것이다 — `CATEGORY_PLAN`의 count 합을 다시 세라(3+3+2+3+3+3+3+3+3+5+3+2+2+2 = 40).

- [ ] **Step 7: 커밋**

```bash
git add src/mocks/fixtures/goods.ts src/mocks/handlers.ts src/api/goods.test.ts
git commit -m "fix(mock): fixture 카테고리를 실 시드 코드에 정렬 + 인증 목 핸들러 추가

루틴 매핑(C002/C001001/C001002/C001003/C004001)이 mock 모드에서 빈 섹션이
되지 않도록 fixture를 V12 시드 코드로 교체하고, /main 도달에 필요한
login·me·refresh·logout·signup 목을 추가한다."
```

---

## Task 2: 루틴 5단계 상수

**근거:** 구성 사양 2장 — "루틴 순서를 카테고리 트리로 표현하지 않는다. 카테고리는 '무엇인가'(분류), 루틴은 '언제 바르는가'(순서)로 축이 다르다. 메인은 매핑을 **명시적 상수**로 들고 굴린다."

**Files:**
- Create: `frontend/src/features/routine/steps.ts`
- Test: `frontend/src/features/routine/steps.test.ts`

**Interfaces:**
- Produces: `RoutineStep` 타입 — `{ id: string; order: number; label: string; categoryCode: string; copy: string; image: string }`
- Produces: `ROUTINE_STEPS: readonly RoutineStep[]` (5개)
- Produces: `ROUTINE_SECTION_SIZE = 4` — 섹션당 상품 개수. T5·T6이 소비한다.
- `id` 값은 앵커 대상이자 T6 네비의 `href="#..."` 타깃이므로 고정이다: `cleansing` · `toner` · `serum` · `cream` · `suncare`.

- [ ] **Step 1: 실패 테스트 작성** — `frontend/src/features/routine/steps.test.ts`

```ts
import { describe, expect, it } from 'vitest';
import { ROUTINE_SECTION_SIZE, ROUTINE_STEPS } from './steps';

describe('ROUTINE_STEPS — 루틴 매핑 상수', () => {
  it('구성 사양 2장의 5단계를 순서대로 담는다', () => {
    expect(ROUTINE_STEPS.map((step) => step.label)).toEqual([
      '클렌징',
      '토너/스킨',
      '에센스/세럼',
      '로션/크림',
      '선크림',
    ]);
  });

  it('categoryCode가 실 시드(V12) 코드와 정확히 일치한다', () => {
    expect(ROUTINE_STEPS.map((step) => step.categoryCode)).toEqual([
      'C002',
      'C001001',
      'C001002',
      'C001003',
      'C004001',
    ]);
  });

  it('order는 1부터 5까지 빈틈없이 증가한다', () => {
    expect(ROUTINE_STEPS.map((step) => step.order)).toEqual([1, 2, 3, 4, 5]);
  });

  it('id는 앵커로 쓰이므로 서로 겹치지 않는다', () => {
    const ids = ROUTINE_STEPS.map((step) => step.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('모든 단계가 카피와 레포 내부 이미지 경로를 갖는다', () => {
    for (const step of ROUTINE_STEPS) {
      expect(step.copy.length, `${step.label}의 카피`).toBeGreaterThan(0);
      // 외부 URL 직접 참조 금지 — public/ 아래 절대경로여야 한다.
      expect(step.image, `${step.label}의 이미지`).toMatch(/^\/images\/routine\//);
    }
  });

  it('섹션당 상품 개수는 4개다(한 줄 + 더보기)', () => {
    expect(ROUTINE_SECTION_SIZE).toBe(4);
  });
});
```

- [ ] **Step 2: 실패 확인**

Run: `npm run test -- steps`
Expected: FAIL — `Failed to resolve import "./steps"`

- [ ] **Step 3: 구현** — `frontend/src/features/routine/steps.ts`

```ts
/**
 * 스킨케어 루틴 5단계 ↔ 카테고리 매핑.
 *
 * 이 상수가 매핑의 유일한 진실이다. 카테고리 트리로 순서를 표현하지 않는다 —
 * 카테고리는 "무엇인가"(분류), 루틴은 "언제 바르는가"(순서)로 축이 다르고,
 * 하나로 합치면 둘 다 망가진다. (docs/plans/2026-07-23-landing-main-composition.md 2장)
 *
 * 단계 깊이가 섞이는 것은 의도적이다: 클렌징은 대분류 하나가 곧 한 단계지만,
 * 스킨케어(C001)는 대분류 하나 안에 3단계가 들어 있다.
 *
 * CLAUDE.md "돈과 재고는 서버, 취향은 클라이언트"에 해당하므로 프론트 상수로 시작하고,
 * 필요해지면 Wave 3의 routine 도메인으로 옮긴다.
 */
export interface RoutineStep {
  /** 앵커 id이자 React key. 네비의 href="#{id}" 타깃이므로 바꾸면 네비가 깨진다. */
  id: string;
  /** 화면에 "STEP 01"로 표기되는 1-based 순번. */
  order: number;
  label: string;
  /** 실 시드(backend V12__seed_catalog.sql) 코드. 접두사 필터로 하위까지 포함된다. */
  categoryCode: string;
  /** 이 단계가 왜 필요한지 한 줄로. 타겟이 "뭘 사야 할지 모르는 남성"이라 순서 자체가 교육이 된다. */
  copy: string;
  /** public/ 기준 절대경로. 외부 URL을 직접 참조하지 않는다(오프라인·CI에서 깨진다). */
  image: string;
}

export const ROUTINE_STEPS: readonly RoutineStep[] = [
  {
    id: 'cleansing',
    order: 1,
    label: '클렌징',
    categoryCode: 'C002',
    copy: '하루 동안 쌓인 피지와 먼지를 씻어냅니다. 무엇을 바르든, 비우는 것이 먼저입니다.',
    image: '/images/routine/01-cleansing.svg',
  },
  {
    id: 'toner',
    order: 2,
    label: '토너/스킨',
    categoryCode: 'C001001',
    copy: '세안 직후 흐트러진 피부 결을 정돈하고, 다음 단계가 잘 스며들 바탕을 만듭니다.',
    image: '/images/routine/02-toner.svg',
  },
  {
    id: 'serum',
    order: 3,
    label: '에센스/세럼',
    categoryCode: 'C001002',
    copy: '고민을 정면으로 겨냥하는 단계입니다. 보습·미백·탄력 중 지금 필요한 하나를 고르세요.',
    image: '/images/routine/03-serum.svg',
  },
  {
    id: 'cream',
    order: 4,
    label: '로션/크림',
    categoryCode: 'C001003',
    copy: '앞 단계에서 채운 수분이 날아가지 않게 덮어 잠급니다. 유분감은 취향껏 조절하세요.',
    image: '/images/routine/04-cream.svg',
  },
  {
    id: 'suncare',
    order: 5,
    label: '선크림',
    categoryCode: 'C004001',
    copy: '아침 루틴의 마지막. 자외선 차단을 건너뛰면 앞의 네 단계가 하는 일이 절반으로 줄어듭니다.',
    image: '/images/routine/05-suncare.svg',
  },
];

/** 섹션당 노출 상품 수. 한 줄(데스크톱 4칼럼)만 보여주고 나머지는 더보기로 넘긴다. */
export const ROUTINE_SECTION_SIZE = 4;
```

- [ ] **Step 4: green 확인**

Run: `npm run test -- steps`
Expected: PASS (6 passed)

- [ ] **Step 5: 커밋**

```bash
git add src/features/routine/steps.ts src/features/routine/steps.test.ts
git commit -m "feat(front): 루틴 5단계 ↔ 카테고리 매핑 상수"
```

---

## Task 3: RequireAuth 가드 + `/main`·`/goods` 라우트 + 로그인 후 이동지 변경

**근거:** 구성 사양 2장 "위치·라우팅" — 루틴 메인은 랜딩이 아니라 로그인 이후의 `/main`이며, 비로그인 접근은 `/login`으로 돌린다.

**주의(설계상 중요):** `authStore.isBootstrapping`이 `true`인 동안에는 리다이렉트하면 안 된다. 앱 부트스트랩(`App.tsx`의 `/auth/refresh`)이 끝나기 전에 판정하면, 새로고침할 때마다 로그인한 사용자도 `/login`으로 튕긴다.

**Files:**
- Create: `frontend/src/components/auth/RequireAuth.tsx`
- Test: `frontend/src/components/auth/RequireAuth.test.tsx`
- Modify: `frontend/src/router.tsx`
- Modify: `frontend/src/pages/Login.tsx` (33행 `navigate('/')` 한 줄)
- Test: `frontend/src/pages/Login.redirect.test.tsx`

**Interfaces:**
- Consumes: `useAuthStore`의 `accessToken`, `isBootstrapping` (`src/stores/authStore.ts`).
- Produces: `<RequireAuth>{children}</RequireAuth>` — 라우트 `element`를 감싸는 형태. T6·T7의 페이지가 이 안에 들어간다.
- Produces: 라우트 경로 `/main`, `/goods`. T5의 더보기 링크와 T3의 로그인 리다이렉트가 이 문자열에 의존한다.

- [ ] **Step 1: 실패 테스트 작성** — `frontend/src/components/auth/RequireAuth.test.tsx`

```tsx
import { afterEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './RequireAuth';
import { useAuthStore } from '../../stores/authStore';

function renderGuarded() {
  return render(
    <MemoryRouter initialEntries={['/main']}>
      <Routes>
        <Route
          path="/main"
          element={
            <RequireAuth>
              <div>MAIN_MARKER</div>
            </RequireAuth>
          }
        />
        <Route path="/login" element={<div>LOGIN_MARKER</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('RequireAuth — 보호 라우트 가드', () => {
  afterEach(() => {
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
  });

  it('부트스트랩이 끝난 비로그인 상태면 /login으로 보낸다', () => {
    useAuthStore.setState({ accessToken: null, isBootstrapping: false });

    renderGuarded();

    expect(screen.getByText('LOGIN_MARKER')).toBeInTheDocument();
    expect(screen.queryByText('MAIN_MARKER')).toBeNull();
  });

  it('로그인 상태면 자식을 그대로 렌더한다', () => {
    useAuthStore.setState({ accessToken: 'token', isBootstrapping: false });

    renderGuarded();

    expect(screen.getByText('MAIN_MARKER')).toBeInTheDocument();
  });

  it('부트스트랩 중에는 판정을 미루고 /login으로 보내지 않는다(새로고침 회귀 방지)', () => {
    useAuthStore.setState({ accessToken: null, isBootstrapping: true });

    renderGuarded();

    expect(screen.queryByText('LOGIN_MARKER')).toBeNull();
    expect(screen.queryByText('MAIN_MARKER')).toBeNull();
    // 대기 중임을 스크린리더에도 알린다.
    expect(screen.getByRole('status')).toHaveTextContent('로그인 상태를 확인하는 중입니다');
  });
});
```

- [ ] **Step 2: 실패 확인**

Run: `npm run test -- RequireAuth`
Expected: FAIL — `Failed to resolve import "./RequireAuth"`

- [ ] **Step 3: 구현** — `frontend/src/components/auth/RequireAuth.tsx`

```tsx
import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';

interface RequireAuthProps {
  children: ReactNode;
}

/**
 * 로그인이 필요한 라우트를 감싸는 가드.
 *
 * isBootstrapping 동안에는 절대 리다이렉트하지 않는다 — App이 /auth/refresh로 세션을
 * 복원하기 전에 판정하면, 로그인한 사용자도 새로고침할 때마다 /login으로 튕긴다.
 * 그동안은 빈 화면 대신 대기 안내를 둔다(DESIGN.md UX 계약: 빈 화면을 흰 여백으로 방치하지 않는다).
 */
export function RequireAuth({ children }: RequireAuthProps) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const isBootstrapping = useAuthStore((state) => state.isBootstrapping);

  if (isBootstrapping) {
    return (
      <p role="status" className="bb-auth-pending">
        로그인 상태를 확인하는 중입니다…
      </p>
    );
  }

  if (!accessToken) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}
```

`replace`를 쓰는 이유: 뒤로가기로 다시 보호 라우트에 들어와 무한 왕복하는 것을 막는다.

- [ ] **Step 4: green 확인**

Run: `npm run test -- RequireAuth`
Expected: PASS (3 passed)

- [ ] **Step 5: 로그인 리다이렉트 실패 테스트 작성** — `frontend/src/pages/Login.redirect.test.tsx`

```tsx
import { afterEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { Login } from './Login';
import { useAuthStore } from '../stores/authStore';

describe('Login — 성공 후 이동지', () => {
  afterEach(() => {
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
  });

  it('로그인에 성공하면 랜딩이 아니라 /main으로 간다', async () => {
    render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/main" element={<div>MAIN_MARKER</div>} />
          <Route path="/" element={<div>LANDING_MARKER</div>} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByLabelText('이메일'), {
      target: { value: 'mock@beautyboy.dev' },
    });
    fireEvent.change(screen.getByLabelText('비밀번호'), {
      target: { value: 'password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => expect(screen.getByText('MAIN_MARKER')).toBeInTheDocument());
    expect(screen.queryByText('LANDING_MARKER')).toBeNull();
  });
});
```

이 테스트는 T1에서 추가한 `POST /auth/login`·`GET /members/me` 목 핸들러에 의존한다(`test/setup.ts`가 `onUnhandledRequest: 'error'`라 핸들러가 없으면 즉시 실패한다).

- [ ] **Step 6: 실패 확인**

Run: `npm run test -- Login.redirect`
Expected: FAIL — `LANDING_MARKER`가 렌더되고 `MAIN_MARKER`를 찾지 못함.

- [ ] **Step 7: 구현** — `frontend/src/pages/Login.tsx` 33행 한 줄 교체

```tsx
      navigate('/main');
```

같은 파일 상단 주석(10~14행)에 이동지 변경을 반영한다:

```tsx
/**
 * 로그인 페이지.
 * 로그인 응답에는 닉네임이 없으므로(accessToken만) 성공 직후 `/members/me`를 호출해
 * Header에 표시할 회원 정보를 스토어에 채운다.
 * 성공 후에는 랜딩(/)이 아니라 루틴 메인(/main)으로 보낸다 — 랜딩은 비로그인 유입 화면이다.
 */
```

- [ ] **Step 8: 라우트 등록** — `frontend/src/router.tsx` 전체를 아래로 교체

```tsx
import { createBrowserRouter } from 'react-router-dom';
import { Layout } from './components/layout/Layout';
import { RequireAuth } from './components/auth/RequireAuth';
import { Home } from './pages/Home';
import { Login } from './pages/Login';
import { Signup } from './pages/Signup';
import { Showcase } from './pages/dev/Showcase';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <Home /> },
      { path: 'login', element: <Login /> },
      { path: 'signup', element: <Signup /> },
    ],
  },
  // 헤더/푸터 없이 토큰·프리미티브·카드 상태만 대조하는 dev 전용 화면. 상용 라우트가 아니다.
  { path: 'dev/components', element: <Showcase /> },
]);
```

> **주의:** 이 스텝에서는 `RequireAuth` import만 추가하고 라우트는 아직 붙이지 않는다 — `/main`·`/goods` 페이지 컴포넌트는 T6·T7에서 만들어진다. **T6·T7이 각자 자기 라우트를 이 파일에 등록한다.** 지금 등록하면 없는 모듈을 import해 빌드가 깨진다.
>
> 즉 이 스텝의 실제 변경은 `import { RequireAuth } ...` 한 줄뿐이다. oxlint가 미사용 import를 지적하면 이 스텝을 건너뛰고 **T6에서 import와 라우트를 함께 추가**한다.

Run: `npm run lint`
Expected: 통과. 미사용 import 경고가 뜨면 위 지시대로 `router.tsx` 변경을 되돌리고(`git checkout src/router.tsx`) T6으로 넘긴다.

- [ ] **Step 9: green 확인**

Run: `npm run test`
Expected: PASS — 전체 green. 기존 `Signup.test.tsx`는 `/`로 가는 흐름이라 영향 없다.

- [ ] **Step 10: 커밋**

```bash
git add src/components/auth src/pages/Login.tsx src/pages/Login.redirect.test.tsx src/router.tsx
git commit -m "feat(front): RequireAuth 가드 + 로그인 성공 후 /main 이동

부트스트랩(/auth/refresh) 완료 전에는 판정을 미룬다 — 새로고침 시
로그인한 사용자가 /login으로 튕기는 것을 막기 위함."
```

---

## Task 4: 루틴 단계 이미지 5장

**근거:** 구성 사양 2장 — 이미지 박스는 `colors.surface` 위에 얹히는 단계별 사진. 외부 URL 직접 참조는 오프라인·CI에서 화면이 깨지므로 레포에 반입한다. 실제 브랜드 제품 사진은 금지.

**Files:**
- Create: `frontend/public/images/routine/01-cleansing.svg`
- Create: `frontend/public/images/routine/02-toner.svg`
- Create: `frontend/public/images/routine/03-serum.svg`
- Create: `frontend/public/images/routine/04-cream.svg`
- Create: `frontend/public/images/routine/05-suncare.svg`

**Interfaces:**
- Consumes: T2의 `ROUTINE_STEPS[].image` 경로 5개. 파일명이 정확히 일치해야 한다.
- Produces: T5의 `<img src>`가 참조할 실제 파일.

**형식 결정:** 무료 스톡 사진(JPG)이 이상적이지만 실행 환경에 네트워크가 없을 수 있다. **SVG 그라디언트 플레이스홀더를 먼저 커밋해 레이아웃을 검증 가능하게 만들고**, 사진 교체는 경로만 바꾸면 되는 후속 작업으로 남긴다. SVG는 용량이 작고 결정적이라 CI에서도 안정적이다.

- [ ] **Step 1: 디렉터리 생성**

```bash
mkdir -p public/images/routine
```

- [ ] **Step 2: 5개 SVG 작성**

각 파일은 아래 틀에서 `{GRAD_FROM}` / `{GRAD_TO}` / `{LABEL}` 세 값만 바꾼다. 무채색 계열만 쓰고 시그널 색은 쓰지 않는다(전역 제약).

`public/images/routine/01-cleansing.svg`:

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 1000" width="800" height="1000" role="presentation">
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#d8d8d8"/>
      <stop offset="100%" stop-color="#a9a9a9"/>
    </linearGradient>
  </defs>
  <rect width="800" height="1000" fill="url(#g)"/>
  <text x="56" y="944" font-family="Inter, system-ui, sans-serif" font-size="34" font-weight="500" letter-spacing="6" fill="#ffffff" fill-opacity="0.72">CLEANSING</text>
</svg>
```

나머지 4개는 같은 구조로, 아래 값만 바꿔 만든다:

| 파일 | `stop-color` 시작 | `stop-color` 끝 | `<text>` 내용 |
|---|---|---|---|
| `02-toner.svg` | `#e0e0e0` | `#b4b4b4` | `TONER` |
| `03-serum.svg` | `#cfcfcf` | `#9e9e9e` | `SERUM` |
| `04-cream.svg` | `#dcdcdc` | `#adadad` | `CREAM` |
| `05-suncare.svg` | `#d2d2d2` | `#a2a2a2` | `SUNCARE` |

- [ ] **Step 3: 파일명이 상수와 일치하는지 확인**

```bash
node -e "const fs=require('fs');const src=fs.readFileSync('src/features/routine/steps.ts','utf8');const paths=[...src.matchAll(/'(\/images\/routine\/[^']+)'/g)].map(m=>m[1]);let ok=true;for(const p of paths){const f='public'+p;if(!fs.existsSync(f)){console.error('MISSING',f);ok=false;}else{console.log('OK',f);}}process.exit(ok?0:1)"
```

Expected: `OK public/images/routine/01-cleansing.svg` 외 4줄, 종료코드 0.
`MISSING`이 나오면 파일명이 T2 상수와 어긋난 것이다 — **상수가 아니라 파일명을 고친다**(상수는 T2의 계약이다).

- [ ] **Step 4: 커밋**

```bash
git add public/images/routine
git commit -m "feat(front): 루틴 단계 이미지 5장(무채색 SVG 플레이스홀더)

네트워크 없이도 레이아웃을 검증할 수 있게 결정적 SVG로 먼저 채운다.
실제 스톡 사진으로 교체할 때는 같은 파일명으로 덮거나 steps.ts의 image 경로만 바꾸면 된다."
```

---

## Task 5: RoutineSection 컴포넌트 (B안 — 타이포 ↔ 이미지 박스 대비)

**근거:** 구성 사양 2장 "시각·UX 사양" — pangram식 B안. 큰 타이포 블록(STEP N / 단계명 / 한 줄 카피) ↔ 이미지 박스(`surface` + `rounded.lg`)가 나란히, 홀수/짝수 단계 좌우 교차. 아래에 상품 4개 + 더보기.

**Files:**
- Create: `frontend/src/components/routine/RoutineSection.tsx`
- Create: `frontend/src/components/routine/RoutineSection.css`
- Test: `frontend/src/components/routine/RoutineSection.test.tsx`

**Interfaces:**
- Consumes: `RoutineStep`, `ROUTINE_SECTION_SIZE` (T2) · `fetchGoodsList` (`src/api/goods.ts`) · `GoodsGrid` (`src/components/goods/GoodsGrid.tsx`, props: `items` / `loading` / `skeletonCount` / `onWishToggle`).
- Produces: `<RoutineSection step={step} index={number} />` — `index`는 0-based, 짝수면 타이포 왼쪽·홀수면 오른쪽. T6이 `ROUTINE_STEPS.map((step, index) => ...)`으로 호출한다.
- Produces: `<section id={step.id}>` — T6 앵커 네비의 스크롤 타깃.

- [ ] **Step 1: 실패 테스트 작성** — `frontend/src/components/routine/RoutineSection.test.tsx`

```tsx
import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RoutineSection } from './RoutineSection';
import { ROUTINE_STEPS } from '../../features/routine/steps';

const cleansing = ROUTINE_STEPS[0];

function renderSection(index = 0, step = cleansing) {
  // 테스트마다 새 QueryClient — 캐시가 테스트 간에 새지 않게. retry는 끈다(실패를 즉시 드러낸다).
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <RoutineSection step={step} index={index} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('RoutineSection', () => {
  it('단계 순번·이름·카피를 보여준다', async () => {
    renderSection();

    expect(screen.getByText('STEP 01')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '클렌징' })).toBeInTheDocument();
    expect(screen.getByText(cleansing.copy)).toBeInTheDocument();
  });

  it('앵커 대상이 되도록 섹션에 단계 id를 단다', () => {
    const { container } = renderSection();

    expect(container.querySelector('section')).toHaveAttribute('id', 'cleansing');
  });

  it('단계 이미지를 레포 내부 경로로 렌더한다', () => {
    renderSection();

    const image = screen.getByRole('img', { name: /클렌징/ });
    expect(image).toHaveAttribute('src', '/images/routine/01-cleansing.svg');
  });

  it('해당 카테고리 상품을 4개까지 보여준다', async () => {
    renderSection();

    // C002(클렌징)는 fixture에 8건 있지만 섹션은 4개만 노출한다.
    await waitFor(() => {
      expect(screen.getAllByRole('link', { name: /No\./ })).toHaveLength(4);
    });
  });

  it('더보기가 해당 카테고리 목록으로 연결된다', () => {
    renderSection();

    const more = screen.getByRole('link', { name: '클렌징 전체 보기' });
    expect(more).toHaveAttribute('href', '/goods?category=C002');
  });

  it('짝수 index는 타이포가 왼쪽, 홀수 index는 오른쪽에 온다', () => {
    const { container: even } = renderSection(0);
    expect(even.querySelector('section')).toHaveClass('bb-routine--text-left');

    const { container: odd } = renderSection(1, ROUTINE_STEPS[1]);
    expect(odd.querySelector('section')).toHaveClass('bb-routine--text-right');
  });
});
```

`getAllByRole('link', { name: /No\./ })`는 fixture 상품명이 `... No.N 남성용 데일리 케어` 형태인 것에 의존한다(`src/mocks/fixtures/goods.ts`). `GoodsCard`가 상품 전체를 `<Link>`로 감싸므로 카드 1개 = 링크 1개다.

- [ ] **Step 2: 실패 확인**

Run: `npm run test -- RoutineSection`
Expected: FAIL — `Failed to resolve import "./RoutineSection"`

- [ ] **Step 3: 구현** — `frontend/src/components/routine/RoutineSection.tsx`

```tsx
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { fetchGoodsList } from '../../api/goods';
import { GoodsGrid } from '../goods/GoodsGrid';
import { ROUTINE_SECTION_SIZE, type RoutineStep } from '../../features/routine/steps';
import './RoutineSection.css';

interface RoutineSectionProps {
  step: RoutineStep;
  /** 0-based 순서. 짝수면 타이포가 왼쪽, 홀수면 오른쪽 — 스크롤에 리듬을 준다. */
  index: number;
}

/**
 * 루틴 한 단계를 그리는 섹션.
 *
 * 시각 형식은 pangram 레퍼런스의 "거대 타이포 ↔ 면으로 나뉜 카드" 대비를 따른다:
 * 큰 타이포 블록과 이미지 박스(surface + rounded.lg)가 나란히 서고, 아래에 상품 4개가 붙는다.
 * 섹션이 자기 데이터를 직접 가져오므로(부모가 5개를 모아 내려주지 않는다) 단계를 추가·제거해도
 * Main은 상수 배열만 map하면 된다.
 */
export function RoutineSection({ step, index }: RoutineSectionProps) {
  const { data, isLoading } = useQuery({
    queryKey: ['routine-goods', step.categoryCode],
    queryFn: () =>
      fetchGoodsList({ page: 0, size: ROUTINE_SECTION_SIZE, categoryCode: step.categoryCode }),
  });

  const orderLabel = String(step.order).padStart(2, '0');
  const sideClass = index % 2 === 0 ? 'bb-routine--text-left' : 'bb-routine--text-right';

  return (
    <section id={step.id} className={`bb-routine ${sideClass}`} aria-labelledby={`${step.id}-title`}>
      <div className="bb-routine__head">
        <div className="bb-routine__text">
          <p className="bb-routine__step">STEP {orderLabel}</p>
          <h2 id={`${step.id}-title`} className="bb-routine__title">
            {step.label}
          </h2>
          <p className="bb-routine__copy">{step.copy}</p>
        </div>

        <div className="bb-routine__figure">
          {/* 장식이 아니라 단계를 식별하는 이미지라 alt를 비우지 않는다. */}
          <img className="bb-routine__image" src={step.image} alt={`${step.label} 단계 이미지`} />
        </div>
      </div>

      <GoodsGrid
        items={data?.content ?? []}
        loading={isLoading}
        skeletonCount={ROUTINE_SECTION_SIZE}
      />

      <p className="bb-routine__more">
        <Link
          className="bb-routine__more-link"
          to={`/goods?category=${encodeURIComponent(step.categoryCode)}`}
        >
          {step.label} 전체 보기
          <span aria-hidden="true"> →</span>
        </Link>
      </p>
    </section>
  );
}
```

`aria-hidden`을 화살표에 두는 이유: 스크린리더가 "오른쪽 화살표"를 읽으면 링크 이름이 지저분해지고, 테스트의 접근성 이름(`'클렌징 전체 보기'`)도 흔들린다.

- [ ] **Step 4: 구현** — `frontend/src/components/routine/RoutineSection.css`

```css
/* 루틴 단계 섹션. 색·간격·라운딩은 전부 index.css 토큰을 참조한다(hex 직접 기입 금지). */

.bb-routine {
  padding: var(--space-section-lg) 0;
  border-top: 1px solid var(--color-hairline);
}

.bb-routine:first-of-type {
  border-top: none;
}

/* 타이포 블록 ↔ 이미지 박스. 이미지 쪽을 조금 넓게 잡아 pangram식 비대칭을 만든다. */
.bb-routine__head {
  display: grid;
  grid-template-columns: 1fr 1.2fr;
  gap: var(--space-xxl);
  align-items: end;
  margin-bottom: var(--space-xl);
}

/* 홀수 단계는 좌우를 뒤집는다 — DOM 순서(타이포 먼저)는 유지하고 배치만 바꾼다.
   읽는 순서와 탭 순서가 시각 배치 때문에 어긋나지 않게 하기 위함이다. */
.bb-routine--text-right .bb-routine__text {
  order: 2;
}

.bb-routine--text-right .bb-routine__head {
  grid-template-columns: 1.2fr 1fr;
}

.bb-routine--text-right .bb-routine__figure {
  order: 1;
}

.bb-routine__step {
  font-size: var(--fs-eyebrow);
  font-weight: var(--fw-eyebrow);
  line-height: var(--lh-eyebrow);
  letter-spacing: var(--ls-eyebrow);
  color: var(--color-stone);
  margin-bottom: var(--space-sm);
}

.bb-routine__title {
  font-size: var(--fs-display-sm);
  font-weight: var(--fw-display-sm);
  line-height: var(--lh-display-sm);
  letter-spacing: var(--ls-display-sm);
  color: var(--color-ink);
}

.bb-routine__copy {
  margin-top: var(--space-md);
  max-width: 34em;
  font-size: var(--fs-body-lg);
  line-height: var(--lh-body-lg);
  color: var(--color-graphite);
}

/* 이미지 박스 — DESIGN.md의 카드 면(surface + rounded.lg). 그림자는 쓰지 않는다. */
.bb-routine__figure {
  background: var(--color-surface);
  border-radius: var(--rounded-lg);
  overflow: hidden;
  aspect-ratio: 16 / 9;
}

.bb-routine__image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.bb-routine__more {
  margin-top: var(--space-lg);
  text-align: right;
}

.bb-routine__more-link {
  font-size: var(--fs-link-sm);
  font-weight: var(--fw-link-sm);
  line-height: var(--lh-link-sm);
  color: var(--color-ink);
  text-decoration: none;
  border-bottom: 1px solid var(--color-ink);
  padding-bottom: var(--space-xxs);
}

.bb-routine__more-link:hover {
  color: var(--color-graphite);
  border-bottom-color: var(--color-graphite);
}

/* 태블릿 이하: 타이포 위, 이미지 아래로 세로 스택. 좌우 교차는 의미가 없어지므로 해제한다. */
@media (max-width: 900px) {
  .bb-routine {
    padding: var(--space-section) 0;
  }

  .bb-routine__head,
  .bb-routine--text-right .bb-routine__head {
    grid-template-columns: 1fr;
    gap: var(--space-lg);
  }

  .bb-routine--text-right .bb-routine__text,
  .bb-routine--text-right .bb-routine__figure {
    order: initial;
  }

  .bb-routine__title {
    font-size: var(--fs-heading-md);
    line-height: var(--lh-heading-md);
  }

  .bb-routine__copy {
    font-size: var(--fs-body);
    line-height: var(--lh-body);
  }
}
```

- [ ] **Step 5: green 확인**

Run: `npm run test -- RoutineSection`
Expected: PASS (6 passed)

상품 4개 테스트가 실패하면 T1의 fixture 배분이 적용되지 않은 것이다 — `npm run test -- goods`를 먼저 돌려 T1이 green인지 확인한다.

- [ ] **Step 6: 커밋**

```bash
git add src/components/routine
git commit -m "feat(front): 루틴 단계 섹션 — 타이포↔이미지 박스 좌우 교차 + 상품 4개 + 더보기"
```

---

## Task 6: `/main` 조립 — 인트로 + 앵커 네비(scroll-spy) + 섹션 5개

**근거:** 구성 사양 2장 — 상단에 페이지 제목 + 한 줄 인트로 + sticky 01–05 앵커 네비(현재 섹션 강조). 첫 스크롤 안에 STEP 01이 보이게 인트로는 가볍게(풀 히어로 금지). 5섹션 × 긴 스크롤에 대한 대비가 이 네비다.

**Files:**
- Create: `frontend/src/pages/Main.tsx`
- Create: `frontend/src/pages/Main.css`
- Test: `frontend/src/pages/Main.test.tsx`
- Modify: `frontend/src/router.tsx`

**Interfaces:**
- Consumes: `ROUTINE_STEPS` (T2) · `RoutineSection` (T5) · `RequireAuth` (T3).
- Produces: 라우트 `/main` — `Layout`의 자식으로 등록되어 헤더·푸터·skip-link를 공유한다.

- [ ] **Step 1: 실패 테스트 작성** — `frontend/src/pages/Main.test.tsx`

```tsx
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Main } from './Main';
import { ROUTINE_STEPS } from '../features/routine/steps';

beforeAll(() => {
  // jsdom에는 IntersectionObserver가 없다. scroll-spy는 브라우저 동작이므로
  // 여기서는 "네비가 렌더되고 앵커가 맞는가"만 검증하고 관찰 자체는 무력화한다.
  class MockIntersectionObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
    takeRecords() {
      return [];
    }
    root = null;
    rootMargin = '';
    thresholds = [];
  }
  vi.stubGlobal('IntersectionObserver', MockIntersectionObserver);
});

function renderMain() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Main />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('Main — 루틴 메인 페이지', () => {
  it('루틴 5단계를 상수 순서대로 렌더한다', () => {
    renderMain();

    const headings = screen.getAllByRole('heading', { level: 2 });
    expect(headings.map((h) => h.textContent)).toEqual([
      '클렌징',
      '토너/스킨',
      '에센스/세럼',
      '로션/크림',
      '선크림',
    ]);
  });

  it('앵커 네비가 5단계를 모두 가리킨다', () => {
    renderMain();

    const nav = screen.getByRole('navigation', { name: '루틴 단계 바로가기' });
    for (const step of ROUTINE_STEPS) {
      expect(within(nav).getByRole('link', { name: new RegExp(step.label) })).toHaveAttribute(
        'href',
        `#${step.id}`,
      );
    }
  });

  it('인트로 제목이 페이지의 유일한 h1이다', () => {
    renderMain();

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });
});
```

- [ ] **Step 2: 실패 확인**

Run: `npm run test -- Main`
Expected: FAIL — `Failed to resolve import "./Main"`

- [ ] **Step 3: 구현** — `frontend/src/pages/Main.tsx`

```tsx
import { useEffect, useState } from 'react';
import { RoutineSection } from '../components/routine/RoutineSection';
import { ROUTINE_STEPS } from '../features/routine/steps';
import './Main.css';

/**
 * 루틴 메인 페이지.
 *
 * 핵심 개념: 스크롤을 내리는 순서가 곧 스킨케어 루틴 순서다.
 * 타겟이 "뭘 사야 할지 모르는 남성"이라 순서 자체가 교육이 된다.
 *
 * 5섹션은 스크롤이 길어지므로 sticky 앵커 네비로 현재 위치를 계속 알려주고
 * 원하는 단계로 바로 건너뛸 수 있게 한다.
 */
export function Main() {
  const [activeId, setActiveId] = useState<string>(ROUTINE_STEPS[0].id);

  useEffect(() => {
    // 화면 상단 1/3 지점을 지나는 섹션을 "현재 단계"로 본다.
    // rootMargin 하단을 크게 깎아, 스크롤을 내릴 때 다음 섹션이 위쪽에 닿는 순간 전환되게 한다.
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            setActiveId(entry.target.id);
          }
        }
      },
      { rootMargin: '-30% 0px -60% 0px', threshold: 0 },
    );

    for (const step of ROUTINE_STEPS) {
      const element = document.getElementById(step.id);
      if (element) observer.observe(element);
    }

    return () => observer.disconnect();
  }, []);

  return (
    <div className="bb-main">
      {/* 인트로는 가볍게 — 풀 히어로를 두면 첫 스크롤에서 STEP 01이 안 보인다. */}
      <header className="bb-main__intro">
        <p className="bb-main__eyebrow">DAILY ROUTINE</p>
        <h1 className="bb-main__title">순서대로 따라오면 됩니다</h1>
        <p className="bb-main__lede">
          씻고, 정돈하고, 채우고, 덮고, 막는 다섯 단계. 아래로 내리는 순서가 그대로 루틴 순서입니다.
        </p>

        <nav className="bb-main__nav" aria-label="루틴 단계 바로가기">
          <ol className="bb-main__nav-list">
            {ROUTINE_STEPS.map((step) => (
              <li key={step.id}>
                <a
                  className={`bb-main__nav-link${
                    activeId === step.id ? ' bb-main__nav-link--active' : ''
                  }`}
                  href={`#${step.id}`}
                  aria-current={activeId === step.id ? 'true' : undefined}
                >
                  <span className="bb-main__nav-order">
                    {String(step.order).padStart(2, '0')}
                  </span>
                  <span className="bb-main__nav-label">{step.label}</span>
                </a>
              </li>
            ))}
          </ol>
        </nav>
      </header>

      {ROUTINE_STEPS.map((step, index) => (
        <RoutineSection key={step.id} step={step} index={index} />
      ))}
    </div>
  );
}
```

현재 단계를 색만으로 표시하지 않는다(`aria-current`가 함께 나가고, CSS에서 밑줄도 같이 준다) — 전역 제약 "색 단독으로 의미를 전달하지 않는다".

- [ ] **Step 4: 구현** — `frontend/src/pages/Main.css`

```css
.bb-main {
  max-width: var(--landing-column);
  margin: 0 auto;
  padding: 0 var(--space-lg);
}

.bb-main__intro {
  padding-top: var(--space-section);
}

.bb-main__eyebrow {
  font-size: var(--fs-eyebrow);
  font-weight: var(--fw-eyebrow);
  line-height: var(--lh-eyebrow);
  letter-spacing: var(--ls-eyebrow);
  color: var(--color-stone);
}

.bb-main__title {
  margin-top: var(--space-sm);
  font-size: var(--fs-heading-md);
  font-weight: var(--fw-heading-md);
  line-height: var(--lh-heading-md);
  letter-spacing: var(--ls-heading-md);
  color: var(--color-ink);
}

.bb-main__lede {
  margin-top: var(--space-sm);
  max-width: 44em;
  font-size: var(--fs-body-lg);
  line-height: var(--lh-body-lg);
  color: var(--color-graphite);
}

/* 앵커 네비 — 5섹션 긴 스크롤의 현재 위치 표시이자 지름길.
   sticky top은 헤더 높이만큼 띄운다(DESIGN.md UX 계약의 sticky 오프셋 규칙). */
.bb-main__nav {
  position: sticky;
  top: 0;
  z-index: 10;
  margin-top: var(--space-xl);
  padding: var(--space-sm) 0;
  background: var(--color-canvas);
  border-bottom: 1px solid var(--color-hairline);
}

.bb-main__nav-list {
  display: flex;
  gap: var(--space-lg);
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-x: auto;
}

.bb-main__nav-link {
  display: flex;
  align-items: baseline;
  gap: var(--space-xs);
  padding-bottom: var(--space-xxs);
  white-space: nowrap;
  text-decoration: none;
  color: var(--color-stone);
  border-bottom: 1px solid transparent;
}

/* 현재 단계는 색만으로 표시하지 않는다 — 밑줄과 aria-current를 함께 준다. */
.bb-main__nav-link--active {
  color: var(--color-ink);
  border-bottom-color: var(--color-ink);
}

.bb-main__nav-link:hover {
  color: var(--color-ink);
}

.bb-main__nav-order {
  font-size: var(--fs-micro-caps);
  font-weight: var(--fw-micro-caps);
  line-height: var(--lh-micro-caps);
  letter-spacing: var(--ls-micro-caps);
}

.bb-main__nav-label {
  font-size: var(--fs-body);
  line-height: var(--lh-body);
}

/* 앵커로 점프할 때 sticky 네비 아래로 섹션이 숨지 않게 여유를 준다. */
.bb-routine {
  scroll-margin-top: var(--space-section);
}

html {
  scroll-behavior: smooth;
}

@media (prefers-reduced-motion: reduce) {
  html {
    scroll-behavior: auto;
  }
}

@media (max-width: 900px) {
  .bb-main {
    padding: 0 var(--space-md);
  }

  .bb-main__intro {
    padding-top: var(--space-xl);
  }

  .bb-main__nav-list {
    gap: var(--space-md);
  }
}
```

> `html { scroll-behavior }`가 전역 선택자라 걸리면(공유 계약 침범으로 판단되면) 이 두 블록을 `Main.css`에서 빼고 **보고**한다. `index.css`에 이미 정의돼 있는지 먼저 확인: `grep -n "scroll-behavior" src/index.css` — 있으면 여기서 중복 정의하지 말고 그대로 둔다.

- [ ] **Step 5: 라우트 등록** — `frontend/src/router.tsx`

`Layout`의 `children` 배열에 아래 항목을 `signup` 다음에 추가하고, 상단에 import 2줄을 더한다:

```tsx
import { RequireAuth } from './components/auth/RequireAuth';
import { Main } from './pages/Main';
```

```tsx
      {
        path: 'main',
        element: (
          <RequireAuth>
            <Main />
          </RequireAuth>
        ),
      },
```

(T3 Step 8에서 `RequireAuth` import를 이미 넣었다면 중복 추가하지 않는다.)

- [ ] **Step 6: green 확인**

Run: `npm run test -- Main`
Expected: PASS (3 passed)

이어서 Run: `npm run test` · Expected: 전체 green.
그리고 Run: `npm run build` · Expected: 타입 에러 없이 빌드 성공.

- [ ] **Step 7: 커밋**

```bash
git add src/pages/Main.tsx src/pages/Main.css src/pages/Main.test.tsx src/router.tsx
git commit -m "feat(front): 루틴 메인 페이지 /main — 인트로 + sticky 앵커 네비 + 5단계 조립"
```

---

## Task 7: `/goods` 목록 페이지 (더보기 행선지)

**근거:** 구성 사양 2장 "위치·라우팅" — 각 섹션의 "○○ 전체 보기 →"가 갈 곳. 죽은 링크를 만들지 않는다는 UX 계약의 연장이다.

**Files:**
- Create: `frontend/src/pages/GoodsList.tsx`
- Create: `frontend/src/pages/GoodsList.css`
- Test: `frontend/src/pages/GoodsList.test.tsx`
- Modify: `frontend/src/router.tsx`

**Interfaces:**
- Consumes: `fetchGoodsList` · `GoodsGrid` · `ROUTINE_STEPS`(제목 표기용) · `RequireAuth`.
- Produces: 라우트 `/goods` — `?category=<코드>` 쿼리로 필터. T5의 더보기 링크가 이 계약에 의존한다.

- [ ] **Step 1: 실패 테스트 작성** — `frontend/src/pages/GoodsList.test.tsx`

```tsx
import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { GoodsList } from './GoodsList';

function renderList(search: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/goods${search}`]}>
        <GoodsList />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('GoodsList — 카테고리 목록', () => {
  it('루틴 단계 코드면 그 단계 이름을 제목으로 쓴다', async () => {
    renderList('?category=C002');

    expect(await screen.findByRole('heading', { name: '클렌징', level: 1 })).toBeInTheDocument();
  });

  it('category 쿼리로 필터한 결과 전체를 보여준다', async () => {
    renderList('?category=C002');

    // C002는 fixture에 8건(C002001:3 + C002002:3 + C002003:2)
    await waitFor(() => {
      expect(screen.getAllByRole('link', { name: /No\./ })).toHaveLength(8);
    });
  });

  it('category가 없으면 전체 상품을 보여준다', async () => {
    renderList('');

    expect(await screen.findByRole('heading', { name: '전체 상품', level: 1 })).toBeInTheDocument();
  });

  it('결과가 0건이면 빈 상태를 보여준다', async () => {
    renderList('?category=C999');

    expect(await screen.findByText('표시할 상품이 없어요')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 실패 확인**

Run: `npm run test -- GoodsList`
Expected: FAIL — `Failed to resolve import "./GoodsList"`

- [ ] **Step 3: 구현** — `frontend/src/pages/GoodsList.tsx`

```tsx
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { fetchGoodsList } from '../api/goods';
import { GoodsGrid } from '../components/goods/GoodsGrid';
import { ROUTINE_STEPS } from '../features/routine/steps';
import './GoodsList.css';

/** 한 번에 불러오는 최대 건수. 페이지네이션은 목록이 실제로 커지는 웨이브에서 붙인다. */
const PAGE_SIZE = 40;

/**
 * 카테고리 목록 페이지. `/goods?category=C002` 형태로 진입한다.
 *
 * 루틴 섹션의 "○○ 전체 보기"가 갈 곳이다 — 섹션은 4개만 보여주므로
 * 나머지를 볼 경로가 없으면 그 더보기는 죽은 링크가 된다.
 */
export function GoodsList() {
  const [searchParams] = useSearchParams();
  const category = searchParams.get('category');

  const { data, isLoading } = useQuery({
    queryKey: ['goods-list', category],
    queryFn: () =>
      fetchGoodsList({
        page: 0,
        size: PAGE_SIZE,
        ...(category ? { categoryCode: category } : {}),
      }),
  });

  // 루틴 단계 코드로 들어왔으면 그 단계 이름을 그대로 제목에 쓴다.
  // 매핑에 없는 코드는 이름을 지어내지 않는다 — 코드만 부제로 노출한다.
  const matchedStep = ROUTINE_STEPS.find((step) => step.categoryCode === category);
  const title = matchedStep?.label ?? (category ? '카테고리 상품' : '전체 상품');

  return (
    <div className="bb-goods-list">
      <header className="bb-goods-list__head">
        <h1 className="bb-goods-list__title">{title}</h1>
        {!isLoading && data && (
          <p className="bb-goods-list__count">{data.totalElements}개의 상품</p>
        )}
      </header>

      <GoodsGrid items={data?.content ?? []} loading={isLoading} skeletonCount={10} />
    </div>
  );
}
```

- [ ] **Step 4: 구현** — `frontend/src/pages/GoodsList.css`

```css
.bb-goods-list {
  max-width: var(--landing-column);
  margin: 0 auto;
  padding: var(--space-section) var(--space-lg);
}

.bb-goods-list__head {
  margin-bottom: var(--space-xl);
}

.bb-goods-list__title {
  font-size: var(--fs-heading-md);
  font-weight: var(--fw-heading-md);
  line-height: var(--lh-heading-md);
  letter-spacing: var(--ls-heading-md);
  color: var(--color-ink);
}

.bb-goods-list__count {
  margin-top: var(--space-xs);
  font-size: var(--fs-meta);
  line-height: var(--lh-meta);
  color: var(--color-stone);
}

@media (max-width: 900px) {
  .bb-goods-list {
    padding: var(--space-xl) var(--space-md);
  }

  .bb-goods-list__title {
    font-size: var(--fs-heading-sm);
    line-height: var(--lh-heading-sm);
  }
}
```

- [ ] **Step 5: 라우트 등록** — `frontend/src/router.tsx`

상단에 `import { GoodsList } from './pages/GoodsList';`를 추가하고, `Layout`의 `children`에서 `main` 다음에 넣는다:

```tsx
      {
        path: 'goods',
        element: (
          <RequireAuth>
            <GoodsList />
          </RequireAuth>
        ),
      },
```

- [ ] **Step 6: green 확인**

Run: `npm run test -- GoodsList`
Expected: PASS (4 passed)

이어서 Run: `npm run test && npm run build && npm run lint` · Expected: 전부 통과.

- [ ] **Step 7: 커밋**

```bash
git add src/pages/GoodsList.tsx src/pages/GoodsList.css src/pages/GoodsList.test.tsx src/router.tsx
git commit -m "feat(front): /goods 카테고리 목록 페이지 — 루틴 섹션 더보기 행선지"
```

---

## Task 8: 반응형·스크린샷 육안 검증 (완료 게이트)

**근거:** `CLAUDE.md` — "화면을 만지는 태스크는 스크린샷으로 눈으로 확인해야 완료다. 테스트 통과는 이 요구를 대체하지 못한다." + 구성 사양 2장 "반응형은 기본값이다."

**Files:**
- 산출: `frontend/.screenshots/` 아래 이미지 (보고서에 경로 명시)
- Modify(필요 시): T5·T6의 CSS — 육안 판정에서 어긋난 부분만

- [ ] **Step 1: 개발서버 기동**

```bash
cd frontend && VITE_USE_MOCK=true npm run dev
```

- [ ] **Step 2: 로그인해서 `/main`에 도달**

`/login`에서 아무 이메일(`mock@beautyboy.dev`)·아무 비밀번호로 제출하면 T1의 목 핸들러가 200을 주고 `/main`으로 이동한다. 이동하지 않으면 T3 회귀다.

- [ ] **Step 3: 5개 폭에서 캡처**

390 / 768 / 1024 / 1440 / 1920 각각에 대해 아래를 `frontend/.screenshots/` 아래 저장한다:

- `main-<폭>-top.png` — 인트로 + 앵커 네비 + STEP 01 상단
- `main-<폭>-section.png` — 좌우 교차가 보이도록 STEP 02~03 구간
- `goods-<폭>.png` — `/goods?category=C002`

추가로 폭 무관 1장씩:
- `main-guard.png` — 로그아웃(또는 새 시크릿 창) 상태로 `/main` 직접 진입 시 `/login`으로 튕기는 결과
- `main-reduced-motion.png` — OS/브라우저의 `prefers-reduced-motion: reduce`를 켠 상태에서 앵커 클릭 시 스무스 스크롤 없이 즉시 점프하는지

- [ ] **Step 4: 판정**

각 스크린샷을 **직접 열어 보고** 아래를 대조한다. 하나라도 어긋나면 해당 태스크로 돌아가 고친다.

- 가로 스크롤이 0인가 (5개 폭 전부). 특히 390에서 앵커 네비가 가로 스크롤되더라도 **페이지 본문**은 넘치지 않아야 한다.
- 900px 이하에서 타이포 위 / 이미지 아래로 세로 스택되는가.
- 900px 초과에서 STEP 01·03·05는 타이포가 왼쪽, 02·04는 오른쪽인가.
- 흰 캔버스(`#f7f7f7`) + 검정 잉크 + 이미지 박스만 `surface`(`#ebebeb`)인가. 카드 그림자 없음.
- 시그널 색이 배경으로 칠해진 곳이 없는가. 한 뷰포트에 시그널 색 2종 이하인가(할인율 정도만).
- 현재 단계 네비가 색 외에 밑줄로도 구분되는가.
- 한글이 어절 중간에서 끊기지 않는가.
- 5개 섹션 모두 상품이 4개씩 차 있는가 (빈 섹션이 있으면 T1 회귀다).
- 뷰포트 높이가 낮을 때(예: 1440×720) 인트로가 화면을 다 먹지 않고 STEP 01이 첫 스크롤 안에 보이는가.

- [ ] **Step 5: 최종 검증 로그**

```bash
npm run test && npm run build && npm run lint
```

Expected: 전부 통과.

- [ ] **Step 6: 보고**

오케스트레이터 리뷰용 보고서에 아래를 남긴다:

- 스크린샷 파일 경로 전체 목록과 폭별 판정 결과
- `npm run test` / `npm run build` / `npm run lint` 출력
- **알려진 미완 사항 명시**: (1) 상품 카드 클릭 시 `/goods/:goodsNo` 라우트가 없어 404 — 별도 웨이브 몫, (2) 헤더에 `/main` 진입 링크 없음 — 진입 경로는 로그인 성공 하나, (3) 루틴 이미지는 SVG 플레이스홀더 — 실제 스톡 사진 교체 필요.

- [ ] **Step 7: 커밋** (CSS 수정이 있었을 때만)

```bash
git add src/components/routine/RoutineSection.css src/pages/Main.css
git commit -m "fix(front): 육안 검증에서 드러난 반응형 어긋남 보정"
```

---

## 통합 마무리 (오케스트레이터)

- [ ] T1~T8 전 태스크 리뷰 통과(테스트 green + Files 목록 준수 + 스크린샷 확인) 후 `feat/main-routine`을 main에 머지.
- [ ] worktree 정리: `git worktree remove ../BeautyBoy-w-main-routine`
- [ ] `docs/plans/2026-07-23-landing-main-composition.md` 3장 미결 항목 갱신 — #2는 이미 해소 표기됨. 남은 #1(랜딩 폼 라벨 예외)·#3(모바일 그룹 정렬)·#4(폰트)는 이 계획의 범위가 아니므로 그대로 둔다.
- [ ] 로드맵(`docs/plans/2026-07-23-roadmap.md`)에 Wave 3 T2가 앞당겨 완료됐음과 이월 항목(상품 상세 라우트, 헤더 내비, 스톡 사진 교체)을 기록.

---

## Self-Review (계획 대 spec)

**1. Spec 커버리지** — 구성 사양 2장·5장의 각 항목이 태스크로 매핑되는지 확인:

| 사양 항목 | 태스크 |
|---|---|
| `/main` 신설 + RequireAuth 가드 + 로그인 후 이동 변경 | T3 |
| 랜딩(`/`)은 손대지 않음 | 전 태스크 — `Home.tsx`는 어느 Files 목록에도 없음 |
| `/goods?category=` 목록 페이지 신설 | T7 |
| 루틴 5단계 ↔ categoryCode 명시적 상수 | T2 |
| 상단 인트로 + sticky 01–05 앵커 네비 scroll-spy | T6 |
| 타이포 ↔ 이미지 박스 좌우 교차(B안) | T5 |
| 섹션당 상품 4개 + 더보기 | T5 (`ROUTINE_SECTION_SIZE`) |
| 모바일 세로 스택 | T5 CSS `@media (max-width: 900px)` |
| 이미지 레포 반입, 외부 URL 금지 | T4 + T2의 경로 형식 테스트 |
| `GoodsCard`/`GoodsGrid` 재사용 | T5·T7 — 새 카드 컴포넌트 없음 |
| DESIGN.md 토큰만 사용 | 전역 제약 + 실존 확인된 토큰 목록 |
| `prefers-reduced-motion` 존중 | T6 CSS + T8 검증 |
| 반응형 5폭 가로 스크롤 0 | T8 |

**사양에 없었으나 추가한 것:** T1(mock 정렬 + 인증 목). 이건 스코프 확장이 아니라 **막힌 전제를 여는 작업**이다 — 현 fixture로는 5섹션 중 4개가 빈 상태로 렌더되고, 인증 목이 없으면 mock 모드에서 `/main`에 도달할 방법 자체가 없어 T8의 스크린샷 DoD를 만족시킬 수 없다.

**2. 플레이스홀더 스캔** — TBD·"적절히 처리"·"위와 유사" 없음. 모든 코드 스텝이 완전한 코드 블록을 포함한다. T4의 SVG는 4개를 표로 압축했으나 바꿀 값 3개를 정확히 명시했다. T5의 CSS 토큰명은 전부 `index.css` grep으로 실존을 확인한 것이다.

**3. 타입 일관성** — 교차 확인:
- `RoutineStep`의 필드(`id`/`order`/`label`/`categoryCode`/`copy`/`image`)를 T2에서 정의하고 T5(`step.order`·`step.image`·`step.copy`)·T6(`step.id`·`step.order`·`step.label`)·T7(`step.categoryCode`·`step.label`)이 같은 이름으로 소비한다.
- `ROUTINE_SECTION_SIZE`는 T2 정의, T5에서 `size`와 `skeletonCount` 양쪽에 사용.
- `RoutineSection`의 props(`step`, `index`)가 T5 정의와 T6 호출부에서 일치.
- `GoodsGrid`의 props(`items`/`loading`/`skeletonCount`)는 실제 파일에서 확인한 시그니처와 일치.
- 앵커 id는 T2 상수가 단일 진실이고, T5(`<section id>`)·T6(`href="#..."` / `getElementById`)이 모두 그것을 참조한다 — 문자열을 따로 적어둔 곳이 없다.
- 더보기 URL `/goods?category=`는 T5가 생성하고 T7이 `searchParams.get('category')`로 읽는다 — 키 이름 일치.
- T1이 만드는 fixture 건수(C002→8)를 T5 테스트(4개 노출)와 T7 테스트(8개 전체)가 각각 전제한다.

**4. 태스크 경계** — 각 태스크가 독립적으로 테스트 가능하고, 리뷰어가 이웃을 통과시키면서 하나만 거절할 수 있다. T3의 라우트 등록만 T6·T7로 분산되는데(없는 모듈 import 방지), 이는 스텝 안에 명시적으로 지시했다.
