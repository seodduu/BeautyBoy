# Wave 3: 루틴·궁합(백엔드) + 프론트 탐색 페이지 조립 구현 계획

> **For agentic workers:** 이 계획서는 오케스트레이터가 태스크마다 서브에이전트를 스폰해 TDD로
> 실행한다. 스텝은 체크박스(`- [ ]`)로 추적한다. 상위 문서 — 웨이브 구조·공유 계약의 진실은
> `docs/plans/2026-07-23-roadmap.md`, 설계 근거는 `docs/superpowers/specs/2026-07-23-beautyboy-design.md`,
> 시각 토큰의 진실은 루트 `DESIGN.md`.

**목표:** 루틴 조회·성분 궁합 진단 백엔드(T1)와 상세·검색·랭킹 탐색 페이지 실API 연동(T2)을 만들어,
로드맵 W3 DoD("루틴 조회·궁합 진단 + 프론트 탐색 플로우가 실API로 동작")를 충족한다.

**아키텍처:** 두 터미널이 겹치지 않는 파일 집합에서 병렬로 돈다.
- **T1(`feat/routine-compat`, 백엔드)** — 궁합 엔진은 Wave 1이 남긴 성분 인프라(`IngredientRule`
  분류쌍 규칙 + `GoodsIngredientQueryService.findCategoriesByGoodsIds`)를 **조립**한다. 새로 만드는 건
  `routine` 도메인(템플릿/단계/추천)과 조립 로직·API 두 개뿐이다.
- **T2(`feat/front-pages`, 프론트)** — 메인(`/main`)·목록(`/goods`)은 이미 main에 있으므로
  **상세·검색·랭킹만 신규**로 만들고, Wave 1 T2 셸이 이월한 UX 프리미티브(토스트·자동완성/무결과·
  aria-live)를 소비처와 함께 만든다.

**Tech Stack:** Spring Boot 3 · JPA · Flyway · JUnit5(H2 MySQL 모드) / React 19 · React Router ·
TanStack Query · Zustand · MSW · Vitest · Testing Library.

## Global Constraints (모든 태스크에 적용)

- **패키지 = 서비스 경계.** 타 도메인 엔티티/리포지토리를 직접 import하지 않는다. 접근은
  `*QueryService`/`*CommandService` 인터페이스 경유(CLAUDE.md). routine은 catalog·ingredient를
  이 인터페이스로만 본다.
- **동결 파일 손대지 않는다.** `common`(ApiResponse·PageResponse·ErrorResponse·전역 핸들러),
  루트 빌드 설정, `docker-compose.yml`, `config/SecurityConfig.java`, `frontend/package.json`.
  `common/ErrorCode.java`는 예외적으로 **자기 도메인 접두사 상수 추가만** 허용(§공유계약).
- **Flyway 자기 대역만.** T1 = **V50~V59**. 대역 밖 번호 금지.
- **공개 엔드포인트는 설계 7장이 진실.** `/routines`·`/compat/check`는 7장 공개 목록에 이미 있다
  (`SecurityConfig`가 Wave 0에서 선반영). **새 공개 경로가 필요하면 열지 말고 보고한다.**
- **시각 토큰은 `DESIGN.md` 이름을 직접 참조**하고 hex를 손으로 옮기지 않는다. 문서에 없는 값이
  필요하면 만들지 말고 보고한다.
- **테스트 규약(§공유계약 5).** 단위/슬라이스 = H2(MySQL 모드, Flyway off, ddl-auto=create-drop).
  마이그레이션을 추가하는 T1은 `./gradlew integrationTest`(실 MySQL validate 스모크)를 DoD에 포함하고
  적용 버전 목록 단언도 함께 늘린다.
- **상태 변경 검증 시 `TestPersistence.DB_왕복_강제(em)`.** 재조회 전에 호출(1차 캐시가 왕복을 가림).
  조회 전용 테스트에는 쓰지 않는다. — T1은 대부분 조회 전용이라 해당 지점이 적다.

---

## 착수 전 확정 사항 (이 계획서에서 결정 — 서브에이전트는 그대로 따른다)

### 결정 1: T2 범위 = 상세·검색·랭킹 신규 + 이월 UX. 메인·목록은 재작업하지 않는다

로드맵 W3 T2는 "메인/목록/상세/검색/랭킹"이지만, `/main`(루틴 메인)과 `/goods`(목록)는
`00d17ed` 등으로 **이미 main에 머지**됐다(`pages/Main.tsx`·`pages/GoodsList.tsx`·`RoutineSection`·
`features/routine/steps.ts`·`RequireAuth`). 이 파일들은 **열지 않는다**(필요하면 보고). T2 신규 작업은
**상세·검색·랭킹 세 페이지 + 이월 UX 프리미티브**뿐이다.

### 결정 2: 상세 탭은 읽기, 단 "장바구니 담기"만 쓰기로 넣어 이월 토스트의 소비처를 만든다

로드맵 이월 항목(로드맵 §"Wave 3 T2로 이월")은 "장바구니 담김 토스트·검색 자동완성/무결과·상태변화
aria-live"를 **"소비처가 생길 때 함께 만든다"**고 못박았다. 지금 안 만들면 소비처 없는 죽은 코드가 된다.
- 검색 자동완성/무결과 → **검색 페이지(2-4)**가 소비처.
- 장바구니 담김 토스트 + aria-live → **상세 페이지(2-3)의 "담기" 버튼**이 소비처.
- 따라서 상세는 읽기 전용(설명·성분배지·리뷰 목록·Q&A 목록)이되, **"장바구니 담기" 한 버튼만
  실동작**(`POST /cart/items` → 성공 토스트)시킨다. 리뷰/문의 **작성**과 장바구니 **화면·주문 플로우**는
  Wave 4(통합) 몫이라 넣지 않는다.

### 결정 3: 루틴 궁합의 프론트 소비(`/routine` 가이드 화면)는 Wave 4다

T1은 `/routines`·`/compat/check` **API만** 낸다. 이 API를 쓰는 루틴 가이드 화면(프로필→단계카드→
궁합검사→전체담기)은 로드맵상 Wave 4 "루틴 프론트 통합"이다. T2는 이 API를 **소비하지 않는다** —
T1·T2가 서로를 기다리지 않아 병렬이 성립한다. T1은 API 계약을 curl/통합테스트로 자기 완결 검증한다.

