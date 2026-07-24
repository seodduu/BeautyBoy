# 성분 종합판정 엔진 Implementation Plan

> **실행 완료 (2026-07-25).** Task 1~6 전부 구현·커밋. 백엔드 전체 테스트 + 프론트 124건 + tsc/lint 통과.
> 실 MySQL(Flyway V60~V62 적용) 스모크 확인: `goods 9`(씻어내는 클렌저)=NO_CONCERN,
> `goods 1`(leave-on, 글리콜릭애씨드)=대체로 무난해요. 근거에 식약처 실제 배합한도 원문이 붙는다.
> 계획 대비 실행 편차: (a) H2 create-drop + Flyway-off 때문에 리포지토리/컨트롤러 테스트는
> Flyway 시드 대신 픽스처 자가주입(@SpringBootTest) 방식으로 작성. (b) `inci_name` 조인을 위해
> `Ingredient.java` 엔티티에 `inciName` 필드 추가(계획의 "불변" 항목 정정 — H2가 엔티티에서 테이블을 만들기 때문).
> 미포함(별도): 옵션 선택 UI·추천 섹션(Wave4 Task 4-8 잔여), 카탈로그 40→150 재구축, 피부타입 보정.


> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (권장) 또는 superpowers:executing-plans 로 태스크 단위 실행. 스텝은 체크박스(`- [ ]`)로 추적.

**Goal:** 상세 페이지가 근거 없는 "자극도 3" 숫자 대신, 식약처 규제 사실(V60/V61 `ingredient_reg_flag`)에 조인해 파생한 4단계 종합판정과 "확인 필요 성분" 패널을 보여준다.

**Architecture:** 백엔드는 `goods_ingredient → ingredient(inci_name) → ingredient_reg_flag` 조인으로 제품의 플래그를 모아 판정 규칙 함수 하나로 4단계 판정을 파생하고 새 엔드포인트 `GET /goods/{goodsNo}/assessment`로 낸다. 프론트는 가격 아래 판정 카드 + 우측 사이드패널/바텀시트를 붙이고 기존 `IngredientBadges`(숫자 노출)를 상세에서 제거한다. 기존 `ingredient` 도메인의 `category`(궁합 엔진 의존)와 `irritation_level`/`comedogenic`(deprecated)은 건드리지 않는다.

**Tech Stack:** Spring Boot(모듈러 모놀리스, JPA + 네이티브 쿼리), MySQL 8.4 + Flyway, React SPA(Vite + TS) + TanStack Query + MSW, Vitest.

## Global Constraints

- 스키마 검증 DoD는 H2가 아니라 **실 MySQL**: `./gradlew integrationTest` 또는 13306 임시 MySQL clean 로드(메모리 [[h2-createdrop-hides-validate]]). utf8mb4 클라이언트로 로드한다(latin1이면 한글이 길이 초과로 오검출됨).
- 색상은 DESIGN.md `signal-*` 5종만. **배경 채움 금지**, 텍스트·아이콘·1px 테두리로만. 확인필요=`signal-caution`, 검토=`signal-danger`, 그 외 중립(`stone`/`graphite`). 청록·파랑 금지(설계 §0.6).
- 판정 문구는 설계 §0.4의 4종 + 검토 1종. 문구 문자열은 아래 규칙에 박은 값을 **그대로** 쓴다.
- 숫자 점수를 화면에 노출하지 않는다(설계 §6/기획서 3장). 개수(`확인 성분 N개`)는 보조로만.
- 상세 하단 고지 필수: "이 정보는 성분 표시와 식약처 공개자료를 근거로 한 안내이며, 목록에 없다고 자극이 없다는 뜻은 아니고 의학적 판단이 아닙니다."
- 커밋은 스텝 단위로 자주. 백엔드는 `backend/`, 프론트는 `frontend/`에서 실행.

**참조 문서:** 설계 `화장품_성분_주의도_점수_설계.md`(§0 확정 사항), UX `docs/남성_화장품_상품상세페이지_UX_기획.md`(§6·7 카드/패널), 데이터 [[mfds-ingredient-api]].

**터미널 운용:** 백엔드→프론트가 하드 의존(프론트 스모크가 실 엔드포인트 필요)이므로 **단일 worktree/브랜치, 순차 실행**한다. 터미널 분할하지 않는다. 착수 전 사람이 확인: 루트에서 `git log --oneline -1`이 `d7a9b8a`(성분 플래그 파이프라인) 이후인지, `git status` 깨끗한지.

---

## File Structure

**백엔드 (신규)**
- `V62__ingredient_inci.sql` — `ingredient`에 `inci_name` 추가 + 30행 백필.
- `ingredient/IngredientRegFlag.java` — `ingredient_reg_flag` 읽기 전용 엔티티.
- `ingredient/IngredientRegFlagRepository.java` — 조인 쿼리.
- `ingredient/AssessmentService.java` + `AssessmentServiceImpl.java` — 판정 규칙(판단 코어).
- `ingredient/dto/GoodsAssessmentResponse.java`, `dto/FlaggedIngredient.java` — 응답 계약.
- `ingredient/AssessmentController.java` — `GET /goods/{goodsNo}/assessment`.

