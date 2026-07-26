# 구현 계획 — 메인 개인화(기기 측 관심 프로필)

> 설계: `docs/superpowers/specs/2026-07-27-main-personalization-design.md` — 범위·근거의 유일한 진실.
> 이 문서는 **태스크·터미널 운용**의 유일한 진실이다. 설계와 어긋나면 설계가 이긴다.

## 0. 사람이 할 일 (사전 조건 2줄)

터미널을 열기 전에 프로젝트 루트에서:

```
git log --oneline -1     # 27ff6ec (docs(spec): 메인 개인화 설계 확정) 이상인지 확인
git status               # 깨끗한지 확인
```

이 계획서와 설계 문서가 **커밋돼 있어야** 새 worktree에 딸려간다.

## 1. 웨이브·터미널 분할

| 웨이브 | 터미널 | 브랜치 | 범위 | 모델 |
|---|---|---|---|---|
| A | T1 | `feature/profile-tag-slugs` | 프로필 태그 교체 + 팔레트 토큰화 + 색·선택 반전 | sonnet |
| A | T2 | `feature/flow-rules-api` | V79 `concern_target_rule` + 규칙 배포 API | sonnet |
| B | T3 | `feature/main-personalization` | `features/affinity/` + 메인 배선 | sonnet |

- **T1·T2는 완전 병렬**이다. 파일이 하나도 겹치지 않고, Flyway 번호를 아래에서 못 박아
  (T1=**V78**, T2=**V79**) 마이그레이션 충돌도 없다. **번호를 즉흥으로 바꾸지 않는다 — 공유 계약이다.**
- **T3는 웨이브 A 두 브랜치가 모두 `main`에 머지된 뒤** 시작한다. T2의 API 계약과 T1의 슬러그
  집합에 동시에 의존한다.
- 모델은 셋 다 sonnet. 설계서가 우선순위·상한·가중치를 값으로 못 박았고, 틀려도 돈·재고 사고가
  아니라 추천이 어긋날 뿐이며 테스트가 전량 잡는다(CLAUDE.md opus 예외 3종에 해당하지 않는다).

### 파일 소유권

| 터미널 | 소유 파일 |
|---|---|
| T1 | `backend/…/migration/V78__migrate_concerns_to_tag_slug.sql`(신규), `frontend/src/index.css`, `frontend/src/components/ui/Tag.css`, `frontend/src/components/skin-profile/SkinProfileFields.tsx`·`.css`, `frontend/src/api/auth.ts`, `frontend/src/pages/Signup.tsx`, `frontend/src/pages/mypage/MyProfile.tsx`, 각 대응 테스트, `DESIGN.md` |
| T2 | `backend/…/migration/V79__concern_target_rule.sql`(신규), `backend/…/routine/ConcernTargetRule.java`·`ConcernTargetRuleRepository.java`·`FlowRuleController.java`·`FlowRuleService.java`·`dto/*`(신규), `backend/…/config/SecurityConfig.java`, 대응 테스트 |
| T3 | `frontend/src/features/affinity/*`(신규), `frontend/src/api/routine.ts`, `frontend/src/types/routine.ts`, `frontend/src/pages/Main.tsx`, `frontend/src/components/routine/RoutineSection.tsx`·`.css`, `frontend/src/components/goods/GoodsCard.tsx`, `frontend/src/pages/Detail.tsx`, `frontend/src/pages/GoodsList.tsx`, `frontend/src/mocks/handlers.ts`, 각 대응 테스트 |

목록 밖 파일은 수정하지 않는다. 안 맞으면 **고치지 말고 보고**한다.

---

## 2. 터미널 T1 — 프로필 태그 교체 · 팔레트 토큰화 · 선택 반전

### 실행 프롬프트 (그대로 붙여넣기)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add ../뷰티보이-프로필태그 -b feature/profile-tag-slugs
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 ../뷰티보이-프로필태그 인지
  - git log --oneline -1 이 루트에서 본 기점 커밋과 같은지
  - docs/plans/2026-07-27-main-personalization.md,
    docs/superpowers/specs/2026-07-27-main-personalization-design.md, DESIGN.md 가 실제로 존재하는지
  - git status가 깨끗한지

[2단계 — 실행]
docs/plans/2026-07-27-main-personalization.md 의 "터미널 T1" 절을 처음부터 끝까지 읽고
Task 1-1 ~ 1-5를 순서대로 실행해라. 설계 근거가 필요하면
docs/superpowers/specs/2026-07-27-main-personalization-design.md §4, §8을 본다.

규칙:
- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다.
- DESIGN.md에 없는 색·간격을 CSS에 직접 만들지 않는다. DESIGN.md를 먼저 고치고(Task 1-1),
  그 표의 hex를 index.css 토큰으로 한 번만 옮긴다. CSS 파일에 hex를 두 번 적지 않는다.
- 이 절의 "파일 소유권 T1" 목록 밖 파일은 수정하지 않는다. 필요하면 중단하고 보고한다.
- 화면 태스크이므로 개발서버를 띄우고 /signup 2스텝과 /mypage/profile을
  스크린샷으로 직접 확인한 뒤 파일 경로를 보고서에 남긴다. 테스트 통과가 이를 대체하지 못한다.
