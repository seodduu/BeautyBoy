# 컨셉 세트 A/B/C 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/main` 히어로 아래에 프로필 파생 컨셉 세트 탭 A/B/C를 추가하고, 탭 선택이 기존 5단계 루틴 조합기를 그 컨셉 단독으로 재조합하게 한다.

**Architecture:** 순수 함수 `deriveSetConcepts`(파생 사다리) + `useComposer`에 `concernOverride` 파라미터 1개 + 탭 UI. `composeStep`·`profile.ts`·백엔드는 무수정. 스펙: `docs/superpowers/specs/2026-07-28-concept-sets-design.md`

**Tech Stack:** React 18 + TypeScript + vitest/@testing-library + msw (기존 그대로, 신규 의존성 없음)

## Global Constraints

- 프론트만 수정. 백엔드·Flyway·공통 타입·루트 빌드 설정 접근 금지 (CLAUDE.md 공유 계약).
- `composeStep`(composer.ts)·`profile.ts`·`steps.ts`는 **읽기 전용** — 테스트 추가는 허용, 구현 수정 금지.
- CSS는 `DESIGN.md` 토큰 참조만. **hex 손 복사 금지**, 액센트(signal-*) 배경 금지, `word-break: keep-all`. 문서에 없는 토큰 신설 금지 — 필요하면 DESIGN.md에 먼저 추가(Task 3이 그 절차다).
- 개별 테스트: `cd frontend && npx vitest run <파일경로>`. 전체 판정: `cd frontend && npm test` (`npx vitest run` 단독은 e2e 스펙을 집어 항상 1 failed — 거짓 적신호, 메모리 참조).
- 타입 게이트: `cd frontend && npx tsc --noEmit -p tsconfig.app.json` (`-p` 없이는 0파일 검사 거짓 녹색).
- 커밋 메시지 한국어, 태스크 단위 원자 커밋.

## 실행 모델 · 터미널 분할

프론트 단일 도메인 + 태스크 간 순차 의존(1→2→4, 3→4) → **터미널 1개 / worktree 1개 / 브랜치 `feature/concept-sets`**. 분할 없음.

- 오케스트레이터(이 계획을 받는 세션): **opus** — 태스크마다 서브에이전트 스폰, 사이사이 리뷰.
- 태스크 실행 서브에이전트: **sonnet** — 모델 배분 예외 3종(결제·재고 차감·궁합 규칙 엔진) 미해당. 궁합 엔진은 소비만 한다.

### 사람이 확인할 사전 조건 (프로젝트 루트에서 2줄)

```
git log --oneline -1   # 이 계획서 커밋이 보여야 한다
git status             # 깨끗해야 한다
```

### 터미널 실행 프롬프트 (그대로 붙여넣기)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add .claude/worktrees/뷰티보이-컨셉세트 -b feature/concept-sets
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 해당 worktree인지
  - git log --oneline -1 이 루트에서 본 기점 커밋과 같은지
  - docs/superpowers/plans/2026-07-28-concept-sets.md 와 DESIGN.md 가 실제로 존재하는지
  - git status가 깨끗한지

[2단계 — 실행]
superpowers:subagent-driven-development 스킬로
docs/superpowers/plans/2026-07-28-concept-sets.md 를 태스크 단위로 실행해라.
태스크 실행 서브에이전트는 model=sonnet 으로 스폰하고, 너는 오케스트레이터로서
태스크 사이마다 테스트 통과·Files 목록 준수를 리뷰해라.
Task 5의 스크린샷은 파일 경로를 보고서에 남기고, 너도 직접 열어 보고 판정해라.
```

---

### Task 1: 컨셉 파생 사다리 `setConcepts.ts`

**Files:**
- Create: `frontend/src/features/affinity/setConcepts.ts`
- Test: `frontend/src/features/affinity/setConcepts.test.ts`

**Interfaces:**
- Consumes: `profile.ts`의 `effectiveConcerns`, `SKIN_TYPE_CONCERNS`, `DerivedConcern` / `api/auth`의 `SkinType` / (테스트에서만) `SkinProfileFields.tsx`의 `CONCERNS`
- Produces: `SET_COUNT: 3`, `FALLBACK_CONCEPTS: DerivedConcern[]`, `interface SetConcept { slug: DerivedConcern; label: string; personalized: boolean }`, `deriveSetConcepts(concerns: string[], skinType: SkinType | null): SetConcept[]` — Task 4가 이 시그니처 그대로 사용

- [ ] **Step 1: 실패하는 테스트 작성**

`setConcepts.test.ts` 전량 (스펙 §9 표가 원본):

```ts
import { describe, expect, it } from 'vitest';
import type { SkinType } from '../../api/auth';
import { CONCERNS } from '../../components/skin-profile/SkinProfileFields';
import { FALLBACK_CONCEPTS, SET_COUNT, deriveSetConcepts } from './setConcepts';

