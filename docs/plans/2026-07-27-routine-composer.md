# 구현 계획 — 루틴 조합기 (메인 개인화 v2)

> 설계: `docs/superpowers/specs/2026-07-27-routine-composer-design.md` — 범위·근거의 유일한 진실.
> 이 문서는 **태스크·터미널 운용**의 유일한 진실이다. 설계와 어긋나면 설계가 이긴다.
> 마이그레이션 없음 — Flyway 번호 이슈 없음.

## 0. 사람이 할 일 (사전 조건 2줄)

터미널을 열기 전에 프로젝트 루트에서:

```
git log --oneline -1     # 이 계획 커밋(제목: docs(plan): 루틴 조합기 구현 계획) 이상인지 확인
git status               # 깨끗한지 확인
```

시뮬레이션용 실데이터 스냅샷(`frontend/src/features/affinity/fixtures/catalog-snapshot.json`,
비HIDDEN 184상품, V83 적용 후)은 오케스트레이터가 이미 추출·커밋했다 — 터미널은 DB에 접근하지
않는다.

## 1. 웨이브·터미널 분할

| 웨이브 | 터미널 | 브랜치 | 범위 | 모델 |
|---|---|---|---|---|
| A | T1 | `feature/composer-engine` | `composer.ts` 순수 함수 + 유닛 + 시뮬레이션 | sonnet |
| A | T2 | `feature/compat-verdicts-api` | `GET /compat/verdicts` + IT | sonnet |
| B | T3 | `feature/composer-ui` | 픽 카드·바로 담기·전체 담기·배선 + DESIGN.md | sonnet |

- **T1·T2는 완전 병렬** — 파일이 하나도 겹치지 않고(T1은 프론트 신규 파일만, T2는 백엔드만),
  서로의 결과물에 의존하지 않는다(§2의 조합기는 verdicts를 **인자로 받는** 순수 함수라 API가
  없어도 테스트된다).
- **T3는 웨이브 A 두 브랜치가 `main`에 머지된 뒤** 시작한다. T1의 함수 계약과 T2의 API 계약에
  모두 의존한다.
- 모델은 셋 다 sonnet — 판단이 갈리는 점수 공식·체인은 이 계획서가 코드로 전량 못 박는다(§2).
- **머지 게이트에서 마이그레이션 번호를 재확인할 필요 없음**(이 계획은 마이그레이션이 없다).

### 파일 소유권

| 터미널 | 소유 파일 |
|---|---|
| T1 | `frontend/src/features/affinity/composer.ts`(신규), `composer.test.ts`(신규), `simulation.test.ts`(신규), `fixtures/catalog-snapshot.json`(읽기만) |
| T2 | `backend/…/compat/CompatVerdictsController.java`(신규), `backend/…/config/SecurityConfig.java`, `backend/src/test/java/com/beautyboy/compat/CompatVerdictsApiIT.java`(신규) |
| T3 | `frontend/src/pages/Main.tsx`·`Main.test.tsx`, `frontend/src/components/routine/RoutineSection.tsx`·`.css`·신규 `PickCard.tsx`·`.css`·`PickCard.test.tsx`, `frontend/src/features/affinity/useComposer.ts`(신규)·`match.ts`(축소), `frontend/src/api/compat.ts`, `frontend/src/mocks/handlers.ts`, `DESIGN.md`, `docs/screenshots/*`, 각 대응 테스트 |

목록 밖 파일은 수정하지 않는다. 안 맞으면 **고치지 말고 보고**한다.
(T3 주의: `api/cart.ts`의 기존 함수는 **호출만** 한다 — 시그니처가 부족하면 보고.)

---

## 2. 공유 계약 — 조합기 코어 (T1이 구현, T3가 소비)

이 절의 코드는 **한 글자도 어긋나면 T3가 깨지는 계약**이므로 전량으로 적는다.
가중치·상한의 근거는 설계 §2.1 표가 유일한 출처다 — 값을 바꾸려면 설계를 먼저 고친다.

