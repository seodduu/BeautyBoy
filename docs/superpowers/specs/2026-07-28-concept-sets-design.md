# 컨셉 세트 A/B/C — /main 프로필 기반 세트 추천 설계

작성일: 2026-07-28. 상태: 사용자 승인 대기.

## 1. 배경과 목표

사용자가 `/main`(루틴 메인)에 들어왔을 때, 프로필(고민·피부타입)을 기반으로
**컨셉이 서로 다른 풀 루틴 세트 3개(A/B/C)** — 예: 모공 / 트러블 / 안티에이징 — 를
바로 제시한다. 각 세트는 화장품 특성(효능 태그)과 성분 궁합을 고려해 구성된다.

핵심 발견: 기존 루틴 조합기(`useComposer` → `composeStep`)가 이미
프로필 고민(`signals.concerns`) + 행동 친화도 + 전이 규칙 + **궁합 게이트(CONFLICT 제거)**로
5단계 세트를 만들고 있다. 이번 기능은 그 조합기에 **"어느 고민으로 조합할지"를
외부에서 지정하는 파라미터 하나를 추가**하고, 그 위에 탭 UI를 얹는 것이다.
**백엔드 변경 0** (새 API·마이그레이션 없음).

## 2. 결정 요약 (브레인스토밍 Q&A)

| 질문 | 결정 |
|---|---|
| 세트 구성 단위 | **풀 루틴 세트** — 기존 5단계(클렌징→토너→세럼→크림→선케어)를 컨셉에 맞춰 채운 것 |
| 컨셉 선정 | **프로필 파생** — 회원 고민 상위 3개, 부족분은 피부타입 파생, 최후엔 고정 3종 |
| 배치 위치 | **`/main` 최상단** — 히어로 바로 아래 탭, 선택 시 아래 5단계 조합기가 그 컨셉으로 재조합 |
| 비로그인/무프로필 | **고정 대표 3종 폴백**(모공·트러블·보습) + 프로필 등록 유도 문구 |

## 3. UX

### 3.1 배치

`/main` 구조 변경 — 히어로(`bb-main__intro`)와 앵커 네비(`bb-main__nav`) **사이**에
세트 탭 섹션을 추가한다:

```
┌──────────────────────────────┐
│ DAILY ROUTINE (기존 히어로)     │
├──────────────────────────────┤
│ 당신을 위한 세트                │  ← 신규 섹션
│ [A 모공 케어] [B 트러블 케어] [C 보습] │  ← 탭 3개
│ (폴백 시) "프로필을 등록하면 맞춤 세트로 바뀌어요" │
├──────────────────────────────┤
│ (기존) sticky 앵커 네비          │
│ (기존) STEP 01~05 조합기 섹션    │
│ (기존) 오늘의 루틴 N개 담기       │
└──────────────────────────────┘
```

### 3.2 인터랙션

- 진입 시 **세트 A가 기본 선택**된 상태로 조합기가 돈다.
- 탭 클릭 → `useComposer`에 해당 컨셉이 전달되어 5단계가 위에서부터 재조합된다
  (체인 특성상 점진 채움 — 기존 스켈레톤 동작 그대로).
- 탭에는 `세트 A` 표기 + 컨셉 한글 라벨(예: "모공 케어")을 함께 쓴다.
- 폴백 사용자에게는 탭 아래에 안내 한 줄 + 프로필 입력 링크(로그인 시 `/mypage` 프로필,
  비로그인 시 `/signup`).
- "오늘의 루틴 N개 담기"는 기존 그대로 — 현재 선택된 세트의 픽이 담긴다.
- 탭 상태는 페이지 로컬 state다. URL 동기화(`?set=`)·저장은 범위 밖(§8).

## 4. 컨셉 파생 로직 (신규, 순수 함수)

`frontend/src/features/affinity/setConcepts.ts` 신규. 판단이 갈리는 곳이므로 전량 명세한다.