- 전체 테스트는 `npm test`로 판정한다(`npx vitest run`은 e2e 스펙을 주워 항상 1 failed가 난다).
  타입 검사는 반드시 `npx tsc --noEmit -p tsconfig.app.json` (`-p` 없으면 0파일 거짓 녹색).
완료하면 커밋하고, 무엇을 바꿨는지·스크린샷 경로·미해결 사항을 보고해라.
```

### Task 1-1 — DESIGN.md 세 절 추가/수정

`DESIGN.md` "태그 컬러" 절(391행~)에 아래를 반영한다. **여기가 hex의 유일한 출처다.**

1. **415행 "TEXTURE는 이 팔레트 대상이 아니다 — 무채색 유지" 줄을 삭제**하고, 표에 3행 추가:

| slug | bg | text |
|---|---|---|
| `fresh` 산뜻함 | `#ECFEFF` | `#155E75` |
| `dewy` 촉촉함 | `#EFF6FF` | `#1E40AF` |
| `matte` 매트 | `#F1F5F9` | `#334155` |

   근거 문장을 함께 적는다: *"저채도 3종(청록·파랑·회청)으로 묶어 효과 태그의 다채로움과 축이
   다름을 유지한다. 효과 태그 일부와 색상환에서 가까우나 두 그룹은 항상 별도 `fieldset`에 있어
   나란히 놓이지 않는다."*

2. **신규 절 "피부 프로필 색 배정"** — 피부타입 4종은 태그가 아니라 팔레트가 없다.
   신호 매핑(설계 §6.2)의 대표 태그에서 색을 빌린다.

| 피부타입 | 대표 태그 | bg | text |
|---|---|---|---|
| DRY 건성 | `moisture` | `#E0F2FE` | `#0369A1` |
| OILY 지성 | `sebum` | `#CCFBF1` | `#0F766E` |
| COMBINATION 복합성 | `barrier` | `#FEF9C3` | `#854D0E` |
| SENSITIVE 민감성 | `gentle` | `#FCE7F3` | `#BE185D` |

   *COMBINATION의 신호 태그는 `[moisture, sebum]`이지만 그 두 색은 DRY·OILY가 이미 쓰므로 색만
   `barrier`에서 빌린다 — 네 카드가 서로 구분되는 것이 우선이다.*

3. **신규 절 "선택 상태 — 색 반전"**

| 요소 | 미선택 | 선택 |
|---|---|---|
| 고민·사용감 칩 | 틴트 배경(`bg`) + 같은 계열 진한 글자(`text`) | 배경 = `text` 색, 글자 = 흰색, 앞에 `✓` |
| 피부타입 카드 | 흰 배경 + 좌측 3px 컬러 바(`text` 색) | 틴트 배경(`bg`) + 2px 테두리(`text` 색) + 제목이 `text` 색 + `✓` |

   **반전 배경 대비 보정** — 흰 글자 대비 4.5:1(WCAG AA) 미달 2종만 한 단계 진한 값을 쓴다.
   나머지는 `text` 색을 그대로 반전 배경에 쓴다.

| slug | `text` | 반전 배경 | 사유 |
|---|---|---|---|
| `barrier` | `#A16207` | `#854D0E` | `#A16207` 위 흰 글자 ≈ 4.05:1 |
| `soothe` | `#15803D` | `#166534` | `#15803D` 위 흰 글자 ≈ 4.35:1 |

   *색은 상태의 유일한 단서가 아니다 — `✓`와 `aria-pressed`에 얹히는 보강 단서다.*

### Task 1-2 — 팔레트를 CSS 토큰으로 한 번만 옮긴다

지금 `Tag.css`는 DESIGN.md의 hex를 손으로 옮겨 적은 상태다. 여기에 프로필 칩까지 더하면
**같은 hex가 세 곳에 흩어진다.** 토큰으로 한 번만 옮기고 두 소비자가 참조한다.

`frontend/src/index.css`의 `:root`에 slug당 3개 변수를 정의한다.

```css
/* ── 태그 팔레트 (DESIGN.md "태그 컬러" 표 그대로 — 여기가 코드 측 유일한 사본이다) ──
   --tag-{slug}-bg   틴트 배경 (미선택 칩·선택된 피부타입 카드)
   --tag-{slug}-ink  진한 글자 (미선택 칩 글자·카드 좌측 바·테두리)
   --tag-{slug}-fill 반전 배경 (선택된 칩). 기본은 ink와 같은 값이고,
                     흰 글자 대비 4.5:1 미달인 barrier·soothe만 한 단계 진한 값을 쓴다. */
--tag-moisture-bg: #e0f2fe;  --tag-moisture-ink: #0369a1;  --tag-moisture-fill: #0369a1;
--tag-soothe-bg:   #dcfce7;  --tag-soothe-ink:   #15803d;  --tag-soothe-fill:   #166534;
--tag-barrier-bg:  #fef9c3;  --tag-barrier-ink:  #a16207;  --tag-barrier-fill:  #854d0e;
/* … 나머지 slug도 같은 형식으로. fill은 barrier·soothe를 뺀 전부가 ink와 같은 값이다. */
```

정의 대상 slug 21종 = 기존 팔레트 15 + `gentle` `pore` `trouble` (이미 표에 있음) + 신규
`fresh` `dewy` `matte`. **DESIGN.md 표에 있는 모든 행을 빠짐없이 옮긴다.**