```ts
// frontend/src/features/affinity/composer.ts
import type { GoodsListItem } from '../../types/goods';
import type { ConcernRuleView, FlowRuleView } from '../../types/routine';
import type { RoutineStep } from '../routine/steps';

/** 가중치 — 설계 §2.1 표가 근거의 유일한 출처. 여기 값만 바꾸고 근거를 안 고치면 안 된다. */
export const WEIGHTS = {
  concern: 2.0,
  behavior: 1.5,
  flow: 1.0,
  texture: 0.5,
  popularity: 0.3,
} as const;

/** 단계당 후보 풀 크기(서버 인기순, 태그 필터 없음). 설계 §9 — 12위 밖은 개인화로도 못 올라온다. */
export const POOL_SIZE = 12;

/** 대안 수 (픽 1 + 대안 3 = 기존 한 줄 4칸 유지). */
export const ALTERNATIVE_COUNT = 3;

export interface ComposerSignals {
  /** 고민 슬러그 — 사용감 제외, 피부타입 파생 포함 (profile.ts의 effectiveConcerns 결과). */
  concerns: string[];
  /** 선호 사용감 슬러그 (preferredTextures 결과). */
  textures: string[];
  /** aggregate() 결과 — key `${cat3}|${tag}`, value 가중치 합. */
  affinity: Map<string, number>;
}

export interface PrevPick {
  goodsNo: number;
  cat3: string;      // 중분류 7자
  tags: string[];    // slug[]
}

export interface StepComposition {
  /** null = 후보 풀이 비었음 → 화면은 기준선 그리드로 폴백(픽 카드 미렌더). */
  pick: GoodsListItem | null;
  alternatives: GoodsListItem[];          // 최대 ALTERNATIVE_COUNT
  /** 발동 규칙의 reason 원문. null이면 문장 없이 근거 칩만(설계 §3.2 폴백 사다리). */
  reason: string | null;
  /** 픽 카드 근거 칩 — 점수에 실제 기여한 태그만(설계 §5). */
  matched: { concerns: string[]; behaviors: string[] };
}

export function composeStep(input: {
  step: RoutineStep;
  candidates: GoodsListItem[];            // 서버 인기순 ≤ POOL_SIZE
  signals: ComposerSignals;
  prevPick: PrevPick | null;
  flowRules: FlowRuleView[];
  concernRules: ConcernRuleView[];
  /** goodsNo → 최악 verdict. null = 게이트 호출 실패(전부 통과 — 설계 §3.3). */
  verdicts: Map<number, string> | null;
}): StepComposition {
  const { step, signals, prevPick, flowRules, concernRules, verdicts } = input;

  // 이 단계에 속하는 행동 친화도만 태그별로 합산한다. Map.get 직접 조회를 쓰면 안 된다 —
  // 이벤트 키는 중분류 7자(C002001|tag)인데 클렌징 단계의 categoryCode는 대분류 4자(C002)라
  // 키가 영영 일치하지 않는다. 접두사 매칭으로 합산해야 단계 깊이 혼재(steps.ts 주석)를 견딘다.
  const affinityByTag = new Map<string, number>();
  for (const [key, w] of signals.affinity) {
    const [cat3, tag] = key.split('|');
    if (cat3.startsWith(step.categoryCode)) {
      affinityByTag.set(tag, (affinityByTag.get(tag) ?? 0) + w);
    }
  }

  // 1) 궁합 게이트 — 이전 픽과 CONFLICT면 후보에서 제거.
  const gated = input.candidates.filter(
    (p) => verdicts === null || verdicts.get(p.goodsNo) !== 'CONFLICT',
  );
  if (gated.length === 0) {
    return { pick: null, alternatives: [], reason: null, matched: { concerns: [], behaviors: [] } };
  }

  // 2) 전이 규칙 — 이전 픽에서 이 단계를 겨냥한 규칙 1개 (설계 §3.2).
  const flowRule = prevPick ? pickFlowRule(prevPick, step, flowRules) : null;
  const flowTag = flowRule?.toTagSlug ?? null;

  // 3) 점수 — behaviorAffinity는 후보 중 최대값 정규화(전부 0이면 0).
  const rawAffinity = gated.map((p) =>
    p.tags.reduce((sum, t) => sum + (affinityByTag.get(t.slug) ?? 0), 0),
  );
  const maxAffinity = Math.max(...rawAffinity);
  const n = gated.length;

  const scored = gated.map((p, i) => {
    const slugs = p.tags.map((t) => t.slug);
    const concernHits = slugs.filter((s) => signals.concerns.includes(s));
    const behaviorHits = slugs.filter((s) => (affinityByTag.get(s) ?? 0) > 0);
    const score =
      WEIGHTS.concern * Math.min(concernHits.length, 2) +
      WEIGHTS.behavior * (maxAffinity > 0 ? rawAffinity[i] / maxAffinity : 0) +
      WEIGHTS.flow * (flowTag !== null && slugs.includes(flowTag) ? 1 : 0) +
      WEIGHTS.texture * Math.min(slugs.filter((s) => signals.textures.includes(s)).length, 1) +
      WEIGHTS.popularity * ((n - i) / n);
    return { p, i, score, concernHits, behaviorHits };
  });

  // 4) 정렬 — 점수 내림차순, 동점은 서버 인기순(i) → goodsNo. 완전 결정적(설계 §2.1).
  scored.sort((a, b) => b.score - a.score || a.i - b.i || a.p.goodsNo - b.p.goodsNo);

  const top = scored[0];
  // 5) reason 폴백 사다리: 전이 규칙 → 이 단계를 겨냥한 고민 규칙(고민 선택 순 → priority) → null.
  const reason =
    flowRule?.reason ?? pickConcernReason(step, signals.concerns, concernRules) ?? null;

  return {
    pick: top.p,
    alternatives: scored.slice(1, 1 + ALTERNATIVE_COUNT).map((s) => s.p),
    reason,
    matched: { concerns: top.concernHits, behaviors: top.behaviorHits },
  };
}

/** 전이 규칙 선택 — kind 서열(BUFFER > NEXT_STEP > PAIRED_REMOVAL) → priority 오름차순.
 *  priority는 이 안에서만 쓴다 — 단계 간 경쟁에는 쓰지 않는다(설계 §2.1). */
const KIND_RANK: Record<string, number> = { BUFFER: 0, NEXT_STEP: 1, PAIRED_REMOVAL: 2 };

export function pickFlowRule(
  prevPick: PrevPick,
  step: RoutineStep,
  rules: FlowRuleView[],
): FlowRuleView | null {
  const matched = rules
    .filter(
      (r) =>
        prevPick.cat3.startsWith(r.fromCategoryCode) &&
        (r.fromTagSlug === null || prevPick.tags.includes(r.fromTagSlug)) &&
        r.toCategoryCode.startsWith(step.categoryCode.slice(0, 7)),
    )
    .sort(
      (a, b) =>
        (KIND_RANK[a.edgeKind] ?? 9) - (KIND_RANK[b.edgeKind] ?? 9) || a.priority - b.priority,
    );
  return matched[0] ?? null;
}

export function pickConcernReason(
  step: RoutineStep,
  concerns: string[],
  rules: ConcernRuleView[],
): string | null {
  for (const c of concerns) {
    const hit = rules
      .filter(
        (r) => r.concernTagSlug === c && r.toCategoryCode.startsWith(step.categoryCode.slice(0, 7)),
      )
      .sort((a, b) => a.priority - b.priority)[0];
    if (hit) return hit.reason;
  }
  return null;
}
```