### 결정 4: routine 매핑 상수는 프론트에 이미 있다 — 백엔드 routine과 이원화하지 않는다

`/main`의 5단계↔categoryCode 매핑은 `features/routine/steps.ts` 프론트 상수다(로드맵 결정 그대로).
T1의 `routine` 도메인은 **그것과 다른 것**을 낸다 — 피부타입별 큐레이션 템플릿(단계별 추천 상품).
같은 "루틴"이라는 말이지만 축이 다르다: 프론트 상수 = 메인 스크롤 교육용 카테고리 순서, 백엔드
routine = 피부타입 맞춤 추천 템플릿(Wave 4 가이드 화면용). **T1은 프론트 상수를 건드리지 않는다.**

### 결정 5: 궁합 규칙 시드는 이미 있다 — 재시드하지 않고 존재를 단언한다

`ingredient_rule` 규칙은 `V12__seed_catalog.sql`에 시드돼 있다(`INSERT INTO ingredient_rule` 확인됨).
T1 궁합 엔진은 이 규칙을 읽기만 한다. **새 규칙 시드를 만들지 않는다.** 대신 궁합 테스트가 시드된
규칙(예: 레티노이드×AHA=CONFLICT)에 의존하므로, 테스트는 시드 존재를 픽스처로 재현하지 말고
**엔진 로직만 단위 검증**하고(규칙 리포지토리는 스텁/실데이터), 통합 확인은 `/compat/check` curl로 한다.

---

## 분기 전 사전 정리 (오케스트레이터, worktree 따기 **전** main 직접 커밋)

로드맵 §"Wave 3 착수 전"의 잠재 버그를 분기 전에 닫는다. 병렬 두 터미널 중 누구의 소유도 아니고
(order 경로), 지금 고치면 나중에 Wave 4에서 되밟지 않는다.

- [ ] **기본배송지 2개일 때 500 → 최신 1건 반환으로 방어**

`AddressRepository.findByMemberIdAndIsDefaultTrue`가 `Optional` 반환인데 DB 유니크 제약이 없어,
기본배송지가 2개가 되면 `NonUniqueResultException`(500)이 난다. 오늘드림 제외로 긴급도는 낮지만
결함은 유효하다. 조회를 "최신 1건"으로 바꿔 방어한다(구조적 유니크 제약은 Wave 4 보정 대역 V60~).

```java
// backend/src/main/java/com/beautyboy/member/AddressRepository.java
// 변경 전: Optional<Address> findByMemberIdAndIsDefaultTrue(Long memberId);
Optional<Address> findFirstByMemberIdAndIsDefaultTrueOrderByIdDesc(Long memberId);
```

호출부(있다면)의 메서드명을 함께 바꾼다. 실패하는 테스트를 먼저 쓴다:

```java
// 같은 member에 isDefault=true 주소 2건을 심고, 조회가 예외 없이 최신 1건을 반환하는지
@Test
void 기본배송지가_둘이어도_500이_아니라_최신_1건을_반환한다() { /* ... */ }
```

- [ ] `./gradlew test` 녹색 확인 후 커밋:
  `git commit -am "fix(member): 기본배송지 중복 시 500 방어 — findFirst...OrderByIdDesc"`

이 커밋이 두 worktree의 공통 기점이 된다.

---

# 터미널 T1 — `feat/routine-compat` (백엔드)