**프론트 (신규)**
- `types/assessment.ts` — 응답 타입(백 DTO와 1:1).
- `api/assessment.ts` — `fetchAssessment`.
- `components/goods/AssessmentCard.tsx` + `.css` — 판정 카드.
- `components/goods/CautionPanel.tsx` + `.css` — 사이드패널/바텀시트(a11y).

**프론트 (수정)**
- `pages/Detail.tsx` — 판정 카드(가격↓/CTA↑) + 패널 배선, `IngredientBadges` 제거, 고지 추가.
- `pages/Detail.css` — 카드/고지 스타일.
- `mocks/handlers.ts` — `/goods/:goodsNo/assessment` 핸들러.

**건드리지 않음:** `Ingredient.java`, `GoodsIngredientQueryService*`, `compat/*`, `IngredientBadges.*`(컴포넌트는 남기되 상세에서만 미사용), `irritation_level`/`comedogenic`.

---

## 공유 계약 (모든 태스크가 의존 — 한 글자도 바꾸지 말 것)

### 판정 규칙 (판단 코어)

```
입력: 제품의 플래그된 성분 목록 [(ingredientId, name, inciName, flags:Set<FlagType>)], rinseOff:boolean
     FlagType = BANNED | LIMIT | ALLERGEN | EXFOLIANT_ACID

1. 어떤 성분이든 BANNED 플래그가 있으면 → verdict=REVIEW, 즉시 반환.
2. N = (ALLERGEN 플래그를 가진 성분 수) + (EXFOLIANT_ACID 플래그를 가진 성분 수).
   - LIMIT·BANNED는 N에 넣지 않는다(설계 §0.3: 한도는 카탈로그 77%라 신호를 죽인다).
   - 한 성분이 ALLERGEN·ACID 둘 다면 2로 센다(각 축 독립).
3. adj = rinseOff ? max(0, N-1) : N        // 씻어내는 제품 -1 (접촉시간 짧음, 정성적이라 한 단계만)
4. adj==0 → NO_CONCERN     "걱정 성분이 거의 없어요"
   adj<=2 → MOSTLY_FINE    "대체로 무난해요"
   adj<=4 → CHECK_SENSITIVE "민감한 피부는 확인이 필요해요"
   adj>=5 → CAUTION        "주의가 필요한 성분이 있어요"
   (REVIEW → "성분 정보를 확인하고 있어요")

임계값 근거(설계 §0.3 실측): cosmetics.csv 1,472개 대상 이 규칙의 분포가 64/18/10/7%로 갈린다.
경계를 3으로 올리면 상위 두 단계가 뭉개지고, 1로 낮추면 절반이 경고가 된다.
```

### rinseOff 판정 (서비스에서 categoryCode로 파생 — goods 스키마 불변)

```
rinseOff = categoryCode 가 아래 접두사로 시작 → true, 아니면 false
  C002(클렌징 전체) | C00302(바디워시) | C00501·C00502(면도날·쉐이빙)
그 외(토너·크림·선케어·헤어스타일링 등)는 leave-on=false.
```

### 응답 DTO (백 record ↔ 프론트 type 동일 필드명)

```java
// GoodsAssessmentResponse.java
public record GoodsAssessmentResponse(
        Long goodsNo,
        String verdictCode,          // NO_CONCERN|MOSTLY_FINE|CHECK_SENSITIVE|CAUTION|REVIEW
        String verdictText,          // 위 규칙의 문구
        int checkCount,              // N (보정 전)
        boolean rinseOff,
        List<FlaggedIngredient> flagged) {}   // 확인필요+정보 성분만(무플래그 성분 제외), sort_order 순

// FlaggedIngredient.java
public record FlaggedIngredient(
        Long ingredientId,
        String name,                 // 한글명(ingredient.name)
        String inciName,
        List<String> flags,          // ["ALLERGEN"], ["LIMIT","EXFOLIANT_ACID"] 등
        String axis,                 // CHECK(착향제/각질산) | INFO(한도) | REVIEW(금지)
        String sourceRef) {}         // reg_flag.source_ref 중 대표 1건(CHECK>REVIEW>INFO 우선)
```

```ts
// types/assessment.ts — 위와 1:1
export interface FlaggedIngredient {
  ingredientId: number; name: string; inciName: string;
  flags: string[]; axis: 'CHECK' | 'INFO' | 'REVIEW'; sourceRef: string | null;
}
export interface GoodsAssessment {
  goodsNo: number; verdictCode: 'NO_CONCERN'|'MOSTLY_FINE'|'CHECK_SENSITIVE'|'CAUTION'|'REVIEW';
  verdictText: string; checkCount: number; rinseOff: boolean; flagged: FlaggedIngredient[];
}
```