- `toCategoryCode.startsWith(step 중분류)` 방향인 이유: 규칙은 7자, `ROUTINE_STEPS`의 클렌징은
  대분류 4자(`C002`)다 — 픽 쪽(`prevPick.cat3.startsWith(from)`)과 방향이 다르니 주의.
- 기존 `match.ts`의 `matchByBehavior`/`matchByProfile`/`rankByTexture`는 **T1이 건드리지 않는다**
  (T3가 배선 교체 시 정리). `aggregate`/`effectiveConcerns`/`preferredTextures`는 그대로 소비한다.

---

## 3. 터미널 T1 — 조합기 엔진 + 시뮬레이션

### 실행 프롬프트 (그대로 붙여넣기)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add ../뷰티보이-조합기 -b feature/composer-engine
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 ../뷰티보이-조합기 인지
  - git log --oneline -1 이 루트에서 본 기점 커밋과 같은지
  - docs/plans/2026-07-27-routine-composer.md 와
    frontend/src/features/affinity/fixtures/catalog-snapshot.json 이 실제로 존재하는지
  - git status가 깨끗한지

[2단계 — 실행]
docs/plans/2026-07-27-routine-composer.md 의 "§2 공유 계약"과 "터미널 T1" 절을 처음부터
끝까지 읽고 Task 1-1 ~ 1-3을 순서대로 실행해라. 설계 근거는
docs/superpowers/specs/2026-07-27-routine-composer-design.md §2·§3·§6을 본다.

