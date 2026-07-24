# 상품 태그 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development(권장) 또는 superpowers:executing-plans로 태스크 단위 실행. 스텝은 체크박스(`- [ ]`).

**Goal:** 상품에 효과·사용감 태그를 붙여 상세/카드에 표시하고, 태그 클릭으로 목록을 거른다. 효과 태그는 근거 성분을 함께 저장한다(수동 큐레이션 + 성분 근거).

**Architecture:** `tag` 마스터 + `goods_tag` 조인(근거 성분 FK 포함) 두 테이블. 목록/상세 응답에 `tags` 배열을 기존 배지 배치 패턴(`findValidBadges` batch → `toItem`)과 동일하게 얹는다. 필터는 기존 `GET /goods`에 `tag=<slug>` 파라미터를 더한다(JPQL exists 서브쿼리). 주의(caution)는 태그로 만들지 않는다 — 이미 성분 판정 카드가 규제 근거로 담당한다.

**Tech Stack:** Spring Boot(JPQL + EntityManager 수동 조립, QueryDSL 미도입), MySQL 8.4 + Flyway, React SPA(Vite+TS) + TanStack Query + MSW, Vitest.

## Global Constraints

- 스키마 검증 DoD는 실 MySQL clean 로드(utf8mb4 클라이언트). 메모리 [[h2-createdrop-hides-validate]] · [[mfds-ingredient-api]](같은 utf8mb4 함정).
- 색: DESIGN.md `signal-*` 5종만, 배경 채움 금지. **효과=ink/graphite + 1px 테두리(무채색 pill), 사용감=stone(회색).** 청록 금지(토큰 없음). 태그엔 주황(주의)을 쓰지 않는다.
- 태그 종류는 `EFFECT` | `TEXTURE` 둘뿐. `CAUTION`은 만들지 않는다(판정 카드가 대체).
- 상품당 태그 표시 **최대 4개**(기획서 5.3). 효과 우선, 사용감 후순위.
- `GoodsListItem`은 "설계 7장 동결 형태"다 — 필드는 **맨 뒤에 추가만** 한다(순서·타입 불변). 프론트 타입·MSW도 함께 맞춘다.
- 커밋은 스텝 단위로 자주. 백엔드 `backend/`, 프론트 `frontend/`.
- 단일 worktree/브랜치, 순차. 착수 전 사람 확인: `git log --oneline -1`이 `6949a9e`(docker) 이후인지, `git status` 깨끗한지.

**참조:** UX `docs/남성_화장품_상품상세페이지_UX_기획.md` §5.3. 설계 결정은 이 대화(수동+근거 성분, 주의 제외, 색 무채색).

---

## File Structure

**백엔드 신규**
- `V70__product_tag.sql` — tag·goods_tag DDL.
- `V71__seed_product_tag.sql` — tag 마스터 + goods_tag 매핑(규칙 기반 INSERT…SELECT + 수동 보정).
- `catalog/Tag.java`, `catalog/GoodsTag.java` — 엔티티.
- `catalog/TagRepository.java`, `catalog/GoodsTagRepository.java` — 배치 조회.
- `catalog/dto/TagView.java` — 응답 원소(name·kind·slug).

**백엔드 수정**
- `dto/GoodsListItem.java` — `tags` 필드 추가(맨 뒤).
- `dto/GoodsDetailResponse.java` — `tags` 필드 추가.
- `dto/GoodsSearchCondition.java` — `tagSlug` 추가.
- `GoodsController.java` — `@RequestParam tag`.
- `GoodsQueryRepository.java` — `appendFilters`/`bindParameters`에 tag exists 서브쿼리 + `findTagsByGoodsIds` batch.
- `GoodsService.java` — `list`·`detail`·`toItem`·`findListItems`·`recommended`에 tags 주입.

**프론트 신규**
- `components/ui/Tag.tsx` + `.css` — 태그 pill.
**프론트 수정**
- `types/goods.ts` — `TagView` + `GoodsListItem.tags`, `types/detail.ts` — `GoodsDetail.tags`.
- `api/goods.ts` — `FetchGoodsListParams.tag`.
- `pages/Detail.tsx` — 한 줄 평 아래 태그 줄.
- `components/goods/GoodsCard.tsx` — 태그 노출.
- `pages/GoodsList.tsx` — `?tag=` 파라미터 조회.
- `mocks/handlers.ts`, `mocks/fixtures/goods.ts` — tags 포함.