그 다음 `Tag.css`의 `.bb-tag--{slug}` 블록들을 hex → `var(--tag-{slug}-bg)` /
`var(--tag-{slug}-ink)`로 치환한다. **선언 구조·클래스 이름·무채색 폴백 경로는 건드리지 않는다** —
기존 `Tag.test.tsx`가 그대로 통과해야 한다.

`fresh`/`dewy`/`matte`는 이제 색이 생겼으므로 `.bb-tag--texture` 무채색 폴백 대신 slug 클래스가
이긴다(기존 CSS 순서상 slug 블록이 뒤에 오므로 자동). `Tag.test.tsx`의
*"TEXTURE는 무채색"* 취지 테스트가 있다면 **"slug 컬러 클래스를 받는다"로 갱신**한다.

### Task 1-3 — 슬러그 집합 교체 (테스트 먼저)

`frontend/src/api/auth.ts`:

```ts
/** 프로필 고민 — tag.slug와 같은 어휘. 설계 §4.1의 9종. */
export type ConcernSlug =
  | 'exfoliate' | 'sebum' | 'pore' | 'trouble' | 'soothe'
  | 'moisture' | 'barrier' | 'bright' | 'anti-aging';

/** 선호 사용감 — TEXTURE 태그 3종. 같은 concerns 컬럼에 함께 저장된다(설계 §4.1). */
export type TextureSlug = 'fresh' | 'dewy' | 'matte';

/** 서버 concerns 컬럼에 실려 가는 값의 합집합. 기존 `Concern` 유니온을 이 이름으로 대체한다. */
export type Concern = ConcernSlug | TextureSlug;
```

`SkinProfileFields.tsx`의 상수를 교체한다(표시명은 `tag` 테이블의 `name`과 같게 맞춘다):

```ts
export const CONCERNS: { value: ConcernSlug; label: string }[] = [
  { value: 'exfoliate',  label: '각질' },
  { value: 'sebum',      label: '피지' },
  { value: 'pore',       label: '모공' },
  { value: 'trouble',    label: '트러블' },
  { value: 'soothe',     label: '진정' },
  { value: 'moisture',   label: '보습' },
  { value: 'barrier',    label: '장벽' },
  { value: 'bright',     label: '브라이트닝' },
  { value: 'anti-aging', label: '안티에이징' },
];

export const TEXTURES: { value: TextureSlug; label: string }[] = [
  { value: 'fresh', label: '산뜻함' },
  { value: 'dewy',  label: '촉촉함' },
  { value: 'matte', label: '매트' },
];
```

- 사용감은 **별도 `fieldset`**("선호하는 사용감")으로 렌더하되, 값은 같은 `concerns` 배열에
  담아 상위(`Signup`/`MyProfile`)로 올린다. 상위는 배열 하나만 다루므로 제출 로직이 그대로다.
- `onToggleConcern` 하나로 두 그룹을 모두 처리한다 — 콜백을 늘리지 않는다.
- 기존 `aria-labelledby`/`aria-describedby`/`aria-pressed` 접근성 설계를 **그대로 유지**한다.

**테스트(먼저 작성):**
- `SkinProfileFields.test.tsx`
  - "고민 칩 9개와 사용감 칩 3개가 각각 별도 fieldset에 렌더된다"
  - "사용감 칩을 누르면 onToggleConcern이 'dewy'로 호출된다" (같은 배열로 합류함을 증명)
  - "선택된 칩은 aria-pressed=true 이고 반전 클래스(`bb-chip--on`)를 함께 받는다"
  - "선택된 피부타입 카드는 `bb-skin-type-card--active` 와 slug 클래스를 함께 받는다"
- `MyProfile.test.tsx` / `Signup.test.tsx`: 기존 `PORE` 등을 새 슬러그로 갱신. **저장 요청 본문에
  고민·사용감이 한 배열로 실린다**를 단언한다.

### Task 1-4 — 색·선택 반전 CSS

`SkinProfileFields.css`에 slug별 클래스를 추가한다. hex를 다시 적지 않고 Task 1-2의 토큰만 쓴다.

```css
/* 칩: 미선택 = 틴트 배경 + 진한 글자 / 선택 = 반전(fill 배경 + 흰 글자) */
.bb-chip--moisture            { background: var(--tag-moisture-bg); color: var(--tag-moisture-ink); border-color: transparent; }
.bb-chip--moisture.bb-chip--on{ background: var(--tag-moisture-fill); color: #fff; }
/* … 12종 반복. 반복이 지루하면 slug 목록으로 생성하되 토큰 이름 규칙은 지킨다. */

/* 피부타입 카드: 한 단계 절제 — 미선택은 흰 배경 + 좌측 3px 바 */
.bb-skin-type-card--dry                              { border-left: 3px solid var(--tag-moisture-ink); }
.bb-skin-type-card--dry.bb-skin-type-card--active    { background: var(--tag-moisture-bg);
                                                       border: 2px solid var(--tag-moisture-ink); }
.bb-skin-type-card--dry.bb-skin-type-card--active
  .bb-skin-type-card__label                          { color: var(--tag-moisture-ink); }
```

- 체크 표시(`✓`)는 **CSS `::after`가 아니라 마크업**으로 넣고 `aria-hidden="true"`를 준다 —
  스크린리더에는 `aria-pressed`가 이미 상태를 알린다.
