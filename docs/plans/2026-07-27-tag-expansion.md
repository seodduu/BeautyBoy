# 태그 확장·컬러 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development로 태스크 단위 실행. 스텝은 체크박스(`- [ ]`).

**Goal:** 태그 파생 기준을 낮춰 태그 0개 상품(49/190)을 거의 없애고, 태그 5종(모공·트러블·장벽·항산화·저자극)을 추가하며, 태그별 컬러(틴트 배경+진한 글자)를 도입한다.

**Architecture:** V72 마이그레이션 하나로 태그 마스터 추가·명칭 변경·파생 INSERT…SELECT 확장. 프론트는 `Tag.css`에 slug별 컬러 페어, 상세 표시 제한 해제. 백엔드 API·프론트 계약(TagView) 변경 없음 — kind에 `PROPERTY` 값이 추가될 뿐(스키마 변경 불필요).

**Tech Stack:** MySQL 8.4 + Flyway, React SPA + Vitest.

## Global Constraints

- **사용자 지시로 DESIGN.md의 "태그 무채색·배경 금지" 규칙을 해제한다.** 대신 확정 팔레트를 DESIGN.md "태그 컬러" 절로 기록하고 CSS는 그 값을 따른다(이 절이 새 진실).
- `TagView(name, kind, slug)` 계약 불변. 프론트 kind 타입에 `'PROPERTY'` 추가는 허용(union 확장).
- 동의어 태그 금지 — "수분"은 별도 태그로 만들지 않고 `moisture`(보습)가 담당한다.
- 시드는 V72 한 파일. V71 등 기존 마이그레이션 수정 금지.
- **마이그레이션 번호 참고:** 흐름 추천 계획(docs/plans/2026-07-26-next-step-recommendation.md)이 V72/V73을 선점했으나 미착수 상태 — 이 계획이 V72를 가져가고, 흐름 추천 재개 시 V74/V75로 밀어 계획서를 수정한다.
- 실 MySQL clean 로드 검증(H2 함정 회피), 화면 변경은 스크린샷 확인이 완료 조건. 커밋 한국어.

## 확정 태그 세트 (13 → 18종)

| slug | name | kind | 파생 근거 (is_key 불문, 매핑된 성분 전체) |
|---|---|---|---|
| cleanse | 세정 | EFFECT | (기존) C002 카테고리 |
| exfoliate | 각질 케어 | EFFECT | (기존) AHA·BHA·SALICYLIC |
| sebum | 피지 관리 | EFFECT | (기존) BHA·SALICYLIC·NIACINAMIDE |
| soothe | 진정 | EFFECT | (기존) CENTELLA + **판테놀(26)·알란토인(27)** + **C005003 애프터쉐이브** |
| moisture | 보습 | EFFECT | (기존) HYALURONIC + **CERAMIDE·글리세린(25)** |
| uv | 자외선차단 | EFFECT | (기존) SPF_FILTER + C004 |
| bright | 브라이트닝 | EFFECT | (기존) VITAMIN_C + **알파비사보롤(30)** |
| firm | 탄력 | EFFECT | (기존) PEPTIDE |
| anti-aging | **안티에이징** (명칭 변경) | EFFECT | (기존) RETINOID + **아데노신(29)** |
| scalp | 두피 케어 | EFFECT | (기존) C003001 |
| **pore** | 모공 케어 | EFFECT | BHA·SALICYLIC·NIACINAMIDE |
| **trouble** | 트러블 케어 | EFFECT | BHA·SALICYLIC |
| **barrier** | 장벽 케어 | EFFECT | CERAMIDE |
| **antioxidant** | 항산화 | EFFECT | VITAMIN_C·토코페롤(28) |
| **gentle** | 저자극 | **PROPERTY** | 성분 매핑이 1개 이상이고 매핑 전 성분 irritation_level ≤ 2 |
| fresh/dewy/matte | 산뜻함/촉촉함/매트 | TEXTURE | (기존) 수동 |

파생 원칙 변경: **is_key 조건 제거**(보조성분도 파생, 단 sort_order를 is_key보다 +20 뒤로), 성분 근거는 기존처럼 `source_ingredient_id`에 남긴다(카테고리·속성 파생은 NULL).

## 컬러 팔레트 (DESIGN.md "태그 컬러" 절로 기록할 확정값)

pill = 틴트 배경 + 같은 계열 진한 글자. TEXTURE는 기존 회색 유지.

| slug | bg | text | | slug | bg | text |
|---|---|---|---|---|---|---|
| moisture | #E0F2FE | #0369A1 | | anti-aging | #FAE8FF | #86198F |
| soothe | #DCFCE7 | #15803D | | firm | #FFE4E6 | #BE123C |
| cleanse | #DBEAFE | #1D4ED8 | | antioxidant | #FEF3C7 | #B45309 |
| exfoliate | #FFEDD5 | #C2410C | | barrier | #FEF9C3 | #A16207 |
| sebum | #CCFBF1 | #0F766E | | scalp | #F5F5F4 | #57534E |
| uv | #FEF08A | #854D0E | | pore | #CFFAFE | #0E7490 |
| bright | #EDE9FE | #6D28D9 | | trouble | #FEE2E2 | #B91C1C |
| gentle | #FCE7F3 | #BE185D | | (TEXTURE) | 기존 회색 | |