규칙:
- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다. composer.ts는 §2의 코드를 그대로 옮긴다 —
  가중치·상한·정렬 순서를 임의로 바꾸지 마라. 바꿔야 할 이유를 발견하면 중단하고 보고해라.
- 기존 파일(match.ts·profile.ts·events.ts 등)을 수정하지 마라 — import만 한다.
- 시뮬레이션 임계 미달이면 값을 조작하지 말고 실측값과 함께 보고해라.
- 전체 테스트는 `npm test`로 판정한다(`npx vitest run`은 e2e 스펙을 주워 항상 1 failed).
  타입 검사는 반드시 `npx tsc --noEmit -p tsconfig.app.json`.
완료하면 커밋하고, 시뮬레이션이 출력한 실측 수치 전부를 보고서에 남겨라.
```

### Task 1-1 — `composer.ts` (§2 전량 그대로)

### Task 1-2 — `composer.test.ts` (테스트 이름·단언 전량)

- `콜드스타트: 신호가 전무하면 픽이 서버 인기 1위다` — popularityPrior만 남음을 증명.
- `고민 1개 일치가 사용감+인기 합을 이긴다` — 가중치 서열(2.0 > 0.8)의 회귀 방어.
- `고민 일치 태그가 3개여도 2개로 캡된다`
- `행동 친화도는 후보 중 최대값으로 정규화된다 — 최대가 0이면 전원 0`
- `대분류 단계(클렌징 C002)에서도 중분류 키(C002001|tag)의 이벤트가 친화도에 합산된다` —
  접두사 합산 회귀(Map 직접 조회로 되돌리면 이 테스트가 잡는다).
- `같은 상품을 담을수록(w:3 반복) 관련 태그 후보의 점수가 단조 증가한다` — 문제 #2 회귀.
- `행동(1.5)+흐름(1.0)이 고민 1개(2.0)를 뒤집는다` — 설계 §2.1의 의도된 역전 통로.
- `이전 픽과 CONFLICT인 후보는 점수와 무관하게 제거된다`
- `verdicts가 null이면(게이트 실패) 전원 통과한다`
- `게이트로 전원 탈락하면 pick이 null이다`
- `pickFlowRule: BUFFER가 NEXT_STEP보다 먼저다 — priority가 높아도`
- `pickFlowRule: from_tag가 null인 규칙은 픽 태그와 무관하게 매칭된다`
- `pickFlowRule: 클렌징(대분류 4자) 단계도 to_category 7자와 매칭된다` — startsWith 방향 회귀.
- `flowTag 일치 후보에 flow 가중치가 붙어 순위가 뒤집힌다`
- `reason 사다리: 전이 규칙 → 고민 규칙 → null 순으로 떨어진다`
- `동점이면 서버 인기순, 그다음 goodsNo — 두 번 실행해도 같은 결과다`
- `대안은 픽을 제외한 점수 2~4위다`

### Task 1-3 — `simulation.test.ts` (설계 §6 — 성과 수치)

스냅샷의 184상품으로 단계별 풀(카테고리 접두사 필터 → viewCount 내림차순 → 상위 12)을 만들고,
`GoodsListItem` 형태로 변환하는 헬퍼를 파일 안에 둔다. 규칙은 V75·V82 시드를 픽스처로 옮긴다
(MSW 불필요 — 전부 순수 함수 호출). 프로필 20종: 고민 0~3개 × 피부타입 4종 × 행동 패턴
(없음/조회 위주/담기 위주)을 결정적으로 조합한다.

- `[성과] 사용자 간 구성 차별화` — 20종의 단계별 상위 4(픽+대안) 집합 간 평균 자카드 유사도를
  출력하고 **< 0.85** 단언 (기준선: 전원 인기순 = 1.0).
- `[성과] 개인화 커버리지` — 신호가 있는 프로필에서 픽 ≠ 인기 1위인 단계 비율을 출력하고
  **≥ 0.4** 단언.
- `[성과] 연속성` — 한 프로필에 이벤트를 0→10개 하나씩 추가하며 인접 구성 간 바뀐 픽 수의
  최대값을 출력하고 **≤ 3** 단언 (특히 4→5개 경계에서 급변 없음 — 문제 #3 회귀).
- `[성과] 강도 반영` — 특정 태그 상품 담기를 1→5회 늘리면 그 태그 후보의 픽 순위가 내려가지
  않음(단조)을 단언.
- `[성과] 궁합 0건` — 충돌 픽스처(§2 verdicts 인자)로 체인 전체를 돌려 인접 픽 쌍에 CONFLICT가
  없음을 단언.
- 임계는 **하한 보증**이다. 미달 시 가중치를 조작해 통과시키지 말고 실측값과 함께 보고한다.

### T1 완료 조건

`npm test` 전량 + `tsc -p` 통과, 시뮬레이션 실측 수치 5종 보고.

---

## 4. 터미널 T2 — compat verdicts API

### 실행 프롬프트 (그대로 붙여넣기)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add ../뷰티보이-궁합API -b feature/compat-verdicts-api
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 ../뷰티보이-궁합API 인지
  - git log --oneline -1 이 루트에서 본 기점 커밋과 같은지
  - docs/plans/2026-07-27-routine-composer.md 가 존재하는지
  - git status가 깨끗한지

[2단계 — 실행]
docs/plans/2026-07-27-routine-composer.md 의 "터미널 T2" 절을 읽고 Task 2-1 ~ 2-2를 실행해라.
설계 근거는 docs/superpowers/specs/2026-07-27-routine-composer-design.md §3.3.

규칙:
- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다.
- compat 패키지의 기존 서비스(CompatQueryService.worstVerdicts)에 위임만 한다 —
  서비스 로직·타 도메인 리포지토리를 건드리지 마라.
- 마이그레이션을 만들지 마라 — 이 태스크에는 스키마 변경이 없다.
- H2 create-drop 함정: 통합 테스트는 실 MySQL(Testcontainers) + ddl-auto=validate로 한다.
- 이 절의 "파일 소유권 T2" 목록 밖 파일은 수정하지 않는다.
완료하면 커밋하고, curl 스모크 결과(대표 CONFLICT 응답)를 보고해라.
```