**모델 배분:** Task 1-5(궁합 엔진)만 **★opus**(CLAUDE.md 모델 배분 예외 — "취향은 클라이언트,
판정은 신중히"에 해당하는 진단 로직). 나머지는 sonnet.

**터미널이 여는 파일:** `backend/src/main/java/com/beautyboy/routine/**`,
`backend/src/main/java/com/beautyboy/compat/**`, `backend/src/main/resources/db/migration/V50~V51`,
`backend/src/main/java/com/beautyboy/catalog/GoodsQueryService.java`(+구현),
`backend/src/main/java/com/beautyboy/common/ErrorCode.java`(자기 접두사 추가만),
`backend/src/test/java/.../FlywayMigrationSmokeTest.java`(버전 목록 단언만).

## Task 1-1: Flyway V50 — routine 스키마

**Files:**
- Create: `backend/src/main/resources/db/migration/V50__routine.sql`

**Interfaces (Produces):** 테이블 `routine_template`·`routine_step`·`routine_step_goods`.
`routine_step_goods.goods_no`는 `goods(goods_no)`를 논리적으로 가리키되 **물리 FK를 걸지 않는다**
(패키지 경계 — catalog 소유 테이블을 routine 스키마가 잠그면 도메인 결합이 생긴다. Wave 1 ingredient
`goods_ingredient`와 동일한 규약).

- [ ] **Step 1: 마이그레이션 작성**

```sql
-- V50__routine.sql — 루틴 큐레이션 템플릿(피부타입×시간대). 추천 상품은 goods_no 논리참조(물리 FK 없음).
CREATE TABLE routine_template (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(80)  NOT NULL,
  skin_type   VARCHAR(20)  NOT NULL,           -- DRY|OILY|COMBINATION|SENSITIVE
  time_slot   VARCHAR(20)  NOT NULL,           -- 1차: BASIC 하나(아침/저녁 미구분)
  description  VARCHAR(300) NOT NULL,
  CONSTRAINT uq_routine_template UNIQUE (skin_type, time_slot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE routine_step (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  template_id  BIGINT      NOT NULL,
  step_order   TINYINT     NOT NULL,           -- 1..5
  step_name    VARCHAR(40) NOT NULL,           -- 클렌징 / 토너 / 세럼 / 크림 / 선크림
  beginner_tip VARCHAR(200) NOT NULL,
  CONSTRAINT fk_step_template FOREIGN KEY (template_id) REFERENCES routine_template(id),
  CONSTRAINT uq_step_order UNIQUE (template_id, step_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE routine_step_goods (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  step_id   BIGINT  NOT NULL,
  goods_no  BIGINT  NOT NULL,                  -- goods(goods_no) 논리참조. 물리 FK 없음(패키지 경계).
  sort_order TINYINT NOT NULL,
  CONSTRAINT fk_step_goods_step FOREIGN KEY (step_id) REFERENCES routine_step(id),
  CONSTRAINT uq_step_goods UNIQUE (step_id, goods_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: 커밋** — `git commit -am "feat(routine): V50 스키마 — 템플릿·단계·단계별추천"`

(엔티티 타입 주의: `step_order`·`sort_order`는 `TINYINT`. 엔티티에서 `int` + `@JdbcTypeCode(SqlTypes.TINYINT)`로
매핑해야 실 MySQL `validate`가 통과한다 — Wave 1 `Ingredient` 교훈. 이건 Task 1-4 엔티티 스텝에서 반영.)

## Task 1-2: Flyway V51 — routine 시드

**Files:**
- Create: `backend/src/main/resources/db/migration/V51__seed_routine.sql`

**Interfaces (Consumes):** `V12__seed_catalog.sql`가 심은 goods_no. **시드 goods_no를 하드코딩하기 전에
V12를 열어 실제 존재하는 번호를 확인한다** — 없는 번호를 참조하면 `/routines` 추천이 빈 목록이 된다.

- [ ] **Step 1: 시드 작성** — 피부타입 4종 × time_slot=`BASIC` 템플릿 4개, 각 5단계
  (클렌징`C002`/토너`C001001`/세럼`C001002`/크림`C001003`/선크림`C004001`), 단계마다 그 카테고리에
  속한 **시드 상품 2~3개**를 `routine_step_goods`로 연결. `beginner_tip`은 초보자용 한 줄
  (예: "거품 낸 뒤 30초 이내로 헹구세요").

- [ ] **Step 2: 커밋** — `git commit -am "feat(routine): V51 시드 — 피부타입 4종 기본 루틴"`

## Task 1-3: catalog `GoodsQueryService` 확장 — 상품 카드 배치 조회

routine이 단계별 추천을 **카드(`GoodsListItem`)**로 내려면 goods 번호 목록을 카드로 바꿔야 하는데,
routine은 goods 테이블에 직접 접근할 수 없다. ranking의 `SalesStatProvider`와 같은 **의존성 역전**으로
catalog가 배치 조회를 내준다. **T1이 catalog의 유일한 소비자이자 Wave 3 백엔드 단일 터미널**이므로
이 파일을 여는 것은 병렬 충돌을 일으키지 않는다.

**Files:**
- Modify: `backend/src/main/java/com/beautyboy/catalog/GoodsQueryService.java`
- Modify: `backend/src/main/java/com/beautyboy/catalog/GoodsService.java` (구현)
- Test: `backend/src/test/java/com/beautyboy/catalog/GoodsServiceTest.java` (신규 케이스 추가)

**Interfaces (Produces):**
```java
/** goods_no 목록 → 카드 아이템. HIDDEN 상품은 제외한다(목록·상세와 같은 노출 기준).
 *  입력 순서를 보존하지 않는다 — 호출자가 필요하면 자기 순서로 재정렬한다. */
List<GoodsListItem> findListItems(Collection<Long> goodsNos);
```

- [ ] **Step 1: 실패 테스트** — 존재하는 goods 2개 + HIDDEN 1개를 주고, `findListItems`가 노출 2개만
  카드로 반환하는지. HIDDEN 제외를 명시적으로 단언한다.

```java
@Test
void findListItems_는_HIDDEN을_빼고_카드로_반환한다() {
    var items = goodsService.findListItems(List.of(노출A, 노출B, 숨김C));
    assertThat(items).extracting(GoodsListItem::goodsNo).containsExactlyInAnyOrder(노출A, 노출B);
}
```

- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*GoodsServiceTest*'` → 컴파일 실패(메서드 없음)
- [ ] **Step 3: 인터페이스 + 구현** — 기존 목록 조회의 `GoodsListItem` 프로젝션 로직을 재사용해
  `WHERE goods_no IN (:ids) AND status <> 'HIDDEN'`로 구현. 새 매퍼를 만들지 말고 기존 것을 부른다(DRY).
- [ ] **Step 4: 통과 확인** — `./gradlew test --tests '*GoodsServiceTest*'` → PASS
- [ ] **Step 5: 커밋** — `git commit -am "feat(catalog): GoodsQueryService.findListItems — 배치 카드 조회(의존성 역전)"`

## Task 1-4: routine 도메인 + `GET /routines?skinType=&time=`

**Files:**
- Create: `routine/RoutineTemplate.java`, `routine/RoutineStep.java`, `routine/RoutineStepGoods.java`
- Create: `routine/RoutineTemplateRepository.java`
- Create: `routine/RoutineQueryService.java`, `routine/RoutineService.java`
- Create: `routine/RoutineController.java`
- Create: `routine/dto/RoutineResponse.java`, `routine/dto/RoutineStepResponse.java`
- Modify: `common/ErrorCode.java` (`ROUTINE_TEMPLATE_NOT_FOUND` 추가 — 자기 접두사)
- Test: `routine/RoutineServiceTest.java`, `routine/RoutineControllerTest.java`

**Interfaces (Consumes):** `GoodsQueryService.findListItems`(Task 1-3).
**Interfaces (Produces):**
```java
public record RoutineResponse(Long templateId, String name, String skinType, String time,
                              String description, List<RoutineStepResponse> steps) {}
public record RoutineStepResponse(int stepOrder, String stepName, String beginnerTip,
                                  List<GoodsListItem> recommendations) {}
```

- [ ] **Step 1: 엔티티** — 3개 엔티티. `step_order`/`sort_order`는 `int` + `@JdbcTypeCode(SqlTypes.TINYINT)`.
  연관은 `@ManyToOne(LAZY)` 또는 명시적 조회로. `routine_step_goods.goodsNo`는 **연관이 아니라 Long 값**
  (물리 FK 없음 — Task 1-1 규약).
- [ ] **Step 2: 실패 테스트(서비스)** — `skinType=DRY, time=BASIC` 조회 시 5단계가 `step_order` 순으로
  오고, 각 단계 추천이 `GoodsListItem` 카드로 채워지는지. `skinType`이 없으면 기본 템플릿(예:
  `COMBINATION/BASIC`)으로 폴백하는지. 매칭 실패(없는 피부타입) 시 `ROUTINE_TEMPLATE_NOT_FOUND`.

```java
@Test
void 피부타입으로_템플릿을_찾고_단계별_추천을_카드로_채운다() {
    given(goodsQueryService.findListItems(any())).willReturn(List.of(카드1, 카드2));
    RoutineResponse r = routineService.find("DRY", "BASIC");
    assertThat(r.steps()).hasSize(5);
    assertThat(r.steps().get(0).stepOrder()).isEqualTo(1);
    assertThat(r.steps().get(0).recommendations()).isNotEmpty();
}
```

- [ ] **Step 3: 실패 확인** → 컴파일/실패
- [ ] **Step 4: 구현** — `RoutineService`: 템플릿 조회 → 단계 정렬 → 단계별 goods_no 모아
  `findListItems` **한 번**으로 배치 조회(단계별 N번 호출 금지 — N+1 방지) 후 단계별로 분배.
- [ ] **Step 5: 통과 확인** → PASS
- [ ] **Step 6: 컨트롤러 + 슬라이스 테스트** — `@GetMapping("/api/v1/routines")`,
  `@RequestParam(required=false) skinType, time`. 응답은 `ApiResponse<RoutineResponse>`.
  `RoutineControllerTest`(`@WebMvcTest`)로 200 + 바디 형태 단언. **공개 경로**이므로 인증 헤더 없이 200.
- [ ] **Step 7: 커밋** — `git commit -am "feat(routine): GET /routines — 피부타입별 큐레이션 템플릿"`

## Task 1-5: ★opus 성분 궁합 엔진 + `POST /compat/check`

**Files:**
- Create: `compat/CompatService.java`, `compat/CompatController.java`
- Create: `compat/dto/CompatCheckRequest.java`, `compat/dto/CompatCheckResponse.java`, `compat/dto/CompatFinding.java`
- Modify: `common/ErrorCode.java` (`COMPAT_EMPTY_SELECTION` 추가 — 자기 접두사)
- Test: `compat/CompatServiceTest.java`, `compat/CompatControllerTest.java`

**Interfaces (Consumes):**
- `ingredient.GoodsIngredientQueryService.findCategoriesByGoodsIds(Collection<Long>) → Map<Long, Set<String>>`
- `ingredient.IngredientRuleRepository.findNormalized(String, String) → Optional<IngredientRule>`
  (`verdict` ∈ CONFLICT|CAUTION|SYNERGY, `reason` 텍스트)

**Interfaces (Produces):**
```java
public record CompatCheckRequest(List<Long> goodsNos) {}

public record CompatCheckResponse(
    String overall,               // CONFLICT|CAUTION|SYNERGY|OK — findings의 최악 등급(없으면 OK)
    List<CompatFinding> findings  // 발견된 판정들. SYNERGY도 포함(설득 재료)
) {}

public record CompatFinding(
    String verdict,               // CONFLICT|CAUTION|SYNERGY
    String categoryA, String categoryB,
    String reason,
    List<Long> goodsNos           // 이 판정에 기여한 상품들(A 또는 B 분류를 가진 것)
) {}
```

**엔진 알고리즘(확정 — 서브에이전트는 그대로):**
1. 요청 `goodsNos`가 비었으면 `COMPAT_EMPTY_SELECTION`(400).
2. `cats = findCategoriesByGoodsIds(goodsNos)` — 상품→분류집합.
3. **분류→그 분류를 가진 상품집합** 역인덱스 `byCategory: Map<String, Set<Long>>`를 만든다.
4. 등장한 **서로 다른 분류의 무순서 쌍**(사전순 `ca ≤ cb`, 같은 분류 쌍 제외) 각각에 대해
   `findNormalized(ca, cb)` 조회. 규칙이 있으면 `CompatFinding` 생성 —
   `goodsNos = byCategory[ca] ∪ byCategory[cb]`를 정렬해 담는다.
   - **주의: 한 상품이 두 분류를 다 가진 자기충돌(예: 레티놀+AHA 한 제품)도 잡힌다** — `ca`·`cb`가
     같은 상품에서 나와도 쌍은 성립한다. 이게 "조합 검사"의 핵심(설계 8장 "자극 중첩").
5. `overall` = findings의 최고 심각도. 순위 `CONFLICT > CAUTION > SYNERGY`, 없으면 `OK`.
6. findings는 `verdict` 심각도 내림차순, 동률이면 `categoryA` 사전순으로 정렬해 반환(결정적 출력).

- [ ] **Step 1: 실패 테스트(엔진, 순수 단위 — mock 리포지토리)**

```java
@Test
void 레티노이드와_AHA가_한_선택에_있으면_CONFLICT를_낸다() {
    given(goodsIngredientQueryService.findCategoriesByGoodsIds(List.of(1L, 2L)))
        .willReturn(Map.of(1L, Set.of("레티노이드"), 2L, Set.of("AHA")));
    given(ruleRepository.findNormalized("AHA", "레티노이드"))
        .willReturn(Optional.of(new IngredientRule(null, "AHA", "레티노이드", "CONFLICT", "자극 중첩")));

    CompatCheckResponse r = compatService.check(new CompatCheckRequest(List.of(1L, 2L)));

    assertThat(r.overall()).isEqualTo("CONFLICT");
    assertThat(r.findings()).singleElement().satisfies(f -> {
        assertThat(f.verdict()).isEqualTo("CONFLICT");
        assertThat(f.goodsNos()).containsExactly(1L, 2L);
    });
}

@Test
void 규칙_없는_조합은_OK다() { /* findNormalized가 전부 empty → overall=OK, findings 비어있음 */ }

@Test
void 빈_선택은_COMPAT_EMPTY_SELECTION을_던진다() { /* BusinessException 단언 */ }
```

- [ ] **Step 2: 실패 확인** → 컴파일/실패
- [ ] **Step 3: 엔진 구현** — 위 알고리즘. 분류 쌍은 `for i<j`로 중복 없이 순회. `findNormalized`
  호출 횟수는 등장 분류 수 K에 대해 최대 K·(K-1)/2 (K는 시드상 한 자릿수 — 캐시 불필요).
- [ ] **Step 4: 통과 확인** → PASS
- [ ] **Step 5: 컨트롤러 + 슬라이스 테스트** — `@PostMapping("/api/v1/compat/check")`, 바디
  `CompatCheckRequest`, 응답 `ApiResponse<CompatCheckResponse>`. **공개 경로**(설계 7장 유일한 공개
  POST)이므로 비로그인 200. `CompatControllerTest`로 요청/응답 형태 + 빈 선택 400 단언.
- [ ] **Step 6: 커밋** — `git commit -am "feat(compat): POST /compat/check — 성분 분류쌍 궁합 진단"`

## Task 1-6: Flyway 스모크 테스트 버전 목록 갱신 + T1 마감

**Files:**
- Modify: `backend/src/test/java/.../FlywayMigrationSmokeTest.java` (적용 버전 단언에 `V50`·`V51` 추가)

- [ ] **Step 1: 스모크 단언 갱신** — 실 MySQL에 걸리는 적용 버전 목록에 `50`·`51`을 추가한다
  (로드맵 §Wave 2 테스트 전략: "마이그레이션 추가 웨이브는 적용 버전 목록 단언도 함께 늘린다").
- [ ] **Step 2: 전체 단위/슬라이스 녹색** — `./gradlew test` → PASS
- [ ] **Step 3: 실 MySQL 스모크** — `./gradlew integrationTest` → `ddl-auto=validate` 컨텍스트 기동 +
  V50/V51 적용 확인. **엔티티↔스키마 TINYINT/BIGINT 불일치가 여기서만 잡힌다**(H2는 가림).
  실패하면 엔티티 `@JdbcTypeCode` 매핑을 고친다.
- [ ] **Step 4: API 자기완결 검증(curl)** — 앱 기동 후:
  `GET /api/v1/routines?skinType=DRY&time=BASIC` → 5단계 카드 채워짐,
  `POST /api/v1/compat/check {"goodsNos":[<레티노이드 상품>,<AHA 상품>]}` → `overall:"CONFLICT"`.
  결과를 보고서에 남긴다.
- [ ] **Step 5: 커밋** — `git commit -am "test(flyway): V50·V51 스모크 버전 단언 + Wave 3 T1 마감"`

---

# 터미널 T2 — `feat/front-pages` (프론트)

**모델 배분:** 전 태스크 sonnet(CLAUDE.md 예외 3종 해당 없음).
**터미널이 여는 파일:** `frontend/src/pages/{Detail,Search,Ranking}.tsx`(+`.css`),
`frontend/src/api/{search,ranking,review,qna,ingredient,cart}.ts`,
`frontend/src/components/ui/Toast.tsx`·`ToastProvider.tsx`,
`frontend/src/components/search/*`, `frontend/src/components/goods/*`(상세 탭 하위 컴포넌트 신규만),
`frontend/src/router.tsx`, `frontend/src/mocks/handlers.ts`(신규 mock 추가),
`frontend/src/main.tsx`(ToastProvider 마운트만).
**열지 않는 파일:** `pages/Main.tsx`·`pages/GoodsList.tsx`·`RoutineSection`·`features/routine/steps.ts`·
`RequireAuth`·`api/client.ts`·`package.json`(결정 1·Global Constraints).

## Task 2-1: 탐색 API 모듈 + 서버 응답 타입

**Files:**
- Create: `api/search.ts`, `api/ranking.ts`, `api/review.ts`, `api/qna.ts`, `api/ingredient.ts`, `api/cart.ts`
- Create: `types/detail.ts`, `types/search.ts`, `types/review.ts` (서버 계약 타입)
- Test: `api/search.test.ts` (계약 형태 + 파라미터 직렬화 1건으로 대표 검증)

**Interfaces (Consumes):** 공용 `api`(`api/client.ts`, 열지 않고 import만), `ApiEnvelope`·`PageResponse`
(`types/goods.ts`의 기존 타입 재사용 — 새로 만들지 않는다).
**Interfaces (Produces):** 아래 fetch 함수들. 페이징 응답은 전부 `PageResponse<T>`(설계 7장 동결 계약).

```ts
// api/search.ts
export function fetchSearch(q: string, params?: { sort?: string; page?: number }):
  Promise<PageResponse<GoodsListItem>>;                       // GET /search?q=&sort=&page=
export function fetchAutocomplete(q: string): Promise<string[]>;   // GET /search/autocomplete?q= (prefix 10)
export function fetchPopularKeywords(): Promise<string[]>;         // GET /search/popular-keywords

// api/ranking.ts
export function fetchRanking(categoryCode?: string): Promise<PageResponse<GoodsListItem>>;  // GET /rankings?categoryCode=

// api/review.ts  — 읽기만
export function fetchReviews(goodsNo: number, params?: { sort?: string; photoOnly?: boolean }):
  Promise<PageResponse<ReviewItem>>;                          // GET /reviews?goodsNo=
export function fetchReviewStats(goodsNo: number): Promise<ReviewStats>;   // GET /reviews/stats?goodsNo=

// api/qna.ts   — 읽기만
export function fetchQna(goodsNo: number): Promise<PageResponse<QnaItem>>; // GET /qna?goodsNo=

// api/ingredient.ts
export function fetchIngredients(goodsNo: number): Promise<IngredientBadge[]>;  // GET /goods/{n}/ingredients

// api/cart.ts  — 상세 "담기"의 최소 쓰기 하나만
export function addCartItem(goodsNo: number, optionNo: number | null, quantity: number): Promise<void>;
                                                              // POST /cart/items
```

- [ ] **Step 1: 타입 정의** — 실제 응답을 백엔드 DTO에서 확인해 옮긴다. **추측 금지** — 필드가
  불확실하면 해당 컨트롤러/DTO(`review/dto/ReviewResponse.java` 등)를 읽고 맞춘다.
- [ ] **Step 2: 실패 테스트(대표 1건)** — MSW로 `/search` 응답을 물리고 `fetchSearch('토너')`가
  `PageResponse<GoodsListItem>`로 언랩되는지, `q`·`sort`·`page`가 쿼리스트링에 실리는지.
- [ ] **Step 3: 구현** — 기존 `api/goods.ts`의 언랩 패턴(`response.data.data`)을 그대로 따른다.
- [ ] **Step 4: 통과 확인** — `cd frontend && npm run test -- api/search` → PASS
- [ ] **Step 5: 커밋** — `git commit -am "feat(front): 탐색 API 모듈(search·ranking·review·qna·ingredient·cart)"`

## Task 2-2: Toast 프리미티브 + aria-live 라이브리전 (이월 해소)

로드맵 이월: "장바구니 담김 토스트 · 상태 변화 aria-live 라이브리전". 소비처는 2-3(담기)·2-4(검색).

**Files:**
- Create: `components/ui/Toast.tsx`, `components/ui/ToastProvider.tsx`, `components/ui/useToast.ts`
- Modify: `main.tsx` (앱 루트에 `<ToastProvider>` 감싸기 — 마운트 한 줄)
- Test: `components/ui/Toast.test.tsx`

**Interfaces (Produces):**
```ts
// useToast() → { toast(message: string, opts?: { tone?: 'success' | 'danger' }): void }
// ToastProvider 내부에 role="status" aria-live="polite" 라이브리전 컨테이너를 둔다.
// prefers-reduced-motion이면 페이드/슬라이드 없이 즉시 표시·제거.
```

- [ ] **Step 1: 실패 테스트** — `toast('장바구니에 담았어요')` 호출 시 `role="status"` 영역에 메시지가
  나타나고, `aria-live="polite"`가 걸려 있는지. 일정 시간 후 사라지는지(타이머는 fake timer로).
  reduced-motion에서 애니메이션 클래스가 빠지는지.
- [ ] **Step 2: 실패 확인** → FAIL
- [ ] **Step 3: 구현** — context + 큐. 라이브리전은 항상 DOM에 존재(빈 채로)해야 스크린리더가 갱신을
  읽는다. 색은 `DESIGN.md` 토큰(`--color-signal-*`)만. 시그널 색 배경 남용 금지(DESIGN.md UX 계약).
- [ ] **Step 4: 통과 확인** → PASS
- [ ] **Step 5: 커밋** — `git commit -am "feat(front): Toast + aria-live 라이브리전(이월 UX 해소)"`

## Task 2-3: 상세 페이지 `/goods/:goodsNo`

**Files:**
- Create: `pages/Detail.tsx`, `pages/Detail.css`
- Create: `components/goods/IngredientBadges.tsx`, `components/goods/DetailTabs.tsx`,
  `components/goods/ReviewList.tsx`, `components/goods/QnaList.tsx`
- Test: `pages/Detail.test.tsx`, `components/goods/IngredientBadges.test.tsx`

**Interfaces (Consumes):** `fetchGoodsDetail`(기존 `api/goods.ts`), `fetchIngredients`·`fetchReviews`·
`fetchReviewStats`·`fetchQna`·`addCartItem`(2-1), `useToast`(2-2), `EmptyState`(기존
`components/common/EmptyState.tsx`), `GoodsCard`·`Price`·`Rating`·`Badge`·`Button`(기존 UI 재사용).

**구성(설계 6장 상세 화면):** 기본정보(대표이미지·브랜드·상품명·가격·배지) + **성분 배지**
(자극도/comedogenic — `IngredientBadge`) + **탭(설명/리뷰/Q&A)**. 리뷰/Q&A는 **읽기만**. 상단에
**"장바구니 담기" 버튼 → `addCartItem` → 성공 토스트**(결정 2 — 이월 토스트의 소비처).

- [ ] **Step 1: 실패 테스트(성분 배지)** — 자극도 높은 성분에 위험 톤 배지, 목록이 비면 렌더하지 않음.
- [ ] **Step 2: 실패 테스트(담기→토스트)** — 담기 버튼 클릭 시 `addCartItem` 호출 + 성공 토스트 노출.
  실패(reject) 시 danger 토스트.
- [ ] **Step 3: 실패 테스트(탭 읽기)** — 리뷰 탭에 리뷰 목록, 리뷰 0건이면 `EmptyState`. Q&A 동일.
- [ ] **Step 4: 실패 확인** → FAIL
- [ ] **Step 5: 구현** — 각 탭은 활성화 시점에 lazy 조회(TanStack Query, 탭 전환 시 fetch). 로딩은
  `GoodsCardSkeleton`/`Skeleton` 재사용. 이미지 `alt`, 아이콘 `aria-label`(DESIGN.md UX 계약 — 이미 준수
  기준). 탭은 키보드 접근(role="tab"/"tabpanel", 방향키 이동).
- [ ] **Step 6: 통과 확인** — `npm run test -- Detail IngredientBadges` → PASS
- [ ] **Step 7: 커밋** — `git commit -am "feat(front): 상세 페이지 — 성분배지·담기토스트·설명/리뷰/Q&A 탭(읽기)"`

## Task 2-4: 검색 페이지 `/search` + 자동완성/무결과 (이월 해소)

**Files:**
- Create: `pages/Search.tsx`, `pages/Search.css`
- Create: `components/search/SearchBox.tsx`, `components/search/AutocompleteOverlay.tsx`
- Test: `pages/Search.test.tsx`, `components/search/AutocompleteOverlay.test.tsx`

**Interfaces (Consumes):** `fetchSearch`·`fetchAutocomplete`·`fetchPopularKeywords`(2-1),
`GoodsGrid`(기존), `EmptyState`(기존).

**구성(설계 6장 `/search?q=` + 자동완성 오버레이):** 검색 결과 그리드(`GoodsGrid` 재사용) +
**자동완성 오버레이(300ms 디바운스, prefix 10개)** + **무결과 시 `EmptyState`**(이월 항목 소비처) +
비어 있을 때 인기검색어. 자동완성 상태 변화는 2-2 aria-live로 안내(예: "제안 N건").

- [ ] **Step 1: 실패 테스트(자동완성 디바운스)** — 입력 후 300ms 지나야 `fetchAutocomplete` 1회 호출
  (fake timer). 연타 시 마지막 1회만. 방향키로 제안 이동, Enter로 선택 → 결과 조회.
- [ ] **Step 2: 실패 테스트(무결과)** — `fetchSearch`가 빈 `content`를 주면 `EmptyState`(`role="status"`)
  렌더, 그리드는 렌더하지 않음.
- [ ] **Step 3: 실패 확인** → FAIL
- [ ] **Step 4: 구현** — 디바운스는 커스텀 훅 또는 setTimeout+cleanup. 오버레이는 접근성
  (`role="listbox"`/`option`, `aria-activedescendant`). `?q=` 쿼리 동기화(`useSearchParams`).
  reduced-motion 존중.
- [ ] **Step 5: 통과 확인** — `npm run test -- Search Autocomplete` → PASS
- [ ] **Step 6: 커밋** — `git commit -am "feat(front): 검색 페이지 — 자동완성 디바운스·무결과 EmptyState(이월 해소)"`

## Task 2-5: 랭킹 페이지 `/ranking`

**Files:**
- Create: `pages/Ranking.tsx`, `pages/Ranking.css`
- Create: `components/ranking/CategoryTabs.tsx`
- Test: `pages/Ranking.test.tsx`

**Interfaces (Consumes):** `fetchRanking`(2-1), `GoodsGrid`(기존), `EmptyState`(기존).

**구성(설계 6장 `/ranking` 카테고리 탭 × 순위):** 카테고리 탭(전체 + 대분류) 선택에 따라
`fetchRanking(categoryCode)` 조회 후 순위 표기(카드에 순위 번호 오버레이 또는 좌측 순번). 탭은
`?category=` 쿼리 동기화.

- [ ] **Step 1: 실패 테스트** — 탭 전환 시 해당 `categoryCode`로 `fetchRanking` 호출 + 순위 번호가
  1부터 표기되는지. 빈 응답이면 `EmptyState`.
- [ ] **Step 2: 실패 확인** → FAIL
- [ ] **Step 3: 구현** — 탭 role="tab", 순위 번호는 스크린리더에 읽히되 장식 아이콘은 `aria-hidden`.
- [ ] **Step 4: 통과 확인** — `npm run test -- Ranking` → PASS
- [ ] **Step 5: 커밋** — `git commit -am "feat(front): 랭킹 페이지 — 카테고리 탭 × 순위"`

## Task 2-6: 라우팅 연결 + MSW 확장 + 반응형 검증 + T2 마감

**Files:**
- Modify: `router.tsx` (`/goods/:goodsNo`·`/search`·`/ranking` 추가 — 기존 `RequireAuth` 패턴 재사용)
- Modify: `mocks/handlers.ts` (신규 엔드포인트 mock: `/search`·`/autocomplete`·`/popular-keywords`·
  `/rankings`·`/reviews`·`/reviews/stats`·`/qna`·`/goods/:n/ingredients`·`POST /cart/items`)
- Test: 라우팅 스모크(각 라우트가 해당 페이지를 렌더)

**라우팅 정책:** 탐색 페이지는 로그인 후 셸 안에 둔다. 상세·검색·랭킹을 `RequireAuth`로 감쌀지
공개로 둘지는 **`/main`·`/goods`와 동일 기준**을 따른다(현재 둘 다 `RequireAuth` 아래). 일관성을 위해
같은 가드 아래 둔다 — 다르게 하려면 보고.

- [ ] **Step 1: 라우트 추가** — `router.tsx`의 기존 중첩 구조에 3개 라우트 추가. `RequireAuth`·`Layout`은
  수정하지 않고 **element로 감싸기만** 한다.
- [ ] **Step 2: MSW 핸들러 추가** — 신규 엔드포인트 mock. 기존 `fixtures/goods.ts` 재사용, 리뷰/Q&A/
  인기검색어용 소규모 픽스처 신설. **dev/test 오프라인에서 화면이 뜨는 것**이 목적(실동작은 dev 프록시).
- [ ] **Step 3: 라우팅 스모크 테스트** → PASS, 전체 `npm run test` 녹색.
- [ ] **Step 4: 반응형·스크린샷 검증** — dev 서버 기동 후 **상세·검색·랭킹** 각각 390/768/1024/1440/1920
  에서 스크린샷을 찍어 **직접 보고** 가로 스크롤 0을 확인. 파일 경로를 보고서에 남긴다(완료 조건).
  뷰포트 높이 낮을 때 콘텐츠 밀림도 확인(랜딩에서 겪은 함정).
- [ ] **Step 5: 커밋** — `git commit -am "feat(front): 라우팅 연결 + MSW 확장 + 반응형 검증 — Wave 3 T2 마감"`

---

## 웨이브 마감 (오케스트레이터, 두 브랜치 머지 후)

- [ ] T1(`feat/routine-compat`)·T2(`feat/front-pages`) 각각 리뷰: 테스트 통과 + Files 목록 준수
      (열지 않기로 한 파일을 안 건드렸는지) + 사양 일치.
- [ ] main에 순차 머지. 충돌 지점 없음이 설계 의도(파일 집합 분리) — 충돌이 나면 소유권 위반을 의심한다.
- [ ] 머지 후 `./gradlew test && ./gradlew integrationTest` 녹색, `cd frontend && npm run test` 녹색.
- [ ] **W3 DoD 확인:** `GET /routines`·`POST /compat/check`가 curl로 동작 + 프론트에서
      메인→목록→**상세→검색→랭킹** 탐색이 실API로 흐르는지(dev 프록시). 상세 담기→토스트,
      검색 자동완성/무결과가 보이는지.
- [ ] 로드맵 갱신: V50·V51 실사용 기록(§Flyway 대역표), 이월 UX 3건 해소 취소선 처리,
      Wave 4로 넘기는 것 명시(루틴 가이드 프론트 `/routine`·장바구니/주문 플로우·리뷰/문의 작성).

---

## 터미널 실행 프롬프트

### 0) 사전 조건 (루트에서 한 번만)

먼저 위 **"분기 전 사전 정리"**(기본배송지 500 방어)를 main에 직접 커밋한다. 그 커밋이 기점이다.

```bash
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" log --oneline -1   # 이 커밋이 두 worktree 공통 기점
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" status --short     # 비어 있어야 한다
```

### 1) 터미널 A (T1 — 루틴·궁합, 백엔드)

```
[1단계 — 작업 공간] 무엇보다 먼저 이것부터.
  git worktree add ../BeautyBoy-w3-routine -b feat/routine-compat
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 옮겨라.
(name으로 새로 만들지 마라 — origin에서 브랜치를 따 계획서 없는 worktree가 생긴다.)
진입 후 확인하고 하나라도 어긋나면 중단·보고:
  - pwd가 BeautyBoy-w3-routine 인지
  - git log --oneline -1 이 루트 기점과 같은지(분기 전 사전 정리 커밋 포함)
  - docs/plans/2026-07-23-wave3-routine-frontpages.md 가 있는지
  - backend/src/main/java/com/beautyboy/ingredient/GoodsIngredientQueryService.java 가 있는지(소비 대상)
  - git status가 깨끗한지
확인 뒤 ./gradlew test 가 녹색인지 먼저 본다.

[2단계 — 실행]
docs/plans/2026-07-23-wave3-routine-frontpages.md 의 "터미널 T1" Task 1-1~1-6을 순서대로 TDD로
실행하라. 너는 오케스트레이터다 — 태스크마다 서브에이전트를 스폰하고(Task 1-5 궁합 엔진만 model: opus,
나머지 model: sonnet), 태스크 사이마다 (1) 테스트 통과 (2) Files 목록 준수 (3) 사양 일치를 리뷰하라.

반드시 지킬 것:
- Flyway는 V50~V59 대역만. common/ErrorCode.java는 ROUTINE_/COMPAT_ 접두사 상수 추가만.
- SecurityConfig·docker-compose·루트 빌드·common 봉투는 열지 마라.
- 궁합 엔진은 ingredient의 GoodsIngredientQueryService·IngredientRuleRepository만 본다
  (성분 테이블 직접 접근 금지). 규칙 시드는 V12에 이미 있다 — 재시드하지 마라.
- 엔티티의 TINYINT 컬럼은 int + @JdbcTypeCode(SqlTypes.TINYINT)로 매핑(실 MySQL validate 통과).
- 마감 전 ./gradlew test 와 ./gradlew integrationTest 를 둘 다 녹색으로 만들고,
  /routines·/compat/check 를 curl로 재현해 결과를 보고서에 남겨라.
```

### 2) 터미널 B (T2 — 탐색 페이지, 프론트)

```
[1단계 — 작업 공간] 무엇보다 먼저 이것부터.
  git worktree add ../BeautyBoy-w3-front -b feat/front-pages
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 옮겨라.
진입 후 확인하고 하나라도 어긋나면 중단·보고:
  - pwd가 BeautyBoy-w3-front 인지
  - git log --oneline -1 이 루트 기점과 같은지
  - docs/plans/2026-07-23-wave3-routine-frontpages.md 와 루트 DESIGN.md 가 있는지
  - frontend/src/components/goods/GoodsGrid.tsx, frontend/src/api/goods.ts 가 있는지(재사용 대상)
  - frontend/src/pages/Main.tsx, frontend/src/pages/GoodsList.tsx 가 있는지(열지 말 것 — 이미 완료)
  - git status가 깨끗한지
확인 뒤 cd frontend && npm install && npm run test 가 녹색인지 먼저 본다.

[2단계 — 실행]
docs/plans/2026-07-23-wave3-routine-frontpages.md 의 "터미널 T2" Task 2-1~2-6을 순서대로 TDD로
실행하라. 너는 오케스트레이터다 — 태스크마다 서브에이전트(model: sonnet)를 스폰하고, 태스크 사이마다
(1) 테스트 통과 (2) Files 목록 준수 (3) 사양 일치를 리뷰하라.

반드시 지킬 것:
- pages/Main.tsx·GoodsList.tsx·RoutineSection·features/routine/steps.ts·RequireAuth·api/client.ts·
  package.json 은 열지 마라(이미 완료 또는 공유 계약).
- 상품 카드는 GoodsCard/GoodsGrid 재사용. UI 프리미티브(Field·Button·Price·Rating·Badge·Skeleton·
  EmptyState)도 재사용. 새로 만들지 마라.
- CSS는 DESIGN.md 토큰 이름을 직접 참조하고 hex를 손으로 적지 마라. 없는 값이 필요하면 보고하라.
- 상세는 읽기 전용, 단 "장바구니 담기" 한 버튼만 실동작(→ 성공 토스트). 리뷰/문의 작성은 넣지 마라.
- 검색 자동완성은 300ms 디바운스, 무결과는 EmptyState. 이월 UX(토스트·자동완성/무결과·aria-live)는
  반드시 소비처와 함께 이 웨이브에서 만든다.
- 화면 태스크는 dev 서버를 띄우고 390/768/1024/1440/1920에서 스크린샷을 찍어 직접 본 뒤
  가로 스크롤 0을 확인하고 파일 경로를 보고서에 남겨야 완료다.
```

---

## 마감 후 발견된 계획서 결함 (2026-07-24, 수동 확인 중)

**Task 2-3(상세 페이지)이 설계 6장의 "옵션"을 누락했다.** 위 Task 2-3 "구성" 줄이 설계 6장을
인용하면서 `옵션`만 빠졌고, 구현은 계획서를 정확히 따랐다 — **구현 결함이 아니라 계획 결함이다.**
결과적으로 `Detail.tsx`가 옵션 선택지를 보여주지 않고 `options[0]`을 말없이 담는다(수량도 `1` 고정).
함께 누락된 것: 설명 탭이 `/goods/{n}/description`(지연 로딩) 대신 짧은 `summary`를 쓰고,
`/goods/{n}/recommended` 추천 섹션이 없다. 3건 모두 **로드맵 "Wave 3에서 Wave 4로 넘기는 것"에
기록**했고 Wave 4 장바구니·주문 화면과 함께 닫는다.

**교훈:** 설계 문서의 화면 정의를 계획서로 옮길 때는 **항목을 하나씩 대조**해야 한다. 요약해서 옮기면
이번처럼 한 단어가 조용히 사라지고, 그 상태로 테스트까지 녹색이 된다(빠진 기능은 테스트도 없으므로).

## Self-Review 결과

- **스펙 커버리지:** W3 DoD "루틴 조회"(Task 1-4) · "궁합 진단"(Task 1-5) · "프론트 탐색 플로우 실API"
  (메인/목록=기존, 상세 2-3·검색 2-4·랭킹 2-5). 설계 7장 공개 엔드포인트 `/routines`·`/compat/check`
  둘 다 구현. 로드맵 이월 UX 3건(토스트·자동완성/무결과·aria-live) = 2-2·2-3·2-4로 소비처와 함께 해소.
  로드맵 §"Wave 3 착수 전" 기본배송지 500 버그 = 분기 전 사전 정리로 닫음. **미커버 없음.**
- **범위 밖(의도적, Wave 4로 명시):** 루틴 가이드 프론트 `/routine`, 장바구니 화면·주문·결제 플로우,
  리뷰/문의 **작성**, admin CRUD, E2E(Playwright) — 전부 로드맵상 Wave 4 통합 웨이브.
- **타입 일관성:** `findListItems`(1-3) → `RoutineStepResponse.recommendations`(1-4) 동일 `GoodsListItem`.
  `CompatFinding.goodsNos`(1-5) 명명 일관. 프론트 fetch는 전부 `PageResponse<T>`/`ApiEnvelope`(설계 7장 동결).
- **플레이스홀더:** 궁합 엔진 알고리즘·엔티티 TINYINT 매핑·디바운스·aria-live 등 비자명 지점은 코드/규약을
  명시. "적절히 처리" 류 없음.
- **병렬 안전:** T1(백엔드 파일 집합) ∩ T2(프론트 파일 집합) = ∅. 공유 계약(common·SecurityConfig·
  package.json) 동결. ErrorCode는 T1만, 자기 접두사 추가만 → 충돌 없음.