- 선택 시 `border`가 1px→2px로 바뀌면 레이아웃이 1px 튄다. **미선택 쪽에 `border: 2px solid
  transparent`를 깔아** 두께를 고정한다(카드의 좌측 3px 바는 `border-left`가 아니라
  `box-shadow: inset 3px 0 0`으로 넣으면 두 규칙이 서로 안 싸운다 — 둘 중 하나로 통일하고
  튀지 않는 것을 스크린샷으로 확인한다).
- `word-break: keep-all` 적용(한글 규칙).

### Task 1-5 — V78 마이그레이션

`backend/src/main/resources/db/migration/V78__migrate_concerns_to_tag_slug.sql` —
설계 §4.2의 SQL을 **그대로** 넣는다(주석 포함).

**테스트:** 실 MySQL clean 로드(V1~V78) 후 `member_profile.concerns`에 대문자 구 어휘가
한 건도 남지 않고, V64 시드 4건이 새 슬러그로 조회된다.

### T1 완료 조건

- `npm test` 전량 통과 / `npx tsc --noEmit -p tsconfig.app.json` 통과
- 실 MySQL clean 로드 + `ddl-auto=validate` 기동 확인
- **스크린샷 4장**: `/signup` 2스텝(선택 전 / 3개 선택 후), `/mypage/profile`(선택 전 / 후)
- `Tag.css`·`SkinProfileFields.css` 어디에도 raw hex가 남아 있지 않다(토큰만 참조)

---

## 3. 터미널 T2 — 규칙 배포 API

### 실행 프롬프트 (그대로 붙여넣기)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add ../뷰티보이-규칙API -b feature/flow-rules-api
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 ../뷰티보이-규칙API 인지
  - git log --oneline -1 이 루트에서 본 기점 커밋과 같은지
  - docs/plans/2026-07-27-main-personalization.md 와
    docs/superpowers/specs/2026-07-27-main-personalization-design.md 가 실제로 존재하는지
  - git status가 깨끗한지

[2단계 — 실행]
docs/plans/2026-07-27-main-personalization.md 의 "터미널 T2" 절을 처음부터 끝까지 읽고
Task 2-1 ~ 2-4를 순서대로 실행해라. 설계 근거는 같은 저장소의
docs/superpowers/specs/2026-07-27-main-personalization-design.md §5를 본다.

규칙:
- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다.
- Flyway 번호는 V79로 고정이다. 바꾸지 마라(다른 터미널이 V78을 쓴다).
- routine 패키지는 자기 테이블만 직접 접근한다. tag·goods 테이블을 직접 조인하지 않는다
  (Task 2-2의 시드 검증 테스트는 예외 — 테스트 코드이고, 시드 정합을 보는 것이 목적이다).
- H2 create-drop은 스키마 불일치를 가린다. 반드시 실 MySQL clean 로드 + ddl-auto=validate로
  확인한다(임시 MySQL 기동은 memory의 "curl 스모크 레시피"를 따른다).
- 이 절의 "파일 소유권 T2" 목록 밖 파일은 수정하지 않는다. 필요하면 중단하고 보고한다.
완료하면 커밋하고, 시드 검증 테스트가 출력한 후보 수와 조정 내역을 보고해라.
```

### Task 2-1 — V79 테이블

`V79__concern_target_rule.sql` — 설계 §5.1의 DDL을 **그대로** 넣는다. 엔티티
`ConcernTargetRule`(`@Immutable` 불필요, 읽기 전용으로만 쓴다)과
`ConcernTargetRuleRepository extends JpaRepository<ConcernTargetRule, Long>`을 만든다.
리포지토리는 `findAll()`만 쓰므로 메서드를 추가하지 않는다.

### Task 2-2 — 시드 20행 (같은 V79 파일 하단)

**슬러그 10개 × 2행.** `gentle`은 프로필에서 직접 못 고르지만 `SENSITIVE` 파생 태그이므로
반드시 포함한다(설계 §6.2).

```sql
INSERT INTO concern_target_rule
  (concern_tag_slug, to_category_code, to_tag_slug, reason, priority) VALUES
