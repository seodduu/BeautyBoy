# 흐름 추천(다음 단계 추천) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development(권장) 또는 superpowers:executing-plans로 태스크 단위 실행. 스텝은 체크박스(`- [ ]`).

**Goal:** PDP 하단에 "다음 단계" 슬롯을 만든다 — 루틴에서 다음에 올 단계의 상품을 전이 규칙으로 골라, 이유 문장과 함께 보여주고, 최종 후보는 성분 궁합 게이트(CONFLICT 제거)를 통과시킨다.

**Architecture:** `routine` 도메인이 `routine_flow_rule`(V74) 소유. `GET /api/v1/goods/{goodsNo}/next-step` 서버 단일 엔드포인트가 규칙 매칭 → 후보 조회(폴백 사다리) → 궁합 게이트 순으로 계산한다. 태그 축은 product-tags 계획(V70/V71) 산출물에 의존하고, 타 도메인 접근은 인터페이스 신설/확장으로만 한다(catalog: `GoodsQueryService` 확장, compat: `CompatQueryService` 신설).

**Tech Stack:** Spring Boot(JPQL), MySQL 8.4 + Flyway, React SPA(Vite+TS) + TanStack Query + MSW, Vitest.

**설계 문서:** `docs/superpowers/specs/2026-07-26-next-step-recommendation-design.md` — 결정 근거·알고리즘·블록 선정 규칙의 진실.

## Global Constraints

- **선행 의존: product-tags 계획(`docs/plans/2026-07-25-product-tags.md`, V70/V71)이 먼저 머지돼 있어야 한다.** 착수 전 `backend/src/main/resources/db/migration/V71__seed_product_tag.sql` 존재와 `catalog/dto/TagView.java` 존재를 확인하고, 없으면 중단·보고. 또한 `backend/src/main/resources/db/migration/V72__expand_product_tag.sql`(태그 확장) 존재도 함께 확인한다 — 존재하면 위 "시드 규칙 12행" 절의 주의사항대로 V72 반영 후 실데이터로 재산정한다.
- `reason` 문구의 유일한 출처는 `routine_flow_rule.reason` 컬럼 — **코드·프론트에 추천 이유 문구를 하드코딩하지 않는다.**
- 패키지 = 서비스 경계. routine은 자기 테이블만 접근, catalog/compat은 이 계획이 정의한 인터페이스로만.
- `GoodsListItem`은 동결 계약 — 수정하지 않는다(블록 DTO가 감싼다).
- 스키마 검증 DoD는 실 MySQL clean 로드 + `ddl-auto=validate`. 메모리 [[h2-createdrop-hides-validate]].
- 프론트는 CSS 작성 전 `DESIGN.md`를 먼저 읽는다. 토큰만 참조, hex 손 복사 금지, 한글 적용 절(자간·행간·`word-break: keep-all`) 준수. 화면 태스크(Task 7)는 **스크린샷 확인이 완료 조건**.
- 커밋 메시지 한국어, 태스크 단위 원자 커밋. 백엔드 `backend/`, 프론트 `frontend/`.
- **모델 배분:** Task 4(규칙 매칭·폴백·궁합 게이트 — 규칙 충돌 해석 영역)는 **opus**, 나머지 태스크는 sonnet. 오케스트레이터는 opus.

## 터미널 운용 (순차 2단계 — 병렬 불가)

②가 ①의 V70/V71·`TagView`·`GoodsTagRepository` 계약에 의존하므로 반드시 순차.

**사람 사전 조건(각 터미널 열기 전, 프로젝트 루트에서):** `git log --oneline -1`이 이 계획서가 커밋된 main 기점인지, `git status`가 깨끗한지.

### 터미널 ① — product-tags (기존 계획서 실행)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add ../뷰티보이-태그 -b feature/product-tags
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 해당 worktree인지
  - git log --oneline -1 이 루트에서 본 기점 커밋과 같은지
  - docs/plans/2026-07-25-product-tags.md 와 DESIGN.md 가 실제로 존재하는지
  - git status가 깨끗한지
[2단계 — 실행] docs/plans/2026-07-25-product-tags.md 를 superpowers:subagent-driven-development로
태스크 단위 실행해라. 태스크 실행 서브에이전트 모델은 sonnet. 전체 태스크 완료·전체 테스트 녹색이면
superpowers:finishing-a-development-branch 로 main 머지까지 마친다.
```

### 터미널 ② — 흐름 추천 (이 계획서 실행, ① 머지 후)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add ../뷰티보이-흐름추천 -b feature/next-step-recommendation
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 해당 worktree인지
  - git log --oneline -1 이 루트에서 본 기점 커밋과 같은지 (product-tags 머지 커밋 이후여야 한다)
  - docs/plans/2026-07-26-next-step-recommendation.md 와 DESIGN.md 가 실제로 존재하는지
  - backend/src/main/resources/db/migration/V71__seed_product_tag.sql 이 존재하는지 (없으면 선행 미완 — 중단)
  - git status가 깨끗한지
[2단계 — 실행] docs/plans/2026-07-26-next-step-recommendation.md 를
superpowers:subagent-driven-development로 태스크 단위 실행해라. 태스크 실행 서브에이전트 모델은
sonnet, 단 Task 4는 opus. 전체 완료·전체 테스트 녹색·Task 7 스크린샷 확인이 끝나면
superpowers:finishing-a-development-branch 로 main 머지까지 마친다.
```

---

## File Structure

**백엔드 신규**
- `V74__routine_flow_rule.sql` — 전이 규칙 DDL.
- `V75__seed_routine_flow_rule.sql` — 규칙 12행 시드.
- `routine/RoutineFlowRule.java`, `routine/RoutineFlowRuleRepository.java`
- `routine/NextStepService.java`, `routine/NextStepController.java`
- `routine/dto/NextStepResponse.java`, `routine/dto/NextStepBlock.java`
- `compat/CompatQueryService.java` — 배치 pairwise 판정 인터페이스.

**백엔드 수정**
- `compat/CompatService.java` — `CompatQueryService` 구현 추가.
- `catalog/GoodsQueryService.java` — `tagSlugs`·`findCandidateGoodsNos` 추가.
- `catalog/GoodsService.java`(구현체) — 위 두 메서드 구현.
- `catalog/GoodsRepository.java` — 후보 조회 쿼리 2개 추가.