### Task 2-1 — 컨트롤러 (공유 계약 — 시그니처 전량)

```java
// backend/src/main/java/com/beautyboy/compat/CompatVerdictsController.java
@RestController
public class CompatVerdictsController {

    /** 한 번에 판정할 후보 상한 — 조합기 풀(12)보다 넉넉하되 무한 배치를 막는다. */
    private static final int MAX_CANDIDATES = 50;

    @GetMapping("/api/v1/compat/verdicts")
    public ResponseEntity<ApiResponse<Map<Long, String>>> verdicts(
            @RequestParam Long base,
            @RequestParam List<Long> candidates) {
        List<Long> clamped = candidates.size() > MAX_CANDIDATES
                ? candidates.subList(0, MAX_CANDIDATES) : candidates;
        return ResponseEntity.ok(ApiResponse.ok(compatQueryService.worstVerdicts(base, clamped)));
    }
}
```

- 응답은 `Map<Long, String>` → JSON에서 키가 문자열이 된다. **프론트 계약: `Record<string,
  string>`** (T3의 `fetchVerdicts`가 이 형태로 받는다).
- `SecurityConfig`에 `GET /api/v1/compat/verdicts` permitAll 1줄.
- 존재하지 않는 base goodsNo의 동작은 기존 `worstVerdicts` 구현을 따른다(새 분기 금지) —
  실제 동작을 IT로 고정한다.

### Task 2-2 — `CompatVerdictsApiIT` (실 MySQL, 테스트 이름·단언 전량)

- `AHA 토너(goods 2) 기준 RETINOID·BHA 세럼이 CONFLICT다` —
  `base=2&candidates=159,190,4` → 159·190은 `"CONFLICT"`, 4는 CONFLICT 아님.