('exfoliate',  'C002003', 'exfoliate',  '각질이 고민이라면 주 1~2회 필링부터 시작하세요', 10),
('exfoliate',  'C001001', 'soothe',     '각질 케어 뒤엔 진정 토너로 완충해 주세요', 20),
('sebum',      'C002001', 'sebum',      '피지가 고민이라면 세안부터 피지 잡는 제품으로', 10),
('sebum',      'C001001', 'sebum',      '세안 뒤 유분 정돈까지 이어가세요', 20),
('pore',       'C001002', 'pore',       '모공은 세럼 단계에서 정면으로 잡는 게 효율적이에요', 10),
('pore',       'C002001', 'pore',       '모공 관리는 잘 씻어내는 것부터예요', 20),
('trouble',    'C001002', 'trouble',    '트러블이 고민이라면 세럼으로 집중 관리하세요', 10),
('trouble',    'C002001', 'trouble',    '트러블 피부일수록 세정 단계 선택이 중요해요', 20),
('soothe',     'C001001', 'soothe',     '예민한 날엔 진정 토너로 결부터 달래 주세요', 10),
('soothe',     'C001002', 'soothe',     '진정 성분 세럼으로 한 겹 더 얹어 보세요', 20),
('moisture',   'C001003', 'moisture',   '보습이 고민이라면 덮어 가두는 크림이 핵심이에요', 10),
('moisture',   'C001002', 'moisture',   '크림 전에 수분 세럼으로 채워 두세요', 20),
('barrier',    'C001003', 'barrier',    '장벽이 무너졌다면 크림으로 지붕부터 올리세요', 10),
('barrier',    'C001002', 'barrier',    '세라마이드 계열 세럼으로 장벽을 채워 보세요', 20),
('bright',     'C001002', 'bright',     '톤이 고민이라면 브라이트닝 세럼이 출발점이에요', 10),
('bright',     'C004001', 'uv',         '미백 관리의 절반은 자외선 차단이에요', 20),
('anti-aging', 'C001002', 'anti-aging', '주름 관리는 세럼 단계에서 시작하세요', 10),
('anti-aging', 'C001003', 'anti-aging', '고영양 크림으로 마무리하면 더 오래 갑니다', 20),
('gentle',     'C002001', 'gentle',     '민감한 피부일수록 순한 세정부터예요', 10),
('gentle',     'C001001', 'gentle',     '자극 없는 토너로 결만 정돈해 주세요', 20);
```

**이 20행은 카테고리 의미(V12)와 태그 파생 규칙(V72)에서 유도한 것이고, 실 상품 수로는
아직 검증되지 않았다.** Task 2-3의 검증 테스트를 먼저 돌리고, **후보 4개 미만인 행은 삭제한다**
(끼워 맞추려고 태그를 바꾸지 않는다 — 후보가 안 나오는 규칙은 화면에서 폴백만 유발한다).
삭제한 행과 실측 후보 수를 보고서에 남긴다.

### Task 2-3 — 시드 검증 테스트 (통합, 실 MySQL)

- `모든 concern_target_rule 행에 후보가 4개 이상이다` — 각 행에 대해
  `goods.category_code LIKE '{to_category_code}%'` AND `goods_tag`에 `to_tag_slug` 보유
  AND `status <> 'HIDDEN'` 인 상품 수를 세고, **4 미만인 행이 있으면 그 행과 실측 수를 출력하며
  실패**한다.
- `concern_tag_slug와 to_tag_slug가 전부 tag 테이블에 실재한다` — 물리 FK가 없으므로 여기서 잡는다.
- `UNIQUE (concern_tag_slug, to_category_code) 제약이 실제로 걸려 있다` — 중복 삽입이 실패한다.
- `to_category_code가 전부 7자(중분류)다` — leaf 10자를 넣으면 접두사 매칭이 무의미해진다.

### Task 2-4 — 배포 API

설계 §5.2의 DTO·엔드포인트를 그대로 구현한다. **판단이 갈리는 부분은 ETag 계산과 캐싱뿐이다:**

```java
/**
 * 규칙은 시드 전용이고 관리자 CRUD가 없다(설계 §5.2). 따라서 기동 시 1회 계산해 캐싱하고,
 * 요청마다 DB를 읽지 않는다 — 이 엔드포인트는 전 사용자가 앱 진입마다 부르는 자리라
 * 매 요청 조회는 그 자체가 이 설계가 없애려던 비용이다.
 *
 * 해시 입력은 두 테이블 전량을 "정렬된 순서로" 직렬화한 문자열이다. 정렬을 빼면 DB가 돌려주는
 * 순서에 따라 같은 데이터가 다른 ETag를 만들어, 내용이 그대로인데 304가 안 나가는 일이 생긴다.
 */