describe('deriveSetConcepts — 파생 사다리(스펙 §4)', () => {
  it('고민 3개 이상이면 선택 순서대로 앞 3개, 전부 개인화', () => {
    const result = deriveSetConcepts(['bright', 'soothe', 'exfoliate', 'pore'], 'OILY');
    expect(result.map((c) => c.slug)).toEqual(['bright', 'soothe', 'exfoliate']);
    expect(result.every((c) => c.personalized)).toBe(true);
  });

  it('고민 1개(pore) + OILY — 파생 sebum 보충, 파생 pore는 중복 소거, 3번째는 폴백 trouble', () => {
    const result = deriveSetConcepts(['pore'], 'OILY');
    expect(result.map((c) => c.slug)).toEqual(['pore', 'sebum', 'trouble']);
    expect(result.map((c) => c.personalized)).toEqual([true, true, false]);
  });

  it('고민 0개 + DRY — 피부타입 파생(moisture·barrier) 뒤 폴백 pore (폴백 moisture는 중복 소거)', () => {
    const result = deriveSetConcepts([], 'DRY');
    expect(result.map((c) => c.slug)).toEqual(['moisture', 'barrier', 'pore']);
    expect(result.map((c) => c.personalized)).toEqual([true, true, false]);
  });

  it('비로그인([], null)이면 폴백 3종 그대로, 전부 비개인화', () => {
    const result = deriveSetConcepts([], null);
    expect(result.map((c) => c.slug)).toEqual(FALLBACK_CONCEPTS);
    expect(result.every((c) => !c.personalized)).toBe(true);
  });

  it('SENSITIVE 무고민 — 파생 전용 gentle이 포함되고 라벨이 비어 있지 않다', () => {
    const result = deriveSetConcepts([], 'SENSITIVE');
    const gentle = result.find((c) => c.slug === 'gentle');
    expect(gentle).toBeDefined();
    expect(gentle!.label.length).toBeGreaterThan(0);
  });

  it('사용감 슬러그(fresh·dewy·matte)는 컨셉이 되지 않는다', () => {
    const result = deriveSetConcepts(['fresh', 'dewy', 'matte'], null);
    expect(result.map((c) => c.slug)).toEqual(FALLBACK_CONCEPTS);
  });

  it('모든 경우 정확히 SET_COUNT개, 슬러그 중복 없음', () => {
    const cases: [string[], SkinType | null][] = [
      [[], null],
      [['pore'], 'OILY'],
      [['moisture', 'barrier'], 'DRY'],
      [['pore', 'trouble', 'moisture', 'bright'], 'COMBINATION'],
      [[], 'SENSITIVE'],
    ];
    for (const [concerns, skinType] of cases) {
      const slugs = deriveSetConcepts(concerns, skinType).map((c) => c.slug);
      expect(slugs).toHaveLength(SET_COUNT);
      expect(new Set(slugs).size).toBe(SET_COUNT);
    }
  });

  it('고민 9종 라벨이 프로필 화면(CONCERNS 상수)과 동일 문구다', () => {
    for (const { value, label } of CONCERNS) {
      const result = deriveSetConcepts([value], null);
      expect(result[0].slug).toBe(value);
      expect(result[0].label).toBe(label);
    }
  });
});
```

- [ ] **Step 2: 실패 확인**

Run: `cd frontend && npx vitest run src/features/affinity/setConcepts.test.ts`
Expected: FAIL — `setConcepts.ts` 모듈 없음.

- [ ] **Step 3: 구현**

파생 사다리는 판단 로직이므로 전량 명세(스펙 §4). 파일 상단 주석에 사다리 3단과 근거를 남긴다.

```ts
import type { SkinType } from '../../api/auth';
import { SKIN_TYPE_CONCERNS, effectiveConcerns, type DerivedConcern } from './profile';