표시 정책: 상세 = 전부 표시(줄바꿈 허용, EFFECT→PROPERTY→TEXTURE 순), 카드 = EFFECT 우선 2개(기존 유지).

---

## Task 1: V72 — 태그 마스터 확장 + 파생 재작성 (backend)

**Files:** Create `backend/src/main/resources/db/migration/V72__expand_product_tag.sql`. Test: 실 MySQL clean 로드 + 검증 쿼리(아래).

- [ ] **Step 1: V72 작성.** 순서: (a) 태그 마스터 — `UPDATE tag SET name='안티에이징' WHERE slug='anti-aging';` + 신규 5행 INSERT `(14,'모공 케어','EFFECT','pore',11),(15,'트러블 케어','EFFECT','trouble',12),(16,'장벽 케어','EFFECT','barrier',13),(17,'항산화','EFFECT','antioxidant',14),(18,'저자극','PROPERTY','gentle',30)`. (b) 성분→태그 파생 INSERT…SELECT를 **위 확정 테이블의 매핑 전체**로 다시 실행 — V71과 같은 CASE 구조이되 `WHERE gi.is_key=1` 제거, `sort_order = 10 + t.sort_order + IF(gi.is_key=1, 0, 20)`, `ON DUPLICATE KEY UPDATE sort_order = goods_tag.sort_order`(V71 기존 행 보존). OTHER는 `i.id IN (25,26,27,28,29,30)` 성분별 CASE. (c) 카테고리 기본값 추가분: `C005003 → soothe`. (d) 저자극: 
```sql
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order)
SELECT g.id, (SELECT id FROM tag WHERE slug='gentle'), NULL, 40
FROM goods g
WHERE EXISTS (SELECT 1 FROM goods_ingredient gi WHERE gi.goods_id=g.id)
  AND NOT EXISTS (SELECT 1 FROM goods_ingredient gi JOIN ingredient i ON i.id=gi.ingredient_id
                  WHERE gi.goods_id=g.id AND i.irritation_level > 2)
ON DUPLICATE KEY UPDATE sort_order = goods_tag.sort_order;
```
- [ ] **Step 2: 실 MySQL clean 로드 검증** (임시 컨테이너, 13306/13307 기존 것 건드리지 않기). 검증 쿼리와 기대: ① 태그 0개 상품 수 `≤ 12` (razor·eyebrow류만), ② goods 96(캄 수분토너: 판테놀 key + 비타민C·히알루론산 보조)에 `보습·진정·브라이트닝·항산화` 포함, ③ goods 7(고영양 크림: CERAMIDE)에 `장벽 케어·보습`, ④ `SELECT COUNT(*) FROM tag` = 18, ⑤ 안티에이징 명칭 확인.
- [ ] **Step 3: 백엔드 전체 테스트** `./gradlew test` 녹색(기존 태그 테스트는 V71 의존이라 영향 없음 — H2라 시드 안 탐).
- [ ] **Step 4: 커밋** — `feat(catalog): 태그 확장 — 파생 기준 완화·신규 5종·안티에이징 개명(V72)`

## Task 2: 프론트 컬러 + 표시 정책 + DESIGN.md (frontend)

**Files:** Modify `frontend/src/components/ui/Tag.tsx`+`.css`, `frontend/src/types/goods.ts`(kind union에 `'PROPERTY'`), `frontend/src/pages/Detail.tsx`(전부 표시·정렬), `frontend/src/mocks/fixtures/goods.ts`(신규 태그 반영), `DESIGN.md`(태그 컬러 절), Test `Tag.test.tsx`·`Detail.test.tsx` 갱신.

- [ ] **Step 1: DESIGN.md에 "태그 컬러" 절 추가** — 위 팔레트 표 그대로(이 절이 태그 색의 진실이라는 문구 포함).
- [ ] **Step 2: Tag.css** — slug별 클래스 `bb-tag--{slug}`에 bg/text 페어(DESIGN.md 값 참조 주석), 미정의 slug·TEXTURE는 기존 무채색 폴백. Tag.tsx가 `bb-tag--${view.slug}` 클래스를 추가.
- [ ] **Step 3: Detail 표시 정책** — 최대 4개 제한 제거, 정렬 EFFECT→PROPERTY→TEXTURE(동순위는 원래 순서), flex-wrap. 카드는 기존(EFFECT 2개) 유지.
- [ ] **Step 4: 테스트 갱신** — Tag: "slug별 컬러 클래스가 붙는다", "미정의 slug는 폴백". Detail: "5개 이상도 전부 렌더". 기존 max-4 단언 제거.
- [ ] **Step 5: 회귀** — `npx vitest run && npx tsc --noEmit -p tsconfig.app.json && npm run lint` (**-p 필수**).
- [ ] **Step 6: 커밋** — `feat(front): 태그 컬러 팔레트·상세 전체 표시 + DESIGN.md 태그 컬러 절`

## Task 3: 통합 스모크 + 스크린샷

- [ ] compose(오버라이드 13307) 재빌드 기동 → `curl /goods/96` 태그 4종+ 확인, `curl /goods?tag=trouble` 결과 존재 확인.
- [ ] `localhost:3000/goods/96`·`/goods/9` 스크린샷 — 컬러 pill 확인(파일 경로 보고).

## Self-Review

플레이스홀더 없음(팔레트·매핑·저자극 SQL 전량). 계약 불변(TagView). V71 불변·V72 단일 파일. 흐름 추천 번호 충돌은 Global Constraints에 기록.