```ts
import type { SkinType } from '../../api/auth';
import { SKIN_TYPE_CONCERNS, effectiveConcerns, type DerivedConcern } from './profile';

/** 세트 수. 탭 3개가 한 줄에 들어가는 최대치이자 "고르는 부담이 없는" 수. */
export const SET_COUNT = 3;

/**
 * 최후 폴백 3종 — 비로그인·무프로필 사용자에게 보이는 대표 컨셉.
 * 근거: pore/trouble은 남성 지성 피부의 최빈 고민, moisture는 건성 계열 커버.
 * 세 슬러그 모두 concern_target_rule(19행)에 타겟 규칙이 있어 reason 문장이 보장된다.
 */
export const FALLBACK_CONCEPTS: DerivedConcern[] = ['pore', 'trouble', 'moisture'];

export interface SetConcept {
  /** composeStep의 signals.concerns에 단독으로 들어갈 슬러그. */
  slug: DerivedConcern;
  /** 탭에 쓸 한글 라벨 (CONCEPT_LABELS). */
  label: string;
  /** true면 프로필에서 나온 컨셉(직접 선택 또는 피부타입 파생), false면 고정 폴백. */
  personalized: boolean;
}

/**
 * 파생 사다리 — 위에서부터 채우고, 슬러그 중복은 제거하며, 항상 정확히 SET_COUNT개를 반환한다:
 *   1) effectiveConcerns(concerns, skinType) 순서대로  ← 직접 고른 고민(선택 순서 보존)
 *      또는 (고민이 비었으면) 피부타입 파생. personalized=true.
 *   2) 부족분은 SKIN_TYPE_CONCERNS[skinType]에서 보충 (1에서 이미 파생됐으면 중복 제거로 소거).
 *      personalized=true.
 *   3) 그래도 부족하면 FALLBACK_CONCEPTS에서 순서대로 보충. personalized=false.
 * 비로그인(concerns=[], skinType=null)이면 1·2가 비므로 결과는 폴백 3종 전체가 된다.
 */
export function deriveSetConcepts(
  concerns: string[],
  skinType: SkinType | null,
): SetConcept[];
```

라벨 맵 — `SkinProfileFields.tsx`의 `CONCERNS` 라벨과 어긋나면 안 되므로 같은 한글을 쓴다.
파생 전용 `gentle`은 그 상수에 없으므로 여기서 정의한다:

```ts
/** 고민 9종 라벨은 SkinProfileFields.tsx CONCERNS와 동일 문구. gentle만 이 파일이 원본. */
const CONCEPT_LABELS: Record<DerivedConcern, string> = {
  exfoliate: '각질', sebum: '피지', pore: '모공', trouble: '트러블',
  soothe: '진정', moisture: '보습', barrier: '장벽', bright: '브라이트닝',
  'anti-aging': '안티에이징', gentle: '저자극',
};
```

(구현 시 `SkinProfileFields.tsx`의 실제 라벨 문자열을 확인해 그대로 옮긴다 —
위 표기는 방향이지 확정 문구가 아니다. 두 곳이 갈라지면 탭과 프로필 화면이 딴말을 한다.)

## 5. 조합기 변경 (`useComposer`)

시그니처 변경:

```ts
export function useComposer(concernOverride?: DerivedConcern | null): StepState[];
```

- `concernOverride`가 주어지면 `signals.concerns`를 **`[concernOverride]` 하나로 대체**한다.
  전체 고민을 유지한 채 가중치만 올리는 방식은 쓰지 않는다 — 세트 간 구성이 겹쳐
  "각각의 특성이 달라야 한다"는 요구가 무너진다. 단독 대체가 차별화를 보장한다.
- `textures`(사용감 tie-break)와 `affinity`(행동 점수)는 **유지**한다 — 컨셉은 "무엇을
  추천할까"의 축이고, 사용감·행동은 "같은 후보 중 무엇을 앞에 둘까"의 축이라 충돌하지 않는다.
- `composeStep`·`profile.ts`는 **수정하지 않는다.** `pickConcernReason`이 concerns 순서로
  규칙을 찾으므로, 단독 배열이면 해당 컨셉의 reason 문장이 자동으로 뽑힌다.
- 새 react-query 키는 없다. 풀 조회 키(`['routine-pool', cat]`)는 컨셉과 무관해 세트 간
  캐시가 공유되고(탭 전환이 빠른 이유), 궁합 키(`['compat-verdicts', base, cat]`)는 픽이
  바뀌면 base가 바뀌어 자연히 분리된다. `composeStep`은 순수 함수라 signals가 바뀌면
  재계산될 뿐이다.
- "확정을 한 번만 한다"(signalsReady) 원칙과의 관계: 탭 전환은 사용자가 명시적으로 일으킨
  재조합이므로 원칙 위반이 아니다. 오버라이드 값이 바뀌면 체인이 처음부터 다시 돈다.

`Main.tsx` 변경:

```ts
const meQuery = useQuery({ queryKey: ['me'], queryFn: fetchMe, enabled: !!accessToken });
// ['me']는 useComposer 내부와 같은 키 — 네트워크 왕복은 캐시가 흡수한다.
const concepts = deriveSetConcepts(meQuery.data?.concerns ?? [], skinType);
const [selected, setSelected] = useState(0);
const states = useComposer(concepts[selected].slug);
```

주의: `me` 도착 전에 concepts를 확정하면 프로필이 붙는 순간 탭이 갈아엎어진다.
**비로그인이거나 `meQuery`가 settle된 뒤에만** 탭·조합기를 활성화한다
(기존 `signalsReady`와 같은 판단 — 그 전엔 탭 스켈레톤).

## 6. UI 컴포넌트

- `frontend/src/components/routine/SetTabs.tsx` 신규 — props:
  `{ concepts: SetConcept[]; selected: number; onSelect: (i: number) => void }`.
  role="tablist"/"tab" + `aria-selected` 적용. 폴백 안내 문구는 `concepts.every(c => !c.personalized)`일 때 렌더.