**건드리지 않음:** ingredient 도메인(판정 엔진), 배지(promotion), 주의/판정 카드.

---

## 공유 계약

### DTO (백 record ↔ 프론트 type 동일 필드명)
```java
// TagView.java
public record TagView(String name, String kind, String slug) {}   // kind: "EFFECT"|"TEXTURE"
// GoodsListItem: 맨 뒤에 List<TagView> tags 추가
// GoodsDetailResponse: List<TagView> tags 추가
// GoodsSearchCondition: String tagSlug 추가(null이면 미필터)
```
```ts
// types/goods.ts
export interface TagView { name: string; kind: 'EFFECT' | 'TEXTURE'; slug: string; }
// GoodsListItem.tags: TagView[]   /  GoodsDetail.tags: TagView[]
```

### 배치 조회 시그니처
```java
// GoodsTagRepository (GoodsQueryRepository.findValidBadges와 같은 batch 패턴)
Map<Long, List<TagView>> findTagsByGoodsIds(Collection<Long> goodsIds);
// 반환: goodsId -> [TagView...], sort_order 순. 빈 입력이면 Map.of().
```

### 효과 태그 큐레이션 규칙 (근거 성분 = is_key 성분의 category)
```
성분 category → 효과 태그(slug)        상품 category_code 접두사 → 기본 태그
  BHA, SALICYLIC → sebum, exfoliate       C002 → cleanse
  AHA            → exfoliate               C004 → uv
  NIACINAMIDE    → sebum                   C003001 → scalp
  CENTELLA       → soothe
  HYALURONIC     → moisture
  SPF_FILTER     → uv
  VITAMIN_C      → bright
  PEPTIDE        → firm
  RETINOID       → anti-aging
근거 성분(source_ingredient_id) = 그 태그를 만든 is_key 성분. 카테고리 기반 태그는 근거 성분 NULL.
사용감(TEXTURE)은 규칙 없음 — summary가 명확한 상품에만 수동으로 소수 부여(근거 NULL).
```

---

## Task 1: 스키마 + 엔티티 + 배치 조회

**Files:** Create `V70__product_tag.sql`, `Tag.java`, `GoodsTag.java`, `TagRepository.java`, `GoodsTagRepository.java`, `dto/TagView.java`; Test `GoodsTagRepositoryTest.java`.

**Interfaces:** Produces `GoodsTagRepository.findTagsByGoodsIds(Collection<Long>) -> Map<Long, List<TagView>>`.

- [ ] **Step 1: V70 DDL 작성** (완전한 DDL — 공유 계약)
```sql
-- V70__product_tag.sql — 상품 효과·사용감 태그. 주의(CAUTION)는 성분 판정 카드가 담당하므로 kind에 없다.
CREATE TABLE tag (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  name       VARCHAR(40)  NOT NULL,          -- "피지 관리"
  kind       VARCHAR(20)  NOT NULL,          -- EFFECT|TEXTURE
  slug       VARCHAR(40)  NOT NULL UNIQUE,   -- 필터 URL·안정 참조 "sebum"
  sort_order INT          NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE goods_tag (
  goods_id             BIGINT NOT NULL,
  tag_id               BIGINT NOT NULL,
  source_ingredient_id BIGINT NULL,          -- 효과 태그 근거 성분(B). 사용감은 NULL.
  sort_order           INT NOT NULL DEFAULT 0,
  PRIMARY KEY (goods_id, tag_id),
  CONSTRAINT fk_gt_goods FOREIGN KEY (goods_id) REFERENCES goods(id),
  CONSTRAINT fk_gt_tag FOREIGN KEY (tag_id) REFERENCES tag(id),
  CONSTRAINT fk_gt_ingredient FOREIGN KEY (source_ingredient_id) REFERENCES ingredient(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_goods_tag_tag ON goods_tag(tag_id);
```