@PostConstruct
void loadRules() {
    List<FlowRuleView> flow = flowRuleRepository.findAll().stream()
            .map(FlowRuleView::from)
            .sorted(Comparator.comparing(FlowRuleView::fromCategoryCode)
                    .thenComparing(v -> v.fromTagSlug() == null ? "" : v.fromTagSlug())
                    .thenComparing(FlowRuleView::toCategoryCode))
            .toList();
    List<ConcernRuleView> concern = concernRuleRepository.findAll().stream()
            .map(ConcernRuleView::from)
            .sorted(Comparator.comparing(ConcernRuleView::concernTagSlug)
                    .thenComparing(ConcernRuleView::toCategoryCode))
            .toList();
    // 두 목록 사이에 구분자를 넣는다 — 없으면 경계가 모호해져 서로 다른 데이터가
    // 같은 문자열로 이어붙어 같은 해시를 만들 수 있다.
    String version = sha256Hex(serialize(flow) + "##" + serialize(concern)).substring(0, 16);
    this.cached = new FlowRulesResponse(version, flow, concern);
}
```

컨트롤러는 `If-None-Match` 헤더가 캐시된 `version`과 같으면 `304`(본문 없음), 아니면
`200` + `ETag` 헤더 + 본문을 돌려준다. `SecurityConfig`에
`GET /api/v1/routine/flow-rules` permitAll 1줄을 추가한다.

**테스트:**
- `flow-rules 응답에 flowRules 12행과 concernRules 전량이 실린다`
- `응답 ETag 헤더와 body.version이 같은 값이다`
- `같은 ETag로 If-None-Match 재요청하면 304이고 본문이 비어 있다`
- `다른 ETag로 요청하면 200이고 본문이 실린다`
- `두 번 호출해도 version이 같다` (기동 시 1회 계산·캐싱 증명)

### T2 완료 조건

- 백엔드 테스트 전량 통과, 실 MySQL clean 로드(V1~V79) + `ddl-auto=validate` 기동
- curl 스모크: `-H 'If-None-Match: <version>'`으로 **304**를 눈으로 확인
- 삭제한 시드 행과 실측 후보 수를 보고

---

## 4. 터미널 T3 — 메인 개인화 (웨이브 B)

### 실행 프롬프트 (그대로 붙여넣기)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
먼저 루트에서 git log --oneline -5 로 feature/profile-tag-slugs 와 feature/flow-rules-api 가
main에 머지돼 있는지 확인해라. 하나라도 없으면 중단하고 보고해라 — 이 터미널은 둘 다에 의존한다.
확인됐으면
  git worktree add ../뷰티보이-메인개인화 -b feature/main-personalization
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 ../뷰티보이-메인개인화 인지
  - git log --oneline -1 이 루트에서 본 기점 커밋과 같은지
  - docs/plans/2026-07-27-main-personalization.md 가 존재하는지
  - backend/src/main/resources/db/migration/V79__concern_target_rule.sql 이 존재하는지
  - git status가 깨끗한지

[2단계 — 실행]
docs/plans/2026-07-27-main-personalization.md 의 "터미널 T3" 절을 처음부터 끝까지 읽고
Task 3-1 ~ 3-6을 순서대로 실행해라. 설계 근거는
docs/superpowers/specs/2026-07-27-main-personalization-design.md §6, §7을 본다.

규칙:
- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다. features/affinity/의 네 모듈은 전부 순수 함수로
  두고 DOM 없이 단위 테스트한다.
- GoodsListItem 타입에 필드를 추가하지 마라 — 동결 계약이다. 카테고리는 화면 문맥에서 prop으로 받는다.
- 계획서에 적힌 가중치·임계값·상한(1/2/3점, 50, 5, ×1.5, 2섹션, size=8)을 임의로 바꾸지 마라.
  근거가 함께 적혀 있다. 바꿔야 할 이유를 발견하면 중단하고 보고해라.
- 이 절의 "파일 소유권 T3" 목록 밖 파일은 수정하지 않는다.
- 화면 태스크이므로 개발서버를 띄우고 /main을 개인화 전/후로 스크린샷을 찍어 직접 본 뒤
  파일 경로를 보고서에 남긴다. 테스트 통과·curl이 이를 대체하지 못한다.
- 전체 테스트는 `npm test`로 판정한다. 타입 검사는 `npx tsc --noEmit -p tsconfig.app.json`.
완료하면 커밋하고, 스크린샷 경로와 미해결 사항을 보고해라.
```

### Task 3-1 — 타입·API 클라이언트

`frontend/src/types/routine.ts`에 서버 DTO와 1:1로 맞춘 타입을 추가하고
(`FlowRuleView`·`ConcernRuleView`·`FlowRulesResponse`), `api/routine.ts`에 `fetchFlowRules()`를
추가한다. **`EdgeKind`는 `types/goods.ts`에 이미 있으므로 재사용한다 — 다시 정의하지 않는다.**

### Task 3-2 — `features/affinity/events.ts`

```ts
/** localStorage 키. 스키마가 바뀌면 v2로 올려 옛 데이터를 자연히 버린다. */
const STORAGE_KEY = 'bb.affinity.v1';

/**
 * 링버퍼 길이. 최근성을 시간 감쇠 함수 대신 길이로 표현한다 — 감쇠 계수는 튜닝할 근거가 없고
 * 테스트도 어렵다. 50개면 한 세션(보통 5~15 이벤트)을 여러 번 덮으면서도 반년 전 취향이 남지 않는다.
 */
const MAX_EVENTS = 50;

/** 가중치: 구매 의도의 강도 순. 조회 3회 = 담기 1회가 되도록 잡았다. 1·5·10처럼 가파르면
 *  우연히 담은 상품 하나가 프로필 전체를 지배한다. */
export const WEIGHT = { view: 1, wish: 2, cart: 3 } as const;

export interface AffinityEvent {
  goodsNo: number;
  cat3: string;      // 중분류 7자
  tags: string[];    // TagView.slug[]
  w: 1 | 2 | 3;
}

/** leaf 10자(C001001001) → 중분류 7자(C001001). 규칙의 category_code가 7자다. */
export function toCat3(categoryCode: string): string {
  return categoryCode.slice(0, 7);
}
```

- `readEvents()`: 파싱 실패·형태 불일치면 **통째로 폐기하고 `[]`** 를 돌려준다. 던지지 않는다
  (`skinProfile.ts`의 `readLocalSkinType` 패턴). 개인화가 안 되는 것은 폴백이 있어 안전하지만
  깨진 값으로 계산하면 무슨 일이 벌어질지 알 수 없다.
- `recordEvent(e)`: 뒤에 붙이고 `MAX_EVENTS` 초과분을 앞에서 잘라낸다.
- `cat3`가 빈 문자열이면 **기록하지 않는다** (문맥 없는 찜 — 설계 §6.1).

### Task 3-3 — `features/affinity/profile.ts`

- `aggregate(events): Map<string, number>` — 키 `` `${cat3}|${tag}` ``, 값은 `w`의 합.
- `tierOf(events, concerns)` — `events.length >= 5`면 `2`, 프로필이 비지 않았으면 `1`, 아니면 `0`.
  **임계 5의 근거:** 이벤트 1~2개는 유입 경로(검색·배너)의 잔상일 뿐 취향이 아니다. 5개면 최소
  두 상품 이상을 능동적으로 본 상태다.
