# 메인 개인화(기기 측 관심 프로필) 설계

> 상태: **확정.** 2026-07-27 브레인스토밍 결정.
> 선행 설계: `2026-07-26-next-step-recommendation-design.md` §11 "범위 밖(이월)"의
> *"클라이언트 규칙 JSON 배포 + 최근 본 상품 기반 개인화"* 가 이 문서다.
> 구현 계획서는 `docs/plans/`에 별도 작성한다.

## 1. 한 줄 요약

행동 신호(조회·찜·담기)와 회원 프로필(고민·사용감·피부타입)을 **기기 안에서만** 합쳐,
서버에서 받은 **규칙 JSON**과 대조해 "다음에 볼 (단계 × 태그)"를 기기가 계산한다.
서버가 하는 일은 **규칙 배포(대개 304)와 기존 `/goods` 조회**뿐이다 — 사용자별 프로필 저장도,
배치 잡도, 추천 계산도 서버에 없다. CLAUDE.md의 *"돈과 재고는 서버, 취향은 클라이언트"* 를
그대로 구현한 사례다.

## 2. 확정된 결정

| 결정 | 선택 | 근거 |
|---|---|---|
| 화면 배치 | **기존 루틴 5단계 섹션의 상품 그리드를 제자리 교체** | 새 섹션이 없어 레이아웃 변화 0. 콜드스타트 폴백이 곧 현재 화면이라 실패 모드가 안전하다 |
| 규칙 배포 | **신규 API + ETag/304** | DB 규칙 한 곳을 고치면 PDP next-step과 메인이 동시에 따라온다. 재방문 시 본문 0바이트 |
| 행동 신호 | **PDP 조회 1점 · 카드 찜 2점 · 담기 3점** | 서버 찜/장바구니 목록을 읽지 않는다 — 읽는 순간 동기화·비용 문제가 되돌아온다 |
| 프로필 신호 | **3단 사다리** (§6) | 프로필이 콜드스타트를 해결한다. 첫 방문자도 개인화된다 |
| 피부타입 | **고민을 하나도 안 고른 경우에만 발동하는 약한 기본값** | 직접 고른 고민이 항상 이긴다 |
| 사용감(TEXTURE) | **목표를 정하지 않고 후보 정렬 tie-break만** | 사용감은 "다음 단계"의 축이 아니다 |
| 궁합 검증 | **메인은 규칙 수준 안전까지만** | 앵커 상품이 없어 pairwise가 성립하지 않는다(§10) |
| 비회원 | **해당 없음** | `/main`은 이미 `RequireAuth` 안(`frontend/src/router.tsx:37`). 프로필은 항상 서버에서 온다 |

## 3. 데이터 흐름

```
[기기] PDP 조회(1) · 카드 찜(2) · 담기(3)
   → localStorage 'bb.affinity.v1' 이벤트 링버퍼(최근 50개)
   → 집계: (중분류 7자 × 태그)별 점수 합
                                    ＋
[서버] GET /members/me → concerns(고민·사용감) · skinType   ← 이미 캐시된 쿼리 재사용
[서버] GET /routine/flow-rules → flowRules 12 + concernRules ~12   ← 대개 304
   ↓
[기기] 3단 사다리 매칭 → 섹션별 목표 (to_cat, to_tag, reason), 최대 2섹션
   ↓
[서버] GET /goods?categoryCode=&tag=&sort=popular&size=8   ← 유일한 데이터 호출
   ↓
[기기] 사용감 tie-break로 정렬 후 상위 4개 렌더 + 섹션 제목 아래 reason 한 줄
```

목표 조합은 규칙 행에서 파생되므로 **유한하다**(수십 개). 사용자마다 URL이 달라도 조합 수가
적어 HTTP 캐시가 잘 먹는다. 서버에 사용자별 상태가 하나도 없다는 것이 진짜 절감 지점이다.

## 4. 프로필 태그 교체 (스키마 변경 없음)

