# 랜딩 + 메인 페이지 구성 사양

> **이 문서의 목적**: 랜딩·메인 화면에 대해 지금까지 확정된 것과 남은 것을 한곳에 모아,
> 대화 컨텍스트가 비워져도 이어서 작업할 수 있게 한다.
> 시각 토큰의 진실은 루트 `DESIGN.md`, 웨이브 구조는 `docs/plans/2026-07-23-roadmap.md`.

**작성 시점**: 2026-07-23 / **최종 갱신**: 2026-07-24 — 메인 페이지 위치·시각 형식·범위가 브레인스토밍으로 확정됨(2장 반영). `feat/design-refresh`는 PR #3으로 main에 머지 완료.

---

## 1. 지금 어디까지 됐나

### 완료 — `feat/design-refresh` 브랜치 (7커밋, main에 아직 안 들어감)

| 커밋 | 내용 |
|---|---|
| `7b37d0c` | 시각 언어 개정 — 디스플레이 스케일 확대, 카드 면 도입 |
| `259811b` | 랜딩 히어로 전면 교체 — 검정 풀블리드 + 물결 리본(canvas) |
| `846a163` | 아치 물결 · 내비 · 가운데 칼럼 |
| `cb76702` | CTA를 이메일 입력 바로 교체 |
| `3d168bb` | 내비·카피를 레퍼런스 실측값에 맞춤, Contact→Login |
| `ce372f9` | 중앙 정렬 배치로 재구성 |
| `da112b5` | 워드마크 한 줄 + 서브카피 위계 완화 + 그룹 45% 배치 |

**랜딩 화면(`/`)의 현재 사양** — 레퍼런스 <https://ramlla.com/>

- 검정(`{colors.primary}`) 풀블리드, 헤더는 이 화면에서만 투명 오버레이
- 헤더: 워드마크 + `About / Work / Services / Packages / Login`
  (Login만 실제 `/login` 링크, 나머지 4개는 라우트 없는 자리표시)
- 중앙 정렬 스택: **Beauty Boy**(한 줄, 최대 272px) → 서브카피(18px, `{colors.ash-soft}`) → 이메일 폼(420px)
- 콘텐츠 그룹 중심을 화면 **45%** 지점에 둔다(`padding-bottom: 15vh`)
- 이메일 제출 → `/signup?email=` → 가입 화면 첫 칸 자동 입력 (동작 확인됨)
- 물결: `frontend/src/components/landing/WaveCanvas.tsx` — canvas 2D, 곡선 260가닥,
  가우시안 아치. 정점이 워드마크 상단 뒤(≈0.20h)에 오고 폼 자리(≈0.81h)에는 닿지 않는다.
  `prefers-reduced-motion`에서 정지 프레임 1장. **외부 라이브러리 없음**(package.json은 공유 계약)

### `DESIGN.md`에 추가된 토큰 (이 작업 중 신설)