**프론트 신규**
- `components/goods/NextStepSection.tsx` + `.css`
**프론트 수정**
- `types/goods.ts` — `NextStepBlock` 타입.
- `api/goods.ts` — `fetchNextStep`.
- `pages/Detail.tsx` — `RecommendedSection` 위에 배선.
- `mocks/handlers.ts`, `mocks/fixtures/goods.ts` — next-step 핸들러·픽스처.

**건드리지 않음:** `GoodsListItem`(동결), `CompatController`/`POST /compat/check`(기존 그대로), `SecurityConfig`(GET `/goods/**` permitAll에 자동 포함), Flyway 기존 파일.

---

## 공유 계약

### DDL (V74 — 완전)

```sql
-- V74__routine_flow_rule.sql — 루틴 전이 규칙. reason이 화면 문구의 유일한 출처.
-- tag slug·category code에 물리 FK를 걸지 않는다(패키지 경계 너머 물리 FK 금지 관례).
CREATE TABLE routine_flow_rule (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  from_category_code VARCHAR(12)  NOT NULL,  -- 중분류(C001001). goods.category_code(leaf)는 접두사 매칭
  from_tag_slug      VARCHAR(40)  NULL,      -- NULL = 태그 무관
  to_category_code   VARCHAR(12)  NOT NULL,  -- 중분류
  to_tag_slug        VARCHAR(40)  NULL,      -- 추천 대상이 가져야 할 태그. NULL 허용
  edge_kind          VARCHAR(20)  NOT NULL,  -- NEXT_STEP | PAIRED_REMOVAL | BUFFER
  reason             VARCHAR(200) NOT NULL,  -- 화면에 그대로 나가는 이유 문장
  priority           INT          NOT NULL DEFAULT 0  -- 낮을수록 우선. BUFFER는 NEXT_STEP보다 낮게 시드
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### API DTO (백 record ↔ 프론트 type 동일 필드명)

```java
// routine/dto/NextStepBlock.java
public record NextStepBlock(String edgeKind, String reason, List<GoodsListItem> items) {}
// routine/dto/NextStepResponse.java
public record NextStepResponse(List<NextStepBlock> blocks) {}   // blocks 최대 2개
```
```ts
// types/goods.ts
export interface NextStepBlock {
  edgeKind: 'NEXT_STEP' | 'PAIRED_REMOVAL' | 'BUFFER';
  reason: string;
  items: GoodsListItem[];
}
```

### 인터페이스 확장·신설 (완전 시그니처)

```java
// catalog/GoodsQueryService.java 에 추가
/** 상품의 태그 슬러그 집합(EFFECT·TEXTURE 모두). 상품이 없거나 태그가 없으면 빈 집합. */
Set<String> tagSlugs(Long goodsNo);

/**
 * 다음 단계 후보 goods_no. categoryPrefix로 leaf 카테고리 접두사 매칭, tagSlug가 null이면
 * 태그 무관. HIDDEN 제외·excludeGoodsNo 제외, view_count desc → id desc, limit 건.
 */
List<Long> findCandidateGoodsNos(String categoryPrefix, String tagSlug, Long excludeGoodsNo, int limit);
```

```java
// compat/CompatQueryService.java (신설 인터페이스 — CompatService가 구현)
public interface CompatQueryService {
    /**
     * 기준 상품과 각 후보의 pairwise 최악 verdict. 후보별로 (기준 성분분류 × 후보 성분분류)
     * 쌍에 걸리는 규칙 중 심각도 최상위를 돌려준다. 걸리는 규칙이 없으면 "OK".
     * 반환 맵은 candidates 전원을 키로 포함한다.
     */
    Map<Long, String> worstVerdicts(Long baseGoodsNo, Collection<Long> candidateGoodsNos);
}
```

> **주의(2026-07-27):** 아래 시드 근거는 V71 기준으로 산정된 것이다. V72(태그 확장 — is_key 조건 제거·신규 5종)가 머지되어 상품별 태그가 크게 늘었으므로, 착수 시 V72 반영 후 실데이터로 근거·기대값을 재산정하라.

### 시드 규칙 12행 (V75 — 완전. 시드 상품·V71 태그와 대조 완료)

시드 근거: 태그는 V71 파생 규칙 기준 — 클렌징폼 goods 9·10(BHA is_key)=`exfoliate`, 토너 goods 2(AHA)=`exfoliate`, 진정토너 goods 3(CENTELLA)=`soothe`, 크림 goods 6(HYALURONIC)=`moisture`, 애프터선 goods 26·27(CENTELLA)=`soothe`, 클렌징오일/밤 goods 11·12=`cleanse`(C002 일괄), 선케어 goods 21~25=`uv`.

```sql
-- V75__seed_routine_flow_rule.sql
-- priority: BUFFER=10 < NEXT_STEP=20 — 같은 상품에 둘 다 매칭되면 완충이 이긴다(설계 §4).
INSERT INTO routine_flow_rule
  (from_category_code, from_tag_slug, to_category_code, to_tag_slug, edge_kind, reason, priority) VALUES
('C002001', 'exfoliate', 'C001001', 'soothe',   'BUFFER',
 '피지·각질까지 씻어낸 다음엔 진정 토너로 완충해 주세요', 10),
('C002001', NULL,        'C001001', 'moisture', 'NEXT_STEP',
 '세안 다음 단계는 수분 충전이에요', 20),
('C002002', NULL,        'C001001', 'moisture', 'NEXT_STEP',
 '지운 다음엔 수분 토너로 결부터 정돈하세요', 20),
('C002003', 'exfoliate', 'C001001', 'soothe',   'BUFFER',
 '각질 케어 다음엔 진정 성분으로 완충하는 게 좋아요', 10),
('C001001', 'exfoliate', 'C001002', 'soothe',   'BUFFER',
 '각질 토너 다음 단계는 진정 세럼으로 완충하세요', 10),
('C001001', NULL,        'C001002', NULL,       'NEXT_STEP',
 '결을 정돈했다면 영양을 채울 차례예요', 20),