`axis` 파생: 성분 flags에 ALLERGEN 또는 EXFOLIANT_ACID 있으면 `CHECK`, BANNED 있으면 `REVIEW`, 그 외(LIMIT만) `INFO`.

---

## Task 1: 스키마 — ingredient.inci_name 추가 + 백필 + reg_flag 엔티티

**Files:**
- Create: `backend/src/main/resources/db/migration/V62__ingredient_inci.sql`
- Create: `backend/src/main/java/com/beautyboy/ingredient/IngredientRegFlag.java`
- Create: `backend/src/main/java/com/beautyboy/ingredient/IngredientRegFlagRepository.java`
- Test: `backend/src/test/java/com/beautyboy/ingredient/IngredientRegFlagRepositoryTest.java`

**Interfaces:**
- Produces: `IngredientRegFlagRepository.findFlagRowsByGoodsId(Long goodsId) -> List<Object[]>`
  각 행 `[ingredientId(Long), name(String), inciName(String), flagType(String), sourceRef(String), sortOrder(Integer)]`, 무플래그 성분도 포함(LEFT JOIN, flagType/sourceRef가 null).

- [ ] **Step 1: V62 마이그레이션 작성** (완전한 DDL — 공유 계약)

```sql
-- V62__ingredient_inci.sql — 판정 엔진 조인 키. ingredient에 INCI 영문명을 추가하고 30행 백필한다.
-- category/irritation_level/comedogenic은 건드리지 않는다(궁합 엔진·기존 배지 의존). inci_name은 reg_flag 조인 키.
ALTER TABLE ingredient ADD COLUMN inci_name VARCHAR(255) NULL AFTER name;

UPDATE ingredient SET inci_name = CASE id
  WHEN 1 THEN 'retinol'                WHEN 2 THEN 'bakuchiol'
  WHEN 3 THEN 'glycolic acid'          WHEN 4 THEN 'lactic acid'
  WHEN 5 THEN 'salicylic acid'         WHEN 6 THEN 'betaine salicylate'
  WHEN 7 THEN 'ascorbic acid'          WHEN 8 THEN 'ascorbyl glucoside'
  WHEN 9 THEN 'niacinamide'            WHEN 10 THEN 'hyaluronic acid'
  WHEN 11 THEN 'sodium hyaluronate'    WHEN 12 THEN 'ceramide np'
  WHEN 13 THEN 'ceramide ap'           WHEN 14 THEN 'palmitoyl pentapeptide-4'
  WHEN 15 THEN 'acetyl hexapeptide-8'  WHEN 16 THEN 'centella asiatica extract'
  WHEN 17 THEN 'madecassoside'         WHEN 18 THEN 'fragrance'
  WHEN 19 THEN 'limonene'              WHEN 20 THEN 'alcohol'
  WHEN 21 THEN 'cetyl alcohol'         WHEN 22 THEN 'titanium dioxide'
  WHEN 23 THEN 'zinc oxide'            WHEN 24 THEN 'ethylhexyl methoxycinnamate'
  WHEN 25 THEN 'glycerin'              WHEN 26 THEN 'panthenol'
  WHEN 27 THEN 'allantoin'             WHEN 28 THEN 'tocopherol'
  WHEN 29 THEN 'adenosine'             WHEN 30 THEN 'bisabolol'
  ELSE inci_name END
WHERE id BETWEEN 1 AND 30;

CREATE INDEX idx_ingredient_inci ON ingredient(inci_name);
```
백필 근거: 30개 성분 한글명→INCI를 원료성분 API 역조회로 확정. reg_flag에 걸리는 것 — 리모넨(ALLERGEN), 글리콜릭/락틱/살리실릭(EXFOLIANT_ACID), 살리실릭/베타인살리실레이트/에칠헥실메톡시신나메이트/토코페롤(LIMIT).

- [ ] **Step 2: 실 MySQL clean 로드로 V60~V62 검증**

Run:
```bash
docker exec -i beautyboy-mysql-1 mysql --default-character-set=utf8mb4 -uroot -plocal1234 \
  -e "DROP DATABASE IF EXISTS vtest; CREATE DATABASE vtest CHARACTER SET utf8mb4;"
for v in V10__catalog V11__ingredient V12__seed_catalog V60__ingredient_reg_flag V61__seed_ingredient_reg_flag V62__ingredient_inci; do
  docker exec -i beautyboy-mysql-1 mysql --default-character-set=utf8mb4 -uroot -plocal1234 vtest \
    < backend/src/main/resources/db/migration/$v.sql; done
docker exec -i beautyboy-mysql-1 mysql --default-character-set=utf8mb4 -uroot -plocal1234 vtest -e "
  SELECT i.name,i.inci_name,f.flag_type FROM ingredient i
  JOIN ingredient_reg_flag f ON f.inci_name=i.inci_name
  WHERE i.id IN (5,19) ORDER BY i.id,f.flag_type;"
```
Expected: 살리실산→LIMIT·EXFOLIANT_ACID, 리모넨→ALLERGEN. 에러 없이 종료. 이후 `DROP DATABASE vtest`.