- [ ] **Step 2: 엔티티 2종 작성** — `Tag`(id,name,kind,slug,sortOrder), `GoodsTag`(복합키 goodsId+tagId, sourceIngredientId nullable, sortOrder). 기존 `GoodsIngredient`의 `@IdClass Pk` 패턴을 그대로 따른다(스칼라 매핑, 연관관계 없음). `TagView` record 작성.

- [ ] **Step 3: 실패 테스트 작성**
```java
// GoodsTagRepositoryTest — @SpringBootTest @ActiveProfiles("test") @Transactional, 픽스처 자가주입
@Test void 상품의_태그를_sort_order순으로_batch_조회한다() {
    Goods g = 상품_저장("C002001");
    Tag cleanse = 태그_저장("세정","EFFECT","cleanse",0);
    Tag exfo = 태그_저장("각질 케어","EFFECT","exfoliate",1);
    goodsTagRepository.save(new GoodsTag(g.getId(), exfo.getId(), null, 2));
    goodsTagRepository.save(new GoodsTag(g.getId(), cleanse.getId(), null, 1));
    Map<Long,List<TagView>> m = goodsTagRepository.findTagsByGoodsIds(List.of(g.getId()));
    assertThat(m.get(g.getId())).extracting(TagView::slug).containsExactly("cleanse","exfoliate"); // sort_order순
}
@Test void 빈_입력은_빈_맵() {
    assertThat(goodsTagRepository.findTagsByGoodsIds(List.of())).isEmpty();
}
```

- [ ] **Step 4: 실패 확인** — `./gradlew test --tests GoodsTagRepositoryTest` → FAIL.

- [ ] **Step 5: 배치 조회 구현** (`GoodsQueryRepository.findValidBadges` 패턴)
```java
// GoodsTagRepository (JpaRepository) 안에 배치 메서드. tag 조인해 TagView로 뽑는다.
@Query("select gt.goodsId, t.name, t.kind, t.slug from GoodsTag gt, Tag t "
     + "where gt.tagId = t.id and gt.goodsId in :ids order by gt.goodsId, gt.sortOrder")
List<Object[]> findTagRows(@Param("ids") Collection<Long> ids);
// default 메서드로 Map<Long,List<TagView>> 조립(빈 입력 Map.of()).
```

- [ ] **Step 6: 통과 확인** — PASS.
- [ ] **Step 7: 커밋** — `feat(catalog): tag·goods_tag 스키마 + 배치 조회`

---

## Task 2: 시드 (V71) — 태그 마스터 + 규칙 기반 매핑

**Files:** Create `V71__seed_product_tag.sql`.

- [ ] **Step 1: 태그 마스터 시드** (완전 — 데이터 계약)
```sql
-- V71__seed_product_tag.sql
INSERT INTO tag (id, name, kind, slug, sort_order) VALUES
(1,'세정','EFFECT','cleanse',1),(2,'각질 케어','EFFECT','exfoliate',2),(3,'피지 관리','EFFECT','sebum',3),
(4,'진정','EFFECT','soothe',4),(5,'보습','EFFECT','moisture',5),(6,'자외선차단','EFFECT','uv',6),
(7,'브라이트닝','EFFECT','bright',7),(8,'탄력','EFFECT','firm',8),(9,'항노화','EFFECT','anti-aging',9),
(10,'두피 케어','EFFECT','scalp',10),
(11,'산뜻함','TEXTURE','fresh',20),(12,'촉촉함','TEXTURE','dewy',21),(13,'매트','TEXTURE','matte',22);
```