`member_profile.concerns`는 `VARCHAR(200)`에 콤마 조인이고 백엔드는 `List<String>`을 그대로
이어 붙일 뿐 enum 검증이 없다(`MemberProfile.java:83` `toConcernsColumn`). 따라서 값 집합 교체에
**DDL 변경이 필요 없다** — 프론트 상수 `CONCERNS`와 TS 유니온 `Concern`만 슬러그로 바꾼다.

기존 4종(`PORE` `TROUBLE` `WRINKLE` `DARK_SPOT`)은 태그 체계(V70~V72의 18종)와 무관한 별도
어휘였고, **어디에서도 로직에 쓰이지 않았다** — 저장하고 다시 표시할 뿐이다(`MyProfile.tsx`,
`SkinProfileStep.tsx`). 이 설계가 `concerns`에 처음으로 쓰임새를 준다.

### 4.1 확정 값 집합

| 그룹 | 슬러그 (표시명) |
|---|---|
| 고민 (9) | `exfoliate`(각질) `sebum`(피지) `pore`(모공) `trouble`(트러블) `soothe`(진정) `moisture`(보습) `barrier`(장벽) `bright`(브라이트닝) `anti-aging`(안티에이징) |
| 사용감 (3) | `fresh`(산뜻함) `dewy`(촉촉함) `matte`(매트) |

- 18종 중 `cleanse` `uv` `scalp` `firm` `antioxidant` `gentle`은 제외한다 — 세정·자외선차단·두피는
  "고민"이 아니라 제품 기능이고, 나머지 3종은 고민 축에서 다른 항목과 의미가 겹친다.
  (`gentle`은 피부타입 SENSITIVE의 파생 신호로만 쓴다 — §6.2.)
- 사용감 3종은 **같은 `concerns` 컬럼에 함께 저장한다.** 서버가 문자열 리스트로만 다루므로 컬럼을
  더 만들 이유가 없고, 프론트 상수가 그룹을 구분한다. 12종 슬러그를 모두 골라도
  `exfoliate,sebum,pore,trouble,soothe,moisture,barrier,bright,anti-aging,fresh,dewy,matte` = 87자로
  `VARCHAR(200)` 안에 들어간다(`ProfileRequest.concernsWithinLimit`의 200자 검증도 통과).

### 4.2 기존 데이터 마이그레이션 (V77)

```sql
-- V77__migrate_concerns_to_tag_slug.sql
-- member_profile.concerns의 구 어휘(PORE/TROUBLE/WRINKLE/DARK_SPOT)를 tag.slug 체계로 옮긴다.
-- 값 집합만 바뀌고 컬럼 타입·제약은 그대로다(설계 §4).
-- WRINKLE→anti-aging, DARK_SPOT→bright: 구 어휘가 증상명이고 신 어휘가 효과명이라 1:1이 아니지만,
-- 그 증상을 겨냥한 태그가 각각 하나뿐이라 모호함이 없다.
UPDATE member_profile SET concerns = REPLACE(concerns, 'PORE',       'pore')       WHERE concerns LIKE '%PORE%';
UPDATE member_profile SET concerns = REPLACE(concerns, 'TROUBLE',    'trouble')    WHERE concerns LIKE '%TROUBLE%';
UPDATE member_profile SET concerns = REPLACE(concerns, 'WRINKLE',    'anti-aging') WHERE concerns LIKE '%WRINKLE%';
UPDATE member_profile SET concerns = REPLACE(concerns, 'DARK_SPOT',  'bright')     WHERE concerns LIKE '%DARK_SPOT%';
```

실데이터는 `V64__seed_member.sql`의 4건뿐이다. `REPLACE`를 쓰는 이유는 콤마 조인 문자열 안의
부분 치환이기 때문이고, 네 토큰이 서로의 부분 문자열이 아니라 오염이 없다.

## 5. 규칙 배포 (서버 신규 — 백엔드 작업의 전부)