/** 세트 수. 탭 3개가 한 줄에 들어가는 최대치이자 고르는 부담이 없는 수. */
export const SET_COUNT = 3;

/**
 * 최후 폴백 3종 — 비로그인·무프로필에게 보이는 대표 컨셉.
 * pore/trouble은 남성 지성 피부 최빈 고민, moisture가 건성 계열을 덮는다.
 * 세 슬러그 모두 concern_target_rule에 타겟 규칙이 있어 reason 문장이 보장된다.
 */
export const FALLBACK_CONCEPTS: DerivedConcern[] = ['pore', 'trouble', 'moisture'];

/**
 * 탭 라벨. 고민 9종은 SkinProfileFields.tsx CONCERNS와 같은 문구여야 한다
 * (setConcepts.test.ts가 동일성을 단언한다). gentle만 이 파일이 원본이다 —
 * 프로필에서 직접 못 고르는 파생 전용 슬러그라 CONCERNS에 없다.
 */
const CONCEPT_LABELS: Record<DerivedConcern, string> = {
  exfoliate: '각질',
  sebum: '피지',
  pore: '모공',
  trouble: '트러블',
  soothe: '진정',
  moisture: '보습',
  barrier: '장벽',
  bright: '브라이트닝',
  'anti-aging': '안티에이징',
  gentle: '저자극',
};

export interface SetConcept {
  /** composeStep의 signals.concerns에 단독으로 들어갈 슬러그. */
  slug: DerivedConcern;
  label: string;
  /** false면 고정 폴백 — 전부 false일 때 화면이 프로필 등록 유도 문구를 낸다. */
  personalized: boolean;
}

/**
 * 파생 사다리(스펙 §4): ① 직접 고른 고민(선택 순서, 비었으면 피부타입 파생 — effectiveConcerns)
 * → ② 피부타입 파생 보충 → ③ 고정 폴백. 중복은 제거하고 항상 정확히 SET_COUNT개.
 */
