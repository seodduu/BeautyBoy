# 프론트 UX 계약 준수 (라이트 재도색 반영 + 갭 보완) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development(권장) 또는 superpowers:executing-plans 로 태스크 단위 실행. 스텝은 체크박스로 추적한다.
> 실행 모델·모델 배분·공통 규칙은 `CLAUDE.md`, 시각 계약은 루트 `DESIGN.md`(특히 신설 `## UX 계약 (뷰티보이)` 절) 참조. 이 작업의 서브에이전트는 **sonnet** (결제·재고·궁합 예외에 해당 없음).

**Goal:** 이미 라이트 편집디자인으로 재도색된 `feat/front-base`를 main에 반영하고, 오늘 `DESIGN.md`에 추가된 UX 계약 4개 갭(skip-link · 폼 에러 `role=alert` · 입력 `inputmode` · 빈 상태)을 채워 프론트가 갱신된 시각 계약을 완전히 준수하게 한다.

**Architecture:** "재도색"은 `feat/front-base`(커밋 `46e0b31` 외 8개)가 이미 완료했다 — 다크/황동 팔레트를 폐기하고 `DESIGN.md`의 흰 캔버스 + 무채색 사다리 + `signal-*` 5종으로 교체, UI 프리미티브(Button/Badge/Field/Price/Rating/Skeleton)·상품 그리드·MSW mock까지 구축. 따라서 이 계획은 **밑바닥 재작업이 아니라 (0) 그 브랜치를 main에 통합하고 (1) UX 계약 준수 패스**를 얹는다. Phase 0은 오케스트레이터(git)가, Phase 1은 단일 worktree에서 서브에이전트가 수행한다.

**Tech Stack:** React 19, Vite 7, TypeScript, React Router 7, Zustand, TanStack Query 5, MSW, Vitest + @testing-library, oxlint.

## Global Constraints (CLAUDE.md·DESIGN.md에서 발췌 — 모든 태스크에 적용)

- **프론트는 `DESIGN.md`를 따른다.** CSS 토큰은 `--color-*` / `--fs-*` 등 `index.css`에 이미 이식된 변수를 참조한다. **hex를 손으로 옮겨 적지 않는다.** 문서에 없는 색·간격이 필요하면 만들지 말고 `DESIGN.md`에 먼저 추가하고 보고.
- **시그널 색은 배경으로 칠하지 않는다** — 글자·아이콘·1px 테두리로만. 한 뷰포트에 시그널 색 2종까지.
- **색 단독으로 의미를 전달하지 않는다** — 에러/품절/성분경고는 색 + 아이콘/텍스트/취소선을 함께.
- **한글 규칙**: `word-break: keep-all`은 이미 전역 적용됨(건드리지 말 것). 본문 최소 16px.
- **화면을 만지는 태스크는 스크린샷으로 눈으로 확인해야 완료**다. 개발서버를 띄우고 담당 화면을 찍어 보고서에 파일 경로를 남긴다. 테스트 통과는 이 요구를 대체하지 못한다.
- 자기 태스크 Files 목록 밖 파일 수정 금지. `index.css` 토큰 정의·`DESIGN.md`·루트 빌드 설정은 공유 계약 — 안 맞으면 수정 말고 보고.
- 커밋 메시지·주석은 한국어, 태스크 단위 원자적 커밋.

## 파일 구조 (이 계획이 만들거나 고치는 것)

| 파일 | 책임 | 태스크 |
|---|---|---|
| `frontend/src/components/layout/Layout.tsx` + `Layout.css` | skip-link 추가, `<main id="main-content">` | T1 |
| `frontend/src/components/ui/Field.tsx` | 에러 `<p>`에 `role="alert"` (aria-invalid/describedby는 이미 있음) | T2 |
| `frontend/src/components/common/EmptyState.tsx` + `EmptyState.css` (신규) | 재사용 빈 상태 — 안내문 + 선택적 행동 버튼 | T3 |
| `frontend/src/components/goods/GoodsGrid.tsx` | 빈 배열일 때 `EmptyState` 렌더 | T3 |
| `frontend/src/pages/Login.tsx` · `Signup.tsx` | 이메일/전화/비밀번호 필드에 `type`/`inputmode`/`autocomplete` 전달 | T4 |
| `frontend/src/components/goods/GoodsCardSkeleton.css` | shimmer가 `prefers-reduced-motion` 존중하는지 확인·보정 | T5 |