- 스타일: **`DESIGN.md`에 세트 탭 사양이 현재 없다.** 구현 전에 `DESIGN.md`에
  "세트 탭" 컴포넌트 절(무채색 사다리 내 선택/비선택 상태, 간격 토큰)을 먼저 추가하고
  보고한 뒤 토큰 참조로만 스타일링한다. 액센트 배경 금지·`word-break: keep-all` 등
  한글 적용 절 준수.

## 7. 에러 처리 · 폴백

| 상황 | 동작 |
|---|---|
| 비로그인 / 프로필 미입력 | 폴백 3종 탭 + "프로필을 등록하면 맞춤 세트로 바뀌어요" 안내. 조합기는 기존대로 인기순 기준선. |
| `['me']` 조회 실패 | 비로그인과 동일 취급 (폴백 3종). 던지지 않는다. |
| 규칙/궁합 조회 실패 | 기존 조합기 정책 그대로 — reason 없이·게이트 없이 진행 (설계 §3.3 상속). |
| 고민 1~2개만 선택 | 파생 사다리 2·3단이 채워 항상 탭 3개. |

## 8. 범위 제외 (YAGNI)

- 세트 이름 자동 생성 (A/B/C + 컨셉 라벨로 충분)
- 세트 저장·공유, URL `?set=` 동기화
- 랜딩(`/`) 티저 카드 (추후 확장 여지만 남김)
- 백엔드 세트 API·스키마

## 9. 테스트 (전량 — 이름과 단언이 곧 사양)

### `setConcepts.test.ts` (신규)

| 케이스 | 단언 |
|---|---|
| 고민 3개 이상 선택한 회원 | 선택 순서대로 앞 3개, 전부 `personalized: true` |
| 고민 1개(pore) + OILY | `[pore(개인화), sebum(개인화·파생), trouble(폴백)]` — 파생의 pore는 중복 제거로 소거, 폴백의 trouble이 3번째 |
| 고민 0개 + DRY | `[moisture, barrier]`(파생·개인화) + `[pore]`(폴백) — 폴백의 moisture는 중복 소거 |
| 비로그인 (`[], null`) | 폴백 3종 그대로, 전부 `personalized: false` |
| SENSITIVE 무고민 | `gentle`이 포함되고 라벨이 비어 있지 않다 |
| 모든 경우 | 반환 길이 === 3, 슬러그 중복 없음 |

### `composer.test.ts` (기존 파일에 추가)

| 케이스 | 단언 |
|---|---|
| concerns가 `['pore']` 단독일 때 | pore 태그 보유 후보가 무태그 인기 1위를 제치고 pick이 된다 (concern 가중치 2.0 > popularity 0.3 검증) |
| concerns 단독 + textures 유지 | 동점 후보 간 texture 일치가 앞선다 (오버라이드가 texture 축을 죽이지 않음) |
| concerns `['pore']`일 때 reason | `pickConcernReason`이 pore 타겟 규칙의 reason을 반환한다 |

### `Main.test.tsx` (기존 파일에 추가)

| 케이스 | 단언 |
|---|---|
| 프로필 있는 회원 렌더 | 탭 3개가 role="tab"으로 렌더되고 첫 탭이 `aria-selected` |
| 탭 클릭 | `useComposer`가 새 컨셉 슬러그로 호출된다 (조합 결과 자체는 composer 유닛 소관) |
| 비로그인 렌더 | 폴백 3종 라벨 + 안내 문구가 보인다 |
| 기존 | '루틴 5단계를 상수 순서대로 렌더한다' 등 기존 단언 전부 그대로 통과 |

## 10. 실행 모델 · DoD

- **단일 터미널 / 단일 브랜치** (`feature/concept-sets`) — 프론트 한 도메인만 만지므로
  병렬 분할 없음. 구현 계획(writing-plans)에서 태스크 분해와 실행 프롬프트를 확정한다.
- 태스크 실행 서브에이전트 모델: **sonnet** (모델 배분 예외 3종에 해당하지 않음 —
  궁합 "엔진"은 건드리지 않고 기존 인터페이스를 소비만 한다).
- DoD:
  1. `npm test` 녹색 (vitest 단독 실행 금지 — e2e 스펙 수집 거짓 적신호).
  2. `npx tsc --noEmit -p tsconfig.app.json` 녹색.
  3. **개발서버를 띄워 스크린샷으로 확인**: (a) 탭 3개 렌더, (b) 탭 전환 시 세럼 단계
     픽이 바뀜, (c) 비로그인 폴백 문구. 스크린샷 경로를 보고서에 남기고 리뷰어가 열어 본다.
  4. `DESIGN.md`에 세트 탭 절 추가 커밋이 스타일 커밋보다 먼저다.