- [ ] **Step 3: reg_flag 엔티티 작성**

```java
// IngredientRegFlag.java
package com.beautyboy.ingredient;
import jakarta.persistence.*;

@Entity
@Table(name = "ingredient_reg_flag")
public class IngredientRegFlag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "inci_name") private String inciName;
    @Column(name = "flag_type") private String flagType;
    @Column(name = "source_ref") private String sourceRef;
    protected IngredientRegFlag() {}
    public Long getId() { return id; }
    public String getInciName() { return inciName; }
    public String getFlagType() { return flagType; }
    public String getSourceRef() { return sourceRef; }
}
```

- [ ] **Step 4: 실패 테스트 작성 — 조인 쿼리**

```java
// IngredientRegFlagRepositoryTest.java (기존 @DataJpaTest + 실 MySQL 프로파일 패턴 따름)
@Test
void goods9의_성분과_플래그를_LEFT_JOIN으로_모은다() {
    List<Object[]> rows = repository.findFlagRowsByGoodsId(9L);
    // goods 9 성분: 5(살리실산)·6(베타인살리실레이트)·21(세틸알코올)
    Set<String> flags = rows.stream().filter(r -> r[3] != null)
            .map(r -> (String) r[3]).collect(toSet());
    assertThat(flags).contains("EXFOLIANT_ACID", "LIMIT");   // 살리실산 BHA + 한도
    // 세틸알코올(21)은 무플래그로도 행에 존재(LEFT JOIN)
    assertThat(rows.stream().anyMatch(r -> r[0].equals(21L) && r[3] == null)).isTrue();
}
```

- [ ] **Step 5: 테스트 실패 확인** — Run: `./gradlew test --tests IngredientRegFlagRepositoryTest` → FAIL(메서드 없음).

- [ ] **Step 6: 리포지토리 구현**

```java
// IngredientRegFlagRepository.java
public interface IngredientRegFlagRepository extends JpaRepository<IngredientRegFlag, Long> {
    // goods_ingredient/ingredient에 JPA 연관이 없어 네이티브로 묶는다(기존 GoodsIngredientRepository 패턴).
    // reg_flag는 inci_name LEFT JOIN — 무플래그 성분도 남긴다(판정에서 "확인 성분 없음"도 사실 진술).
    @Query(value = "SELECT gi.ingredient_id, i.name, i.inci_name, f.flag_type, f.source_ref, gi.sort_order "
        + "FROM goods_ingredient gi JOIN ingredient i ON gi.ingredient_id = i.id "
        + "LEFT JOIN ingredient_reg_flag f ON f.inci_name = i.inci_name "
        + "WHERE gi.goods_id = :goodsId ORDER BY gi.sort_order", nativeQuery = true)
    List<Object[]> findFlagRowsByGoodsId(@Param("goodsId") Long goodsId);
}
```

- [ ] **Step 7: 테스트 통과 확인** — Run: `./gradlew test --tests IngredientRegFlagRepositoryTest` → PASS.

- [ ] **Step 8: 커밋** — `git add backend/... && git commit -m "feat(ingredient): inci_name 백필 + reg_flag 조인 리포지토리"`

---

## Task 2: 판정 서비스 (판단 코어)

**Files:**
- Create: `backend/src/main/java/com/beautyboy/ingredient/AssessmentService.java` (인터페이스)
- Create: `backend/src/main/java/com/beautyboy/ingredient/AssessmentServiceImpl.java`
- Create: `backend/src/main/java/com/beautyboy/ingredient/dto/GoodsAssessmentResponse.java`, `dto/FlaggedIngredient.java`
- Test: `backend/src/test/java/com/beautyboy/ingredient/AssessmentServiceTest.java`

**Interfaces:**
- Consumes: `IngredientRegFlagRepository.findFlagRowsByGoodsId` (Task 1), `GoodsQueryService.exists`/`categoryCode` 조회(기존).
- Produces: `AssessmentService.assess(Long goodsNo) -> GoodsAssessmentResponse` (공유 계약 DTO).

- [ ] **Step 1: DTO 2종 작성** — 위 "공유 계약 DTO" record 그대로.

- [ ] **Step 2: 실패 테스트 작성 — 규칙 전량** (통과 조건 = 사양)