**범위 밖(YAGNI, 후속 웨이브로 연기):** 장바구니·검색은 현재 헤더에 "준비 중" 자리만 있으므로, 장바구니 담김 토스트·검색 자동완성·상태 `aria-live` 라이브리전은 해당 기능이 실제로 붙는 웨이브에서 함께 만든다. 지금 만들면 소비처 없는 죽은 코드가 된다.

---

## Phase 0 — front-base 검토·머지 (오케스트레이터 / main 세션 / 서브에이전트 없음)

> 이 단계는 git 통합이라 사람+오케스트레이터가 main에서 직접 한다. 서브에이전트를 스폰하지 않는다.

- [ ] **0-1. 사전 조건 확인** (사람 몫 2줄)

```
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" log --oneline -1   # 기점: e3d70b5 (DESIGN.md UX 계약 흡수)
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" status              # 깨끗해야 함
```

- [ ] **0-2. front-base 렌더 결과를 눈으로 검토**

front-base worktree에서 개발서버를 띄우고 화면을 확인한다. 라이트 편집디자인이 `DESIGN.md`와 맞는지, Wave 0의 히어로 줄바꿈 깨짐이 해소됐는지 육안 판정.

```bash
cd "/Users/doo._.hyun/Study/Project/BeautyBoy-w1-front/frontend"
npm install
VITE_USE_MOCK=true npm run dev   # 브라우저로 / (홈), /login, /signup, /dev/showcase 확인
```

판정 기준: 흰 캔버스 + 검정 잉크 + `signal-sale` 할인율만 색을 가짐 / 카드 그림자·라운딩 없음 / 히어로 제목이 어절 중간에서 안 끊김. 어긋나면 여기서 중단하고 front-base를 먼저 고친다.

- [ ] **0-3. main에 머지**

main의 프론트는 아직 Wave 0 다크 셸이고 front-base가 분기 후 프론트를 전면 교체했다. main은 그 사이 `DESIGN.md`/문서만 바꿨으므로 프론트 파일 충돌은 없어야 한다.

```bash
cd "/Users/doo._.hyun/Study/Project/Beauty Boy"
git merge --no-ff feat/front-base -m "merge(front): 라이트 편집디자인 재도색 + UI 프리미티브·상품 그리드 반영"
```

충돌이 나면(예상 밖) 중단하고 보고. 머지 후 확인:

```bash
git grep -c "signal-\|--color-canvas" frontend/src/index.css   # >0 이어야 함 (라이트 토큰 반영됨)
grep -c "15181b\|c08552" frontend/src/index.css || echo "다크/황동 잔재 없음(정상)"
```

- [ ] **0-4. main에서 테스트·빌드 green 확인**

```bash
cd "/Users/doo._.hyun/Study/Project/Beauty Boy/frontend" && npm install && npm run test && npm run build
```

Phase 0가 끝나면 main = 라이트 프론트 + 갱신된 `DESIGN.md`. 이제 Phase 1의 worktree는 이 통합된 main에서 딴다(그래야 코드와 갱신된 `DESIGN.md`가 한 기점에 있다).

---

## Phase 1 — UX 계약 준수 패스 (터미널 1개 = worktree 1개 = 브랜치 `feat/front-ux`)

병렬성 분석: 4개 갭은 모두 프론트 단일 도메인이고 파일이 겹치지 않지만 양이 작아 **직렬 1터미널**이 맞다(worktree를 더 쪼갤 이득 없음). 아래 실행 프롬프트를 프로젝트 루트에서 연 새 터미널에 그대로 붙여넣는다.