- `후보가 MAX_CANDIDATES를 넘으면 앞 50개만 판정된다`
- `비로그인으로도 200이다` — permitAll 검증.
- `존재하지 않는 base의 동작을 고정한다` — 실동작(404든 빈 맵이든)을 확인해 그대로 단언하고
  주석에 남긴다.

### T2 완료 조건

`./gradlew test integrationTest` 전량 통과, 실 MySQL 기동 + curl 스모크로 CONFLICT 응답 확인
(memory의 "curl 스모크 레시피" 참조).

---

## 5. 터미널 T3 — 화면 (웨이브 B)

### 실행 프롬프트 (그대로 붙여넣기)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
먼저 루트에서 git log --oneline -5 로 feature/composer-engine 과 feature/compat-verdicts-api 가
main에 머지돼 있는지 확인해라. 하나라도 없으면 중단하고 보고해라.
확인됐으면
  git worktree add ../뷰티보이-조합기UI -b feature/composer-ui
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 ../뷰티보이-조합기UI 인지
  - git log --oneline -1 이 루트에서 본 기점 커밋과 같은지
  - frontend/src/features/affinity/composer.ts 와
    backend/src/main/java/com/beautyboy/compat/CompatVerdictsController.java 가 존재하는지
  - git status가 깨끗한지

[2단계 — 실행]
docs/plans/2026-07-27-routine-composer.md 의 "터미널 T3" 절을 읽고 Task 3-1 ~ 3-5를 순서대로
실행해라. 설계 근거는 docs/superpowers/specs/2026-07-27-routine-composer-design.md §3.4·§4·§5.

규칙:
- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다.
- composer.ts의 함수·상수를 그대로 소비한다 — 수정 금지. 계약이 부족하면 중단하고 보고해라.
- CSS를 쓰기 전에 DESIGN.md를 읽고, 새 컴포넌트(픽 카드·전체 담기 CTA) 사양을 DESIGN.md에
  먼저 추가한 뒤 토큰만 참조해라. hex를 손으로 옮겨 적지 마라(태그 팔레트는 index.css의
  --tag-* 토큰이 코드 측 유일 사본이다).
- reason 문장·근거 문구를 코드에 하드코딩하지 마라 — reason은 서버 규칙 원문, 칩은 태그 데이터다.
- api/cart.ts의 기존 함수는 호출만 한다. 시그니처가 부족하면 중단하고 보고해라.
- 화면 태스크다: 개발서버를 띄우고 dry@beautyboy.dev 와 oily@beautyboy.dev 두 계정으로
  /main 을 각각 스크린샷 찍어(구성이 다른 것이 보여야 한다) 직접 본 뒤 경로를 보고서에 남겨라.
  바로 담기 토스트 스크린샷도 포함한다. 테스트 통과·curl은 이를 대체하지 못한다.