('C001002', NULL,        'C001003', 'moisture', 'NEXT_STEP',
 '세럼의 수분을 크림으로 덮어 가두세요', 20),
('C001003', NULL,        'C004001', 'uv',       'NEXT_STEP',
 '아침 루틴의 마지막은 자외선 차단이에요', 20),
('C004001', NULL,        'C004003', 'soothe',   'NEXT_STEP',
 '햇빛을 본 날엔 진정 케어로 마무리하세요', 20),
('C004001', NULL,        'C002002', 'cleanse',  'PAIRED_REMOVAL',
 '자외선차단제는 클렌징오일로 지워야 남지 않아요', 10),
('C004002', NULL,        'C002002', 'cleanse',  'PAIRED_REMOVAL',
 '선스틱도 저녁엔 오일 클렌징으로 지워 주세요', 10),
('C005002', NULL,        'C005003', NULL,       'NEXT_STEP',
 '면도 다음엔 진정 제품으로 마무리하세요', 20);
```

시드가 만드는 대표 데모(검증 기준):
- **goods 2(딥모이스처 토너, AHA)** → BUFFER 규칙 5행 매칭 → 세럼 중 `soothe` 태그 0개 → 폴백으로 인기 세럼 [4, 5] → **궁합 게이트가 goods 5(RETINOID 함유, AHA×RETINOID=CONFLICT) 제거** → [4]만 노출. 폴백 사다리 + 게이트가 한 응답에서 다 보인다.
- **goods 21(무기자차 선크림)** → 순방향(애프터선 soothe: 26, 27) + PAIRED_REMOVAL(오일: 11, 12) **2블록**.
- 헤어(15~20)·면도기(28, 29)·메이크업(34~40)은 규칙 없음 → 빈 blocks → 섹션 미노출.

---

## Task 1: V74 스키마 + 엔티티 + 리포지토리

**Files:**
- Create: `backend/src/main/resources/db/migration/V74__routine_flow_rule.sql`
- Create: `backend/src/main/java/com/beautyboy/routine/RoutineFlowRule.java`
- Create: `backend/src/main/java/com/beautyboy/routine/RoutineFlowRuleRepository.java`
- Test: `backend/src/test/java/com/beautyboy/routine/RoutineFlowRuleRepositoryTest.java`

**Interfaces:**
- Produces: `RoutineFlowRuleRepository.findAllByOrderByPriorityAscIdAsc() -> List<RoutineFlowRule>` (Task 4가 소비), 엔티티 getter: `getFromCategoryCode() getFromTagSlug() getToCategoryCode() getToTagSlug() getEdgeKind() getReason() getPriority()`.

- [ ] **Step 1: V74 DDL 작성** — 위 "공유 계약"의 DDL을 그대로 파일로.
- [ ] **Step 2: 실패 테스트 작성**

```java
// RoutineFlowRuleRepositoryTest — @SpringBootTest @ActiveProfiles("test") @Transactional, 픽스처 자가주입
@Test void 규칙을_priority_id순으로_전량_조회한다() {
    규칙_저장("C001001", "exfoliate", "C001002", "soothe", "BUFFER", "완충", 10);
    규칙_저장("C001001", null, "C001002", null, "NEXT_STEP", "다음", 20);
    List<RoutineFlowRule> rules = repository.findAllByOrderByPriorityAscIdAsc();
    assertThat(rules).extracting(RoutineFlowRule::getEdgeKind).containsExactly("BUFFER", "NEXT_STEP");
    assertThat(rules.get(0).getFromTagSlug()).isEqualTo("exfoliate");
    assertThat(rules.get(1).getFromTagSlug()).isNull();
}
```

- [ ] **Step 3: 실패 확인** — `./gradlew test --tests RoutineFlowRuleRepositoryTest` → FAIL(클래스 없음).
- [ ] **Step 4: 엔티티·리포지토리 구현** — `RoutineFlowRule`: `@Entity @Table(name = "routine_flow_rule")`, 필드 7개 스칼라 매핑(연관관계 없음), 기존 `RoutineStep` 패턴(보호 기본 생성자 + 전체 필드 생성자 + getter). `RoutineFlowRuleRepository extends JpaRepository<RoutineFlowRule, Long>`에 메서드명 파생 쿼리 `findAllByOrderByPriorityAscIdAsc()`.
- [ ] **Step 5: 통과 확인** — PASS.
- [ ] **Step 6: 커밋** — `feat(routine): 전이 규칙 스키마·엔티티(V74)`

---

## Task 2: compat 배치 pairwise 인터페이스

**Files:**
- Create: `backend/src/main/java/com/beautyboy/compat/CompatQueryService.java`
- Modify: `backend/src/main/java/com/beautyboy/compat/CompatService.java`
- Test: `backend/src/test/java/com/beautyboy/compat/CompatQueryServiceTest.java`

**Interfaces:**
- Consumes: `ingredient/GoodsIngredientQueryService.findCategoriesByGoodsIds(Collection<Long>) -> Map<Long, Set<String>>`, `ingredient/IngredientRuleRepository.findAll()` (compat는 이미 ingredient 인터페이스 소비자 — 기존 구조 그대로).
- Produces: `CompatQueryService.worstVerdicts(Long, Collection<Long>) -> Map<Long, String>` (Task 4가 소비).

- [ ] **Step 1: 인터페이스 작성** — 위 "공유 계약"의 `CompatQueryService` 그대로.
- [ ] **Step 2: 실패 테스트 작성**

```java
// CompatQueryServiceTest — @SpringBootTest @ActiveProfiles("test") @Transactional.
// 픽스처: 성분·규칙은 V12 시드에 의존하지 않고 자가 주입한다(터미널 병렬 안전 원칙 유지).
// base: AHA is_key / candA: RETINOID / candB: NIACINAMIDE / candC: VITAMIN_C / candD: 성분 없음
// 자가 주입 규칙: (AHA,RETINOID)=CONFLICT, (AHA,VITAMIN_C)=CAUTION  ※ (AHA,NIACINAMIDE) 규칙 없음
@Test void 기준상품과_후보들의_최악_판정을_배치로_돌려준다() {
    Map<Long, String> verdicts = compatQueryService.worstVerdicts(base, List.of(candA, candB, candC));
    assertThat(verdicts.get(candA)).isEqualTo("CONFLICT");
    assertThat(verdicts.get(candB)).isEqualTo("OK");       // 걸리는 규칙 없음
    assertThat(verdicts.get(candC)).isEqualTo("CAUTION");
}
@Test void 성분이_없는_후보는_OK() {
    assertThat(compatQueryService.worstVerdicts(base, List.of(candD)).get(candD)).isEqualTo("OK");
}
@Test void 빈_후보면_빈_맵() {
    assertThat(compatQueryService.worstVerdicts(base, List.of())).isEmpty();
}
```

- [ ] **Step 3: 실패 확인** — FAIL.
- [ ] **Step 4: 구현** — `CompatService implements CompatQueryService`. 판단이 갈리는 로직이므로 전량:

```java
@Override
public Map<Long, String> worstVerdicts(Long baseGoodsNo, Collection<Long> candidateGoodsNos) {
    if (candidateGoodsNos.isEmpty()) {
        return Map.of();
    }
    List<Long> all = new ArrayList<>(candidateGoodsNos);
    all.add(baseGoodsNo);
    Map<Long, Set<String>> cats = goodsIngredientQueryService.findCategoriesByGoodsIds(all);
    Set<String> baseCats = cats.getOrDefault(baseGoodsNo, Set.of());

    // 규칙 전량(시드 18행) 1회 로드 후 정규화 키("A|B", A<B 사전순)로 인덱싱 —
    // 후보 × 분류쌍마다 findNormalized를 부르면 쿼리가 후보 수 × 쌍 수만큼 나간다.
    Map<String, String> verdictByPair = new HashMap<>();
    for (IngredientRule rule : ruleRepository.findAll()) {
        verdictByPair.put(rule.getCategoryA() + "|" + rule.getCategoryB(), rule.getVerdict());
    }

    Map<Long, String> result = new HashMap<>();
    for (Long candidate : candidateGoodsNos) {
        Set<String> candCats = cats.getOrDefault(candidate, Set.of());
        String worst = "OK";
        for (String ca : baseCats) {
            for (String cb : candCats) {
                if (ca.equals(cb)) {
                    continue;    // 같은 분류끼리는 규칙 대상이 아니다(check()의 i<j와 동일 의미)
                }
                String key = ca.compareTo(cb) < 0 ? ca + "|" + cb : cb + "|" + ca;
                String verdict = verdictByPair.get(key);
                if (verdict != null && severity(verdict) > severity(worst)) {
                    worst = verdict;
                }
            }
        }
        result.put(candidate, worst);
    }
    return result;
}
```

(기존 `severity()`는 `default -> 0`이라 `"OK"` 입력에 그대로 쓸 수 있다 — 새 메서드 불필요.)

- [ ] **Step 5: 통과 + 회귀** — `./gradlew test --tests 'com.beautyboy.compat.*'` PASS.
- [ ] **Step 6: 커밋** — `feat(compat): 기준상품×후보 배치 pairwise 판정 인터페이스`

---

## Task 3: catalog 후보 조회·태그 슬러그 인터페이스 확장

**Files:**
- Modify: `backend/src/main/java/com/beautyboy/catalog/GoodsQueryService.java`
- Modify: `backend/src/main/java/com/beautyboy/catalog/GoodsService.java`
- Modify: `backend/src/main/java/com/beautyboy/catalog/GoodsRepository.java`
- Test: `backend/src/test/java/com/beautyboy/catalog/GoodsCandidateQueryTest.java`

**Interfaces:**
- Consumes: `catalog/GoodsTagRepository.findTagsByGoodsIds(Collection<Long>) -> Map<Long, List<TagView>>` (product-tags Task 1 산출물).
- Produces: `GoodsQueryService.tagSlugs(Long) -> Set<String>`, `GoodsQueryService.findCandidateGoodsNos(String, String, Long, int) -> List<Long>` (Task 4가 소비).

- [ ] **Step 1: 인터페이스 메서드 추가** — 위 "공유 계약"의 두 시그니처와 Javadoc을 `GoodsQueryService`에 추가.
- [ ] **Step 2: 실패 테스트 작성**

```java
// GoodsCandidateQueryTest — @SpringBootTest @ActiveProfiles("test") @Transactional, 픽스처 자가주입.
// 픽스처: C001002001 세럼 3개(viewCount 300/200/100 순), 그중 상위 1개에만 'soothe' 태그.
//        C001002002 세럼 1개 HIDDEN. 태그 마스터·goods_tag 자가 주입.
@Test void 접두사와_태그로_후보를_인기순으로_뽑는다() {
    List<Long> got = goodsQueryService.findCandidateGoodsNos("C001002", "soothe", 다른상품, 4);
    assertThat(got).containsExactly(soothe세럼);   // 태그 없는 세럼은 빠진다
}
@Test void 태그가_null이면_태그_무관_인기순() {
    List<Long> got = goodsQueryService.findCandidateGoodsNos("C001002", null, 다른상품, 4);
    assertThat(got).containsExactly(뷰300, 뷰200, 뷰100);  // viewCount desc, HIDDEN 제외
}
@Test void 자기_자신과_HIDDEN은_제외된다() {
    List<Long> got = goodsQueryService.findCandidateGoodsNos("C001002", null, 뷰300, 4);
    assertThat(got).doesNotContain(뷰300, hidden세럼);
}
@Test void tagSlugs는_태그_슬러그_집합_없으면_빈집합() {
    assertThat(goodsQueryService.tagSlugs(soothe세럼)).contains("soothe");
    assertThat(goodsQueryService.tagSlugs(태그없는세럼)).isEmpty();
}
```

- [ ] **Step 3: 실패 확인** — FAIL.
- [ ] **Step 4: 구현** — `GoodsRepository`에 쿼리 2개(성능 의도가 있는 조회 — 전량 기재):

```java
@Query("select g.id from Goods g "
        + "where g.categoryCode like concat(:prefix, '%') and g.status <> :hidden and g.id <> :excludeId "
        + "order by g.viewCount desc, g.id desc")