### 붙여넣기용 실행 프롬프트

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add ../BeautyBoy-w-front-ux -b feat/front-ux
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로(../BeautyBoy-w-front-ux)를 넘겨 세션을 그 안으로 옮겨라.
(EnterWorktree를 name으로 새로 만들지 마라 — origin에서 브랜치를 따 갱신된 DESIGN.md가 없는 worktree가 생긴다. 반드시 위 git worktree add로 로컬 HEAD에서 딴 뒤 path로 진입한다.)
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 ../BeautyBoy-w-front-ux 인지
  - git log --oneline -1 이 머지된 main 기점과 같은지 (front-base 머지 커밋이 조상에 있어야 함)
  - 루트 DESIGN.md에 "## UX 계약 (뷰티보이)" 절이 있는지 (grep으로 확인)
  - frontend/src/components/ui/Field.tsx, components/goods/GoodsGrid.tsx 가 실제로 존재하는지
  - git status가 깨끗한지
확인 뒤: cd frontend && npm install 로 의존성을 깔고, npm run test 가 green인지 먼저 본다.

[2단계 — 실행] docs/plans/2026-07-23-front-ux-contract.md 의 Phase 1 Task 1~5를 순서대로,
각 태스크를 TDD(실패 테스트 → 구현 → green → 커밋)로 실행한다. 화면을 만지는 태스크는
반드시 개발서버를 띄우고 스크린샷을 찍어 직접 본 뒤 파일 경로를 보고서에 남긴다.
각 태스크 종료 시 오케스트레이터의 리뷰(테스트 통과 + 파일 소유권 준수 + 스크린샷 확인)를 받는다.
```

### 사전 조건 확인 (사람 몫 2줄)

```
git -C ../BeautyBoy-w-front-ux log --oneline -1   # 머지된 main과 같은 기점인지
git -C ../BeautyBoy-w-front-ux status             # 깨끗한지
```

---

### Task 1: Skip-link (키보드 사용자용 본문 바로가기)

**근거:** `DESIGN.md` UX 계약 > 접근성 — "skip-link('본문 바로가기')를 헤더 앞에 둔다." 현재 `Layout.tsx`는 skip-link가 없고 `<main>`에 id가 없다.

**Files:**
- Modify: `frontend/src/components/layout/Layout.tsx`
- Modify: `frontend/src/components/layout/Layout.css`
- Test: `frontend/src/components/layout/Layout.test.tsx` (신규)

**Interfaces:**
- Produces: `<main id="main-content">` — 앵커 대상 id는 `main-content`로 고정(다른 태스크가 참조하지 않음).

- [ ] **Step 1: 실패 테스트 작성** — `frontend/src/components/layout/Layout.test.tsx`

```tsx
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Layout } from './Layout';

function renderLayout() {
  render(
    <MemoryRouter>
      <Layout />
    </MemoryRouter>,
  );
}

test('skip-link가 본문 앵커를 가리킨다', () => {
  renderLayout();
  const link = screen.getByRole('link', { name: '본문 바로가기' });
  expect(link).toHaveAttribute('href', '#main-content');
});

test('main 랜드마크에 앵커 id가 있다', () => {
  renderLayout();
  expect(screen.getByRole('main')).toHaveAttribute('id', 'main-content');
});
```

- [ ] **Step 2: 실패 확인** — Run: `npm run test -- Layout` · Expected: FAIL (skip-link/ id 없음)

- [ ] **Step 3: 구현** — `Layout.tsx` 를 아래로 수정

```tsx
import { Outlet } from 'react-router-dom';
import { Header } from './Header';
import { Footer } from './Footer';
import './Layout.css';