```java
// AssessmentServiceTest.java — 리포지토리는 Mock, 규칙만 검증
@Test void 무플래그_leaveon은_걱정없음() {
    given(repo.findFlagRowsByGoodsId(1L)).willReturn(rows(row(25L,"글리세린","glycerin",null,null)));
    var r = service.assess(1L);
    assertThat(r.verdictCode()).isEqualTo("NO_CONCERN");
    assertThat(r.verdictText()).isEqualTo("걱정 성분이 거의 없어요");
    assertThat(r.flagged()).isEmpty();               // 무플래그는 응답에서 제외
}
@Test void 착향제1_각질산1_leaveon은_대체로무난() {   // N=2, leave-on
    given(catCode(2L)).willReturn("C001002");         // 세럼 = leave-on
    given(repo.findFlagRowsByGoodsId(2L)).willReturn(rows(
        row(19L,"리모넨","limonene","ALLERGEN","...25종"),
        row(5L,"살리실산","salicylic acid","EXFOLIANT_ACID","BHA")));
    var r = service.assess(2L);
    assertThat(r.verdictCode()).isEqualTo("MOSTLY_FINE");
    assertThat(r.checkCount()).isEqualTo(2);
    assertThat(r.rinseOff()).isFalse();
}
@Test void 씻어내는제품은_N에서_1을_뺀다() {            // 같은 N=2지만 rinse-off → adj=1 → 여전히 MOSTLY_FINE, checkCount는 보정전 2
    given(catCode(9L)).willReturn("C002001");         // 클렌징 = rinse-off
    given(repo.findFlagRowsByGoodsId(9L)).willReturn(rows(
        row(5L,"살리실산","salicylic acid","EXFOLIANT_ACID","BHA"),
        row(19L,"리모넨","limonene","ALLERGEN","...")));
    var r = service.assess(9L);
    assertThat(r.rinseOff()).isTrue();
    assertThat(r.checkCount()).isEqualTo(2);          // 표시는 보정 전
    assertThat(r.verdictCode()).isEqualTo("MOSTLY_FINE");   // adj=1
}
@Test void 확인성분5개_leaveon은_주의필요() {           // N=5 → CAUTION
    given(catCode(1L)).willReturn("C001001");
    given(repo.findFlagRowsByGoodsId(1L)).willReturn(rows(
        row(19L,"리모넨","limonene","ALLERGEN","a"), row(3L,"글리콜릭","glycolic acid","EXFOLIANT_ACID","AHA"),
        row(4L,"락틱","lactic acid","EXFOLIANT_ACID","AHA"), row(5L,"살리실산","salicylic acid","EXFOLIANT_ACID","BHA"),
        rowAllergen2()));                              // 착향제 하나 더
    assertThat(service.assess(1L).verdictCode()).isEqualTo("CAUTION");
}
@Test void 한도만_있으면_판정을_올리지_않는다() {         // LIMIT은 N=0 → NO_CONCERN, 단 flagged엔 INFO로 포함
    given(catCode(7L)).willReturn("C001003");
    given(repo.findFlagRowsByGoodsId(7L)).willReturn(rows(
        row(28L,"토코페롤","tocopherol","LIMIT","* 배합한도 : ...")));
    var r = service.assess(7L);
    assertThat(r.verdictCode()).isEqualTo("NO_CONCERN");
    assertThat(r.flagged()).hasSize(1);
    assertThat(r.flagged().get(0).axis()).isEqualTo("INFO");
}
@Test void 금지성분이_있으면_검토필요로_즉시반환() {
    given(catCode(5L)).willReturn("C001002");
    given(repo.findFlagRowsByGoodsId(5L)).willReturn(rows(
        row(99L,"금지가상","banned-x","BANNED",null),
        row(19L,"리모넨","limonene","ALLERGEN","a")));
    var r = service.assess(5L);
    assertThat(r.verdictCode()).isEqualTo("REVIEW");
    assertThat(r.flagged().stream().anyMatch(f -> f.axis().equals("REVIEW"))).isTrue();
}
@Test void 없는상품은_GOODS_NOT_FOUND() {
    given(goodsQueryService.exists(404L)).willReturn(false);
    assertThatThrownBy(() -> service.assess(404L))
        .isInstanceOf(BusinessException.class);
}
```

- [ ] **Step 3: 테스트 실패 확인** — Run: `./gradlew test --tests AssessmentServiceTest` → FAIL.

- [ ] **Step 4: 서비스 구현** (규칙 전량 — 판단 코어)

