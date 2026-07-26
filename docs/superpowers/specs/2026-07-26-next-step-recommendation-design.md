# 흐름 추천(다음 단계 추천) 설계

> 상태: **확정.** 출처: `docs/2026-07-25-흐름-추천-설계메모.md`(아이디어·객관적 검토) + 2026-07-26 브레인스토밍 결정.
> 구현 계획서는 `docs/plans/`에 별도 작성한다.

## 1. 한 줄 요약

"같이 산 상품"이 아니라 **루틴에서 다음에 올 단계의 상품을, 지금 보고 있는 상품의 상태와
이어지게** 추천한다. 컨셉을 물려주는 추천이 아니라 **"다음 단계로 안전하게 넘겨주는 추천"** —
추천에는 항상 규칙에 저장된 이유 문장이 따라붙고, 최종 후보는 성분 궁합 게이트를 통과한다.

## 2. 확정된 결정 (브레인스토밍 결과)

| 결정 | 선택 | 근거 |
|---|---|---|
| 1차 적용 범위 | **PDP 하단 슬롯만** | 완성도 우선. /routine 폴백·장바구니 빈 단계 제안은 다음 웨이브로 이월 |
| edge_kind | **3종 유지** (`NEXT_STEP`·`PAIRED_REMOVAL`·`BUFFER`) | 완충은 다른 의미이고, 종류가 없으면 "선크림→애프터선"과 "선크림→클렌징오일"이 한 자리를 놓고 싸운다 |
| 시간대 축 | **제외** | 시간대 충돌(비타민C↔레티노이드)은 궁합 게이트가 이미 거른다. 규칙 수 절반 유지 |
| 규칙 커버리지 | **시드 상품이 덮는 조합만** 수작업 | 카테고리×태그 전량은 과함. 나머지는 폴백 사다리 |
| 태그 의존 | **product-tags 계획 선행** (`docs/plans/2026-07-25-product-tags.md`) | 메모가 전제한 tag/goods_tag는 미구현(V66까지만 존재). ingredient.category로 대체하면 궁합 엔진과 축이 겹쳐 역할 분리가 무너진다 |
| 계산 위치 | **서버 단일 엔드포인트** | 메모의 "클라이언트 규칙 배포" 근거였던 최근 본 상품 localStorage가 실재하지 않고, v1은 개인화 입력이 없으며 궁합 검사는 어차피 서버다 |

## 3. 소유 도메인과 테이블

새 패키지를 만들지 않고 **`routine` 도메인이 소유**한다. 루틴 순서에 대한 지식은 routine의
책임이고, 규칙 테이블 이름도 메모 그대로 `routine_flow_rule`.

```sql
-- V72 (V70/V71 product-tags 뒤 대역)
CREATE TABLE routine_flow_rule (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  from_category_code VARCHAR(12) NOT NULL,   -- 중분류(C001001). goods.category_code(leaf)는 접두사 매칭
  from_tag_slug      VARCHAR(40) NULL,       -- NULL = 태그 무관, 단계만 보고 전이
  to_category_code   VARCHAR(12) NOT NULL,   -- 중분류
  to_tag_slug        VARCHAR(40) NULL,       -- 추천 대상이 가져야 할 태그. NULL 허용
  edge_kind          VARCHAR(20) NOT NULL,   -- NEXT_STEP | PAIRED_REMOVAL | BUFFER
  reason             VARCHAR(200) NOT NULL,  -- 화면에 그대로 나가는 이유 문장. 유일한 출처
  priority           INT NOT NULL DEFAULT 0  -- 낮을수록 우선
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- `reason`이 이 기능의 값어치다 — 화면 문구는 이 컬럼이 유일한 출처이고, 코드·프론트에
  문구를 하드코딩하지 않는다. 규칙을 고치면 문구도 같이 바뀐다.
- **루틴 순서 매핑 테이블은 만들지 않는다.** 규칙 행이 from→to를 명시하므로 순서 지식은 규칙
  자체에 들어 있다(메모 미결 4번 해소). `category.sort_order`를 루틴 순서로 오독할 여지 제거.
- tag slug에 물리 FK를 걸지 않는다(패키지 경계 너머 물리 FK 금지 관례). 시드 무결성은
  검증 테스트로 잡는다.

## 4. API — 서버 단일 엔드포인트

```
GET /api/v1/goods/{goodsNo}/next-step        (routine 패키지 컨트롤러)
→ ApiResponse<NextStepResponse>