- [ ] **Step 2: 효과 태그 = is_key 성분 category에서 파생해 근거와 함께 저장** (규칙을 SQL로 못 박음)
```sql
-- 각 INSERT…SELECT = "규칙 한 줄". source_ingredient_id에 근거 성분(is_key)을 남긴다(설계 B).
-- 성분 근거 태그
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order)
SELECT gi.goods_id, t.id, gi.ingredient_id, 10 + t.sort_order
FROM goods_ingredient gi JOIN ingredient i ON gi.ingredient_id=i.id
JOIN tag t ON t.slug = CASE
  WHEN i.category IN ('BHA','SALICYLIC') THEN 'sebum'
  WHEN i.category = 'AHA' THEN 'exfoliate'
  WHEN i.category = 'NIACINAMIDE' THEN 'sebum'
  WHEN i.category = 'CENTELLA' THEN 'soothe'
  WHEN i.category = 'HYALURONIC' THEN 'moisture'
  WHEN i.category = 'SPF_FILTER' THEN 'uv'
  WHEN i.category = 'VITAMIN_C' THEN 'bright'
  WHEN i.category = 'PEPTIDE' THEN 'firm'
  WHEN i.category = 'RETINOID' THEN 'anti-aging'
  ELSE NULL END
WHERE gi.is_key = 1
ON DUPLICATE KEY UPDATE sort_order = goods_tag.sort_order;  -- 같은 상품·태그 중복 무시
-- BHA/SALICYLIC은 각질 케어도 추가
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order)
SELECT gi.goods_id, (SELECT id FROM tag WHERE slug='exfoliate'), gi.ingredient_id, 12
FROM goods_ingredient gi JOIN ingredient i ON gi.ingredient_id=i.id
WHERE gi.is_key=1 AND i.category IN ('BHA','SALICYLIC')
ON DUPLICATE KEY UPDATE sort_order = goods_tag.sort_order;
-- 제품 유형 기반(근거 성분 없음): 클렌징→세정, 선케어→자외선차단, 샴푸→두피
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order)
SELECT g.id, (SELECT id FROM tag WHERE slug='cleanse'), NULL, 1 FROM goods g WHERE g.category_code LIKE 'C002%'
ON DUPLICATE KEY UPDATE sort_order = goods_tag.sort_order;
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order)
SELECT g.id, (SELECT id FROM tag WHERE slug='uv'), NULL, 1 FROM goods g WHERE g.category_code LIKE 'C004%'
ON DUPLICATE KEY UPDATE sort_order = goods_tag.sort_order;
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order)
SELECT g.id, (SELECT id FROM tag WHERE slug='scalp'), NULL, 1 FROM goods g WHERE g.category_code LIKE 'C003001%'
ON DUPLICATE KEY UPDATE sort_order = goods_tag.sort_order;
```

- [ ] **Step 3: 수동 보정 — 이름/성분 어긋난 데모 상품** (규칙이 낸 이상한 태그 제거)
```sql
-- 예: goods 1 "수분 진정 토너"인데 is_key에 AHA·RETINOID가 섞여 항노화/각질 태그가 붙는다 → 진정/보습만 남긴다.
DELETE FROM goods_tag WHERE goods_id=1 AND tag_id IN ((SELECT id FROM tag WHERE slug='exfoliate'),(SELECT id FROM tag WHERE slug='anti-aging'));
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order)
VALUES (1,(SELECT id FROM tag WHERE slug='soothe'),16,2) ON DUPLICATE KEY UPDATE sort_order=2; -- 근거: 병풀(16)
-- 사용감(TEXTURE) 소수 수동: summary가 명확한 상품에만.
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order) VALUES
(9,(SELECT id FROM tag WHERE slug='fresh'),NULL,30),    -- "산뜻함이 오래" (goods 9)
(2,(SELECT id FROM tag WHERE slug='dewy'),NULL,30)      -- "고보습 토너" (goods 2)
ON DUPLICATE KEY UPDATE sort_order=30;
```
> 실행자 주의: `V12__seed_catalog.sql`의 상품별 summary·is_key 성분을 대조해 어긋나는 상품(이름 ≠ 성분 계열)을 찾아 위 보정 블록을 채운다. 최소한 goods 1·5(수분/브라이트닝인데 RETINOID 섞임)는 확인한다.

- [ ] **Step 4: 실 MySQL clean 로드 + 검증**
```bash
# V10~V12 + V70~V71 순서로 clean 로드 후:
SELECT g.name, GROUP_CONCAT(t.name) FROM goods g JOIN goods_tag gt ON gt.goods_id=g.id
JOIN tag t ON t.id=gt.tag_id GROUP BY g.id ORDER BY g.id LIMIT 12;
# 기대: goods 9=세정·피지 관리·각질 케어·산뜻함, goods 21=자외선차단, goods 1에 각질/항노화 없음
```
- [ ] **Step 5: 커밋** — `feat(catalog): 상품 태그 시드(규칙 파생 + 근거 성분 + 수동 보정)`