- `effectiveConcerns(concerns, skinType)` — 고민(사용감 제외)이 하나라도 있으면 그대로 쓰고,
  **비어 있을 때만** 피부타입에서 파생한다. 직접 고른 것이 추론한 것을 이긴다.

```ts
/** 피부타입 → 파생 고민 태그. 고민을 하나도 안 고른 회원에게만 쓰는 약한 기본값(설계 §6.2). */
export const SKIN_TYPE_CONCERNS: Record<SkinType, ConcernSlug[]> = {
  DRY:         ['moisture', 'barrier'],
  OILY:        ['sebum', 'pore'],
  COMBINATION: ['moisture', 'sebum'],
  SENSITIVE:   ['soothe', 'gentle'],
};
```

`gentle`은 `CONCERNS` 상수에 없지만 `concern_target_rule`에는 있다(설계 §5.1) — 파생 전용
슬러그이므로 타입에서 `ConcernSlug | 'gentle'`로 받는다.

### Task 3-4 — `features/affinity/match.ts` (판단이 갈리는 핵심 — 전량)

```ts
/** 개인화 섹션 상한. 5단계가 전부 바뀌면 개인화가 아니라 "다른 화면"으로 읽힌다.
 *  2개면 나머지 3개가 기준선이 되어 무엇이 바뀌었는지 사용자가 알아볼 수 있다. */
const MAX_PERSONALIZED_SECTIONS = 2;

/** 목표 to_tag가 고민에 있을 때의 배수. 고정값을 더하면 이벤트가 적을 때 프로필이 전부를
 *  결정하고 많을 때는 아무 영향도 없다. 비율이면 어느 쪽에서도 "거들기"로 작동한다. */
const CONCERN_BOOST = 1.5;

export interface Target {
  stepId: string;   // ROUTINE_STEPS의 id
  tag: string;      // to_tag_slug
  reason: string;
}

/** 티어2 — 행동 신호가 있을 때. flowRules를 이벤트 점수로 매칭하고 프로필로 가산한다. */
export function matchByBehavior(
  events: AffinityEvent[],
  rules: FlowRuleView[],
  concerns: string[],
): Target[] {
  const scored = rules
    .map((r) => {
      // from 매칭: 중분류 접두사 일치 + (태그 무관이거나 상품 태그에 포함)
      const base = events
        .filter((e) => e.cat3.startsWith(r.fromCategoryCode)
          && (r.fromTagSlug == null || e.tags.includes(r.fromTagSlug)))
        .reduce((sum, e) => sum + e.w, 0);
      if (base === 0) return null;
      const boosted = r.toTagSlug != null && concerns.includes(r.toTagSlug)
        ? base * CONCERN_BOOST
        : base;
      return { rule: r, score: boosted };
    })
    .filter((x): x is { rule: FlowRuleView; score: number } => x !== null)
    // priority 오름차순이 먼저다 — BUFFER(10)가 NEXT_STEP(20)을 이긴다(next-step 설계 §4).
    // 점수가 아무리 높아도 완충이 먼저라는 것이 규칙의 의도다.
    .sort((a, b) => a.rule.priority - b.rule.priority || b.score - a.score);

  return takeTopPerStep(scored.map((s) => s.rule));
}

/** 티어1 — 프로필만. concernRules를 고민 선택 순서로 훑는다. */
export function matchByProfile(
  concerns: string[],
  rules: ConcernRuleView[],
): Target[] {
  const ordered = concerns.flatMap((c) =>
    rules.filter((r) => r.concernTagSlug === c).sort((a, b) => a.priority - b.priority));
  return takeTopPerStep(ordered);
}

/** 공통 마무리: to_category → STEP 매핑, STEP당 1개, 최대 2섹션.
 *  입력 배열은 이미 우선순위 순이므로 먼저 온 것이 이긴다. */
function takeTopPerStep(rules: { toCategoryCode: string; toTagSlug: string; reason: string }[]): Target[] {
  const used = new Set<string>();
  const out: Target[] = [];
  for (const r of rules) {
    const step = ROUTINE_STEPS.find((s) => r.toCategoryCode.startsWith(s.categoryCode));
    if (!step || used.has(step.id)) continue;   // 매핑 안 되는 카테고리는 조용히 건너뛴다
    used.add(step.id);
    out.push({ stepId: step.id, tag: r.toTagSlug, reason: r.reason });
    if (out.length === MAX_PERSONALIZED_SECTIONS) break;
  }
  return out;
}
```

> `startsWith(s.categoryCode)`인 이유: `ROUTINE_STEPS`의 클렌징은 대분류 `C002`(4자)이고
> 나머지는 중분류 7자다(`steps.ts` 주석의 "단계 깊이가 섞이는 것은 의도적"). 규칙의
> `to_category_code`는 항상 7자이므로 방향이 이쪽이어야 둘 다 맞는다.