NextStepResponse(List<NextStepBlock> blocks)                              // 최대 2블록
NextStepBlock(String edgeKind, String reason, List<GoodsListItem> items)  // 블록당 최대 4개
```

- 기존 `SecurityConfig`의 `GET /api/v1/goods/**` permitAll에 자동 포함 — 설정 변경 없음.
- 블록 최대 2개인 이유: 선크림처럼 순방향(애프터선)과 대응관계(클렌징오일)가 공존하는
  케이스를 한 화면에 담는다. 블록 선정 규칙 — **순방향(`NEXT_STEP`·`BUFFER`) 통틀어 priority
  최상위 1개 + `PAIRED_REMOVAL` 최상위 1개.** BUFFER와 NEXT_STEP이 같은 상품에 동시에 매칭되면
  둘 다 보여주지 않고 priority가 낮은 쪽만 쓴다(BUFFER 규칙을 더 낮은 priority로 시드).
- `GoodsListItem`은 동결 계약이므로 그대로 싣고, `reason`·`edgeKind`는 블록 레벨에 둔다.

## 5. 서버 알고리즘 — 규칙 적용 → 폴백 → 궁합 게이트

1. **현재 상품 조회**: leaf 카테고리 코드 + 태그(catalog의 `GoodsTagRepository` 배치 계약을
   catalog 인터페이스 경유로 사용 — routine이 catalog 리포지토리를 직접 import하지 않는다).
2. **규칙 매칭**: `from_category_code` 접두사 일치 + (`from_tag_slug`가 NULL이거나 상품 태그에
   포함). priority 오름차순으로, 순방향(`NEXT_STEP`·`BUFFER`) 1개 + `PAIRED_REMOVAL` 1개 —
   최대 2블록(§4 블록 선정 규칙).
3. **후보 조회**: to_category 접두사 + to_tag 보유, HIDDEN 제외, 자기 자신 제외, viewCount desc.
   **폴백 사다리**: 4개 미만이면 태그 조건을 떼고 같은 to_category 인기순으로 채운다.
   0개면 블록 미노출(빈 슬롯을 화면에 내지 않는다).
   랭킹 스냅샷은 대분류(C001) 단위뿐이므로 쓰지 않는다 — 인기 = goods.viewCount 정렬.
4. **궁합 게이트**: 현재 상품 × 각 후보의 pairwise 판정에서 `CONFLICT`인 후보를 제거한다.
   compat 도메인에 배치 인터페이스를 신설해 경유한다(§7). 제거 후 재폴백은 하지 않는다
   (남은 후보만 노출; 0개면 블록 미노출).

## 6. 시드 규칙 (V73)

시드 상품이 실제로 덮는 (단계×태그) 조합만 수작업 작성 — 대략 8~12행.
메모 §4 예시 4행이 출발점이고, 계획서 작성 시 V12·V71 시드를 대조해 확장한다:

| from | from_tag | to | to_tag | kind | reason(예시) |
|---|---|---|---|---|---|
| C002001 클렌징폼 | soothe | C001001 토너 | moisture | NEXT_STEP | 순한 세정 뒤엔 수분 충전으로 |
| C001001 토너 | exfoliate | C001002 세럼 | soothe | BUFFER | 각질 케어 다음엔 진정으로 완충 |
| C001002 세럼 | moisture | C001003 크림 | moisture | NEXT_STEP | 수분을 덮어 가둘 차례 |
| C004001 선크림 | uv | C002002 클렌징오일/밤 | cleanse | PAIRED_REMOVAL | 자외선차단제는 오일로 지워야 남지 않아요 |

전이표는 궁합 규칙의 하위 호환이어야 한다 — "각질→각질" 같은 자극 중첩 계승 규칙은 쓰지
않는다(BUFFER로 전이). 최종 안전망은 어차피 §5-4 궁합 게이트가 잡는다.

## 7. 패키지 경계 (신설 인터페이스)

- **compat → routine**: `compat` 패키지에 배치 pairwise 인터페이스 신설.
  예: `CompatQueryService.worstVerdicts(Long baseGoodsNo, Collection<Long> candidates)
  → Map<Long, String>`(후보별 최악 verdict). 구현은 기존 `CompatService` 로직 재사용 —
  routine이 ingredient 리포지토리를 직접 만지지 않는다.
- **catalog → routine**: 태그 조회·후보 상품 조회는 catalog가 제공하는 인터페이스 경유
  (기존 `GoodsQueryService` 확장 또는 신설 — 계획서에서 시그니처 확정).
- routine은 자기 테이블(`routine_flow_rule`)만 직접 접근한다.

## 8. 프론트 (PDP)

- `Detail.tsx`의 `RecommendedSection` **위**에 `NextStepSection` 신설 — "함께 보면 좋은 상품"
  (같은 카테고리)보다 다른 단계로 넘겨주는 쪽이 먼저 온다.
- 블록마다 reason 문장 + 기존 `GoodsGrid` 재사용. 빈 blocks면 섹션 자체 미렌더
  (`RecommendedSection`의 null 반환 패턴 동일).
- DESIGN.md 토큰만 사용(액센트 배경 금지, 한글 적용 절 준수). queryKey
  `['goods-next-step', goodsNo]`, `api/goods.ts`에 `fetchNextStep`, MSW 핸들러·픽스처 추가.
- 화면 태스크이므로 **스크린샷 확인이 DoD**.

## 9. 검증 (DoD) — "정확도"를 주장하지 않는다

- **궁합 충돌 0건**: CONFLICT 조합이 나오는 시드(AHA 토너 등)로 next-step 응답에 CONFLICT
  후보가 없음을 단언하는 테스트.
- **규칙 커버리지**: 비HIDDEN 전 상품 중 next-step blocks가 비어 있지 않은 비율을 계산·출력하는
  테스트. 임계 미달은 실패가 아니라 경고(수치는 계획서에서 시드 대조 후 확정).
- 실 MySQL clean 로드(V70~V73) + `ddl-auto=validate` 통합 테스트, curl 스모크
  (H2 create-drop이 validate 불일치를 가리는 함정 회피).
- PDP 렌더 스크린샷.

## 10. 실행 구조

터미널 2개 **순차** (병렬 불가 — ②가 ①의 V70/V71·`TagView` 계약에 의존):

1. **product-tags** — 기존 계획서 `docs/plans/2026-07-25-product-tags.md` 그대로 실행.
2. **흐름 추천** — 이 설계의 구현 계획서(작성 예정, `docs/plans/`) 실행.

## 11. 범위 밖 (이월)

- `/routine` 단계 카드 2차 폴백, 장바구니 빈 단계 제안 (적용 지점 확장)
- 시간대(아침/저녁) 규칙 축, 클라이언트 규칙 JSON 배포 + 최근 본 상품 기반 개인화
- 관리자 규칙 CRUD (v1 규칙은 시드 전용)