### 5.1 concern_target_rule (V78)

프로필만 있는 티어1에는 앵커 상품이 없어 `routine_flow_rule`의 `from`이 성립하지 않는다.
그렇다고 "트러블이 고민이라면…" 같은 문구를 프론트에 하드코딩하면 *reason은 DB가 유일한 출처*라는
선행 설계의 원칙(next-step 설계 §3)이 깨진다. 따라서 고민 → 목표 규칙을 데이터로 둔다.

```sql
-- V78__concern_target_rule.sql
CREATE TABLE concern_target_rule (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  concern_tag_slug  VARCHAR(40)  NOT NULL,   -- 프로필 고민 슬러그. tag.slug와 같은 어휘(물리 FK는 걸지 않는다)
  to_category_code  VARCHAR(12)  NOT NULL,   -- 중분류 7자. goods.category_code(leaf 10자)는 접두사 매칭
  to_tag_slug       VARCHAR(40)  NOT NULL,   -- 추천 대상이 가져야 할 태그
  reason            VARCHAR(200) NOT NULL,   -- 화면에 그대로 나가는 문구. 유일한 출처
  priority          INT          NOT NULL DEFAULT 0,  -- 낮을수록 우선
  CONSTRAINT uq_concern_target UNIQUE (concern_tag_slug, to_category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- `routine_flow_rule`에 합치지 않는 이유: 그 테이블의 `from_category_code`는 `NOT NULL`이고,
  고민 규칙에는 from이 없다. 관례값을 넣어 한 테이블에 밀어 넣으면 두 종류의 행이 섞여
  next-step 매칭 쿼리가 오염된다.
- `UNIQUE (concern_tag_slug, to_category_code)`: 한 고민이 같은 단계를 두 번 겨냥하지 못하게 막는다.
  (한 고민이 서로 다른 단계 2개를 겨냥하는 것은 허용 — 예: `trouble` → 세럼, `trouble` → 클렌징폼.)
- 시드 대상 슬러그는 **10개**다 — 고민 9종 + `gentle`. `gentle`은 프로필에서 직접 고를 수 없지만
  `SENSITIVE` 피부타입의 파생 태그(§6.2)이므로, 규칙이 없으면 파생 태그 하나가 무효가 된다.
  슬러그당 1~2행이라 **16~20행**이 된다. 계획서에서 V71/V72 실데이터를 대조해 확정하고,
  §11의 시드 검증 테스트가 "각 행의 (to_category × to_tag) 조합에 비HIDDEN 상품이 4개 이상"을
  단언한다 — 후보가 안 나오는 규칙을 시드에 넣지 않기 위해서다.

### 5.2 배포 API

```
GET /api/v1/routine/flow-rules      (routine 패키지 컨트롤러, permitAll)
→ ApiResponse<FlowRulesResponse>

FlowRulesResponse(
    String version,                        // 규칙 내용 해시 앞 16자. ETag와 같은 값
    List<FlowRuleView> flowRules,          // routine_flow_rule 12행
    List<ConcernRuleView> concernRules)    // concern_target_rule ~12행

FlowRuleView(String fromCategoryCode, String fromTagSlug, String toCategoryCode,
             String toTagSlug, String edgeKind, String reason, int priority)
ConcernRuleView(String concernTagSlug, String toCategoryCode, String toTagSlug,
                String reason, int priority)