```java
// AssessmentServiceImpl.java 핵심. 같은 inci_name의 여러 flag 행을 성분 단위로 접는다.
@Override @Transactional(readOnly = true)
public GoodsAssessmentResponse assess(Long goodsNo) {
    if (!goodsQueryService.exists(goodsNo)) throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
    boolean rinseOff = isRinseOff(goodsQueryService.categoryCode(goodsNo));

    // ingredientId -> (name, inci, flags, sourceRefByFlag) 로 접기(정렬 유지: LinkedHashMap)
    Map<Long, Agg> byIng = new LinkedHashMap<>();
    for (Object[] r : regFlagRepository.findFlagRowsByGoodsId(goodsNo)) {
        Long id = ((Number) r[0]).longValue();
        Agg a = byIng.computeIfAbsent(id, k -> new Agg((String) r[1], (String) r[2]));
        if (r[3] != null) a.add((String) r[3], (String) r[4]);
    }
    boolean banned = byIng.values().stream().anyMatch(a -> a.flags.contains("BANNED"));
    int n = 0;
    List<FlaggedIngredient> flagged = new ArrayList<>();
    for (Map.Entry<Long, Agg> e : byIng.entrySet()) {
        Agg a = e.getValue();
        if (a.flags.isEmpty()) continue;                       // 무플래그 성분 제외
        if (a.flags.contains("ALLERGEN")) n++;
        if (a.flags.contains("EXFOLIANT_ACID")) n++;
        flagged.add(new FlaggedIngredient(e.getKey(), a.name, a.inci,
                List.copyOf(a.flags), a.axis(), a.repRef()));
    }
    String code; String text;
    if (banned) { code = "REVIEW"; text = "성분 정보를 확인하고 있어요"; }
    else {
        int adj = rinseOff ? Math.max(0, n - 1) : n;
        if (adj == 0) { code = "NO_CONCERN"; text = "걱정 성분이 거의 없어요"; }
        else if (adj <= 2) { code = "MOSTLY_FINE"; text = "대체로 무난해요"; }
        else if (adj <= 4) { code = "CHECK_SENSITIVE"; text = "민감한 피부는 확인이 필요해요"; }
        else { code = "CAUTION"; text = "주의가 필요한 성분이 있어요"; }
    }
    return new GoodsAssessmentResponse(goodsNo, code, text, n, rinseOff, flagged);
}

private static final Set<String> RINSE_PREFIX = Set.of("C002", "C00302", "C00501", "C00502");
static boolean isRinseOff(String cat) {
    return cat != null && RINSE_PREFIX.stream().anyMatch(cat::startsWith);
}
// Agg.axis(): flags에 ALLERGEN|EXFOLIANT_ACID → "CHECK", BANNED → "REVIEW", 그 외 → "INFO"
// Agg.repRef(): CHECK 축 flag의 sourceRef 우선, 없으면 REVIEW, 없으면 INFO(LIMIT 원문)
```
`GoodsQueryService`에는 현재 `categoryCode(Long)`가 **없다**(확인됨: `exists`·`findOrderSnapshot`·`findListItems`만 존재). 이 태스크에서 `String categoryCode(Long goodsNo)`를 추가한다 — `findListItems`가 반환하는 `GoodsListItem.categoryCode`를 재사용하거나 전용 조회를 붙인다.

- [ ] **Step 5: 테스트 통과 확인** — Run: `./gradlew test --tests AssessmentServiceTest` → PASS.

- [ ] **Step 6: 커밋** — `git commit -m "feat(ingredient): 성분 종합판정 규칙 서비스(한도 제외·rinse-off 보정)"`

---

## Task 3: 판정 엔드포인트

**Files:**
- Create: `backend/src/main/java/com/beautyboy/ingredient/AssessmentController.java`
- Test: `backend/src/test/java/com/beautyboy/ingredient/AssessmentApiTest.java`

**Interfaces:**
- Consumes: `AssessmentService.assess` (Task 2).
- Produces: `GET /api/v1/goods/{goodsNo}/assessment` → `ApiResponse<GoodsAssessmentResponse>`.

- [ ] **Step 1: 실패 테스트(MockMvc)**

```java
@Test void goods9_판정_응답_형태() throws Exception {
    mockMvc.perform(get("/api/v1/goods/9/assessment"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.verdictCode").exists())
        .andExpect(jsonPath("$.data.verdictText").exists())
        .andExpect(jsonPath("$.data.flagged").isArray());
}
@Test void 없는상품_404() throws Exception {
    mockMvc.perform(get("/api/v1/goods/99999/assessment")).andExpect(status().isNotFound());
}
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew test --tests AssessmentApiTest` → FAIL.

- [ ] **Step 3: 컨트롤러 구현** (기존 `IngredientController` 패턴)

```java
@GetMapping("/api/v1/goods/{goodsNo}/assessment")
public ResponseEntity<ApiResponse<GoodsAssessmentResponse>> assessment(@PathVariable Long goodsNo) {
    return ResponseEntity.ok(ApiResponse.ok(assessmentService.assess(goodsNo)));
}
```
`SecurityConfig`는 `HttpMethod.GET, "/api/v1/goods/**"`를 이미 `permitAll` 한다(확인됨) — assessment 경로가 자동 공개되므로 **보안 설정 추가 작업 없음**.

- [ ] **Step 4: 통과 확인** — Run: `./gradlew test --tests AssessmentApiTest` → PASS.