| 토큰 | 값 | 이유 |
|---|---|---|
| `colors.canvas` | `#ffffff` → `#f7f7f7` | 순백 위에서는 카드 면의 명도차가 안 보인다 |
| `colors.surface` | `#ebebeb` | 카드·패널 면 |
| `colors.ash-soft` | `#b0b0b0` | 검정 위 보조 카피. ash(#999)는 위계 차이가 과하다 |
| `typography.display-hero` | 272px | 랜딩 워드마크 전용 상한 |
| `typography.nav-link` | 18px | 헤더 내비 전용 |
| `typography.body-lg` | 18px | body(16)와 subtitle(20) 사이 |
| `rounded.lg` | 12px (기존 16px은 `xl`로 이동) | 카드 라운딩 |

---

## 2. 메인 페이지 구성 — 확정 사양

**핵심 개념: 스크롤을 내리는 순서가 곧 스킨케어 루틴 순서다.**

타겟이 "뭘 사야 할지 모르는 남성"이므로 순서 자체가 교육이 된다. 올리브영 클론이면서
올리브영이 하지 않는 지점이라 차별점이 명확하고, 포트폴리오에서 설명하기 좋다.

### 위치·라우팅 (2026-07-24 확정)

- **루틴 메인은 랜딩이 아니라 로그인 이후의 신규 페이지 `/main`이다.**
  - `RequireAuth` 가드로 감싼다 — 비로그인 접근 시 `/login` 리다이렉트(`authStore` 인증 상태 기준)
  - 로그인 성공 시 이동을 `navigate('/')` → `navigate('/main')`으로 변경
  - **랜딩(`/`)은 손대지 않는다** — 기존 3컬럼 섹션 유지 (구 미결 항목 #2 해소)
- **`/goods` 목록 페이지 신설** — `?category=C002` 쿼리 필터, 제목 + `GoodsGrid` 전체 목록.
  각 루틴 섹션의 "○○ 전체 보기 →" 더보기 행선지. 같은 가드 아래 둔다.

### 루틴 단계 ↔ 카테고리 매핑

카탈로그 API가 이미 접두사 필터를 지원하므로 각 섹션은 `GET /goods?categoryCode=` 한 번이면 된다.

| 단계 | 이름 | categoryCode | 실제로 담기는 것 |
|---|---|---|---|
| 1 | 클렌징 | `C002` | 클렌징폼 · 클렌징오일/밤 · 필링/스크럽 |
| 2 | 토너/스킨 | `C001001` | 수분토너 · 진정토너 |
| 3 | 에센스/세럼 | `C001002` | 고보습에센스 · 미백세럼 |
| 4 | 로션/크림 | `C001003` | 데일리로션 · 고영양크림 |
| 5 | 선크림 | `C004001` | 선크림 |

### 이 매핑에 걸린 결정과 근거

- **단계 깊이가 섞이는 것은 의도적이다.** 단계는 깊이가 아니라 의미로 정한다.
  클렌징은 대분류 하나가 곧 한 단계고, 스킨케어는 대분류 하나 안에 3단계가 들어 있다.
- **루틴 순서를 카테고리 트리로 표현하지 않는다.** 카테고리는 "무엇인가"(분류),
  루틴은 "언제 바르는가"(순서)로 축이 다르다. 하나로 합치면 둘 다 망가진다.
  메인은 위 매핑을 **명시적 상수**로 들고 굴린다. CLAUDE.md "돈과 재고는 서버, 취향은 클라이언트"에
  해당하므로 프론트 상수로 시작하고, 필요해지면 Wave 3의 `routine` 도메인으로 옮긴다.
- **클렌징을 포함한다.** 실제 스킨케어 순서의 첫 단계다. "순서대로 보여준다"면서 1단계를 빼면
  아는 사람은 바로 알아챈다.
- **아침/저녁을 구분하지 않는다.** 기본 루틴 하나만 둔다(선크림이 마지막인 것은 그 결과다).
- **루틴 축에 없는 카테고리는 이 섹션에 넣지 않는다** — 헤어·바디(`C003`), 쉐이빙·그루밍(`C005`),
  메이크업(`C006`), 선스틱·애프터선(`C004002`·`C004003`)은 별도 진입점으로.

### 시각·UX 사양 (2026-07-24 확정 — pangram식 B안, 풀블리드 원안 폐기)

레퍼런스는 <https://pangrampangram.com/>의 "거대 타이포 ↔ 면으로 나뉜 카드" 대비.
목업 3안(A 풀블리드 사진 / B 타이포↔이미지 박스 / C 그리드 첫 셀) 중 **B안 확정**.

- **페이지 상단**: 페이지 제목 + 한 줄 인트로 + **01–05 앵커 네비**(sticky, 현재 섹션
  scroll-spy 강조 — `DESIGN.md` UX 계약의 sticky 헤더 오프셋 규칙 연장). 첫 스크롤
  안에 STEP 01이 보이게 인트로는 가볍게 둔다(풀 히어로 금지).
- **각 단계 = `RoutineSection`**: 큰 타이포 블록(STEP N / 단계명 / 한 줄 카피) ↔
  이미지 박스(`colors.surface` + `rounded.lg`)가 나란히, **홀수/짝수 단계 좌우 교차**로
  스크롤 리듬을 만든다. 아래에 그 카테고리 상품 **4개**(`GoodsGrid` 재사용) +
  "○○ 전체 보기 →"(→ `/goods?category=`).
- 모바일(390): 타이포 위, 이미지 박스 아래 세로 스택.
- **사진은 무료 스톡(Unsplash 등)을 레포에 받아서 쓴다**(`public/images/routine/`).
  외부 URL 직접 참조는 오프라인·CI에서 화면이 깨진다. 실제 브랜드 제품 사진은 금지.
- 상품 카드는 `GoodsCard`/`GoodsGrid`를 그대로 재사용한다. 새로 만들지 않는다.
- 색·라운딩은 `DESIGN.md` 토큰만. 시그널 색 배경 금지. `prefers-reduced-motion` 존중
  (앵커 스무스 스크롤 포함 — reduce면 즉시 점프).
- **반응형은 기본값이다.** 390 / 768 / 1024 / 1440 / 1920에서 가로 스크롤 0을 확인하고,
  뷰포트 높이가 낮을 때 콘텐츠가 밀려나지 않는지도 본다(랜딩에서 실제로 겪은 함정)

---

## 3. 미결 항목 (착수 전 정해야 함)

1. **`DESIGN.md` 폼 규칙 예외** — 랜딩 이메일 입력에서 보이는 라벨을 뺐다(`aria-label`로 대체).
   문서의 "placeholder는 라벨을 대체하지 않는다"와 충돌하는 상태다.
   문서에 "랜딩 히어로 예외" 한 줄을 넣어 정리해야 한다. **안 하면 나중에 규칙대로 되돌려진다.**
2. ~~랜딩 아래 기존 3컬럼 섹션 교체 여부~~ — **해소(2026-07-24)**: 루틴 섹션은 랜딩이
   아니라 신규 `/main`으로 가므로 랜딩 3컬럼은 그대로 둔다.
3. **모바일 그룹 정렬** — 랜딩 콘텐츠 그룹 중심이 데스크톱은 45%인데 390px에서는 0.53이다.
   모바일도 45%로 맞출지.
4. **폰트** — 레퍼런스는 Aeonik Pro(유료). 현재 Inter + Pretendard라 글자 모양 차이는 남는다.

---

## 4. 언제 만드나

로드맵상 **Wave 3 T2(`feat/front-pages`)** 몫이다. Wave 2는 백엔드 3종(search·ranking /
cart·order·payment / review·qna·wishlist)이라 프론트를 건드리지 않는다.

다만 **기술적으로는 지금도 만들 수 있다** — 카탈로그 API(카테고리 접두사 필터)와 MSW mock이
이미 있고 루틴 매핑도 확정됐다. Wave 3까지 미루는 것은 순서상의 선택이지 의존성 때문이 아니다.

> **2026-07-24: 앞당겨 착수하기로 확정.** 5장 태스크(T1~T7)대로 진행한다.

---

## 5. 실행 계획 (착수 시)

**모델 배분**: 전 태스크 sonnet (CLAUDE.md 예외 3종 — 결제·재고차감·궁합엔진 — 해당 없음)
**터미널**: 1개면 충분하다. 프론트 단일 도메인이고 파일이 한 갈래라 쪼갤 이득이 없다.

| 태스크 | 내용 | Files |
|---|---|---|
| T1 | `RequireAuth` 가드 + `/main`·`/goods` 라우트 뼈대 + 로그인 후 `/main` 이동 | `components/auth/RequireAuth.tsx`, `router.tsx`, `pages/Login.tsx`(navigate만) |
| T2 | 루틴 단계 상수(5단계 매핑·카피·이미지 경로) | `features/routine/steps.ts` |
| T3 | 스톡 사진 확보·최적화 후 레포 반입 | `public/images/routine/*` |
| T4 | `RoutineSection` 컴포넌트(B안: 타이포↔이미지 박스 교차 + GoodsGrid 4개 + 더보기) | `components/routine/*` |
| T5 | `/main` 조립(인트로 + 앵커 네비 scroll-spy + 섹션 5개) | `pages/Main.tsx`, `Main.css` |
| T6 | `/goods` 목록 페이지(카테고리 쿼리 필터 + GoodsGrid) | `pages/GoodsList.tsx` |
| T7 | 반응형·스크린샷 검증 (5개 폭, 가로 스크롤 0) | — |

테스트(TDD): steps 상수↔매핑 검증 · RoutineSection 렌더(타이포/이미지/4개 제한/더보기 링크) ·
RequireAuth 리다이렉트 · 로그인 후 `/main` 이동 · GoodsList 카테고리 필터.

### 붙여넣기용 실행 프롬프트

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add ../BeautyBoy-w-main-routine -b feat/main-routine
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
(EnterWorktree를 name으로 새로 만들지 마라 — 기본 설정이 로컬 HEAD가 아니라 origin에서
브랜치를 따서, 계획서도 DESIGN.md도 없는 worktree가 생긴다.)
진입 후 아래를 확인하고 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 BeautyBoy-w-main-routine 인지
  - git log --oneline -1 이 루트에서 본 기점과 같은지
  - docs/plans/2026-07-23-landing-main-composition.md 와 루트 DESIGN.md 가 있는지
  - frontend/src/components/goods/GoodsGrid.tsx 가 있는지 (재사용 대상)
  - git status가 깨끗한지
확인 뒤 cd frontend && npm install && npm run test 가 green인지 먼저 본다.

[2단계 — 실행]
docs/plans/2026-07-23-landing-main-composition.md 의 2장(구성 사양)과 5장(태스크)을 읽고
T1~T7을 순서대로 TDD로 실행해라. 너는 오케스트레이터다 — 태스크마다 서브에이전트(model: sonnet)를
스폰하고, 태스크 사이마다 (1) 테스트 통과 (2) Files 목록 준수 (3) 사양 일치를 리뷰해라.

반드시 지킬 것:
- 루틴 단계 ↔ categoryCode 매핑은 2장 표 그대로. 임의로 바꾸지 마라.
- 상품 카드는 GoodsCard/GoodsGrid 재사용. 새로 만들지 마라.
- CSS는 DESIGN.md 토큰 이름을 직접 참조하고 hex를 손으로 적지 마라.
  문서에 없는 값이 필요하면 만들지 말고 보고해라.
- 화면을 만드는 태스크는 개발서버를 띄우고 390/768/1024/1440/1920에서 스크린샷을 찍어
  직접 본 뒤 파일 경로를 보고서에 남겨야 완료다. 가로 스크롤 0도 확인해라.
- 외부 이미지 URL을 직접 참조하지 마라. 스톡 사진은 public/ 아래로 받아서 쓴다.
```

### 사전 조건 (사람 몫 2줄)

```bash
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" log --oneline -1   # 이 커밋이 기점
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" status --short     # 비어 있어야 한다
```

---

## 6. 참고

- 랜딩 레퍼런스: <https://ramlla.com/> (검정 + 물결 + 거대 워드마크)
- 초기 시각 언어 레퍼런스: <https://pangrampangram.com/> (거대 타이포 ↔ 작은 라벨 대비, 면으로 나뉜 카드)
- 시드 카테고리 원본: `backend/src/main/resources/db/migration/V12__seed_catalog.sql`
- 상품 목록 API 계약: `docs/plans/2026-07-23-wave1-catalog-frontbase.md` 7장 `GoodsListItem`