---

## Task 3: 응답에 tags 주입 (목록·상세)

**Files:** Modify `GoodsListItem.java`, `GoodsDetailResponse.java`, `GoodsService.java`; Test 확장 `GoodsApiTest`(기존)·`GoodsIngredientApiTest` 인근 패턴.

**Interfaces:** Consumes `findTagsByGoodsIds`(Task 1). Produces `tags` 배열이 실린 목록/상세 응답.

- [ ] **Step 1: DTO 필드 추가** — `GoodsListItem` 맨 뒤 `List<TagView> tags`, `GoodsDetailResponse`에 `List<TagView> tags`.

- [ ] **Step 2: 실패 테스트** — 상세/목록 응답에 tags가 실리는지.
```java
@Test void 상세응답에_태그가_실린다() throws Exception {
    // goods + goods_tag 픽스처 후
    mockMvc.perform(get("/api/v1/goods/"+id))
      .andExpect(jsonPath("$.data.tags[0].slug").exists())
      .andExpect(jsonPath("$.data.tags[0].kind").value("EFFECT"));
}
```

- [ ] **Step 3: 서비스 배선** — `list`/`detail`/`findListItems`/`recommended`에서 `findValidBadges` 옆에 `findTagsByGoodsIds(goodsIds)` 호출, `toItem(row, badges, tags)`·detail 생성자에 전달. `toItem` 시그니처에 `List<TagView> tags` 추가(호출처 4곳 갱신).

- [ ] **Step 4: 통과 + 회귀** — `./gradlew test` 전체 PASS.
- [ ] **Step 5: 커밋** — `feat(catalog): 목록·상세 응답에 태그 배열 추가`

---

## Task 4: 태그 필터 (GET /goods?tag=)

**Files:** Modify `GoodsSearchCondition.java`, `GoodsController.java`, `GoodsQueryRepository.java`; Test `GoodsApiTest`.

- [ ] **Step 1: 실패 테스트**
```java
@Test void tag_슬러그로_목록을_거른다() throws Exception {
    // goods A엔 uv 태그, B엔 없음
    mockMvc.perform(get("/api/v1/goods").param("tag","uv"))
      .andExpect(jsonPath("$.data.content[*].goodsNo", hasItem(aId.intValue())))
      .andExpect(jsonPath("$.data.content[*].goodsNo", not(hasItem(bId.intValue()))));
}
```
- [ ] **Step 2: 실패 확인.**
- [ ] **Step 3: 구현** — `GoodsSearchCondition`에 `String tagSlug`. 컨트롤러 `@RequestParam(required=false) String tag` → condition. `appendFilters`에:
```java
if (condition.tagSlug() != null && !condition.tagSlug().isBlank()) {
    jpql.append(" and exists (select 1 from GoodsTag gt2, Tag t2 "
        + "where gt2.tagId = t2.id and gt2.goodsId = g.id and t2.slug = :tagSlug)");
}
```
`bindParameters`에 `tagSlug` 세팅. `count`도 같은 `appendFilters`를 타므로 자동 반영.
- [ ] **Step 4: 통과 + 회귀.**
- [ ] **Step 5: 커밋** — `feat(catalog): GET /goods tag 슬러그 필터`

---

## Task 5: 프론트 표시 (pill + 상세 줄 + 카드)

**Files:** Create `components/ui/Tag.tsx`+`.css`; Modify `types/goods.ts`, `types/detail.ts`, `pages/Detail.tsx`, `components/goods/GoodsCard.tsx`, `mocks/handlers.ts`, `mocks/fixtures/goods.ts`; Test `Tag.test.tsx`, `Detail.test.tsx`.

**Interfaces:** `<Tag view={TagView} />` — EFFECT=무채색 pill, TEXTURE=회색. `to` 있으면 Link.