```

- **ETag**: 두 테이블 전량을 정렬된 순서로 직렬화해 SHA-256 해시, 앞 16자. 규칙은 시드 전용이고
  관리자 CRUD가 없으므로(next-step 설계 §11) **애플리케이션 기동 시 1회 계산해 메모리에 캐싱**한다.
  요청마다 DB를 읽지 않는다.
- `If-None-Match`가 일치하면 `304 Not Modified` + 본문 없음. 재방문 비용이 사실상 0이 된다.
- `SecurityConfig`에 `GET /api/v1/routine/flow-rules` permitAll 1줄 추가.
  (기존 `/api/v1/goods/**` 패턴에 안 걸리는 새 경로다.)

## 6. 기기 측 알고리즘

### 6.1 이벤트 기록

```ts
// localStorage 'bb.affinity.v1'
interface AffinityEvent {
  goodsNo: number;
  cat3: string;      // 중분류 7자. leaf 10자를 slice(0, 7)로 절단
  tags: string[];    // TagView.slug[]
  w: 1 | 2 | 3;      // 조회 1 · 찜 2 · 담기 3
}
```

| 기록 지점 | `cat3` 출처 | 판정 |
|---|---|---|
| PDP 진입 (조회, 1점) | `GoodsDetail.categoryCode` | 기록 |
| PDP 장바구니 담기 (3점) | `GoodsDetail.categoryCode` | 기록 |
| 목록 카드 찜 (2점) | **화면 문맥의 카테고리를 `GoodsCard`에 optional prop으로 전달** | prop이 있을 때만 기록 |

- 찜 버튼은 `GoodsCard`에만 있고(`GoodsCard.tsx:92`) PDP에는 없다. 그런데 `GoodsListItem`에는
  `categoryCode`가 없고 **이 타입은 동결 계약이라 필드를 추가하지 않는다.**
  루틴 섹션(`step.categoryCode`)과 목록 페이지(필터 카테고리)는 문맥으로 카테고리를 알고 있으므로
  prop으로 넘기고, 검색 결과·추천 레일처럼 문맥이 없는 곳의 찜은 **기록하지 않는다.**
  태그만으로 기록하면 "각질 클렌징"과 "각질 토너"를 구분할 수 없어 단계 축이 무너진다 —
  이 구분이 이 기능의 전부이므로, 반쪽 신호를 넣느니 버리는 쪽이 정직하다. 주 신호는 조회다.
- **가중치 1·2·3의 근거**: 구매 의도의 강도 순서이고, 조회 3회가 담기 1회와 같은 무게가 되도록
  잡았다. 비율이 이보다 가파르면(예: 1·5·10) 우연히 담은 상품 하나가 프로필 전체를 지배한다.
- **창 크기 50**: 최근성을 시간 감쇠 함수 대신 링버퍼 길이로 표현한다. 감쇠 계수는 튜닝할 근거가
  없고 테스트도 어렵다. 50개면 한 세션(보통 5~15 이벤트)을 여러 번 덮으면서도, 반년 전 취향이
  남지 않는다. 초과분은 오래된 것부터 버린다.
- 손상된 JSON·스키마 불일치는 **통째로 폐기하고 빈 배열로 시작한다**(`skinProfile.ts`의
  `readLocalSkinType` 패턴과 동일). 개인화가 안 되는 것은 폴백이 있어 안전하지만, 깨진 값으로
  계산하면 무슨 일이 벌어질지 알 수 없다.

### 6.2 3단 사다리

| 티어 | 조건 | 동작 |
|---|---|---|
| 0 | 프로필도 행동도 없음 | 개인화 없음 — 현재 화면 그대로 |
| 1 | 프로필 있음, 행동 < 5 | `concernRules` 매칭 |
| 2 | 행동 ≥ 5 | `flowRules` 매칭 + 프로필 가산 |

- **임계 5의 근거**: 이벤트 1~2개는 유입 경로(검색·배너)의 잔상일 뿐 취향이 아니다. 5개면 최소
  두 상품 이상을 능동적으로 본 상태다. 미만이면 티어1로 내려가 프로필로 개인화하므로,
  **로그인 회원에게 "아무것도 안 바뀌는 상태"는 프로필이 비어 있을 때뿐이다.**
- **피부타입 파생**(고민 목록이 비어 있을 때만 발동):
  `DRY → [moisture, barrier]` / `OILY → [sebum, pore]` /
  `COMBINATION → [moisture, sebum]` / `SENSITIVE → [soothe, gentle]`
  파생 태그를 고민 자리에 대입해 티어1을 그대로 태운다. 고민을 하나라도 골랐으면 발동하지 않는다 —
  직접 고른 것이 추론한 것을 이긴다.

### 6.3 티어2 매칭 (행동 있음)

1. 규칙 `r`의 점수 = `e.cat3`가 `r.fromCategoryCode`로 시작하고
   (`r.fromTagSlug`가 null이거나 `e.tags`에 포함)인 이벤트 `e`의 `w` 합. 0이면 탈락.
2. **프로필 가산**: `r.toTagSlug`가 고민 목록에 있으면 점수 × 1.5.
   (배수로 두는 이유 — 고정값을 더하면 이벤트가 적을 때 프로필이 전부를 결정하고, 많을 때는
   아무 영향도 없다. 비율이면 어느 쪽에서도 "거들기"로 작동한다.)
3. 정렬: `priority` 오름차순(BUFFER 10 < NEXT_STEP 20 — 완충이 이긴다) → 점수 내림차순.
4. `r.toCategoryCode`가 속한 `ROUTINE_STEPS` 항목으로 매핑(`step.categoryCode` 접두사 일치).
   **STEP당 목표 1개**, **개인화 섹션 최대 2개**.
5. `PAIRED_REMOVAL`도 그대로 쓴다 — "선크림을 많이 봄 → STEP 01 클렌징이 클렌징오일로"는
   메인에서 오히려 자연스럽다.

**개인화 섹션 상한 2의 근거**: 5단계가 전부 바뀌면 개인화가 아니라 "다른 화면"으로 읽힌다.
2개면 나머지 3개가 기준선 역할을 해 무엇이 바뀌었는지 사용자가 알아볼 수 있다.

### 6.4 티어1 매칭 (프로필만)

고민 슬러그마다 `concernRules`에서 규칙을 찾아 `priority` 오름차순 → 고민 선택 순서로 정렬,
STEP당 1개·최대 2섹션은 티어2와 같다.

### 6.5 후보 조회와 폴백

```
GET /goods?categoryCode={toCategoryCode}&tag={toTagSlug}&sort=popular&size=8
```

- **8개를 받아 4개를 그린다**: 사용감 tie-break에 쓸 여유분이다. 4개만 받으면 정렬해도 결과가
  같아 tie-break가 무의미해진다.
- **사용감 tie-break**: 프로필의 사용감 슬러그를 `tags`에 가진 후보를 앞으로 당긴다(안정 정렬).
  일치 개수가 같으면 서버가 준 인기순을 유지한다.
- **폴백**: 후보가 4개 미만이면 그 섹션만 태그를 떼고 기존 기본 쿼리로 되돌린다.
  쿼리키가 기존 `['routine-goods', categoryCode]`와 같아 캐시 히트한다 — 추가 호출이 없다.
- 전체 매칭 결과가 0개여도 화면은 정상이다. **폴백이 곧 현재 화면**이므로 빈 슬롯이 생기지 않는다.

## 7. 프론트 구조

```
frontend/src/features/affinity/
  events.ts      기록·읽기·링버퍼 유지. 손상 시 폐기
  profile.ts     이벤트 → (cat3 × tag) 점수 집계, 프로필 병합, 티어 판정
  flowRules.ts   규칙 fetch + ETag/localStorage 캐시
  match.ts       티어1·2 매칭 → 섹션별 목표 산출
```

- 네 모듈 모두 **순수 함수**(`flowRules`의 fetch만 예외)로, DOM·React 없이 단위 테스트한다.
- `Main.tsx`가 목표를 계산해 `RoutineSection`에 `override?: { tag: string; reason: string }`
  prop으로 내려준다. 섹션은 지금처럼 자기 데이터를 직접 가져오되 쿼리에 `tag`가 붙고,
  제목 아래 `reason` 한 줄이 추가된다. **컴포넌트 구조는 그대로다.**
- `queryKey`는 `['routine-goods', categoryCode, tag ?? null]` — 개인화/기본 결과가 서로를
  덮어쓰지 않게 한다.

## 8. 시각 설계 (DESIGN.md 확장)

현재 가입 2스텝·마이페이지 프로필은 전부 무채색이라 선택 상태가 테두리 굵기로만 구분된다.
고민 칩이 태그 슬러그가 되면서 **DESIGN.md "태그 컬러" 절(391행)의 승인된 팔레트 18종을 그대로
재사용**할 수 있다 — 프로필에서 고른 "모공"과 상품 카드의 "모공 케어" 태그가 같은 색이 되어
프로필과 상품이 시각적으로 이어진다. 팔레트를 새로 발명하지 않는다.

DESIGN.md에 **추가**해야 하는 것은 아래 세 가지뿐이다.

### 8.1 사용감(TEXTURE) 3종 색 배정

현재 "TEXTURE는 기존 회색 유지"(415행)로 못 박혀 있다. 이 줄을 아래 표로 대체한다.

| slug | bg | text |
|---|---|---|
| `fresh` 산뜻함 | `#ECFEFF` | `#155E75` |
| `dewy` 촉촉함 | `#EFF6FF` | `#1E40AF` |
| `matte` 매트 | `#F1F5F9` | `#334155` |

저채도 3종(청록·파랑·회청)으로 묶어 효과 태그의 다채로움과 **축이 다름**을 시각적으로 유지한다.
효과 태그 일부와 색상환에서 가까우나, 두 그룹은 항상 별도 `fieldset`에 있어 나란히 놓이지 않는다.

### 8.2 피부타입 4종 색 배정 (신규 절)

태그가 아니라 팔레트가 없다. §6.2의 피부타입 → 신호 태그 매핑에서 **대표 태그의 색을 빌린다** —
색이 곧 "이 선택이 어떤 추천으로 이어지는지"를 보여준다.

| 피부타입 | 대표 태그 | bg | text |
|---|---|---|---|
| DRY 건성 | `moisture` | `#E0F2FE` | `#0369A1` |
| OILY 지성 | `sebum` | `#CCFBF1` | `#0F766E` |
| COMBINATION 복합성 | `barrier` | `#FEF9C3` | `#854D0E` |
| SENSITIVE 민감성 | `gentle` | `#FCE7F3` | `#BE185D` |

COMBINATION의 신호 태그는 `[moisture, sebum]`이지만 그 두 색은 DRY·OILY가 이미 쓰므로 색만
`barrier`에서 빌린다. 네 카드가 서로 구분되는 것이 우선이다.

### 8.3 선택 상태 — 색 반전 (신규 절)

미선택에도 색이 있으므로 "색 유무"로는 선택을 표현할 수 없다. **반전**으로 표현한다.

| 요소 | 미선택 | 선택 |
|---|---|---|
| 고민·사용감 칩 | 틴트 배경(`bg`) + 같은 계열 진한 글자(`text`) | **배경 = `text` 색, 글자 = 흰색, 앞에 `✓`** |
| 피부타입 카드 | 흰 배경 + 좌측 3px 컬러 바(`text` 색) | 틴트 배경(`bg`) + 2px 테두리(`text` 색) + 제목이 `text` 색 + `✓` |

카드는 면적이 넓어 칩과 같은 채도로 채우면 화면이 요란해진다 — **한 단계 절제한다.**
설명글은 두 상태 모두 계속 읽힌다.

**반전 배경의 대비 보정**: 흰 글자 대비 4.5:1(WCAG AA)에 미달하는 슬러그 2종은 반전 배경에
한 단계 진한 값을 쓴다. 다른 슬러그는 `text` 색을 그대로 반전 배경에 쓴다.

| slug | `text` (미선택 글자) | 반전 배경 | 사유 |
|---|---|---|---|
| `barrier` | `#A16207` | **`#854D0E`** | `#A16207` 위 흰 글자 ≈ 4.05:1 (AA 미달) |
| `soothe` | `#15803D` | **`#166534`** | `#15803D` 위 흰 글자 ≈ 4.35:1 (AA 미달) |

적용 범위는 공용 컴포넌트 `SkinProfileFields.tsx` 하나이므로 **가입 2스텝과 마이페이지에 동시에
반영된다.** 기존 `aria-labelledby`/`aria-describedby` 접근성 이름 설계와 `aria-pressed`는 그대로
유지한다 — 색은 상태의 유일한 단서가 아니라 `✓`와 `aria-pressed`에 얹히는 보강 단서다.

## 9. 서버 비용

추가되는 상시 부하는 **0에 가깝다.**

| 호출 | 빈도 | 비용 |
|---|---|---|
| `/routine/flow-rules` | 앱 진입 1회 | 첫 회만 본문(~4KB), 이후 **304 + 본문 0바이트**. 해시는 기동 시 1회 계산이라 DB 조회 없음 |
| `/members/me` | 이미 있음 | 메인이 캐시된 쿼리를 재사용 — 신규 호출 아님 |
| `/goods?...` | 개인화 섹션당 1회 (최대 2) | 기존 엔드포인트. 목표 조합이 유한해 캐시 친화적 |

사용자별 프로필 테이블도, 이벤트 수집 엔드포인트도, 야간 배치도 없다.

## 10. 한계 — 버그가 아니라 트레이드오프

- **기기 간 비일관.** 행동 프로필이 `localStorage`라 폰과 노트북의 추천이 다르다. 서버 비용·프라이버시와
  맞바꾼 결과이므로 고치려 들지 않고 한계로 명시한다. (프로필 신호는 서버에서 오므로 기기가 달라도
  티어1 결과는 같다 — 어긋나는 것은 행동 부분뿐이다.)
- **메인 레일은 규칙 수준 안전까지만.** PDP next-step은 "현재 상품 × 후보" 쌍이 있어 궁합 게이트를
  태웠지만(next-step 설계 §5-4), 메인은 앵커 상품이 없어 pairwise 검사가 성립하지 않는다.
  전이 규칙 자체가 궁합의 하위 호환(자극 중첩 계승 없음)으로 설계돼 있고, **쌍 단위 검증은 PDP의
  역할**이다. 이 역할 분리를 지키는 한 메인에서 위험 조합이 나오지 않는다.
- **행동 프로필이 기기 밖으로 나가지 않는다.** 부수 효과가 아니라 설계 의도다.

## 11. 검증 (DoD)

### 순수 함수 유닛 테스트 (`features/affinity/`)

- `events`: 링버퍼가 50개를 넘으면 오래된 것부터 버린다 / 손상된 JSON을 읽으면 빈 배열을 돌려주고
  던지지 않는다 / leaf 10자 카테고리가 `cat3` 7자로 절단된다 / `cat3` 없는 찜 호출은 기록되지 않는다.
- `profile`: 조회 3회(3점)와 담기 1회(3점)의 (cat3 × tag) 점수가 같다 / 이벤트 4개면 티어1,
  5개면 티어2로 판정한다 / 고민이 하나라도 있으면 피부타입 파생 태그가 쓰이지 않는다 /
  고민이 비면 `SENSITIVE`가 `[soothe, gentle]`로 파생된다.
- `match` 티어2: `from` 태그가 null인 규칙이 태그 무관하게 매칭된다 / 같은 상품에 BUFFER와
  NEXT_STEP이 걸리면 `priority`가 낮은 BUFFER만 남는다 / `toTagSlug`가 고민에 있으면 점수가
  1.5배가 되어 순위가 뒤집힌다 / 목표가 3개 이상 나와도 **2섹션만** 반환한다 / 한 STEP에 목표가
  둘이면 상위 1개만 남는다.
- `match` 티어1: 고민 2개가 서로 다른 STEP을 겨냥하면 둘 다 반환한다 / 프로필이 비면 빈 배열을 반환한다.
- 사용감 tie-break: 8개 후보 중 `dewy`를 가진 후보가 앞으로 오고, 동점이면 서버 인기순이 유지된다.

### 백엔드 테스트

- `flow-rules` 응답에 `flowRules` 12행 + `concernRules` 전량이 실린다.
- 같은 ETag로 `If-None-Match` 재요청 시 **304이고 본문이 비어 있다.**
- **시드 정합**: `concern_target_rule`의 모든 행에 대해 (`to_category_code` 접두사 + `to_tag_slug`
  보유 + 비HIDDEN) 상품이 **4개 이상** 존재한다. 미달 행이 있으면 실패하고 그 행을 출력한다.
- `concern_tag_slug`·`to_tag_slug`가 전부 `tag` 테이블에 실재한다(물리 FK가 없으므로 테스트로 잡는다).
- 실 MySQL clean 로드(V1~V78) + `ddl-auto=validate` 통합 테스트.
  (H2 `create-drop`이 validate 불일치를 가리는 함정 회피 — 기존 웨이브에서 실제로 겪었다.)

### 화면 테스트

- `Main` 렌더 테스트(MSW): 프로필·행동이 모두 없으면 기존 쿼리 그대로 / 프로필만 있으면 지정
  섹션에 `tag`가 붙고 reason 문장이 렌더된다 / 후보 4개 미만이면 기본 쿼리로 폴백한다.
- `SkinProfileFields`: 칩 선택 시 `aria-pressed="true"`와 반전 클래스가 함께 붙는다.
- **스크린샷 (DoD — 테스트 통과가 대체하지 못한다)**
  1. 색이 들어간 가입 2스텝(선택 전 / 몇 개 선택 후)
  2. 개인화 전 메인 / 개인화 후 메인(reason 문장 포함)

## 12. 실행 구조

터미널 3개, **2웨이브**. 파일 소유권이 겹치지 않게 나눴다.

**웨이브 A (병렬 2터미널)**

1. **프로필 태그 교체** — V77 마이그레이션 + `SkinProfileFields.tsx`·`api/auth.ts`(`Concern` 유니온)
   + `SkinProfileFields.css` + DESIGN.md §8 세 절 추가. 화면 태스크이므로 스크린샷 DoD.
2. **규칙 배포 API** — V78 테이블·시드 + `routine` 패키지 컨트롤러·서비스·DTO + `SecurityConfig` 1줄.

Flyway 번호(V77 / V78)를 계획서에서 미리 고정하므로 두 터미널이 마이그레이션 파일을 두고
충돌하지 않는다. **공유 계약이므로 번호를 즉흥으로 바꾸지 않는다.**

**웨이브 B (웨이브 A 머지 후, 1터미널)**

3. **메인 개인화** — `features/affinity/` 4모듈 + `Main.tsx`·`RoutineSection.tsx`·`GoodsCard.tsx`
   배선 + MSW 핸들러. ②의 API 계약과 ①의 슬러그 집합에 모두 의존한다.

모델 배분: 세 태스크 모두 **sonnet**. 규칙 충돌 해석이 있는 §6.3 매칭은 CLAUDE.md의 opus 예외
3종(결제·재고 차감·궁합 엔진)에 해당하지 않는다 — 이 설계서가 우선순위와 상한을 값으로 못 박았고,
틀려도 돈·재고 사고가 아니라 추천이 어긋날 뿐이며 테스트가 전량 잡는다.

## 13. 범위 밖 (이월)

- 크로스 디바이스 행동 프로필 동기화 (`bb.skinType`의 로그인 승격 패턴 재사용 여지만 남긴다)
- 개인화 끄기 토글·행동 기록 초기화 UI
- 검색어·태그 필터 사용을 신호로 추가 (카테고리 축이 없어 별도 매핑 규칙이 필요)
- 관리자 `concern_target_rule` CRUD (시드 전용)
- 시간대(아침/저녁) 축