- [ ] **Step 5: 전체 백엔드 테스트 회귀** — Run: `./gradlew test` → 전부 PASS(궁합·기존 배지 회귀 없음 확인).

- [ ] **Step 6: 커밋** — `git commit -m "feat(ingredient): GET /goods/{n}/assessment 엔드포인트"`

---

## Task 4: 프론트 — 판정 카드

**Files:**
- Create: `frontend/src/types/assessment.ts`, `frontend/src/api/assessment.ts`
- Create: `frontend/src/components/goods/AssessmentCard.tsx` + `.css`
- Modify: `frontend/src/mocks/handlers.ts`, `frontend/src/pages/Detail.tsx`, `Detail.css`
- Test: `frontend/src/components/goods/AssessmentCard.test.tsx`

**Interfaces:**
- Consumes: 공유 계약 `GoodsAssessment`(types/assessment.ts).
- Produces: `<AssessmentCard assessment onOpenPanel />` — 판정 문구 + `확인 성분 N개 보기` 버튼(onOpenPanel 호출). CHECK 축 성분이 0개면 버튼 숨김.

- [ ] **Step 1: 타입·api·MSW 핸들러 추가**

```ts
// api/assessment.ts
export async function fetchAssessment(goodsNo: number): Promise<GoodsAssessment> {
  const r = await api.get<ApiEnvelope<GoodsAssessment>>(`/goods/${goodsNo}/assessment`);
  return r.data.data;
}
```
handlers.ts: `/goods/:goodsNo/assessment`를 `:goodsNo` 보다 **먼저** 등록(MSW 순서 규약, 기존 ingredients 주석 참조). 고정 판정 예시 반환.

- [ ] **Step 2: 실패 테스트(AssessmentCard)**

```tsx
it('판정 문구를 보여주고 확인 성분 개수 버튼을 낸다', () => {
  render(<AssessmentCard assessment={{...base, verdictText:'대체로 무난해요',
    flagged:[{...allergen}], checkCount:1}} onOpenPanel={fn} />);
  expect(screen.getByText('대체로 무난해요')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /확인 성분 1개/ }));
  expect(fn).toHaveBeenCalled();
});
it('CHECK 성분이 없으면 확인 버튼을 숨긴다', () => {
  render(<AssessmentCard assessment={{...base, flagged:[{...limitInfo}]}} onOpenPanel={fn} />);
  expect(screen.queryByRole('button', { name: /확인 성분/ })).toBeNull();
});
it('숫자 점수를 노출하지 않는다', () => {
  const { container } = render(<AssessmentCard assessment={base} onOpenPanel={fn} />);
  expect(container.textContent).not.toMatch(/자극도|\b[0-9]점\b/);
});
```

- [ ] **Step 3: 실패 확인** — Run: `cd frontend && npx vitest run AssessmentCard` → FAIL.

- [ ] **Step 4: AssessmentCard 구현** — verdictCode→톤 매핑(CHECK_SENSITIVE/CAUTION=caution, REVIEW=danger, 그 외 중립), 배경 채움 없이 좌측 아이콘+텍스트. CHECK 성분 수 = `flagged.filter(f=>f.axis==='CHECK').length`, >0일 때만 버튼.

- [ ] **Step 5: 통과 확인** — Run: `npx vitest run AssessmentCard` → PASS.

- [ ] **Step 6: Detail 배선** — `fetchAssessment` useQuery 추가, `<AssessmentCard>`를 `<Price>`와 `<Button>` 사이에 삽입, `onOpenPanel`은 다음 태스크의 패널 상태와 연결(우선 `useState`로 open 토글만). `<IngredientBadges>`(자극도 숫자) 상세에서 제거. Detail.test.tsx의 assessment 핸들러 추가.

- [ ] **Step 7: Detail 테스트 통과** — Run: `npx vitest run Detail` → PASS.

- [ ] **Step 8: 커밋** — `git commit -m "feat(front): 성분 판정 카드 + 자극도 숫자 배지 제거"`

---

## Task 5: 프론트 — 확인 성분 패널 (사이드패널/바텀시트)

**Files:**
- Create: `frontend/src/components/goods/CautionPanel.tsx` + `.css`
- Modify: `frontend/src/pages/Detail.tsx`
- Test: `frontend/src/components/goods/CautionPanel.test.tsx`

**Interfaces:**
- Consumes: `GoodsAssessment.flagged`.
- Produces: `<CautionPanel open flagged onClose />` — 열리면 오버레이 + 우측 패널(데스크톱)/바텀시트(모바일, CSS 미디어쿼리). 성분별 `왜/누가/근거` 표시. UX §7·§8.

- [ ] **Step 1: 실패 테스트(a11y 핵심)**