- [ ] **Step 1: 타입 + 픽스처** — `TagView` 추가, `GoodsListItem.tags`/`GoodsDetail.tags`. MSW/fixtures가 tags를 내려주도록(빈 배열 기본, 일부 상품에 샘플).
- [ ] **Step 2: 실패 테스트(Tag)**
```tsx
it('EFFECT는 무채색, TEXTURE는 회색 클래스', () => {
  const { rerender, container } = render(<Tag view={{name:'세정',kind:'EFFECT',slug:'cleanse'}} />);
  expect(container.querySelector('.bb-tag')).toHaveClass('bb-tag--effect');
  rerender(<Tag view={{name:'산뜻함',kind:'TEXTURE',slug:'fresh'}} />);
  expect(container.querySelector('.bb-tag')).toHaveClass('bb-tag--texture');
});
it('숫자·주황 주의색을 쓰지 않는다', () => {
  const { container } = render(<Tag view={{name:'세정',kind:'EFFECT',slug:'cleanse'}} />);
  expect(container.textContent).toBe('세정');
});
```
- [ ] **Step 3: Tag 구현 + CSS** — kind→클래스, 배경 없이 텍스트/테두리. EFFECT `--color-graphite`+`--color-hairline` 테두리, TEXTURE `--color-stone`.
- [ ] **Step 4: Detail 배선** — 한 줄 평(`bb-detail__summary`) 아래에 `tags` pill 줄(최대 4개, effect 먼저). Detail.test에 tags 핸들러 반영.
- [ ] **Step 5: GoodsCard 배선** — 배지 줄 인근에 effect 태그 1~2개.
- [ ] **Step 6: 통과 + 회귀** — `npx vitest run && npx tsc --noEmit && npm run lint`.
- [ ] **Step 7: 커밋** — `feat(front): 태그 pill + 상세·카드 표시`

---

## Task 6: 프론트 필터 + 스모크

**Files:** Modify `api/goods.ts`, `pages/GoodsList.tsx`, `components/ui/Tag.tsx`; Test `GoodsList.test.tsx`.

- [ ] **Step 1: api 파라미터** — `FetchGoodsListParams`에 `tag?: string`, `fetchGoodsList`가 params로 전달.
- [ ] **Step 2: 실패 테스트(GoodsList)** — `?tag=uv`로 진입 시 `fetchGoodsList`가 `{tag:'uv'}`로 호출되고 결과가 렌더되는지(MSW가 tag 파라미터로 거른 목록 반환).
- [ ] **Step 3: 구현** — `GoodsList`가 `searchParams.get('tag')`를 읽어 `fetchGoodsList({tag})`. `Tag`에 `to` prop(있으면 `<Link to={/goods?tag=${slug}}>`), 상세·카드의 태그를 클릭 가능하게.
- [ ] **Step 4: 통과 + 회귀.**
- [ ] **Step 5: 통합 스모크** — `docker compose up -d --build` 후:
```bash
curl -s 'localhost:8080/api/v1/goods/9' | jq '.data.tags'          # 세정·피지 관리·각질 케어·산뜻함
curl -s 'localhost:8080/api/v1/goods?tag=uv' | jq '.data.content|length'  # 선케어 상품만
```
`localhost:3000/goods/9`에서 태그 줄 육안 확인 + 태그 클릭 → 목록 필터.
- [ ] **Step 6: 커밋** — `feat(front): 태그 클릭 목록 필터 + 스모크`

---

## Self-Review

**스펙 커버리지:** 수동+근거(Task 2 source_ingredient_id) · tag 마스터+goods_tag(Task 1) · 표시(Task 3·5) · 필터(Task 4·6) · 색 무채색(Task 5) · 주의 제외(스키마 kind에 CAUTION 없음) · 최대 4개(Task 5). UX §5.3 위치(한 줄 평 아래) 반영.

**비커버(의도적):** 관리자 UI · 다중 태그 AND 필터 · 사용감 대량 시드(명확한 것만) · 카탈로그 재구축 · GIFT 우선순위.

**플레이스홀더:** DDL·시드 규칙·DTO·필터 JPQL·테스트 단언 전량 기재. Task 3 `toItem` 호출처 4곳 갱신 명시.

**타입 일관성:** `TagView{name,kind,slug}`가 백 record·프론트 type·MSW·테스트 동일. `findTagsByGoodsIds`·`tagSlug`·`tag`(param) 명명 일치. `GoodsListItem.tags`는 맨 뒤 추가(동결 계약 준수).