export function deriveSetConcepts(
  concerns: string[],
  skinType: SkinType | null,
): SetConcept[] {
  const out: SetConcept[] = [];
  const push = (slug: DerivedConcern, personalized: boolean) => {
    if (out.length < SET_COUNT && !out.some((c) => c.slug === slug)) {
      out.push({ slug, label: CONCEPT_LABELS[slug], personalized });
    }
  };
  for (const slug of effectiveConcerns(concerns, skinType)) push(slug, true);
  if (skinType) for (const slug of SKIN_TYPE_CONCERNS[skinType]) push(slug, true);
  for (const slug of FALLBACK_CONCEPTS) push(slug, false);
  return out;
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd frontend && npx vitest run src/features/affinity/setConcepts.test.ts`
Expected: PASS 8건.

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/features/affinity/setConcepts.ts frontend/src/features/affinity/setConcepts.test.ts
git commit -m "feat(main): 컨셉 세트 파생 사다리 deriveSetConcepts — 고민→피부타입→폴백"
```

---

### Task 2: 조합기 오버라이드 — `useComposer(concernOverride)`

**Files:**
- Modify: `frontend/src/features/affinity/useComposer.ts` (signals useMemo와 함수 시그니처만)
- Test: `frontend/src/features/affinity/composer.test.ts` (describe 블록 추가 — 기존 케이스 수정 금지)

**Interfaces:**
- Consumes: `profile.ts`의 `DerivedConcern`
- Produces: `useComposer(concernOverride?: DerivedConcern | null): StepState[]` — 인자 없이 부르면 기존과 완전 동일 동작(기존 호출부 `Routine.tsx`/`Main.tsx` 무수정 호환). Task 4가 인자 있는 형태를 사용

- [ ] **Step 1: 오버라이드 의미를 고정하는 테스트 작성 (composeStep은 무수정 — 기존 동작의 사양화)**

`composer.test.ts` 말미에 추가. 기존 헬퍼 `goods`/`stepOf`/`signalsOf`/`concernRule`를 그대로 쓴다:

```ts
describe('composeStep — 컨셉 단독 오버라이드(세트 A/B/C, 스펙 §5)', () => {
  const step = stepOf('C001002');
  const base = { step, prevPick: null, flowRules: [], concernRules: [], verdicts: null };

  it('concerns가 단독 슬러그면 그 태그 보유 후보가 인기 1위를 제친다 (concern 2.0 > popularity 0.3)', () => {
    const result = composeStep({
      ...base,
      candidates: [goods(1, []), goods(2, ['pore'])],
      signals: signalsOf({ concerns: ['pore'] }),
    });
    expect(result.pick?.goodsNo).toBe(2);
    expect(result.matched.concerns).toEqual(['pore']);
  });

  it('오버라이드가 texture tie-break를 죽이지 않는다 (texture 0.5 > 인기 격차 0.15)', () => {
    const result = composeStep({
      ...base,
      candidates: [goods(1, ['pore']), goods(2, ['pore', 'matte'])],
      signals: signalsOf({ concerns: ['pore'], textures: ['matte'] }),
    });
    expect(result.pick?.goodsNo).toBe(2);
  });

  it('단독 concerns의 reason은 그 컨셉을 겨냥한 규칙에서 나온다', () => {
    const poreRule = concernRule({
      concernTagSlug: 'pore',
      toCategoryCode: 'C001002',
      reason: '모공은 세럼 단계에서 정면으로 잡는 게 효율적이에요',
    });
    const result = composeStep({
      ...base,
      candidates: [goods(1, ['pore'])],
      signals: signalsOf({ concerns: ['pore'] }),
      concernRules: [concernRule({ concernTagSlug: 'moisture' }), poreRule],
    });
    expect(result.reason).toBe(poreRule.reason);
  });
});
```

- [ ] **Step 2: 테스트 실행 — 즉시 통과 확인**

Run: `cd frontend && npx vitest run src/features/affinity/composer.test.ts`
Expected: **PASS** — composeStep은 이미 이 동작을 한다. 이 케이스들은 "단독 배열이면 세트가 차별화된다"는 스펙 §5의 전제를 회귀 방지로 고정하는 것이다. FAIL이면 전제가 틀린 것이니 **중단하고 오케스트레이터에 보고**한다.

- [ ] **Step 3: `useComposer` 시그니처 변경**

`useComposer.ts` — 변경은 아래 두 곳뿐이다:

```ts
// (1) 시그니처 — 기본값 null이라 기존 호출부는 무수정
export function useComposer(concernOverride: DerivedConcern | null = null): StepState[] {

// (2) signals useMemo — concerns만 분기, textures·affinity는 유지(스펙 §5)
  const signals: ComposerSignals = useMemo(() => {
    const profileConcerns = meQuery.data?.concerns ?? [];
    return {
      concerns: concernOverride
        ? [concernOverride]
        : effectiveConcerns(
            profileConcerns,
            (meQuery.data?.skinType ?? null) as SkinType | null,
          ),
      textures: preferredTextures(profileConcerns),
      affinity: aggregate(events),
    };
  }, [events, meQuery.data, concernOverride]);
```

import에 `type { DerivedConcern }`를 `./profile`에서 추가한다. 함수 doc 주석에 한 줄 추가: "concernOverride — 세트 탭이 지정한 컨셉. 단독 대체라 세트 간 구성이 겹치지 않는다(스펙 §5)."

- [ ] **Step 4: 타입·기존 테스트 회귀 확인**

Run: `cd frontend && npx tsc --noEmit -p tsconfig.app.json && npx vitest run src/features/affinity/ src/pages/Main.test.tsx`
Expected: 전부 PASS (기존 Main.test.tsx는 인자 없는 호출이라 동작 불변).

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/features/affinity/useComposer.ts frontend/src/features/affinity/composer.test.ts
git commit -m "feat(main): useComposer에 concernOverride — 세트 탭용 단독 컨셉 대체"
```

---

### Task 3: DESIGN.md `set-tabs` 절 + `SetTabs` 컴포넌트

**Files:**
- Modify: `DESIGN.md` (Components 절, `list-toolbar` 항목 뒤에 삽입)
- Create: `frontend/src/components/routine/SetTabs.tsx`
- Create: `frontend/src/components/routine/SetTabs.css`

**Interfaces:**
- Consumes: Task 1의 `SetConcept`
- Produces: `SetTabs({ concepts, selected, onSelect }: { concepts: SetConcept[]; selected: number; onSelect: (index: number) => void })` — Task 4가 이 props 그대로 사용. 렌더 계약: pill이 `role="tab"` + 선택 탭만 `aria-selected="true"`, 폴백 안내 문구는 `concepts.every((c) => !c.personalized)`일 때만 (Task 4의 테스트가 이 계약을 단언한다)

- [ ] **Step 1: DESIGN.md에 절 추가 (스타일 코드보다 먼저 — 문서가 진실)**

`list-toolbar` 항목 뒤에 아래 내용을 추가한다. **토큰명은 반드시 DESIGN.md의 Colors·Typography·Shapes 절과 대조해 실존 토큰만 쓴다** — 아래 초안에서 실존하지 않는 이름이 있으면 그 절의 대응 토큰으로 바꾸고 보고에 명시한다:

```markdown
**`set-tabs`** — /main 히어로 아래 "당신을 위한 세트" 섹션
- 구성: 아이브로우 "PERSONAL SETS"(`{typography.micro-caps}`) + 섹션 제목 "당신을 위한 세트"
  + pill 탭 3개 한 줄. 탭 라벨은 "세트 A · 모공" 형식(세트 문자 + 고민 태그와 같은 문구).
- 탭 pill은 `{rounded.full}`. 타이포·크기·간격은 **list-toolbar 카테고리 탭 pill과 동일 사양**을
  그대로 쓴다 — 같은 조작(카테고리 전환/세트 전환)이 화면마다 다르게 생기면 안 된다.
  선택 상태는 "선택 상태 — 색 반전" 규칙(검정 채움 + 흰 글자), 비선택은 1px
  `{colors.hairline-soft}` 테두리 + `{colors.ink}` 글자에 배경 없음.
- 액센트(signal-*) 금지 — 이 섹션도 무채색 사다리 안이다. 개인화 여부를 색으로 구분하지 않는다.
- 폴백 상태(전부 비개인화): 탭 아래 안내 한 줄 "프로필을 등록하면 맞춤 세트로 바뀌어요" —
  `{typography.meta}`, 본문보다 한 단 낮은 무채색. 문구 끝에 프로필 화면 텍스트 링크.
- 로딩(프로필 확정 전): 같은 크기의 스켈레톤 pill 3개, 조작 불가. 확정 후 높이가 흔들리지 않는다.
- 모바일(768px 미만): 탭 가로 스크롤(list-toolbar와 동일). 한글이므로 `word-break: keep-all`.
```

- [ ] **Step 2: DESIGN.md 커밋 (컴포넌트 코드보다 먼저 — 스펙 §10 DoD 4)**

```bash
git add DESIGN.md
git commit -m "docs(design): set-tabs 절 추가 — /main 컨셉 세트 탭 사양"
```

- [ ] **Step 3: `SetTabs.tsx` + CSS 구현**

컴포넌트는 표시 전용(로직 없음)이라 시그니처 + 사양 문장으로 충분하다:

- props는 Interfaces 블록 그대로. 내부 상태 없음 — 선택은 부모(Main) 소관.
- 마크업: 섹션 래퍼 → 아이브로우/제목 → `<div role="tablist" aria-label="추천 세트">` → 탭 3개
  `<button role="tab" aria-selected={i === selected} onClick={() => onSelect(i)}>`.
  라벨: `세트 {['A','B','C'][i]} · {concept.label}`.
- 폴백 안내: `concepts.every((c) => !c.personalized)`일 때만 안내 문구 + `<Link>`(react-router)
  — 로그인 여부에 따라 `/mypage` 또는 `/signup`. 로그인 여부는 `useAuthStore`의
  `accessToken` 유무로 판단한다(이 컴포넌트가 유일하게 읽는 전역).
- CSS는 방금 추가한 DESIGN.md `set-tabs` 절의 토큰만 참조. 기존 목록 화면 카테고리 탭 pill의
  CSS(해당 파일을 찾아 읽고)와 같은 값 체계를 쓴다 — 단 클래스는 `bb-set-tabs__*`로 분리
  (목록 화면 스타일에 의존하면 그쪽 수정이 이 화면을 깨뜨린다).

- [ ] **Step 4: 타입 게이트**

Run: `cd frontend && npx tsc --noEmit -p tsconfig.app.json`
Expected: PASS. (렌더 검증은 Task 4 테스트와 Task 5 스크린샷 소관.)

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/routine/SetTabs.tsx frontend/src/components/routine/SetTabs.css
git commit -m "feat(main): SetTabs 컴포넌트 — 세트 A/B/C pill 탭 + 폴백 안내"
```

---

### Task 4: `Main.tsx` 통합 + 화면 테스트

**Files:**
- Modify: `frontend/src/pages/Main.tsx`
- Modify: `frontend/src/pages/Main.css` (세트 섹션 배치 간격만)
- Test: `frontend/src/pages/Main.test.tsx` (describe 추가 — 기존 케이스 수정 금지)

**Interfaces:**
- Consumes: Task 1 `deriveSetConcepts`/`SetConcept`, Task 2 `useComposer(slug)`, Task 3 `SetTabs`
- Produces: 없음 (최종 화면)

- [ ] **Step 1: 실패하는 테스트 작성**

`Main.test.tsx`에 추가. 기존 파일의 msw 핸들러·픽스처 헬퍼(`tagOf`, 단계별 풀 통제, authStore 로그인 패턴)를 그대로 사용한다. 케이스와 단언(스펙 §9):

```ts
describe('컨셉 세트 탭 (스펙 §3)', () => {
  it('프로필 회원 — 탭 3개가 role=tab으로 렌더되고 첫 탭만 aria-selected', async () => {
    // given: 로그인 + me 응답 concerns=['pore','trouble','moisture']
    // then:
    const tabs = await screen.findAllByRole('tab');
    expect(tabs).toHaveLength(3);
    expect(tabs[0]).toHaveAttribute('aria-selected', 'true');
    expect(tabs[0]).toHaveTextContent('모공');
    expect(tabs[1]).toHaveAttribute('aria-selected', 'false');
  });

  it('탭 클릭 — 세럼 단계 픽이 새 컨셉의 태그 보유 상품으로 바뀐다', async () => {
    // given: 로그인 concerns=['pore','trouble'] +
    //        세럼(C001002) 풀에 인기 1위=무태그, 2위=tags:['pore'], 3위=tags:['trouble']
    // when: 초기 렌더 → 세럼 픽이 pore 상품 / '세트 B' 탭 클릭
    // then:
    //   expect(await serumSection.findByText(/*(trouble 상품명)*/)).toBeInTheDocument();
    //   expect(tabs[1]).toHaveAttribute('aria-selected', 'true');
  });

  it('비로그인 — 폴백 3종 라벨(모공·트러블·보습)과 프로필 등록 유도 문구가 보인다', async () => {
    const tabs = await screen.findAllByRole('tab');
    expect(tabs.map((t) => t.textContent)).toEqual([
      expect.stringContaining('모공'),
      expect.stringContaining('트러블'),
      expect.stringContaining('보습'),
    ]);
    expect(screen.getByText(/프로필을 등록하면 맞춤 세트로 바뀌어요/)).toBeInTheDocument();
  });
});
```

두 번째 케이스의 given/when 주석은 기존 파일의 풀 통제 헬퍼로 구체화한다 — 단언 3줄(픽 교체, aria-selected 이동)은 그대로 유지해야 한다.

- [ ] **Step 2: 실패 확인**

Run: `cd frontend && npx vitest run src/pages/Main.test.tsx`
Expected: 신규 3건 FAIL (탭 미렌더), 기존 케이스는 전부 PASS 유지 — 기존이 깨지면 통합 방식이 틀린 것이다.

- [ ] **Step 3: `Main.tsx` 통합**

판단이 갈리는 타이밍 로직이므로 핵심을 전량 명세(스펙 §5 "탭 활성화 타이밍"):

```ts
const accessToken = useAuthStore((s) => s.accessToken);
// useComposer 내부와 같은 ['me'] 키 — 네트워크 왕복은 react-query 캐시가 흡수한다.
const meQuery = useQuery({ queryKey: ['me'], queryFn: fetchMe, enabled: !!accessToken });
// me가 확정되기 전에 탭을 그리면 프로필 도착 순간 탭이 갈아엎어진다(스펙 §5).
const meSettled = !accessToken || meQuery.isSuccess || meQuery.isError;
const concepts = useMemo(
  () =>
    deriveSetConcepts(
      meQuery.data?.concerns ?? [],
      (meQuery.data?.skinType ?? null) as SkinType | null,
    ),
  [meQuery.data],
);
const [selectedSet, setSelectedSet] = useState(0);
// 확정 전에도 항상 호출한다(훅 순서). 확정 전엔 useComposer 내부 signalsReady가
// 같은 me 쿼리를 기다리므로 조합이 시작되지 않는다 — 픽이 두 번 계산되는 일은 없다.
const states = useComposer(concepts[selectedSet].slug);
```

JSX: 히어로(`bb-main__intro`) 닫힌 뒤, `bb-main__body` 안 앵커 네비 **앞**에 배치:

```tsx
{meSettled ? (
  <SetTabs concepts={concepts} selected={selectedSet} onSelect={setSelectedSet} />
) : (
  <div className="bb-set-tabs__skeleton" aria-hidden />
)}
```

`Main.css`에는 세트 섹션과 네비 사이 간격만 추가(DESIGN.md `set-tabs` 절 + spacing 토큰 참조). 히어로·네비·섹션·일괄 담기 등 기존 렌더는 무수정.

- [ ] **Step 4: 통과 확인**

Run: `cd frontend && npx vitest run src/pages/Main.test.tsx`
Expected: 신규 3건 포함 전부 PASS.

- [ ] **Step 5: 전체 게이트**

Run: `cd frontend && npx tsc --noEmit -p tsconfig.app.json && npm test`
Expected: 전부 PASS.

- [ ] **Step 6: 커밋**

```bash
git add frontend/src/pages/Main.tsx frontend/src/pages/Main.css frontend/src/pages/Main.test.tsx
git commit -m "feat(main): 컨셉 세트 탭 통합 — 탭 선택이 5단계 조합기를 재조합"
```

---

### Task 5: 렌더 검증 (스크린샷 DoD)

**Files:** 수정 없음 (검증 전용 — 스크린샷은 커밋하지 않는다)

- [ ] **Step 1: 개발서버 기동**

`cd frontend && npm run dev` (백엔드 없이도 화면은 뜬다 — 조합기는 조회 실패 시 인기순 기준선 폴백. 풀까지 실데이터로 보려면 compose 스택(README 참조)을 쓴다.)

- [ ] **Step 2: 스크린샷 3장 — 찍고 직접 본다**

Playwright로 `/main`을 열어 워크트리 밖 스크래치 경로에 저장하고 **파일을 직접 열어 눈으로 확인**한다 (CLAUDE.md: 테스트 통과·curl은 이 요구를 대체하지 못한다):

1. `set-tabs-anon.png` — 비로그인: 탭 3장(모공·트러블·보습) + 유도 문구, 히어로/네비와의 간격
2. `set-tabs-member.png` — 시드 회원 로그인: 개인화 탭 3장, 첫 탭 색 반전
3. `set-tabs-switch.png` — 세트 B 클릭 후: aria-selected 이동 + 세럼 단계 픽 변화

주의(메모리): 스크롤·클릭 검증 시 Playwright `click()`은 자동 스크롤한다 — 보이는 요소는 `evaluate`로 클릭한다.

- [ ] **Step 3: 판정 체크리스트**

- 한글 줄바꿈 잘림 없음(keep-all), 탭 높이 흔들림 없음(스켈레톤↔확정)
- 색 반전 pill 외 액센트 배경 없음
- 히어로 검정 밴드 → 세트 섹션 → 네비의 밝기 계단이 자연스러움

- [ ] **Step 4: 보고**

스크린샷 3장의 절대 경로 + 판정 결과를 오케스트레이터에 보고. 오케스트레이터도 파일을 열어 보고 판정한다.

---

## 머지

전 태스크 완료 + Task 5 판정 통과 후, 오케스트레이터가 `superpowers:finishing-a-development-branch` 스킬로 `feature/concept-sets` → `main` 통합을 진행한다.