```tsx
it('열리면 확인 성분과 근거를 보여준다', () => {
  render(<CautionPanel open flagged={[allergenLimonene]} onClose={fn} />);
  expect(screen.getByText('리모넨')).toBeInTheDocument();
  expect(screen.getByText(/착향제 알레르기 유발물질 25종/)).toBeInTheDocument();
});
it('Esc·닫기버튼·오버레이 클릭으로 닫힌다', () => {
  render(<CautionPanel open flagged={[allergenLimonene]} onClose={fn} />);
  fireEvent.keyDown(document, { key: 'Escape' });
  fireEvent.click(screen.getByRole('button', { name: '닫기' }));
  expect(fn).toHaveBeenCalledTimes(2);
});
it('role=dialog + aria-modal', () => {
  render(<CautionPanel open flagged={[allergenLimonene]} onClose={fn} />);
  expect(screen.getByRole('dialog')).toHaveAttribute('aria-modal', 'true');
});
```

- [ ] **Step 2: 실패 확인** — Run: `npx vitest run CautionPanel` → FAIL.

- [ ] **Step 3: 패널 구현** — `role="dialog" aria-modal`, Esc/오버레이/닫기 → onClose, 열릴 때 포커스 이동·닫힐 때 트리거로 복귀(UX §7.2), 패널 내부만 스크롤. CHECK 축 성분 먼저, INFO(한도) 성분은 "배합한도가 있는 성분(참고)"로 구분. 색은 signal-caution 텍스트/테두리만.

- [ ] **Step 4: 통과 확인** — Run: `npx vitest run CautionPanel` → PASS.

- [ ] **Step 5: Detail 연결 + 고지** — AssessmentCard `onOpenPanel`↔CautionPanel `open` 연결, 상세 하단에 Global Constraints의 고지 문구 추가.

- [ ] **Step 6: 프론트 전체 회귀** — Run: `cd frontend && npx vitest run && npx tsc --noEmit && npm run lint` → 전부 통과.

- [ ] **Step 7: 커밋** — `git commit -m "feat(front): 확인 성분 패널(사이드/바텀시트) + 근거 고지"`

---

## Task 6: 통합 스모크 (DoD)

**Files:** 없음(검증만). 문서: `docs/plans/2026-07-24-wave4-integration.md`의 Task 4-8 상태 갱신.

- [ ] **Step 1: 백엔드 실 MySQL 기동 + 판정 curl**

Run(메모리 [[curl-smoke-recipe]] 변형): 13306 임시 MySQL로 bootRun 후
```bash
curl -s localhost:8080/api/v1/goods/9/assessment | jq '.data | {verdictCode, checkCount, rinseOff, flagged: (.flagged|length)}'
```
Expected: `verdictCode` 유효 값, goods 9는 rinseOff=true. 200 응답.

- [ ] **Step 2: Docker 프론트 반영 + 화면 확인** — `docker compose up -d --build frontend` 후 `localhost:3000/goods/9`에서 판정 카드·패널·고지 육안 확인(자극도 숫자 사라진 것 확인).

- [ ] **Step 3: 로드맵/계획서 상태 갱신** — Task 4-8의 "설명 탭"·"성분 판정" 항목 완료 표시, 남은 것(옵션 UI·추천 섹션) 명시.

- [ ] **Step 4: 커밋** — `git commit -m "docs: 성분 판정 엔진 완료 반영 + Wave4 잔여 정리"`

---

## Self-Review

**스펙 커버리지:** 설계 §0.3 한도 제외(Task 2 규칙·`한도만_있으면` 테스트) · §0.4 4단계 문구(Task 2) · §0.2 착향제 25종(Task 1 조인, reg_flag에 이미 시드) · §0.6 색상(Task 4/5 톤 매핑) · UX §6 판정 카드(Task 4) · §7·§8 패널(Task 5) · §13 함량 미표시(INFO 축 문구) · 고지(Task 5). rinse-off는 스키마 대신 categoryCode 파생(공유 계약에 명시).

**비커버 항목(의도적 제외, 별도):** 옵션 선택 UI·추천 섹션(Wave4 Task 4-8 잔여) · cosmetics.csv 40→150 카탈로그 재구축(Task 3 대역, 별도 계획) · `irritation_level`/`comedogenic` 컬럼 물리 제거(deprecated 유지) · 사용자 피부타입 보정(설계 §7.2 `userProfile`, 저장은 사용자무관·조회시 보정 원칙만 남기고 MVP 제외).

**플레이스홀더 스캔:** 규칙·DDL·DTO·테스트 단언 전량 기재. "적절한 에러처리" 류 없음.

**타입 일관성:** `verdictCode`/`verdictText`/`checkCount`/`rinseOff`/`flagged` + `FlaggedIngredient{ingredientId,name,inciName,flags,axis,sourceRef}`가 백(record)·프론트(type)·MSW·테스트에서 동일. `assess(Long)`·`findFlagRowsByGoodsId(Long)` 시그니처 일치.