export function Layout() {
  return (
    <div className="bb-layout">
      <a className="bb-skip-link" href="#main-content">
        본문 바로가기
      </a>
      <Header />
      <main id="main-content" className="bb-layout__main">
        <Outlet />
      </main>
      <Footer />
    </div>
  );
}
```

`Layout.css` 에 추가 (평소 화면 밖, 포커스 시 좌상단 노출 — 시각 계약: 흰 캔버스 + 검정 잉크, `{rounded.sm}`):

```css
.bb-skip-link {
  position: absolute;
  left: var(--space-sm);
  top: calc(-1 * var(--space-xxl));
  z-index: 100;
  padding: var(--space-xs) var(--space-md);
  background: var(--color-canvas);
  color: var(--color-ink);
  border: 1px solid var(--color-ink);
  border-radius: var(--radius-sm);
  font: var(--fw-button, 600) var(--fs-button, 14px) / 1.43 var(--font-body);
  transition: top 0.15s ease;
}
.bb-skip-link:focus {
  top: var(--space-sm);
}
@media (prefers-reduced-motion: reduce) {
  .bb-skip-link { transition: none; }
}
```

> 주의: `--space-*` / `--radius-*` / `--fs-button` 실제 변수명은 `index.css`에 정의된 것을 grep으로 확인해 그대로 쓴다. 위는 예시 이름 — 다르면 실제 토큰명으로 맞추고, **없는 토큰을 새로 만들지 않는다**(없으면 보고).

- [ ] **Step 4: green 확인** — Run: `npm run test -- Layout` · Expected: PASS

- [ ] **Step 5: 커밋** — `git add frontend/src/components/layout/Layout.tsx frontend/src/components/layout/Layout.css frontend/src/components/layout/Layout.test.tsx && git commit -m "feat(front): 키보드용 skip-link + main 랜드마크 앵커"`

---

### Task 2: 폼 에러 스크린리더 알림 (`role="alert"`)

**근거:** `DESIGN.md` UX 계약 > 접근성/폼 — "폼 에러는 `role=alert`/`aria-live`." `Field.tsx`는 이미 `aria-invalid`·`aria-describedby`·에러를 필드 아래에 렌더하지만, 에러 `<p>`가 라이브 리전이 아니라 스크린리더가 변경을 읽지 않는다.

**Files:**
- Modify: `frontend/src/components/ui/Field.tsx`
- Test: `frontend/src/components/ui/Field.test.tsx` (신규)

- [ ] **Step 1: 실패 테스트 작성** — `frontend/src/components/ui/Field.test.tsx`

```tsx
import { render, screen } from '@testing-library/react';
import { Field } from './Field';

test('에러가 있으면 alert 역할로 노출된다', () => {
  render(
    <Field id="email" label="이메일" value="" onChange={() => {}} error="이메일 형식이 아닙니다" />,
  );
  const alert = screen.getByRole('alert');
  expect(alert).toHaveTextContent('이메일 형식이 아닙니다');
});

test('에러 없으면 alert가 없다', () => {
  render(<Field id="email" label="이메일" value="" onChange={() => {}} />);
  expect(screen.queryByRole('alert')).toBeNull();
});
```

- [ ] **Step 2: 실패 확인** — Run: `npm run test -- Field` · Expected: FAIL (alert role 없음)

- [ ] **Step 3: 구현** — `Field.tsx` 의 에러 `<p>`에 `role="alert"` 추가:

```tsx
      {error && (
        <p id={helperId} className="bb-field__error" role="alert">
          {error}
        </p>
      )}
```

- [ ] **Step 4: green 확인** — Run: `npm run test -- Field` · Expected: PASS

- [ ] **Step 5: 커밋** — `git add frontend/src/components/ui/Field.tsx frontend/src/components/ui/Field.test.tsx && git commit -m "feat(front): 폼 에러를 role=alert로 스크린리더에 알림"`

---

### Task 3: 재사용 빈 상태 컴포넌트 + 상품 그리드 빈 상태

**근거:** `DESIGN.md` UX 계약 > 상태 — "빈 상태는 안내 + 행동을 함께. 빈 화면을 흰 여백으로 방치하지 않는다." `GoodsGrid.tsx`는 로딩·목록 분기만 있고 빈 배열 분기가 없어 결과 0건이면 아무것도 안 그린다.

**Files:**
- Create: `frontend/src/components/common/EmptyState.tsx`
- Create: `frontend/src/components/common/EmptyState.css`
- Modify: `frontend/src/components/goods/GoodsGrid.tsx`
- Test: `frontend/src/components/common/EmptyState.test.tsx` (신규)
- Test: `frontend/src/components/goods/GoodsGrid.test.tsx` (신규)

**Interfaces:**
- Produces: `EmptyState` — `{ title: string; description?: string; action?: { label: string; onClick: () => void } }`. GoodsGrid 및 후속 웨이브의 장바구니·검색 무결과가 재사용.

- [ ] **Step 1: 실패 테스트 작성** — `frontend/src/components/common/EmptyState.test.tsx`

```tsx
import { render, screen } from '@testing-library/react';
import { EmptyState } from './EmptyState';