- 전체 테스트는 `npm test`, 타입 검사는 `npx tsc --noEmit -p tsconfig.app.json`.
완료하면 커밋하고, 스크린샷 경로와 미해결 사항을 보고해라.
```

### Task 3-1 — DESIGN.md: 픽 카드·전체 담기 CTA 사양 추가

픽 카드(그리드 첫 칸을 2칸 폭으로 확장 or 첫 줄 위 별도 카드 — 기존 그리드 리듬을 깨지 않는
쪽을 렌더로 확인해 선택하고 DESIGN.md에 기록), reason 문장·근거 칩 배치, [바로 담기] 버튼
(`{button-primary}` 파생), 전체 담기 CTA. 무채색 규칙 준수 — 액센트 배경 금지, 태그 칩만 팔레트.

### Task 3-2 — `useComposer.ts` (체인 오케스트레이션 — 구조 전량)

판단이 갈리는 곳은 **의존 쿼리 체인**뿐이므로 구조를 못 박는다:

```ts
// 단계 s의 쿼리는 이전 단계 픽이 확정되어야 enabled 된다 — 위에서부터 점진 렌더(설계 §3.4).
// step[0]: 풀 조회(size=POOL_SIZE, 태그 필터 없음) → composeStep(prevPick=null, verdicts=null)
// step[k]: 풀 조회 ∥ verdicts 조회(base=pick[k-1], candidates=풀 goodsNo들)
//          → 둘 다 오면 composeStep. verdicts 쿼리 실패는 null로 넘긴다(게이트 생략, throw 금지).
// 반환: StepComposition[] (미확정 단계는 undefined — 섹션은 스켈레톤 유지).
```

- 풀 쿼리키 `['routine-pool', categoryCode]` — 기존 `['routine-goods', …]`와 분리(size가 다르다).
- verdicts 쿼리키 `['compat-verdicts', baseGoodsNo, categoryCode]`.
- 신호(events·me·rules) 로딩은 기존 Main 로직 재사용. 규칙 로딩 실패 시 flowRules=[]로 진행
  (조합은 계속된다 — reason만 없어진다).

### Task 3-3 — `PickCard.tsx` + `RoutineSection` 개편

- `RoutineSection`은 `composition?: StepComposition`을 받아: `pick`이 있으면 픽 카드 + 대안 3,
  없으면(콜드스타트 포함 — pick은 있되 reason null) 기존 그리드. `pick === null`(풀 비었음)이면
  기존 기준선 그리드 폴백.
- 부분 채움·`rankByTexture` 배선 제거, `match.ts`에서 v2가 안 쓰는
  `matchByBehavior`/`matchByProfile`/`takeTopPerStep`을 삭제하고 그 테스트도 정리한다
  (`aggregate` 등 profile.ts는 그대로).
- 바로 담기: 클릭 → `fetchGoodsDetail`(캐시 활용) → 재고 있는 첫 옵션 → cart POST → 토스트
  "담았어요 — 옵션 변경은 장바구니에서". 전 옵션 품절이면 버튼 비활성 + "일시품절".
  Header 장바구니 배지 쿼리(`['cart']`) 무효화 — Detail.tsx의 기존 패턴을 따른다.
- 전체 담기: STEP 05 아래 CTA. 5개 픽 순차 담기, 실패는 건너뛰고 집계 토스트
  ("5개 담았어요" / "4개 담았어요 — 1개는 품절로 제외").

### Task 3-4 — MSW 핸들러 (`/compat/verdicts` — `Record<string,string>` 응답) + 픽스처

### Task 3-5 — 테스트 (이름·단언 전량)

- `Main: 콜드스타트면 5단계 픽이 전부 인기 1위이고 reason이 없다`
- `Main: 고민 프로필이 있으면 픽이 인기 1위와 달라지는 단계가 존재하고 reason이 렌더된다`
- `Main: 이전 픽과 CONFLICT인 후보는 다음 단계 픽·대안에 나오지 않는다` (MSW verdicts 픽스처)
- `Main: verdicts 요청이 실패해도 체인이 멈추지 않는다`
- `Main: 위 단계가 미확정이면 아래 섹션은 스켈레톤이다` — 점진 렌더.
- `PickCard: reason과 근거 칩(고민 일치 태그)이 렌더된다`
- `PickCard: 바로 담기 클릭 → 재고 첫 옵션으로 cart POST가 나가고 토스트가 뜬다`
- `PickCard: 전 옵션 품절이면 버튼이 비활성이다`
- `전체 담기: 픽 5개 중 1개 실패 시 4개 담고 집계 토스트를 띄운다`

### T3 완료 조건

`npm test` + `tsc -p` 통과, **스크린샷 3장**(dry 계정 메인 / oily 계정 메인 — 구성이 다른 것 /
바로 담기 토스트), docker 스택 기준 실기동 확인.

---

## 6. 머지 게이트 (오케스트레이터 세션)

1. 파일 소유권 준수 — 목록 밖 파일 변경 여부
2. 테스트 전량(`npm test` / `./gradlew test integrationTest`) + `tsc -p`
3. **T1: 시뮬레이션 실측 수치 5종이 보고서에 있고 임계를 넘는가** — 미달 보고면 가중치 재론
   (조작 통과가 아닌지 diff로 WEIGHTS 확인)
4. T2: curl로 CONFLICT 응답 재현
5. T3: **스크린샷 3장을 실제로 열어 두 계정 구성이 다른 것을 눈으로 확인**
6. reason·근거 문구 하드코딩 없음, raw hex 없음(DESIGN.md·토큰 경유)
7. 웨이브 A 머지 후 **합친 상태로 전체 테스트 재실행** 후 T3 시작