**테스트 (`match.test.ts`) — 이름과 단언 전량:**
- `from 태그가 null인 규칙은 태그와 무관하게 매칭된다`
- `from 카테고리가 안 맞으면 점수 0이라 후보에서 빠진다`
- `같은 상품에 BUFFER(10)와 NEXT_STEP(20)이 걸리면 점수가 낮아도 BUFFER만 남는다`
- `toTagSlug가 고민에 있으면 점수가 1.5배가 되어 같은 priority 안에서 순위가 뒤집힌다`
- `목표가 3개 이상 나와도 2섹션만 반환한다`
- `같은 STEP을 겨냥한 규칙이 둘이면 앞선 것 하나만 남는다`
- `to_category가 ROUTINE_STEPS에 없으면 그 규칙은 건너뛰고 다음 규칙이 자리를 채운다`
- `matchByProfile: 고민 2개가 서로 다른 STEP을 겨냥하면 둘 다 반환한다`
- `matchByProfile: 고민 목록이 비면 빈 배열을 반환한다`
- `matchByProfile: 고민 선택 순서가 우선순위다 — 먼저 고른 고민의 규칙이 STEP을 차지한다`

`profile.test.ts` / `events.test.ts`도 설계 §11의 케이스를 그대로 옮겨 작성한다.

### Task 3-5 — `flowRules.ts` (ETag 캐시)

`localStorage 'bb.flowRules.v1'`에 `{ version, flowRules, concernRules }`를 저장하고,
요청 시 `If-None-Match: version`을 보낸다. **304면 저장본을 그대로 쓴다.**
네트워크 실패·304 처리 실패 시에도 저장본이 있으면 그것을 쓰고, 없으면 빈 규칙(→ 티어0 폴백)으로
간다 — 규칙을 못 받았다고 메인이 깨지면 안 된다.

### Task 3-6 — 배선

- **`GoodsCard.tsx`**: `categoryCode?: string` prop 추가. 찜 클릭 핸들러에서 prop이 있을 때만
  `recordEvent({ …, w: WEIGHT.wish })`. **`GoodsListItem` 타입은 건드리지 않는다.**
  호출부 중 `RoutineSection`(=`step.categoryCode`)과 `GoodsList`(=필터 카테고리)만 prop을 넘기고,
  검색·추천·next-step 레일은 넘기지 않는다.
- **`Detail.tsx`**: 상세 로드 성공 시 조회 이벤트(`w: 1`), 담기 성공 시 `w: 3`.
  `categoryCode`는 `GoodsDetail`에 이미 있다.
- **`Main.tsx`**: `me` 쿼리(이미 캐시됨)와 `fetchFlowRules`를 읽어 `tierOf` → 티어별 매칭 →
  `Target[]`을 계산하고, `stepId`가 일치하는 `RoutineSection`에 `override` prop을 내린다.
- **`RoutineSection.tsx`**: `override?: { tag: string; reason: string }`.
  - `queryKey: ['routine-goods', step.categoryCode, override?.tag ?? null]`
  - `size`: `override`가 있으면 **8**(tie-break 여유분), 없으면 기존 `ROUTINE_SECTION_SIZE`(4)
  - 응답이 4개 미만이면 `override`를 버리고 기본 쿼리로 폴백 — 쿼리키가 기존과 같아 **캐시 히트**,
    추가 호출이 없다.
  - 사용감 tie-break: 프로필의 사용감 슬러그를 `tags`에 가진 후보를 앞으로 당기는 **안정 정렬**.
    일치 개수가 같으면 서버가 준 인기순을 유지한다. 그 뒤 상위 4개만 그린다.
  - `reason`은 제목 아래 한 줄. **문구를 코드에 하드코딩하지 않는다** — 서버 값 그대로 렌더한다.
    DESIGN.md 토큰만 쓰고 `word-break: keep-all`을 적용한다.
- **`mocks/handlers.ts`**: `GET /routine/flow-rules` 핸들러 + 픽스처.

**테스트 (`Main.test.tsx`):**
- `프로필도 행동도 없으면 모든 섹션이 tag 없는 기본 쿼리를 부른다`
- `프로필만 있으면 지정 STEP에 tag가 붙고 reason 문장이 렌더된다`
- `행동 5건 이상이면 flowRules 매칭 결과가 우선한다`
- `개인화 후보가 4개 미만이면 그 섹션만 기본 쿼리로 폴백하고 reason이 사라진다`
- `flow-rules 요청이 실패해도 메인이 기본 화면으로 렌더된다`

### T3 완료 조건

- `npm test` 전량 통과 / `npx tsc --noEmit -p tsconfig.app.json` 통과
- **스크린샷 2장**: 개인화 전 `/main`, 개인화 후 `/main`(reason 문장이 보이는 상태).
  개인화 상태는 `localStorage`에 이벤트를 심어 만든다 — 방법을 보고서에 적는다.

---

## 5. 머지 게이트 (오케스트레이터 세션)

각 터미널 완료 시 리뷰 항목:

1. 파일 소유권 준수 — 목록 밖 파일이 변경됐는가
2. 테스트 **전량 통과**(`npm test` / `./gradlew test`), 타입 검사 통과
3. **스크린샷을 실제로 열어보고** 판정 — T1·T3는 이것이 없으면 미완료다
4. raw hex가 CSS에 새로 들어오지 않았는가 (T1)
5. 매직넘버가 계획서 값과 같은가 (T3: 1/2/3, 50, 5, 1.5, 2, 8)
6. `reason` 문구가 코드에 하드코딩되지 않았는가 (T2·T3)

웨이브 A 두 브랜치를 모두 머지한 뒤에만 T3를 시작한다.