test('제목과 설명을 보여준다', () => {
  render(<EmptyState title="표시할 상품이 없어요" description="다른 조건으로 찾아보세요" />);
  expect(screen.getByText('표시할 상품이 없어요')).toBeInTheDocument();
  expect(screen.getByText('다른 조건으로 찾아보세요')).toBeInTheDocument();
});

test('action이 있으면 버튼을 렌더한다', () => {
  render(<EmptyState title="비었어요" action={{ label: '상품 둘러보기', onClick: () => {} }} />);
  expect(screen.getByRole('button', { name: '상품 둘러보기' })).toBeInTheDocument();
});
```

그리고 `frontend/src/components/goods/GoodsGrid.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { GoodsGrid } from './GoodsGrid';

test('빈 배열이고 로딩 아니면 빈 상태를 보여준다', () => {
  render(<GoodsGrid items={[]} loading={false} />);
  expect(screen.getByText('표시할 상품이 없어요')).toBeInTheDocument();
});

test('로딩 중이면 빈 상태 대신 스켈레톤을 보여준다', () => {
  render(<GoodsGrid items={[]} loading />);
  expect(screen.queryByText('표시할 상품이 없어요')).toBeNull();
});
```

- [ ] **Step 2: 실패 확인** — Run: `npm run test -- EmptyState GoodsGrid` · Expected: FAIL

- [ ] **Step 3: 구현** — `EmptyState.tsx`:

```tsx
import './EmptyState.css';

interface EmptyStateProps {
  title: string;
  description?: string;
  action?: { label: string; onClick: () => void };
}

/**
 * DESIGN.md UX 계약: 빈 장바구니·찜·검색 무결과·주문 없음이 재사용하는 빈 상태.
 * 안내문(body/graphite) + 선택적 행동(button-ghost). 배경 채움·아이콘 색 없음(무채색).
 */
export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="bb-empty" role="status">
      <p className="bb-empty__title">{title}</p>
      {description && <p className="bb-empty__desc">{description}</p>}
      {action && (
        <button type="button" className="bb-btn bb-btn--ghost" onClick={action.onClick}>
          {action.label}
        </button>
      )}
    </div>
  );
}
```

> `bb-btn--ghost` 클래스는 front-base의 `Button.css`(button-ghost) 실제 클래스명을 grep으로 확인해 재사용한다. 자체 버튼 스타일을 새로 만들지 않는다. 다르면 `Button` 컴포넌트를 import 해서 쓴다.

`EmptyState.css` (무채색, 중앙 정렬, 넉넉한 세로 여백):

```css
.bb-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-section) var(--space-lg);
  text-align: center;
}
.bb-empty__title {
  font: var(--fw-body-strong, 600) var(--fs-body, 16px) / 1.5 var(--font-body);
  color: var(--color-ink);
}
.bb-empty__desc {
  font: 400 var(--fs-body, 16px) / 1.5 var(--font-body);
  color: var(--color-graphite);
}
```

`GoodsGrid.tsx` — 로딩 분기 뒤, 목록 분기 앞에 빈 상태 추가:

```tsx
  if (!loading && items.length === 0) {
    return <EmptyState title="표시할 상품이 없어요" description="다른 조건으로 다시 찾아보세요" />;
  }