List<Long> findCandidateIds(@Param("prefix") String prefix, @Param("hidden") String hidden,
                            @Param("excludeId") Long excludeId, Pageable pageable);

@Query("select g.id from Goods g "
        + "where g.categoryCode like concat(:prefix, '%') and g.status <> :hidden and g.id <> :excludeId "
        + "and exists (select 1 from GoodsTag gt, Tag t where gt.tagId = t.id and gt.goodsId = g.id and t.slug = :tagSlug) "
        + "order by g.viewCount desc, g.id desc")
List<Long> findCandidateIdsByTag(@Param("prefix") String prefix, @Param("tagSlug") String tagSlug,
                                 @Param("hidden") String hidden, @Param("excludeId") Long excludeId,
                                 Pageable pageable);
```

`GoodsService`(GoodsQueryService 구현체): `findCandidateGoodsNos`는 tagSlug null 여부로 위 둘 중 하나를 `PageRequest.of(0, limit)`로 호출. `tagSlugs`는 `goodsTagRepository.findTagsByGoodsIds(List.of(goodsNo))` 결과에서 slug만 `Set`으로 수집(없으면 빈 집합).

- [ ] **Step 5: 통과 + 회귀** — `./gradlew test --tests 'com.beautyboy.catalog.*'` PASS.
- [ ] **Step 6: 커밋** — `feat(catalog): 다음 단계 후보·태그 슬러그 조회 인터페이스 확장`

---

## Task 4: NextStepService + 컨트롤러 (opus — 규칙 매칭·폴백·궁합 게이트)

**Files:**
- Create: `backend/src/main/java/com/beautyboy/routine/NextStepService.java`
- Create: `backend/src/main/java/com/beautyboy/routine/NextStepController.java`
- Create: `backend/src/main/java/com/beautyboy/routine/dto/NextStepResponse.java`
- Create: `backend/src/main/java/com/beautyboy/routine/dto/NextStepBlock.java`
- Test: `backend/src/test/java/com/beautyboy/routine/NextStepServiceTest.java`, `backend/src/test/java/com/beautyboy/routine/NextStepApiTest.java`

**Interfaces:**
- Consumes: Task 1 `findAllByOrderByPriorityAscIdAsc()`, Task 2 `CompatQueryService.worstVerdicts`, Task 3 `tagSlugs`·`findCandidateGoodsNos`, 기존 `GoodsQueryService.exists`·`categoryCode`·`findListItems(Collection<Long>, Long)`.
- Produces: `GET /api/v1/goods/{goodsNo}/next-step -> ApiResponse<NextStepResponse>` (Task 6 프론트가 소비).

- [ ] **Step 1: DTO 2개 작성** — 위 "공유 계약" record 그대로.
- [ ] **Step 2: 실패 테스트 작성 (서비스)** — 픽스처 자가주입(상품·태그·성분·규칙·전이규칙 모두. V75 시드 의존 금지 — 시드 검증은 Task 5).

```java
// NextStepServiceTest — @SpringBootTest @ActiveProfiles("test") @Transactional
@Test void 각질_토너는_BUFFER가_NEXT_STEP보다_우선한다() {
    // 전이규칙 2행: (C001001, exfoliate → C001002, soothe, BUFFER, p10) / (C001001, null → C001002, null, NEXT_STEP, p20)
    // exfoliate 태그 토너로 조회하면 순방향 블록은 BUFFER 하나만 나온다.
    NextStepResponse res = nextStepService.find(각질토너, null);
    assertThat(res.blocks()).hasSize(1);
    assertThat(res.blocks().get(0).edgeKind()).isEqualTo("BUFFER");
    assertThat(res.blocks().get(0).reason()).isEqualTo("각질 케어 다음엔 진정으로 완충");  // reason은 규칙 행 그대로
}
@Test void 태그_일치가_부족하면_같은_카테고리_인기순으로_채운다() {
    // to_tag=soothe인데 soothe 세럼 1개뿐, 세럼 3개 존재 → items가 [soothe세럼, 인기1, 인기2] 순
    List<GoodsListItem> items = nextStepService.find(각질토너, null).blocks().get(0).items();
    assertThat(items).extracting(GoodsListItem::goodsNo).containsExactly(soothe세럼, 인기세럼1, 인기세럼2);
}
@Test void CONFLICT_후보는_게이트에서_제거된다() {
    // 각질토너(AHA) × 레티노이드세럼: (AHA,RETINOID)=CONFLICT 규칙 자가주입 → 후보에서 빠진다
    List<GoodsListItem> items = nextStepService.find(각질토너, null).blocks().get(0).items();
    assertThat(items).extracting(GoodsListItem::goodsNo).doesNotContain(레티노이드세럼);
}
@Test void 후보가_모두_제거되면_블록을_내지_않는다() {
    // 세럼이 레티노이드세럼 하나뿐인 픽스처 → blocks 빈 배열
    assertThat(nextStepService.find(각질토너만있는픽스처, null).blocks()).isEmpty();
}
@Test void 선크림은_순방향과_PAIRED_REMOVAL_두_블록() {
    NextStepResponse res = nextStepService.find(선크림, null);
    assertThat(res.blocks()).extracting(NextStepBlock::edgeKind)
        .containsExactly("NEXT_STEP", "PAIRED_REMOVAL");   // 순방향 먼저
}
@Test void 규칙이_없는_상품은_빈_blocks() {
    assertThat(nextStepService.find(헤어왁스, null).blocks()).isEmpty();
}
@Test void 없는_상품은_GOODS_NOT_FOUND() {
    assertThatThrownBy(() -> nextStepService.find(999999L, null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.GOODS_NOT_FOUND);
}
```

- [ ] **Step 3: 실패 테스트 작성 (API)**

```java
// NextStepApiTest — MockMvc. 비로그인 GET이 200인지(permitAll 확인 포함).
@Test void next_step_응답_형태() throws Exception {
    mockMvc.perform(get("/api/v1/goods/" + 각질토너 + "/next-step"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.data.blocks[0].edgeKind").value("BUFFER"))
        .andExpect(jsonPath("$.data.blocks[0].reason").isNotEmpty())
        .andExpect(jsonPath("$.data.blocks[0].items[0].goodsNo").exists());
}
```

- [ ] **Step 4: 실패 확인** — FAIL.
- [ ] **Step 5: 서비스 구현** — 판단이 갈리는 핵심이므로 전량:

```java
@Service
public class NextStepService {

    /** 블록당 카드 수. 기존 RecommendedSection 스켈레톤 4와 맞춘다(설계 §4). */
    static final int BLOCK_ITEM_LIMIT = 4;
    private static final Set<String> FORWARD_KINDS = Set.of("NEXT_STEP", "BUFFER");
    private static final String PAIRED_REMOVAL = "PAIRED_REMOVAL";

    private final RoutineFlowRuleRepository ruleRepository;
    private final GoodsQueryService goodsQueryService;
    private final CompatQueryService compatQueryService;

    public NextStepService(RoutineFlowRuleRepository ruleRepository,
                           GoodsQueryService goodsQueryService,
                           CompatQueryService compatQueryService) {
        this.ruleRepository = ruleRepository;
        this.goodsQueryService = goodsQueryService;
        this.compatQueryService = compatQueryService;
    }

    @Transactional(readOnly = true)
    public NextStepResponse find(Long goodsNo, Long viewerId) {
        if (!goodsQueryService.exists(goodsNo)) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        String leafCategory = goodsQueryService.categoryCode(goodsNo);
        Set<String> slugs = goodsQueryService.tagSlugs(goodsNo);

        // 규칙 전량(시드 12행) 로드 후 자바에서 매칭 — priority asc, id asc가 이미 보장된 순서라
        // "처음 만나는 것"이 곧 최우선 규칙이다.
        RoutineFlowRule forward = null;
        RoutineFlowRule removal = null;
        for (RoutineFlowRule rule : ruleRepository.findAllByOrderByPriorityAscIdAsc()) {
            if (!leafCategory.startsWith(rule.getFromCategoryCode())) {
                continue;
            }
            if (rule.getFromTagSlug() != null && !slugs.contains(rule.getFromTagSlug())) {
                continue;
            }
            if (forward == null && FORWARD_KINDS.contains(rule.getEdgeKind())) {
                forward = rule;      // 순방향(NEXT_STEP·BUFFER) 통틀어 1개 — 설계 §4 블록 선정 규칙
            } else if (removal == null && PAIRED_REMOVAL.equals(rule.getEdgeKind())) {
                removal = rule;
            }
            if (forward != null && removal != null) {
                break;
            }
        }

        List<NextStepBlock> blocks = new ArrayList<>();
        for (RoutineFlowRule rule : Arrays.asList(forward, removal)) {   // 순방향 먼저
            if (rule != null) {
                buildBlock(rule, goodsNo, viewerId).ifPresent(blocks::add);
            }
        }
        return new NextStepResponse(blocks);
    }

    private Optional<NextStepBlock> buildBlock(RoutineFlowRule rule, Long baseGoodsNo, Long viewerId) {
        // 1) 목표 태그 일치 후보(인기순)
        List<Long> candidates = new ArrayList<>(goodsQueryService.findCandidateGoodsNos(
                rule.getToCategoryCode(), rule.getToTagSlug(), baseGoodsNo, BLOCK_ITEM_LIMIT));
        // 2) 폴백: 부족하면 태그 조건을 떼고 같은 카테고리 인기순으로 채운다(설계 §5-3).
        //    limit을 넉넉히 뽑는 이유: 앞에서 이미 뽑힌 후보와 겹치는 만큼 걸러야 하기 때문.
        if (candidates.size() < BLOCK_ITEM_LIMIT && rule.getToTagSlug() != null) {
            for (Long no : goodsQueryService.findCandidateGoodsNos(
                    rule.getToCategoryCode(), null, baseGoodsNo, BLOCK_ITEM_LIMIT + candidates.size())) {
                if (candidates.size() >= BLOCK_ITEM_LIMIT) {
                    break;
                }
                if (!candidates.contains(no)) {
                    candidates.add(no);
                }
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        // 3) 궁합 게이트: CONFLICT 후보 제거. 제거 후 재폴백은 하지 않는다(설계 §5-4).
        Map<Long, String> verdicts = compatQueryService.worstVerdicts(baseGoodsNo, candidates);
        List<Long> safe = candidates.stream()
                .filter(no -> !"CONFLICT".equals(verdicts.get(no)))
                .toList();
        if (safe.isEmpty()) {
            return Optional.empty();
        }
        // findListItems는 입력 순서를 보존하지 않는다 — 후보 순서(태그 일치 → 인기순)로 재정렬.
        Map<Long, GoodsListItem> byNo = goodsQueryService.findListItems(safe, viewerId).stream()
                .collect(Collectors.toMap(GoodsListItem::goodsNo, item -> item));
        List<GoodsListItem> items = safe.stream().map(byNo::get).filter(Objects::nonNull).toList();
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new NextStepBlock(rule.getEdgeKind(), rule.getReason(), items));
    }
}
```

- [ ] **Step 6: 컨트롤러 구현** — 위임만 하므로 시그니처만: `NextStepController`, `@GetMapping("/api/v1/goods/{goodsNo}/next-step")`, 파라미터 `@PathVariable Long goodsNo, @AuthenticationPrincipal Long memberId`, 반환 `ResponseEntity<ApiResponse<NextStepResponse>>` — 기존 `RoutineController` 스타일 그대로.
- [ ] **Step 7: 통과 + 회귀** — `./gradlew test` 전체 PASS.
- [ ] **Step 8: 커밋** — `feat(routine): 다음 단계 추천 서비스·API — 규칙 매칭·폴백·궁합 게이트`

---

## Task 5: V75 시드 + 시드 검증 (충돌 0건 · 커버리지)

**Files:**
- Create: `backend/src/main/resources/db/migration/V75__seed_routine_flow_rule.sql`
- Test: `backend/src/integrationTest/java/com/beautyboy/routine/NextStepSeedIT.java` (기존 integrationTest 소스셋·실 MySQL 관례를 따른다 — 위치·베이스클래스는 기존 IT 파일과 동일하게)

**Interfaces:**
- Consumes: Task 4 `NextStepService.find`, V12·V71·V75 시드 전체.

- [ ] **Step 1: V75 작성** — 위 "공유 계약"의 시드 12행 그대로.
- [ ] **Step 2: 시드 검증 테스트 작성** (실 시드 전제 — 자가주입 아님)

```java
// NextStepSeedIT — 실 MySQL + Flyway 전체 로드. DoD 두 축(설계 §9).
@Test void 전_상품에서_궁합_CONFLICT_후보가_한_건도_없다() {
    // 비HIDDEN 전 상품 순회, 응답의 모든 아이템을 기준상품과 pairwise 재검 — CONFLICT면 실패.
    for (Long goodsNo : 비HIDDEN_전상품_goodsNo()) {
        NextStepResponse res = nextStepService.find(goodsNo, null);
        for (NextStepBlock block : res.blocks()) {
            List<Long> itemNos = block.items().stream().map(GoodsListItem::goodsNo).toList();
            Map<Long, String> verdicts = compatQueryService.worstVerdicts(goodsNo, itemNos);
            assertThat(verdicts.values()).noneMatch("CONFLICT"::equals);
        }
    }
}
@Test void 대표_데모_케이스가_시드에서_성립한다() {
    // goods 2(AHA 토너): BUFFER 블록, 폴백 작동, goods 5(RETINOID)는 게이트에 제거됨
    NextStepResponse res = nextStepService.find(2L, null);
    assertThat(res.blocks().get(0).edgeKind()).isEqualTo("BUFFER");
    assertThat(res.blocks().get(0).items()).extracting(GoodsListItem::goodsNo)
        .contains(4L).doesNotContain(5L);
    // goods 21(선크림): 2블록 — 순방향(애프터선) + PAIRED_REMOVAL(클렌징오일/밤)
    NextStepResponse sun = nextStepService.find(21L, null);
    assertThat(sun.blocks()).extracting(NextStepBlock::edgeKind)
        .containsExactly("NEXT_STEP", "PAIRED_REMOVAL");
}
@Test void 규칙_커버리지를_출력한다_정확도는_주장하지_않는다() {
    // 커버리지 = blocks가 비어있지 않은 상품 / 비HIDDEN 전 상품. 40% 미만이면 WARN 로그만(실패 아님).
    long total = 0, covered = 0;
    for (Long goodsNo : 비HIDDEN_전상품_goodsNo()) {
        total++;
        if (!nextStepService.find(goodsNo, null).blocks().isEmpty()) covered++;
    }
    double coverage = (double) covered / total;
    System.out.printf("next-step 규칙 커버리지: %.1f%% (%d/%d)%n", coverage * 100, covered, total);
    if (coverage < 0.40) System.out.println("WARN: 커버리지 40% 미만 — 규칙 추가 검토");
    assertThat(covered).isGreaterThan(0);   // 유일한 하드 단언: 완전 공백이면 시드가 깨진 것
}
```

(시드 기대치: 토너 3 + 세럼 2 + 크림 2 + 폼 3 + 오일/밤 2 + 필링 2 + 선크림 3 + 선스틱 2 + 쉐이빙폼/젤 2 = 21개 커버 / 39개 ≈ 54%.)

- [ ] **Step 3: 실 MySQL clean 로드 + IT 실행** — 메모리 [[curl-smoke-recipe]]의 13306 임시 MySQL 사용. `./gradlew integrationTest` PASS. 커버리지 출력값을 태스크 보고서에 기록.
- [ ] **Step 4: 커밋** — `feat(routine): 전이 규칙 시드 12행 + 충돌0·커버리지 검증(V75)`

---

## Task 6: 프론트 타입·API·MSW

**Files:**
- Modify: `frontend/src/types/goods.ts`, `frontend/src/api/goods.ts`, `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/fixtures/goods.ts`

**Interfaces:**
- Produces: `fetchNextStep(goodsNo: number): Promise<NextStepBlock[]>`, MSW `GET /api/v1/goods/:goodsNo/next-step` (Task 7이 소비).

- [ ] **Step 1: 타입** — `types/goods.ts`에 "공유 계약"의 `NextStepBlock` 그대로.
- [ ] **Step 2: API 함수** — `api/goods.ts`:

```ts
/**
 * GET /goods/:goodsNo/next-step — "다음 단계" 슬롯. 서버가 전이 규칙 적용·폴백·궁합 게이트까지
 * 끝낸 결과를 받는다. blocks가 비면 화면이 섹션 자체를 그리지 않는다(NextStepSection).
 */
export async function fetchNextStep(goodsNo: number): Promise<NextStepBlock[]> {
  const response = await api.get<ApiEnvelope<{ blocks: NextStepBlock[] }>>(
    `/goods/${goodsNo}/next-step`,
  );
  return response.data.data.blocks;
}
```

- [ ] **Step 3: MSW** — `handlers.ts`에 핸들러 추가: goods 2 요청이면 `{ blocks: [{ edgeKind: 'BUFFER', reason: '각질 토너 다음 단계는 진정 세럼으로 완충하세요', items: [goods 4 카드] }] }`, goods 21이면 NEXT_STEP+PAIRED_REMOVAL 2블록, 그 외 `{ blocks: [] }`. 카드 아이템은 기존 fixtures의 `GoodsListItem`을 재사용.
- [ ] **Step 4: 타입체크** — `npx tsc --noEmit` PASS.
- [ ] **Step 5: 커밋** — `feat(front): next-step 타입·API·MSW`

---

## Task 7: NextStepSection + Detail 배선 + 스크린샷

**Files:**
- Create: `frontend/src/components/goods/NextStepSection.tsx`, `frontend/src/components/goods/NextStepSection.css`
- Modify: `frontend/src/pages/Detail.tsx`
- Test: `frontend/src/components/goods/NextStepSection.test.tsx`

**Interfaces:**
- Consumes: Task 6 `fetchNextStep`·MSW. 기존 `GoodsGrid`.

- [ ] **Step 0: DESIGN.md 정독** — 섹션 제목·본문 타이포 토큰, 간격 토큰, 한글 적용 절. `RecommendedSection.css`가 기준 참고물.
- [ ] **Step 1: 실패 테스트 작성**

```tsx
// NextStepSection.test.tsx — MSW 기반, RecommendedSection 테스트 스타일 그대로
it('블록마다 이유 문장과 상품 그리드를 그린다', async () => {
  render(<NextStepSection goodsNo={2} />, { wrapper });
  expect(await screen.findByText('각질 토너 다음 단계는 진정 세럼으로 완충하세요')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '다음 단계' })).toBeInTheDocument();
});
it('blocks가 비면 아무것도 그리지 않는다', async () => {
  const { container } = render(<NextStepSection goodsNo={6} />, { wrapper }); // MSW가 빈 blocks 반환하는 상품
  await waitFor(() => expect(container.querySelector('.bb-next-step')).not.toBeInTheDocument());
});
```

- [ ] **Step 2: 실패 확인** — `npx vitest run NextStepSection` FAIL.
- [ ] **Step 3: 구현** — `RecommendedSection` 구조를 그대로 따른다:

```tsx
/**
 * "다음 단계" — GET /goods/:goodsNo/next-step.
 * 이유 문장(reason)은 서버 규칙 행이 유일한 출처 — 여기서 문구를 만들지 않는다.
 * blocks가 비면 섹션 자체를 렌더하지 않는다(RecommendedSection과 동일 원칙).
 */
export function NextStepSection({ goodsNo }: { goodsNo: number }) {
  const nextStepQuery = useQuery({
    queryKey: ['goods-next-step', goodsNo],
    queryFn: () => fetchNextStep(goodsNo),
  });
  // 로딩: 제목 + GoodsGrid 스켈레톤 4 (RecommendedSection과 동일)
  // 데이터: <section className="bb-next-step"> <h2>다음 단계</h2>
  //   블록마다 <p className="bb-next-step__reason">{block.reason}</p> + <GoodsGrid items={block.items} />
  // 빈 blocks: null
}
```

CSS는 `bb-next-step` 네임스페이스, DESIGN.md 토큰만. reason 문장은 본문 스타일 + `word-break: keep-all`.
- [ ] **Step 4: Detail 배선** — `Detail.tsx`의 `<RecommendedSection goodsNo={goodsNo} />` **바로 윗줄**에 `<NextStepSection goodsNo={goodsNo} />` 삽입(다른 단계 추천이 같은 카테고리 추천보다 먼저 — 설계 §8).
- [ ] **Step 5: 통과 + 회귀** — `npx vitest run && npx tsc --noEmit && npm run lint` PASS.
- [ ] **Step 6: 렌더 확인(완료 조건)** — 개발서버(백엔드 실기동 또는 MSW)로 `/goods/2`(BUFFER 1블록)와 `/goods/21`(2블록) 화면을 열어 **스크린샷을 찍고 직접 본 뒤** 파일 경로를 보고서에 남긴다. 줄바꿈 깨짐·빈 레이아웃·토큰 위반 여부를 눈으로 확인.
- [ ] **Step 7: 커밋** — `feat(front): PDP 다음 단계 섹션 + 상세 배선`

---

## Task 8: 통합 스모크 (compose 전체 스택)

**Files:** 없음(검증 전용). 산출물은 태스크 보고서.

- [ ] **Step 1: 전체 스택 기동** — `docker compose up -d --build` (mysql 13306 + backend + frontend).
- [ ] **Step 2: curl 스모크**

```bash
# goods 2: BUFFER 1블록, 폴백+게이트 — goods 4 포함·goods 5 부재 확인
curl -s localhost:8080/api/v1/goods/2/next-step | jq '.data.blocks[0].edgeKind, [.data.blocks[0].items[].goodsNo]'
# 기대: "BUFFER", 배열에 4 포함·5 없음
# goods 21: 2블록
curl -s localhost:8080/api/v1/goods/21/next-step | jq '[.data.blocks[].edgeKind]'
# 기대: ["NEXT_STEP","PAIRED_REMOVAL"]
# goods 19(헤어왁스): 규칙 없음
curl -s localhost:8080/api/v1/goods/19/next-step | jq '.data.blocks | length'
# 기대: 0
```

- [ ] **Step 3: 브라우저 확인** — `localhost:3000/goods/21`에서 두 블록 렌더를 육안 확인(스크린샷).
- [ ] **Step 4: 최종 커밋·머지 준비** — 전체 테스트 재실행 후 superpowers:finishing-a-development-branch.

---

## Self-Review

**스펙 커버리지:** 설계 §3 테이블(Task 1) · §4 API/블록 선정(Task 4) · §5 알고리즘 3단(Task 4 Step 5) · §6 시드(Task 5, 12행) · §7 인터페이스(Task 2·3) · §8 프론트(Task 6·7) · §9 DoD(충돌0·커버리지 Task 5, validate·스모크 Task 8, 스크린샷 Task 7) · §10 터미널 순차(프롬프트 2개) · §11 범위 밖 미포함.

**플레이스홀더:** DDL·시드 12행·핵심 서비스 2개(worstVerdicts, NextStepService)·후보 쿼리 2개·테스트 단언 전량 기재. 위임 컨트롤러·DTO·컴포넌트 뼈대는 시그니처+사양 문장(plan-conventions 기준).

**타입 일관성:** `NextStepBlock(edgeKind, reason, items)`가 백 record·프론트 type·MSW·테스트 동일. `worstVerdicts`·`tagSlugs`·`findCandidateGoodsNos`·`findAllByOrderByPriorityAscIdAsc` 명명이 정의(공유 계약)와 소비(Task 4) 일치. queryKey `['goods-next-step', goodsNo]` 일관.

**시드 정합:** 규칙 12행의 태그 전제(goods 2 exfoliate, goods 3 soothe, goods 6 moisture, goods 26·27 soothe, C002 cleanse, C004 uv)는 V71 파생 규칙과 대조 완료. 단 **V71에는 수동 보정 재량이 있으므로**(product-tags Task 2 Step 3), 터미널 ② 착수 시 V71 최종본과 위 전제를 대조하고 어긋나면 V75 행을 맞춰 조정 후 보고한다.