```

상단 import 추가: `import { EmptyState } from '../common/EmptyState';`

- [ ] **Step 4: green 확인** — Run: `npm run test -- EmptyState GoodsGrid` · Expected: PASS

- [ ] **Step 5: 커밋** — `git add frontend/src/components/common frontend/src/components/goods/GoodsGrid.tsx frontend/src/components/goods/GoodsGrid.test.tsx && git commit -m "feat(front): 재사용 빈 상태 컴포넌트 + 상품 그리드 빈 상태"`

---

### Task 4: 인증 폼 입력 타입·모바일 키보드·자동완성

**근거:** `DESIGN.md` UX 계약 > 폼 — "입력 타입·키보드를 맞춘다: 이메일·전화·숫자는 `type`과 `inputmode`를 지정하고 `autocomplete`를 채운다." `Field`는 `...rest`로 이 속성들을 이미 통과시키므로(호출부만 넘기면 됨), `Login.tsx`·`Signup.tsx`에서 각 필드에 지정한다.

**Files:**
- Modify: `frontend/src/pages/Login.tsx`
- Modify: `frontend/src/pages/Signup.tsx`
- Test: `frontend/src/pages/Login.inputs.test.tsx` (신규)

**Interfaces:**
- Consumes: `Field`의 `...rest` 통과(이미 `InputHTMLAttributes` 확장). `type`/`inputMode`/`autoComplete`를 prop으로 전달.

- [ ] **Step 1: 실패 테스트 작성** — `frontend/src/pages/Login.inputs.test.tsx`

```tsx
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Login } from './Login';

test('이메일 입력이 email 타입·키보드·자동완성을 갖는다', () => {
  render(
    <MemoryRouter>
      <Login />
    </MemoryRouter>,
  );
  const email = screen.getByLabelText(/이메일/);
  expect(email).toHaveAttribute('type', 'email');
  expect(email).toHaveAttribute('inputmode', 'email');
  expect(email).toHaveAttribute('autocomplete', 'email');
});
```

> 라벨 텍스트(`/이메일/`)와 비밀번호 필드 유무는 `Login.tsx` 실제 구현을 열어 맞춘다. 비밀번호 필드가 있으면 `type=password`·`autocomplete=current-password`(로그인)/`new-password`(가입) 검증도 추가한다.

- [ ] **Step 2: 실패 확인** — Run: `npm run test -- Login.inputs` · Expected: FAIL

- [ ] **Step 3: 구현** — `Login.tsx`·`Signup.tsx`의 해당 `<Field>` 호출에 속성 추가. 예:

```tsx
<Field id="email" label="이메일" value={email} onChange={setEmail}
  type="email" inputMode="email" autoComplete="email" />
<Field id="password" label="비밀번호" value={password} onChange={setPassword}
  type="password" autoComplete="current-password" />
```

Signup의 전화번호 필드가 있으면 `type="tel" inputMode="numeric" autoComplete="tel"`, 비밀번호는 `autoComplete="new-password"`. **필드 구성은 실제 파일을 열어 맞추고, 없는 필드를 새로 만들지 않는다.**

- [ ] **Step 4: green 확인** — Run: `npm run test -- Login.inputs` · Expected: PASS. 이어서 기존 `Signup.test.tsx`도 깨지지 않는지 `npm run test` 전체 확인.

- [ ] **Step 5: 커밋** — `git add frontend/src/pages/Login.tsx frontend/src/pages/Signup.tsx frontend/src/pages/Login.inputs.test.tsx && git commit -m "feat(front): 인증 폼 입력 타입·모바일 키보드·autocomplete 지정"`

---

### Task 5: reduced-motion 확인 + 스크린샷 육안 검증 (완료 게이트)

**근거:** `DESIGN.md` UX 계약 > 접근성 — "`prefers-reduced-motion` 준수: 어떤 트랜지션도 즉시 완료로 축약." + CLAUDE.md "화면 태스크는 스크린샷으로 눈으로 확인해야 완료." 이 태스크는 코드 확인 + 시각 검증을 함께 한다.

**Files:**
- Modify(필요 시): `frontend/src/components/goods/GoodsCardSkeleton.css`
- 산출: 스크린샷 파일 (보고서에 경로 명시)

- [ ] **Step 1: 스켈레톤 shimmer가 reduced-motion을 존중하는지 확인**

`GoodsCardSkeleton.css`(및 shimmer를 쓰는 다른 스켈레톤)에서 애니메이션이 `@media (prefers-reduced-motion: reduce)`로 꺼지는지 grep. 이미 처리돼 있으면(현재 `prefers-reduced-motion` 4곳 존재) 수정 없이 다음 스텝. 안 돼 있으면 추가:

```css
@media (prefers-reduced-motion: reduce) {
  .bb-goods-card-skeleton__shimmer { animation: none; }
}
```

(선택자명은 실제 CSS에 맞춘다.) 변경이 있으면 커밋: `git commit -m "fix(front): 스켈레톤 shimmer가 reduced-motion 존중"`.

- [ ] **Step 2: 개발서버 띄우고 담당 화면 스크린샷**

```bash
cd frontend && VITE_USE_MOCK=true npm run dev
```

브라우저(또는 claude-in-chrome / Playwright)로 아래를 찍어 `frontend/.screenshots/` 아래 저장:
- `/` 홈 — skip-link가 Tab 첫 포커스에서 좌상단에 뜨는지(포커스 상태 캡처), 상품 그리드가 라이트 편집디자인인지
- `/login`, `/signup` — 폼 에러 상태(일부러 잘못 입력) 캡처: 에러가 필드 아래 `signal-danger` 텍스트로 뜨고 배경은 안 바뀌는지
- 빈 상태 — GoodsGrid에 빈 배열이 들어가는 경로(mock을 빈 응답으로 두거나 Showcase에 빈 그리드 추가)로 "표시할 상품이 없어요" 캡처

- [ ] **Step 3: 판정** — 각 스크린샷을 열어 `DESIGN.md`와 대조: 시그널 색이 배경으로 안 칠해짐 / 색 단독 의미전달 없음(에러=색+텍스트) / 카드 그림자·라운딩 없음 / 한글 안 잘림. 어긋나면 해당 태스크로 돌아가 고친다.

- [ ] **Step 4: 보고** — 오케스트레이터 리뷰용으로 스크린샷 파일 경로 목록과 판정 결과를 보고서에 남긴다. 테스트 전체 green(`npm run test`) + 빌드(`npm run build`) 확인 로그 포함.

---

## 통합 마무리 (오케스트레이터)

- [ ] Phase 1 전 태스크 리뷰 통과(테스트 green + 파일 소유권 준수 + 스크린샷 확인) 후 `feat/front-ux`를 main에 머지.
- [ ] worktree 정리: `git worktree remove ../BeautyBoy-w-front-ux`.
- [ ] `DESIGN.md` UX 계약 절과 구현이 일치하는지 최종 대조. 남은 항목(장바구니 토스트·검색 자동완성·상태 aria-live)은 해당 기능 웨이브로 이월됨을 로드맵에 기록.

---

## Self-Review (계획 대 spec)

- **Spec 커버리지**: `DESIGN.md` UX 계약의 4개 갭 → skip-link(T1)·폼 에러 알림(T2)·빈 상태(T3)·입력 타입/키보드(T4)·reduced-motion+시각검증(T5)로 전부 매핑. 재도색 자체는 front-base가 완료(Phase 0에서 통합). 소비처 없는 항목(장바구니/검색 관련)은 명시적으로 범위 밖 처리.
- **플레이스홀더 스캔**: 각 태스크에 실제 테스트·구현 코드 포함. 토큰/클래스명은 "index.css·Button.css에서 grep해 실제 이름으로 맞추라"고 명시(하드코딩 방지) — TBD 아님, 검증 지시.
- **타입 일관성**: `EmptyState` prop 타입(`title`/`description`/`action`)이 T3 정의와 GoodsGrid 사용처에서 일치. `main-content` 앵커 id는 T1에서만 정의·사용.
- **모델 배분**: sonnet(예외 3종 해당 없음) — 계획 헤더에 명시.
