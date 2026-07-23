# Wave 1: 카탈로그·성분 + 프론트 디자인 기반 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development 또는 superpowers:executing-plans 로 태스크 단위 실행. 스텝은 체크박스로 추적한다.
> 실행 모델·모델 배분·공통 규칙은 `CLAUDE.md`, 웨이브 구조·공유 계약은 `docs/plans/2026-07-23-roadmap.md` 참조.
> **이 웨이브의 모든 서브에이전트는 sonnet** (모델 배분 예외 태스크 없음 — 결제·재고·궁합엔진은 Wave 2·3).

**Goal:** 상품을 "보여줄 수 있는" 상태를 만든다. 백엔드는 카테고리 트리·상품 목록/상세·성분 API를 시드 데이터로 응답하고, 프론트는 그 응답 형태(`GoodsListItem`)에 맞는 상품 카드·디자인시스템을 MSW mock 위에서 완성한다. 두 터미널은 설계 7장 `GoodsListItem` 계약 하나로만 연결되고 파일은 전혀 겹치지 않는다.

**Architecture:** T1은 `catalog`·`ingredient` 두 도메인 패키지를 추가한다 — 서로 다른 테이블을 소유하고, 상품 성분 조회는 `catalog`가 `ingredient.GoodsIngredientQueryService` 인터페이스를 경유한다(엔티티 직접 import 금지). T2는 백엔드를 전혀 켜지 않고 MSW mock만으로 개발한다 — 실 API 연동은 Wave 3 T2 몫이다.

**Tech Stack:** (Wave 0과 동일) Spring Boot 3.5 / JPA / Flyway / MySQL 8·H2(test) / React 19 + Vite 8 + TS + TanStack Query 5 + Zustand + MSW 2 + Vitest 4

## 터미널 분할

| 터미널 | 브랜치 | 범위 | 소유 파일 |
|---|---|---|---|
| **T1** | `feat/catalog` | catalog + ingredient + 시드 데이터 | `backend/**` |
| **T2** | `feat/front-base` | 디자인시스템 + 공통 컴포넌트 + Wave 0 이월 항목 | `frontend/**` |

두 터미널은 **파일 교집합이 없다.** 유일한 접점은 설계 문서 7장 `GoodsListItem` 형태이며, 그것은 이미 동결돼 있어 협의가 필요 없다. 머지 순서는 무관하다.

## Global Constraints (모든 태스크에 적용)

- **Flyway 대역: V10~V19만 사용** (T1 소유). 자기 대역 밖 번호 금지, V1~V9 수정 금지.
- **`common` 패키지는 동결.** 예외는 `ErrorCode`에 **도메인 접두사 상수 추가만** (승인 접두사는 설계 9장). 기존 상수 수정·삭제 금지, `ApiResponse`/`PageResponse`/`ErrorResponse`/`GlobalExceptionHandler`는 손대지 않는다.
- **`SecurityConfig`를 수정하지 않는다** (로드맵 §6 운용 규칙). 이 웨이브가 만드는 공개 경로 `GET /categories/tree`, `/goods`, `/goods/**`는 **이미 permitAll로 등록돼 있다.** 목록에 없는 공개 경로가 필요해지면 수정하지 말고 보고.
- **루트 빌드 설정 동결**: `backend/build.gradle.kts`, `frontend/package.json`, `vite.config.ts`는 의존성 **추가**만 허용하고 기존 설정 변경은 보고. (T2는 msw browser worker 때문에 `package.json` scripts 1줄을 건드리므로 Task 2-6에 명시)
- **프론트 시각 언어는 루트 `DESIGN.md`가 진실.** 토큰 이름을 직접 참조하고 hex를 손으로 옮겨 적지 않는다. 문서에 없는 색·간격·컴포넌트가 필요하면 임의로 만들지 말고 문서에 먼저 추가하고 보고. (T2 전용)
- 패키지 = 서비스 경계. `catalog`는 `ingredient` 엔티티/리포지토리를 직접 import하지 않는다.
- 페이징 응답은 전부 `PageResponse<T>` (설계 7장 공통 응답 계약). 도메인 페이징 타입 신설 금지.
- 단위/슬라이스 테스트는 H2(MySQL 모드, Flyway off, ddl-auto=create-drop). **Flyway 시드는 테스트에 존재하지 않으므로 테스트는 자기 픽스처를 직접 만든다** (아래 "시드와 테스트의 관계" 참조).
- 커밋 메시지·주석·문서 한국어, 태스크 단위 원자 커밋.

## 착수 전 확정 사항 (이 계획서에서 결정 — 서브에이전트는 그대로 따른다)

### 결정 1: `goodsNo`는 숫자 PK다 (설계 문서 `A#########`에서 이탈)

**이 항목은 설계 문서에 반영 완료됐다** — 설계 5장이 한때 `goods(goodsNo A#########)`라 적어 7장의 `Long goodsNo`와 어긋나 있었으나, 5장을 `BIGINT PK`로 고쳐 모순을 없앴다. 아래는 그 근거의 기록이다.

따라서 `goods.id BIGINT AUTO_INCREMENT`가 곧 `goodsNo`이고, URL은 `/api/v1/goods/123`이다. 올리브영식 `A000000123` 표시 코드 컬럼은 **만들지 않는다** — 아무도 읽지 않는 두 번째 식별자를 두면 조인·캐시·URL이 어느 쪽을 쓸지 매번 흔들린다.

### 결정 2: `popular`/`sales` 정렬은 `goods`의 비정규화 카운터로 지금 실동작시킨다

설계 5장의 `goods_daily_stat`·`ranking_snapshot`은 Wave 2 T1 소유다. 그렇다고 Wave 1에서 정렬 5종 중 2종을 빈 껍데기로 두면, 프론트가 "동작하지 않는 정렬"을 화면에 노출하게 된다. `goods`에 카탈로그가 소유하는 카운터 2개를 둔다.

- `view_count INT NOT NULL DEFAULT 0` → `sort=popular`
- `sales_count INT NOT NULL DEFAULT 0` → `sort=sales`

시드가 그럴듯한 값을 채우므로 Wave 1부터 5종 정렬이 전부 실동작한다. Wave 2 T1의 랭킹 스냅샷은 이 컬럼을 **대체하지 않고** 별도 집계로 공존한다(랭킹은 스냅샷만 읽는다 — 설계 5장). Wave 2 T2의 결제 완료가 `sales_count`를 올리는 것은 Wave 2 범위이고, 그때 `catalog`가 `GoodsStatCommandService` 인터페이스로 노출한다.

### 결정 3: ErrorCode 접두사는 `GOODS_`로 통일한다

설계 문서 9장이 승인한 접두사 8종에 `CATEGORY_`·`INGREDIENT_`는 없다. 새 접두사를 임의로 늘리면 9장이 진실이 아니게 되므로, 이 웨이브가 추가하는 상수는 전부 `GOODS_` 아래 둔다.

```java
GOODS_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다"),
GOODS_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다"),
GOODS_INVALID_SORT(HttpStatus.BAD_REQUEST, "지원하지 않는 정렬 조건입니다")
```

성분 조회는 상품 단위 엔드포인트라 `GOODS_NOT_FOUND`로 충분하다. 성분 전용 에러는 Wave 3 궁합 엔진이 `COMPAT_` 접두사로 갖는다.

### 결정 4: 시드와 테스트의 관계 — 시드는 Flyway, 테스트는 픽스처

시드 데이터는 `V12__seed_catalog.sql`로 넣는다(데이터도 스키마와 같은 진실 원천). 그런데 테스트 프로필은 Flyway가 꺼져 있고 H2 `create-drop`이므로 **테스트에는 시드가 존재하지 않는다.** 이걸 헷갈리면 "로컬에선 되는데 테스트에선 빈 목록"이 나온다.

- 테스트: 각 테스트가 자기 픽스처를 저장한다. 시드 개수(40개)에 의존하는 단언 금지.
- 시드 검증: 로컬 MySQL + curl로 Task 1-8(웨이브 마감)에서 눈으로 확인한다. **이것이 W1 DoD의 "시드 데이터로 응답"이다.**
- 시드를 수정해 다시 넣으려면 `docker compose down -v && docker compose up -d` (Flyway는 적용된 마이그레이션을 다시 실행하지 않는다). 로컬 MySQL 3306이 이미 점유돼 있으면 포트를 바꿔 임시 컨테이너로 검증한다(Wave 0에서 13306으로 우회한 전례).

---

# 터미널 T1 — `feat/catalog`

**Goal:** 카테고리 트리 / 상품 목록(필터·정렬 5종·페이지네이션) / 상품 상세 3종(기본·설명·추천) / 상품 성분 API가 시드 데이터로 응답한다.

**산출 디렉터리**

```
backend/src/main/java/com/beautyboy/
  catalog/
    Category.java  CategoryRepository.java  CategoryService.java  CategoryController.java
    Brand.java     BrandRepository.java
    Goods.java     GoodsRepository.java  GoodsQueryRepository.java
    GoodsOption.java
    Promotion.java PromotionGoods.java  PromotionRepository.java
    GoodsService.java  GoodsController.java
    GoodsSort.java
    dto/{CategoryTreeNode,GoodsListItem,GoodsSearchCondition,GoodsDetailResponse,
         GoodsOptionResponse,GoodsDescriptionResponse}.java
  ingredient/
    Ingredient.java  IngredientRepository.java
    GoodsIngredient.java  GoodsIngredientRepository.java
    IngredientRule.java   IngredientRuleRepository.java
    GoodsIngredientQueryService.java          # 인터페이스 (타 도메인 진입점)
    GoodsIngredientQueryServiceImpl.java
    IngredientController.java
    dto/{GoodsIngredientResponse,IngredientBadge}.java
  common/ErrorCode.java                        # 상수 추가만
backend/src/main/resources/db/migration/
  V10__catalog.sql  V11__ingredient.sql  V12__seed_catalog.sql
```

---

### Task 1-1: Flyway V10 — catalog 스키마

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__catalog.sql`

**Interfaces:**
- Produces: `brand`, `category`, `goods`, `goods_option`, `promotion`, `promotion_goods` 테이블. **이 DDL이 이후 전 웨이브의 카탈로그 스키마 진실이다.**

- [ ] **Step 1: DDL 작성**

```sql
-- V10__catalog.sql
CREATE TABLE brand (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(60) NOT NULL UNIQUE,
  logo_url VARCHAR(300) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 계층 인코딩: C001(대) → C001001(중) → C001001001(소). 부모코드가 접두사이므로
-- 하위 전체 조회는 code LIKE 'C001%' 하나로 끝난다(설계 2장 올리브영 실측 반영).
CREATE TABLE category (
  code VARCHAR(12) PRIMARY KEY,
  parent_code VARCHAR(12) NULL,
  name VARCHAR(60) NOT NULL,
  depth TINYINT NOT NULL,             -- 1|2|3
  sort_order INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_category_parent FOREIGN KEY (parent_code) REFERENCES category(code)
);

CREATE TABLE goods (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  brand_id BIGINT NOT NULL,
  category_code VARCHAR(12) NOT NULL,   -- 항상 leaf(depth=3)
  name VARCHAR(200) NOT NULL,
  summary VARCHAR(300) NULL,
  description TEXT NULL,                -- 지연 로딩 대상(상세 본문)
  thumbnail_url VARCHAR(300) NOT NULL,
  list_price INT NOT NULL,              -- 정가
  sale_price INT NOT NULL,              -- 판매가
  status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',   -- ON_SALE|SOLD_OUT|HIDDEN
  view_count INT NOT NULL DEFAULT 0,    -- sort=popular (결정 2)
  sales_count INT NOT NULL DEFAULT 0,   -- sort=sales   (결정 2)
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_goods_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
  CONSTRAINT fk_goods_category FOREIGN KEY (category_code) REFERENCES category(code)
);
-- 목록 조회는 항상 "카테고리 접두사 + 노출상태"로 좁힌 뒤 정렬한다
CREATE INDEX idx_goods_category_status ON goods(category_code, status);
CREATE INDEX idx_goods_brand ON goods(brand_id);

CREATE TABLE goods_option (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  goods_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,           -- "100ml", "민감성용"
  add_price INT NOT NULL DEFAULT 0,
  stock INT NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_option_goods FOREIGN KEY (goods_id) REFERENCES goods(id)
);
CREATE INDEX idx_option_goods ON goods_option(goods_id);

-- 배지(SALE|COUPON|GIFT|ONE_PLUS_ONE)는 저장하지 않고 이 조인에서 파생한다(설계 2장)
CREATE TABLE promotion (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  badge_type VARCHAR(20) NOT NULL,      -- SALE|COUPON|GIFT|ONE_PLUS_ONE
  starts_at DATETIME NOT NULL,
  ends_at DATETIME NOT NULL
);
CREATE TABLE promotion_goods (
  promotion_id BIGINT NOT NULL,
  goods_id BIGINT NOT NULL,
  PRIMARY KEY (promotion_id, goods_id),
  CONSTRAINT fk_pg_promotion FOREIGN KEY (promotion_id) REFERENCES promotion(id),
  CONSTRAINT fk_pg_goods FOREIGN KEY (goods_id) REFERENCES goods(id)
);
```

- [ ] **Step 2: 검증** — 로컬 MySQL 기동 후 `./gradlew bootRun --args='--spring.profiles.active=local'` → 로그에 마이그레이션 적용 확인, `flyway_schema_history`에 V10 success. 3306 점유 시 임시 컨테이너 포트로 우회하고 검증 후 정리한다.
- [ ] **Step 3: 커밋** — `git commit -m "feat(catalog): 카탈로그 스키마 V10"`

---

### Task 1-2: Flyway V11 — ingredient 스키마

**Files:**
- Create: `backend/src/main/resources/db/migration/V11__ingredient.sql`

**Interfaces:**
- Produces: `ingredient`, `goods_ingredient`, `ingredient_rule` 테이블. 규칙은 **성분 분류 쌍** 단위다(설계 8장 — 개별 성분 쌍으로 하면 규칙 수가 폭발한다).

- [ ] **Step 1: DDL 작성**

```sql
-- V11__ingredient.sql
CREATE TABLE ingredient (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL UNIQUE,
  category VARCHAR(40) NOT NULL,       -- RETINOID|AHA|BHA|VITAMIN_C|NIACINAMIDE|
                                       -- HYALURONIC|CERAMIDE|PEPTIDE|CENTELLA|
                                       -- SALICYLIC|FRAGRANCE|ALCOHOL|SPF_FILTER|OTHER
  irritation_level TINYINT NOT NULL,   -- 1(순함) ~ 5(자극 강함)
  comedogenic TINYINT NOT NULL,        -- 0 ~ 5 (모공 막힘 지수)
  summary VARCHAR(200) NOT NULL        -- 한줄설명(비전문가용)
);
CREATE INDEX idx_ingredient_category ON ingredient(category);

CREATE TABLE goods_ingredient (
  goods_id BIGINT NOT NULL,
  ingredient_id BIGINT NOT NULL,
  is_key TINYINT(1) NOT NULL DEFAULT 0,  -- 핵심 성분(상세 배지 노출 대상)
  sort_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (goods_id, ingredient_id),
  CONSTRAINT fk_gi_goods FOREIGN KEY (goods_id) REFERENCES goods(id),
  CONSTRAINT fk_gi_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredient(id)
);

-- 분류 쌍 규칙. (A,B)와 (B,A)를 둘 다 넣지 않기 위해 저장 시 사전순 정렬을 규약으로 한다
-- (category_a < category_b). 조회 측도 같은 규약으로 정규화해서 찾는다.
CREATE TABLE ingredient_rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_a VARCHAR(40) NOT NULL,
  category_b VARCHAR(40) NOT NULL,
  verdict VARCHAR(20) NOT NULL,        -- CONFLICT|CAUTION|SYNERGY
  reason VARCHAR(300) NOT NULL,
  CONSTRAINT uk_rule_pair UNIQUE (category_a, category_b)
);
```

- [ ] **Step 2: 검증** — Task 1-1과 동일하게 V11 적용 확인
- [ ] **Step 3: 커밋** — `git commit -m "feat(ingredient): 성분·궁합 스키마 V11"`

---

### Task 1-3: 카테고리 트리 API

**Files:**
- Create: `catalog/{Category,CategoryRepository,CategoryService,CategoryController}.java`, `catalog/dto/CategoryTreeNode.java`
- Modify: `common/ErrorCode.java` (`GOODS_CATEGORY_NOT_FOUND` 추가만)
- Test: `backend/src/test/java/com/beautyboy/catalog/CategoryServiceTest.java`

**Interfaces:**
- Produces: `GET /api/v1/categories/tree` → `ApiResponse<List<CategoryTreeNode>>` (depth 1 루트 배열, 각 노드가 children 보유)

```java
public record CategoryTreeNode(String code, String name, int depth, List<CategoryTreeNode> children) {}
```
- Produces: `CategoryService.findLeafPrefix(String code)` — 목록 API가 `LIKE code%` 조건에 쓸 접두사를 돌려주고, 없는 코드면 `GOODS_CATEGORY_NOT_FOUND`

- [ ] **Step 1: 실패 테스트** — 3계층 픽스처(C001 / C001001 / C001001001)를 저장하고
  - `tree()`가 루트 1개를 반환하고 그 아래 children이 2계층까지 중첩되는지
  - 형제 노드가 `sort_order` 오름차순인지
  - 없는 코드로 `findLeafPrefix("C999")` 시 `BusinessException(GOODS_CATEGORY_NOT_FOUND)`
  - **N+1 확인**: 전체 조회 1번으로 트리를 조립하는지 (`CategoryRepository.findAll()` 1회 후 메모리 조립). 카테고리는 수십 개라 재귀 쿼리보다 이쪽이 단순하고 빠르다.
- [ ] **Step 2: 실패 확인** → 컴파일 실패
- [ ] **Step 3: 구현** — `Category` 엔티티는 `parentCode`를 **연관관계가 아닌 문자열**로 둔다(트리 조립을 메모리에서 하므로 FK 객체 그래프가 필요 없고, LAZY 프록시로 인한 N+1을 원천 차단한다). 서비스가 `findAll()` → `Map<parentCode, List<Category>>` → 재귀 조립.
- [ ] **Step 4: 통과 확인** — `./gradlew test`
- [ ] **Step 5: 커밋** — `git commit -m "feat(catalog): 카테고리 트리 조회"`

---

### Task 1-4: 상품 목록 API (필터·정렬 5종·페이지네이션)

**Files:**
- Create: `catalog/{Brand,BrandRepository,Goods,GoodsOption,GoodsRepository,GoodsQueryRepository,GoodsSort,GoodsService,GoodsController}.java`, `catalog/{Promotion,PromotionGoods,PromotionRepository}.java`, `catalog/dto/{GoodsListItem,GoodsSearchCondition}.java`
- Modify: `common/ErrorCode.java` (`GOODS_INVALID_SORT` 추가만)
- Test: `backend/src/test/java/com/beautyboy/catalog/GoodsListApiTest.java`

**Interfaces:**
- Produces: `GET /api/v1/goods?categoryCode=&brandId=&minPrice=&maxPrice=&sort=&page=&size=`
  → `ApiResponse<PageResponse<GoodsListItem>>`
- `GoodsListItem`은 **설계 7장 동결 형태 그대로**다. 이 웨이브가 못 채우는 값은 기본값으로 낸다.

```java
public record GoodsListItem(
    Long goodsNo, String brandName, String name, String thumbnailUrl,
    int listPrice, int salePrice, int discountRate,
    List<String> badges,           // SALE|COUPON|GIFT|ONE_PLUS_ONE
    double rating,                 // Wave 2 T3까지 0.0
    int reviewCount,               // Wave 2 T3까지 0
    boolean wished,                // Wave 2 T3까지 false
    boolean todayDreamAvailable) {} // Wave 3까지 false
```

- 파라미터 규약: `sort` 기본 `popular`, `page` 기본 0(0-based), `size` 기본 20·최대 100. `brandId`는 다중 허용(`brandId=1&brandId=2`).
- `GoodsSort` enum: `POPULAR(view_count DESC)`, `NEW(created_at DESC)`, `SALES(sales_count DESC)`, `PRICE_ASC(sale_price ASC)`, `DISCOUNT(할인율 DESC)`. 정렬 키가 같은 값일 때 페이지 경계에서 상품이 중복/누락되지 않도록 **모든 정렬에 `id DESC`를 2차 키로 붙인다** (offset 페이징에서 이걸 빠뜨리면 2페이지에 1페이지 상품이 다시 나온다).

- [ ] **Step 1: 실패 테스트** (H2 + 직접 만든 픽스처. 시드에 의존하지 않는다)
  - 카테고리 접두사 필터: `C001001001`·`C001002001` 상품을 섞어 저장 → `categoryCode=C001001`이면 앞의 것만
  - 정렬 5종이 각각 기대 순서를 내는지 (`DISCOUNT`는 정가·판매가를 달리 준 3건으로 검증)
  - 페이지네이션: 25건 저장 → `size=10&page=1`에서 `content` 10건, `page=1`, `totalElements=25`, `totalPages=3`, `hasNext=true`
  - **동점 정렬 안정성**: `view_count`가 동일한 5건에서 page 0과 page 1의 `goodsNo`가 겹치지 않음
  - 가격 필터 경계: `minPrice`/`maxPrice`는 `sale_price` 기준 **이상/이하**(경계 포함)
  - 배지 파생: 기간이 유효한 프로모션이 걸린 상품만 `badges`에 해당 타입이 들어가고, 종료된 프로모션은 빠짐
  - `status='HIDDEN'` 상품은 목록에 안 나옴
  - 미래 필드 기본값: `rating=0.0`, `reviewCount=0`, `wished=false`, `todayDreamAvailable=false`
  - `sort=bogus` → 400 `GOODS_INVALID_SORT`
  - `size=1000` → 100으로 클램프(예외 아님)
  - **N+1 방지 단언**: 상품 20건 조회 시 브랜드명·배지가 상품당 추가 쿼리를 발생시키지 않을 것. Hibernate `Statistics`의 `getPrepareStatementCount()`로 상한(≤3)을 단언한다. 상품 카드는 전 화면에서 가장 많이 호출되는 쿼리라 여기서 N+1이 새면 나중에 어디서도 안 잡힌다.
- [ ] **Step 2: 실패 확인** → 컴파일 실패
- [ ] **Step 3: 구현**
  - `Goods` 엔티티: `brand`는 `@ManyToOne(fetch = LAZY)`, 옵션은 `@OneToMany(mappedBy="goods", cascade=ALL, orphanRemoval=true)`. `description`은 `@Basic(fetch = LAZY)`가 아니라 **별도 조회**로 분리(Task 1-5) — 목록 쿼리가 TEXT를 끌고 오지 않게 한다.
  - `GoodsQueryRepository`: 조건 조립은 JPQL + `EntityManager`로 직접 작성한다(QueryDSL 미도입 — 의존성·빌드 설정 추가는 이 웨이브의 동결 규칙에 걸리고, 조건이 5개뿐이라 값을 못 한다). 목록은 `join fetch g.brand`로 1쿼리, 배지는 조회된 `goodsId` 목록으로 **프로모션을 1쿼리 일괄 조회**해 메모리에서 매핑한다.
  - `count` 쿼리는 fetch join 없이 별도로.
  - `discountRate` = `list_price`가 0이면 0, 아니면 `(listPrice - salePrice) * 100 / listPrice` 정수 내림.
  - 컨트롤러: `@RequestParam` 바인딩 → `GoodsSearchCondition` record. `sort` 파싱 실패는 `BusinessException(GOODS_INVALID_SORT)`(Spring 기본 400 대신 우리 에러 포맷을 내기 위해 문자열로 받아 직접 변환한다).
- [ ] **Step 4: 통과 확인** — `./gradlew test`
- [ ] **Step 5: 커밋** — `git commit -m "feat(catalog): 상품 목록 조회(필터·정렬·페이지네이션)"`

---

### Task 1-5: 상품 상세 3종 (기본 / 설명 / 추천)

**Files:**
- Create: `catalog/dto/{GoodsDetailResponse,GoodsOptionResponse,GoodsDescriptionResponse}.java`
- Modify: `catalog/{GoodsService,GoodsController,GoodsRepository}.java`, `common/ErrorCode.java` (`GOODS_NOT_FOUND` 추가만)
- Test: `backend/src/test/java/com/beautyboy/catalog/GoodsDetailApiTest.java`

**Interfaces:**
- Produces (설계 2장 "PDP 지연 로딩" 패턴 그대로 3분할):
  - `GET /api/v1/goods/{goodsNo}` → 빠른 기본 정보
  - `GET /api/v1/goods/{goodsNo}/description` → 상세 본문(무거움, 지연 로딩)
  - `GET /api/v1/goods/{goodsNo}/recommended` → `List<GoodsListItem>` (같은 leaf 카테고리 내 `view_count DESC` 상위 8건, 자기 자신 제외)

```java
public record GoodsDetailResponse(
    Long goodsNo, String brandName, Long brandId, String name, String summary,
    String categoryCode, List<String> categoryPath,   // ["스킨케어","토너/스킨","토너"]
    String thumbnailUrl,
    int listPrice, int salePrice, int discountRate,
    List<String> badges, String status,
    List<GoodsOptionResponse> options,
    double rating, int reviewCount, boolean wished,    // 미래 웨이브가 채움
    boolean todayDreamAvailable) {}

public record GoodsOptionResponse(Long optionNo, String name, int addPrice, int stock, boolean soldOut) {}
public record GoodsDescriptionResponse(Long goodsNo, String description) {}
```

**상세 응답을 여기서 정의하는 근거**: 로드맵 §6이 "상품 상세 응답 형태는 Wave 1 T1이 계획서에서 정의한다"로 위임했다. 목록 아이템과 필드명을 의도적으로 일치시켜(`goodsNo`, `salePrice`, `badges`, `discountRate`) 프론트가 카드→상세 전환에서 매핑 코드를 새로 쓰지 않게 한다.

- [ ] **Step 1: 실패 테스트**
  - 존재하는 상품 → 200, 옵션이 `sort_order` 순, `stock=0`인 옵션은 `soldOut=true`
  - `categoryPath`가 depth 1→3 순서로 3개
  - 없는 goodsNo → 404 `GOODS_NOT_FOUND`
  - `status='HIDDEN'` 상품 상세 → 404 (목록에서 숨긴 걸 URL로는 볼 수 있으면 안 된다)
  - `/description`이 본문을 반환하고, **기본 상세 응답에는 description 필드가 아예 없음**(지연 로딩 분리 검증)
  - `/recommended`가 같은 카테고리만·자기 자신 제외·최대 8건
- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인** — 상세는 `join fetch brand`, 옵션은 별도 조회(컬렉션 fetch join 2개 이상은 카테시안 곱). `categoryPath`는 코드 접두사를 잘라(`C001`,`C001001`,`C001001001`) 한 번에 `IN` 조회.
- [ ] **Step 3: 커밋** — `git commit -m "feat(catalog): 상품 상세·설명·추천"`

---

### Task 1-6: 성분 도메인 + 상품 성분 API

**Files:**
- Create: `ingredient/{Ingredient,IngredientRepository,GoodsIngredient,GoodsIngredientRepository,IngredientRule,IngredientRuleRepository,GoodsIngredientQueryService,GoodsIngredientQueryServiceImpl,IngredientController}.java`, `ingredient/dto/{GoodsIngredientResponse,IngredientBadge}.java`
- Test: `backend/src/test/java/com/beautyboy/ingredient/GoodsIngredientApiTest.java`

**Interfaces:**
- Produces: `GET /api/v1/goods/{goodsNo}/ingredients` → `ApiResponse<GoodsIngredientResponse>`

```java
public record GoodsIngredientResponse(
    Long goodsNo,
    List<IngredientBadge> ingredients,   // is_key 우선, sort_order 순
    int maxIrritation,                   // 파생: 성분 중 최대 자극도 1~5
    int maxComedogenic) {}               // 파생: 최대 모공 지수 0~5

public record IngredientBadge(
    Long ingredientId, String name, String category,
    int irritationLevel, int comedogenic, String summary, boolean key) {}
```

- Produces (**타 도메인 진입점 — Wave 3 궁합 엔진이 이 인터페이스만 본다**):

```java
public interface GoodsIngredientQueryService {
    /** 상품별 성분 분류 집합. Wave 3 궁합 엔진이 분류 쌍 규칙을 적용할 입력. */
    Map<Long, Set<String>> findCategoriesByGoodsIds(Collection<Long> goodsIds);
    List<IngredientBadge> findBadges(Long goodsId);
}
```

- [ ] **Step 1: 실패 테스트**
  - 성분 3개 매핑된 상품 → `is_key` 성분이 앞에 오고, `maxIrritation`이 최댓값
  - 성분이 하나도 없는 상품 → 200 + 빈 배열 + `maxIrritation=0` (404 아님 — 성분 데이터가 없는 것은 정상 상태다)
  - 없는 goodsNo → 404 `GOODS_NOT_FOUND`
  - `findCategoriesByGoodsIds`가 상품 3개를 **1쿼리**로 묶어 반환(Wave 3 궁합 검사는 장바구니 전체를 한 번에 넘긴다 — 여기서 N+1이면 그쪽이 못 쓴다)
  - `ingredient_rule` 저장 시 `category_a < category_b` 규약 위반은 리포지토리 레벨에서 정규화되어 조회된다
- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인**
  - **경계 주의**: `ingredient` 패키지는 `catalog.Goods` 엔티티를 import하지 않는다. `goods_ingredient.goods_id`는 FK지만 JPA 연관 없이 `Long goodsId` 스칼라로 매핑한다. 상품 존재 여부 확인이 필요하면 컨트롤러가 아니라 `catalog` 쪽 엔드포인트가 책임진다 — 이 엔드포인트는 `catalog.GoodsController`가 아닌 `IngredientController`가 `/api/v1/goods/{goodsNo}/ingredients`를 잡되, 상품 존재 검증은 `catalog`가 제공하는 `GoodsQueryService.exists(Long)` 인터페이스를 경유한다. (이 인터페이스는 Task 1-5에서 `catalog`에 추가한다 — Task 1-6 서브에이전트는 `catalog` 파일을 수정하지 말고, 없으면 보고할 것.)
- [ ] **Step 3: 커밋** — `git commit -m "feat(ingredient): 성분 스키마·상품 성분 조회"`

> **오케스트레이터 주의**: Task 1-5 브리프에 `GoodsQueryService.exists(Long goodsNo)` 인터페이스 추가를 반드시 포함시킬 것. 이걸 빠뜨리면 Task 1-6이 파일 소유권 규칙과 실행 가능성 사이에서 막힌다.

---

### Task 1-7: 시드 데이터 (V12)

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__seed_catalog.sql`

**Interfaces:**
- Produces: 브랜드 12개 / 카테고리 3계층(대 6 · 중 18 · 소 36) / 상품 **40개**(옵션 포함) / 프로모션 4개 + 매핑 / 성분 30개 / 상품-성분 매핑 / 궁합 규칙 18개

**시드 규모의 근거**: W1 DoD는 "시드 데이터로 응답"이고 페이지네이션 검증에는 2페이지 이상이면 충분하다(size=20 → 40개면 2페이지). 로드맵의 상품 150개 확충은 Wave 4 몫이다. 지금 150개를 손으로 쓰면 Wave 2·3에서 스키마가 조금만 흔들려도 전부 다시 쓰게 된다.

- [ ] **Step 1: 카테고리 6개 대분류 작성** — 스킨케어(C001) / 클렌징(C002) / 헤어·바디(C003) / 선케어(C004) / 쉐이빙·그루밍(C005) / 메이크업(C006). 각 대분류 아래 중 3개, 각 중분류 아래 소 2개.
- [ ] **Step 2: 브랜드·상품** — 상품은 실제 브랜드·제품명을 쓰지 않는다(설계 11장 "저작권/크롤링 리스크 제거"). 자작 브랜드명 + 일반명사 조합. 각 상품에 `list_price`/`sale_price`를 다르게 줘 할인율이 0~40% 범위로 흩어지게 하고, `view_count`·`sales_count`도 서로 다른 분포로 채워 5종 정렬이 눈에 띄게 다른 결과를 내게 한다. 옵션은 상품당 1~3개, 일부는 `stock=0`(품절 표시 확인용).
- [ ] **Step 3: 프로모션 4종** — SALE / COUPON / GIFT / ONE_PLUS_ONE 각 1개, 기간은 넉넉히(`2026-01-01`~`2027-12-31`). 상품 일부에만 매핑해 배지 있는 카드와 없는 카드가 섞이게 한다. **종료된 프로모션 1개**를 넣어 기간 필터가 실제로 도는지 로컬에서 확인할 수 있게 한다.
- [ ] **Step 4: 성분 30개 + 규칙 18개** — 설계 8장의 예시를 포함할 것: (RETINOID, AHA)→CONFLICT "자극 중첩", (RETINOID, VITAMIN_C)→CAUTION "시간대 분리 권장", (NIACINAMIDE, RETINOID)→SYNERGY. 규칙 저장 시 `category_a < category_b` 사전순 규약 준수. 상품당 핵심 성분 3~6개 매핑.
- [ ] **Step 5: 로컬 검증** — 컨테이너 재기동(`docker compose down -v && up -d`) 후 마이그레이션 3개(V10·V11·V12) 전부 success 확인, `SELECT COUNT(*)`로 개수 확인
- [ ] **Step 6: 커밋** — `git commit -m "feat(catalog): 카탈로그·성분 시드 데이터 V12"`

---

### Task 1-8: T1 마감

- [ ] `cd backend && ./gradlew test` 전체 녹색 (Wave 0의 41개 + 이번 웨이브 추가분)
- [ ] **curl 시나리오** (로컬 MySQL + 시드 — 이것이 W1 DoD의 백엔드 절반이다). 각 명령의 실제 응답을 보고서에 붙일 것:
  - `GET /api/v1/categories/tree` → 대분류 6개, 3계층 중첩
  - `GET /api/v1/goods?size=20&page=0` / `page=1` → 20 + 20, `totalElements=40`, 두 페이지 `goodsNo` 교집합 없음
  - `GET /api/v1/goods?categoryCode=C001&sort=discount` → 스킨케어만, 할인율 내림차순
  - `GET /api/v1/goods?sort=priceAsc&minPrice=10000&maxPrice=30000` → 경계 포함 확인
  - `GET /api/v1/goods?sort=bogus` → 400 `GOODS_INVALID_SORT`
  - `GET /api/v1/goods/{존재}` / `{없음}` → 200 / 404 `GOODS_NOT_FOUND`
  - `GET /api/v1/goods/{n}/description`, `/recommended`, `/ingredients` → 각각 200
  - **무토큰으로 위 전부** → 401이 아니라 정상 응답 (설계 7장 공개 경로가 실제로 열려 있는지)
- [ ] **응답 봉투 확인** — 모든 성공 응답이 `{code:"OK", message:"성공", data:...}`이고 목록은 `data`가 `PageResponse` 형태인지 (T2가 이 형태로 mock을 만들었다)
- [ ] `git diff main --stat`으로 **`frontend/`·`common/`(ErrorCode 외)·`SecurityConfig` 변경이 0인지** 확인 — 변경이 있으면 규칙 위반이므로 보고
- [ ] 오케스트레이터에 보고 → 리뷰 통과 시 `feat/catalog`를 main에 머지

---

# 터미널 T2 — `feat/front-base`

**Goal:** 루트 `DESIGN.md`의 시각 언어를 코드로 이식하고, 그 위에서 상품 카드를 포함한 공통 컴포넌트를 MSW mock으로 완성한다. Wave 0이 남긴 화면 결함 9건도 함께 해소한다. 백엔드는 켜지 않는다.

> **이 터미널의 첫 번째 규칙: `DESIGN.md`가 시각 언어의 유일한 진실이다.**
> 색·간격·타이포·컴포넌트 사양을 스스로 정하지 않는다. 토큰 이름을 직접 참조하고 **hex를 손으로 옮겨 적지 않는다.**
> 문서에 없는 것이 필요하면 임의로 만들지 말고 `DESIGN.md`에 먼저 추가하고 보고한다.
>
> **Wave 0의 다크 차콜 + 황동 팔레트는 이 웨이브에서 폐기된다.** DESIGN.md는 흰 캔버스 + 검정 잉크의
> 밝은 편집디자인 시스템이다. 두 시스템 모두 `ink`라는 이름을 쓰는데 **뜻이 반대**다
> (Wave 0 `--ink`=어두운 배경 / DESIGN.md `ink`=검정 글자). Task 2-1이 이 함정을 먼저 처리한다.

**산출 디렉터리**

```
frontend/src/
  index.css                       # DESIGN.md 토큰 이식 (다크 → 라이트 전면 교체)
  components/layout/*.css         # Wave 0 CSS를 새 토큰으로 이관 (Task 2-1 Step 3)
  pages/{Auth,Home}.css           #  ″
  types/goods.ts                  # GoodsListItem 등 서버 계약 타입
  components/ui/{Button,Badge,Field,Skeleton,Rating,Price}.tsx (+ .css)
  components/goods/{GoodsCard,GoodsCardSkeleton,GoodsGrid}.tsx (+ .css)
  components/layout/{Layout,Header}.tsx    # Outlet 전환 + 로그아웃 UI
  mocks/{handlers.ts,browser.ts,fixtures/goods.ts}
  pages/dev/Showcase.tsx          # 컴포넌트 쇼케이스(스크린샷 대상)
  api/goods.ts                    # 조회 함수 (mock 대상)
  router.tsx  App.tsx  main.tsx
frontend/README.md                # Vite 템플릿 교체
```

> **CLAUDE.md 화면 검증 규칙**: 이 터미널의 Task 2-2·2-3·2-5·2-6·2-7은 **개발서버를 띄우고 담당 화면을 스크린샷으로 찍어 직접 본 뒤** 그 파일 경로를 보고서에 남겨야 완료다. 테스트 통과는 이 요구를 대체하지 못한다. Wave 0에서 유닛테스트 47개가 전부 녹색인 채로 히어로 제목 줄바꿈이 깨져 있었다.

---

### Task 2-1: DESIGN.md 토큰 이식 (다크 → 라이트 편집디자인 전면 교체)

**Files:**
- Modify: `frontend/src/index.css`, `frontend/src/components/layout/{Header,Footer,Layout}.css`, `frontend/src/pages/{Auth,Home}.css`, `frontend/src/components/signup/SkinProfileStep.css`
- Test: 없음(토큰만) — 검증은 Step 5 스크린샷과 Task 2-2·2-3

**선행 필독:** 루트 `DESIGN.md` 전체. 특히 front matter의 `colors`/`typography`/`rounded`/`spacing`,
"Semantic (뷰티보이 커머스 확장)", "한글 적용", "Do's and Don'ts".

**Interfaces:**
- Produces: `index.css`의 색 토큰을 **DESIGN.md 값으로 전면 교체**한다. Wave 0의 다크 차콜 + 황동 팔레트는 폐기된다.

> **가장 위험한 지점 — `--ink`의 의미가 뒤집힌다.**
> Wave 0: `--ink: #15181b` = 어두운 **배경색**. DESIGN.md: `ink: #030303` = 검정 **글자색**.
> 이름이 같고 뜻이 반대다. 기계적으로 값만 바꾸면 배경과 글자가 모두 검정이 되어 화면이 사라진다.
> **각 토큰이 배경인지 글자인지 확인하고 옮긴다.** 옮기고 나서 반드시 브라우저를 연다.

- CSS 변수 이름은 DESIGN.md 토큰 경로를 그대로 따른다: `--color-ink`, `--color-canvas`, `--color-hairline`,
  `--color-signal-sale`, `--space-lg`, `--rounded-full`, `--fs-display`, `--lh-body` …
  DESIGN.md에서 이름을 찾을 수 있어야 하고, **hex를 손으로 옮겨 적는 것 외에 값을 지어내지 않는다.**
- `:root`의 `color-scheme: dark` → `light`, 배경 `--color-canvas`(#ffffff), 글자 `--color-ink`(#030303)
- 폰트: DESIGN.md "한글 적용"의 스택 그대로. `abcNormal`은 독점 서체라 못 쓴다 — Inter + Pretendard.
- **전역 `word-break: keep-all` + `overflow-wrap: anywhere`** (Wave 0 히어로 줄바꿈 결함의 근본 원인)
- 한글 조정: 디스플레이·헤딩 `line-height` 1.15~1.25, 자간 `-0.02em` 상한, 본문 자간 0

- [ ] **Step 1: DESIGN.md 정독 후 토큰 매핑표 작성** — 어느 Wave 0 토큰이 어느 DESIGN.md 토큰으로 가는지, 배경/글자 역할을 명시한 표를 보고서에 남긴다. 이 표 없이 치환하면 위 함정에 빠진다.
- [ ] **Step 2: `index.css` 교체**
- [ ] **Step 3: Wave 0 CSS 5개 파일 갱신** — `Header.css` `Footer.css` `Layout.css` `Auth.css` `Home.css`가 삭제된 다크 토큰을 참조하고 있다. 전부 새 토큰으로 옮긴다. **이 단계를 건너뛰면 화면이 통째로 깨진다.**
  - `Auth.css`의 `.bb-auth__error` raw hex(`#e37b6a`) → `var(--color-signal-danger)` (Wave 0 이월 항목)
  - `Footer.css`는 DESIGN.md에서 **유일하게 다크로 남는 영역**(`footer: #030303` + 흰 글자)
- [ ] **Step 4: 대비 검증** — `signal-*` 5종과 `slate`/`stone`/`ash`가 `canvas`(#ffffff) 위에서 본문 4.5:1 이상인지 실제로 계산해 **수치를 보고서에 남긴다.** 미달하면 임의로 색을 바꾸지 말고 DESIGN.md 수정을 보고한다.
- [ ] **Step 5: 화면 확인 (필수)** — `npm run dev`로 `/`, `/login`을 열어 스크린샷. **이 시점의 목표는 "예쁨"이 아니라 "읽을 수 있음"이다** — 글자가 배경에 묻히지 않았는지, 사라진 요소가 없는지만 본다. 시각 완성은 2-2·2-3에서.
- [ ] **Step 6: 커밋** — `git commit -m "feat(front): DESIGN.md 디자인 토큰 이식(라이트 편집디자인 전환)"`

---

### Task 2-2: 레이아웃 구조 정리 + 인증 UX 이월 항목

**Files:**
- Modify: `frontend/src/router.tsx`, `frontend/src/components/layout/{Layout,Header}.tsx` (+ 각 `.css`), `frontend/src/App.tsx`, `frontend/src/stores/authStore.ts`, `frontend/src/api/auth.ts`
- Test: `frontend/src/components/layout/Header.test.tsx`, `frontend/src/App.test.tsx`

**Interfaces:**
- Produces: `Layout`이 `children` 대신 `<Outlet />`을 렌더하고, 라우터는 부모 라우트 1개 + `children` 배열 구조가 된다. 이후 웨이브가 라우트를 10개 넘게 추가해도 `Layout` 인스턴스는 1개다.
- Produces: 헤더 로그아웃 버튼 → `POST /api/v1/auth/logout` 후 `authStore.clear()` + `/`로 이동
- Produces: 앱 부트스트랩 시 `/auth/refresh` 1회 시도 → 성공하면 액세스 토큰·회원 정보 복원, 실패(401)면 조용히 로그아웃 상태로 진행. 복원 중에는 헤더의 로그인 영역만 스켈레톤 처리하고 **페이지 전체를 막지 않는다**(공개 화면이 대부분인 커머스에서 첫 렌더를 인증 왕복에 묶으면 비로그인 사용자 전원이 손해를 본다).

**이 3건을 한 태스크로 묶은 이유**: 셋 다 `App`/`router`/`Layout`/`Header`/`authStore`라는 같은 파일 집합을 건드린다. 나누면 서브에이전트 3개가 같은 파일을 연달아 고치게 된다.

- [ ] **Step 1: 실패 테스트**
  - 로그인 상태(`authStore`에 member 주입)에서 헤더에 "로그아웃" 버튼이 보이고, 클릭 시 msw가 `POST /auth/logout`을 받고 스토어가 비워지는지
  - 비로그인 상태에서는 "로그인" 링크가 보이고 로그아웃 버튼이 없는지
  - 부트스트랩: msw가 `/auth/refresh` 200(accessToken + member)을 주면 렌더 후 헤더에 닉네임이 나타나는지 / 401을 주면 "로그인" 링크가 유지되고 **콘솔 에러나 무한 재시도가 없는지**
  - **재귀 방지**: 부트스트랩 refresh가 실패해도 `client.ts`의 401 인터셉터가 다시 refresh를 부르지 않는지 (Wave 0 인터셉터에 in-flight promise 공유가 이미 있다 — 이걸 깨뜨리지 않았는지 확인하는 회귀 테스트)
- [ ] **Step 2: 실패 확인** → `npm test` FAIL
- [ ] **Step 3: 구현** — 라우터를 중첩 구조로 전환:

```tsx
export const router = createBrowserRouter([
  { path: '/', element: <Layout />, children: [
      { index: true, element: <Home /> },
      { path: 'login', element: <Login /> },
      { path: 'signup', element: <Signup /> },
  ]},
]);
```

- [ ] **Step 4: 통과 확인** — `npm test` PASS
- [ ] **Step 5: 화면 확인 (필수)** — `npm run dev` 후 `/`를 **1440px과 390px 두 폭에서** 스크린샷. 헤더에 로그인 링크가 보이는 상태와, 스토어에 임시로 member를 넣어 로그아웃 버튼이 보이는 상태를 각각 찍는다. 파일 경로를 보고서에 기재.
- [ ] **Step 6: 커밋** — `git commit -m "feat(front): Outlet 레이아웃 전환 + 로그아웃·세션 복원"`

---

### Task 2-3: 히어로·랜딩 시각 결함 해소

**Files:**
- Modify: `frontend/src/pages/{Home.tsx,Home.css}`
- Test: 없음 — **판정은 전적으로 스크린샷**

**Interfaces:**
**선행 필독:** `DESIGN.md`의 `hero-photo`, "Editorial Eyebrow + Display Lockup", "Whitespace Philosophy", "한글 적용".

- Produces: 랜딩을 DESIGN.md의 편집디자인 리듬으로 재구성하면서 Wave 0 이월 3건을 함께 해소
  1. 히어로 제목이 단어 중간에서 잘림("관리할 시 / 간") → Task 2-1의 전역 `keep-all`로 이미 해소됐어야 한다. **여기서는 실제로 해소됐는지 확인**하고, 남아 있으면 고친다.
  2. 1440px에서 히어로 우측 절반이 빔 → **DESIGN.md가 답을 이미 정해뒀다.** 히어로는 `{hero-photo}` 패턴 — `{colors.scrim}` 풀블리드 배경 + 왼쪽 정렬 lockup(eyebrow → display 헤드라인 → 서브카피 → `{button-primary-on-dark}`). 2단 분할이 아니므로 "우측 절반"이라는 문제 자체가 사라진다. 사진 자산이 없으므로 scrim 단색 또는 CSS 그라디언트/노이즈로 대체하고, 실사진 교체 지점을 주석으로 남긴다.
  3. "30ML / 80ML / 150ML / 200ML" 눈금 장식 → **제거한다.** DESIGN.md의 "장식 없음(no decorative ornament)" 원칙과 정면으로 충돌하고, 아무 데이터도 나타내지 않는다.
- Produces: 이하 섹션은 DESIGN.md의 "eyebrow → display → body" 3단 lockup과 `{spacing.section}`(64px)/`{spacing.section-lg}`(96px) 리듬을 따른다. 카드 그림자·컬러 배경 금지.

- [ ] **Step 1: 현재 상태 스크린샷** — 고치기 전 1440px·390px을 먼저 찍어 둔다(before/after 비교 없이는 "고쳤다"를 판정할 수 없다)
- [ ] **Step 2: 구현**
- [ ] **Step 3: 화면 확인 (필수)** — 1440 / 1024 / 768 / 390 네 폭에서 스크린샷. 확인 항목: 제목이 어절 단위로만 줄바꿈되는가 / 어느 폭에서도 가로 스크롤이 없는가 / 빈 영역이 없는가. 파일 경로 전부 보고서에 기재.
- [ ] **Step 4: 커밋** — `git commit -m "fix(front): 히어로 줄바꿈·빈 레이아웃·장식 정리"`

---

### Task 2-4: 서버 계약 타입 + UI 프리미티브

**Files:**
- Create: `frontend/src/types/goods.ts`, `frontend/src/components/ui/{Button,Badge,Field,Skeleton,Rating,Price}.tsx` (+ 각 `.css`)
- Modify: `frontend/src/pages/{Login,Signup}.tsx`, `frontend/src/components/signup/SkinProfileStep.tsx`, `frontend/src/pages/Auth.css` (프리미티브로 교체)
- Test: `frontend/src/components/ui/ui.test.tsx`

**Interfaces:**
- Produces: `types/goods.ts` — **설계 7장 동결 형태의 TS 미러**. 필드명·타입을 한 글자도 바꾸지 않는다.

```ts
export type BadgeType = 'SALE' | 'COUPON' | 'GIFT' | 'ONE_PLUS_ONE';

export interface GoodsListItem {
  goodsNo: number; brandName: string; name: string; thumbnailUrl: string;
  listPrice: number; salePrice: number; discountRate: number;
  badges: BadgeType[];
  rating: number; reviewCount: number; wished: boolean;
  todayDreamAvailable: boolean;
}

export interface PageResponse<T> {
  content: T[]; page: number; size: number;
  totalElements: number; totalPages: number; hasNext: boolean;
}
export interface ApiEnvelope<T> { code: string; message: string; data: T }
```

- Produces: 프리미티브 6종. **각 컴포넌트는 DESIGN.md의 대응 사양을 그대로 구현한다** — 아래 괄호가 그 대응이다.
  - `Button` (`{button-primary}` / `{button-primary-on-dark}` / `{button-ghost}` / `{button-text-link}`)
    — 검정 알약(`{rounded.full}`), 높이 40px(모바일 48px), `{typography.button}` 14px/600.
    **크기 변형 없음**(DESIGN.md: "no large/small distinction"). `loading`(스피너 + `aria-busy`), `disabled` 추가.
  - `Badge` (`{badge-sale}` / `{badge-neutral}` / `{badge-today-dream}`) — 흰 바탕 + 색 글자 + `{typography.micro-caps}`.
    **SALE만 색을 갖고** COUPON·GIFT·1+1은 graphite. 배경 채움 금지.
  - `Field` (`{form-field}` / `{form-field-focused}` / `{form-field-error}`) — **테두리 박스가 아니라 1px 밑줄**
    (`{colors.hairline-soft}`), 포커스 시 밑줄이 `{colors.ink}`로 진해짐. 라운딩 없음.
    **Wave 0 이월 "입력 필드가 배경과 명도차가 작다"는 이 밑줄형 사양으로 해소된다** — 흰 배경 위 진한 밑줄이라 대비가 확보된다.
  - `Skeleton` (`{media-thumbnail}`의 `{colors.surface-cool}` 플레이스홀더 규칙) — `prefers-reduced-motion`에서 애니메이션 정지
  - `Rating` — `{typography.meta}` / `{colors.ash}`. `reviewCount===0`이면 "리뷰 없음"
  - `Price` (`{price-discount-rate}` / `{price-sale}` / `{price-list-struck}`) — 할인율(signal-sale) → 판매가(ink) → 정가 취소선(ash) 순. `discountRate===0`이면 정가를 아예 렌더하지 않는다

- [ ] **Step 1: 실패 테스트**
  - `Button loading` 시 클릭 핸들러가 호출되지 않고 `aria-busy="true"`
  - `Field`가 label과 input을 `htmlFor`/`id`로 연결하고, 에러 시 `aria-invalid` + `aria-describedby`
  - `Price`: 할인 0%면 정가 노드 없음 / 할인 있으면 취소선 정가와 할인율 표시
  - `Rating`: `reviewCount=0` → "리뷰 없음", `rating=4.3` → 접근성 텍스트에 "4.3"
- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인**
- [ ] **Step 3: 기존 인증 화면 교체** — Login/Signup/SkinProfileStep의 수기 input·button을 `Field`·`Button`으로 교체. **Wave 0의 `Signup.test.tsx` 6개가 계속 통과해야 한다** — 깨지면 마크업이 아니라 테스트 셀렉터 문제인지 먼저 확인하고, 접근성 이름(label 텍스트)은 보존한다.
- [ ] **Step 4: 커밋** — `git commit -m "feat(front): UI 프리미티브 + 서버 계약 타입"`

---

### Task 2-5: 상품 카드 + 그리드

**Files:**
- Create: `frontend/src/components/goods/{GoodsCard,GoodsCardSkeleton,GoodsGrid}.tsx` (+ `.css`)
- Test: `frontend/src/components/goods/GoodsCard.test.tsx`

**Interfaces:**
**선행 필독:** `DESIGN.md`의 "커머스 컴포넌트 (뷰티보이)" 절 — `goods-card` 사양이 거기 전부 적혀 있다. 이 태스크는 그 사양의 구현이지 새 디자인이 아니다.

- Produces: `GoodsCard({ item, onWishToggle })` — 설계 6장의 "상품 카드 컴포넌트 단일화(배지·찜 포함) 후 전 화면 재사용". 목록·검색·랭킹·추천·루틴이 전부 이 컴포넌트를 쓴다. DESIGN.md `{goods-card}` 사양 요약:
  - **테두리·그림자·라운딩 없음.** 카드를 구분하는 것은 여백뿐이다(플로팅 카드 금지)
  - 스택: 썸네일(1:1, `{rounded.md}`, `object-fit: cover`, `loading="lazy"`) → **배지 줄(썸네일 위에 겹치지 않고 아래)** → 브랜드명(`{typography.meta}`/`{colors.slate}`) → 상품명(`{typography.body-tight}`/`{colors.ink}`, 2줄 고정 말줄임) → `Price` → `Rating`
  - 카드 전체가 `/goods/{goodsNo}` 링크, **찜 버튼은 링크 안의 중첩 버튼이 아니라 형제로 배치**해 클릭이 링크로 새지 않게 한다. 썸네일 우상단, `aria-pressed`로 상태 표현
  - `rating=0 && reviewCount=0`(Wave 1 백엔드 기본값)일 때도 레이아웃이 무너지지 않아야 한다 — Wave 2 T3에서 값이 채워질 때 카드 높이가 흔들리면 그리드 전체가 다시 흐르므로 별점 영역은 **값이 없어도 자리를 유지**한다
  - 품절: 썸네일 `opacity: 0.45` + "품절" 라벨. **색만으로 알리지 않는다**
- Produces: `GoodsGrid({ items, loading, skeletonCount })` — DESIGN.md 사양대로 1440px 5열 / 1024px 4열 / 768px 3열 / 640px 2열, 열 간격 `{spacing.lg}` 행 간격 `{spacing.xl}`, 구분선 없음. `loading`이면 스켈레톤 카드

- [ ] **Step 1: 실패 테스트**
  - 배지 4종이 모두 있는 아이템 → 배지 4개 렌더
  - `discountRate=0` → 정가 취소선 없음
  - `wished=true` → 하트가 눌린 상태(`aria-pressed="true"`), 클릭 시 `onWishToggle(goodsNo)` 호출
  - 찜 버튼 클릭이 링크 네비게이션을 트리거하지 않음
  - `rating=0, reviewCount=0` → "리뷰 없음"이 뜨고 카드가 렌더됨(빈 화면 아님)
  - 상품명이 아주 길어도 카드 높이가 다른 카드와 동일(2줄 고정)
- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인**
- [ ] **Step 3: 화면 확인 (필수)** — 쇼케이스(Task 2-6)가 아직 없다면 임시 라우트로라도 카드 12개를 렌더해 1440·390 스크린샷. 확인 항목: 배지가 썸네일을 가리지 않는가 / 이름 2줄 말줄임이 실제로 동작하는가 / 카드 높이가 전부 같은가 / 이미지 로드 실패 시(고의로 깨진 URL 1개) 깨진 아이콘이 아니라 플레이스홀더가 보이는가.
- [ ] **Step 4: 커밋** — `git commit -m "feat(front): 상품 카드·그리드 컴포넌트"`

---

### Task 2-6: MSW mock + 조회 훅 + 쇼케이스 화면

**Files:**
- Create: `frontend/src/mocks/{handlers.ts,browser.ts}`, `frontend/src/mocks/fixtures/goods.ts`, `frontend/src/api/goods.ts`, `frontend/src/pages/dev/Showcase.tsx` (+ `.css`)
- Modify: `frontend/src/mocks/server.ts`(공통 핸들러 등록), `frontend/src/main.tsx`(dev에서 worker 시작), `frontend/src/router.tsx`(쇼케이스 라우트), `frontend/package.json`(msw worker 스크립트 1줄 — **동결 규칙 예외로 이 태스크에만 허용**)
- Test: `frontend/src/api/goods.test.ts`

**Interfaces:**
- Produces: `handlers.ts`가 T1의 실제 응답 형태를 그대로 흉내 낸다 — `GET /api/v1/goods`(정렬·페이지네이션·카테고리 필터 실동작), `GET /api/v1/goods/:goodsNo`, `GET /api/v1/categories/tree`. **응답은 항상 `{code,message,data}` 봉투 + 목록은 `PageResponse`.**
- Produces: `api/goods.ts` — `fetchGoodsList(params)`, `fetchGoodsDetail(goodsNo)`. Wave 0의 `client.ts` axios 인스턴스를 재사용하고, `ApiEnvelope`를 벗겨 `data`만 반환한다.
- Produces: `/dev/components` 쇼케이스 — 토큰 팔레트, 프리미티브 6종의 모든 상태, 상품 카드 그리드(mock 40건), 스켈레톤, 빈 상태. **이 화면이 이 터미널의 스크린샷 판정 대상이다.**

**dev 모드에서 worker를 켜는 이유**: 컴포넌트 테스트만으로는 CLAUDE.md의 "렌더 결과를 눈으로 확인" 요구를 못 채운다. 백엔드 없이 브라우저에서 실제 데이터가 흐르는 화면을 봐야 한다. 워커는 `import.meta.env.DEV && VITE_USE_MOCK === 'true'`일 때만 시작해, Wave 3에서 실 API로 붙일 때 환경변수 하나로 끈다.

- [ ] **Step 1: 픽스처 작성** — 상품 40건. **T1 시드와 성격을 맞춘다**: 할인율 0~40% 분포, 배지 있는 것과 없는 것 혼재, 일부는 `todayDreamAvailable=true`(Wave 3 대비 렌더 확인용), 전부 `rating=0/reviewCount=0`(Wave 1 백엔드가 실제로 내는 값). 썸네일은 외부 URL이 아니라 **로컬 SVG/데이터 URI**로 만든다 — 외부 이미지에 의존하면 오프라인·CI에서 화면이 통째로 깨진다.
- [ ] **Step 2: 실패 테스트** — msw 핸들러 대상으로
  - `fetchGoodsList({page:1,size:20})`가 21~40번째를 반환하고 `hasNext=false`
  - `sort=priceAsc`가 실제로 가격순
  - `categoryCode=C001`이 접두사 필터로 동작
  - 404 응답 시 에러가 그대로 던져지는지(TanStack Query가 잡을 수 있게)
- [ ] **Step 3: 실패 확인 → 구현 → 통과 확인**
- [ ] **Step 4: 화면 확인 (필수)** — `VITE_USE_MOCK=true npm run dev` → `/dev/components` 스크린샷 (1440·390). 확인 항목: 팔레트가 **DESIGN.md front matter 값과 실제로 일치하는가**(쇼케이스에 토큰명과 hex를 함께 출력해 문서와 대조할 수 있게 한다) / 프리미티브 상태(로딩·비활성·에러)가 전부 구분되는가 / 카드 그리드가 열 수 대로 접히는가 / 스켈레톤이 실제 카드와 같은 크기인가 / **시그널 색이 배경으로 칠해진 곳이 없는가**.
- [ ] **Step 5: 커밋** — `git commit -m "feat(front): MSW mock + 상품 조회 훅 + 컴포넌트 쇼케이스"`

---

### Task 2-7: T2 마감

- [ ] `cd frontend && npm test` 전체 녹색 (Wave 0의 6개 포함 — **기존 테스트가 깨졌다면 마감 불가**)
- [ ] `npm run build` 성공 (TS 타입 에러 0)
- [ ] `npm run lint` 통과
- [ ] `frontend/README.md` 교체 — Vite 템플릿 문구 삭제, 실행법(`npm run dev`, `VITE_USE_MOCK=true`로 mock 모드, `npm test`), 디렉터리 구조, 디자인 토큰 위치, 상품 카드 사용법 (Wave 0 이월 항목)
- [ ] **최종 화면 점검 (필수)** — `/`, `/login`, `/signup`, `/dev/components`를 1440px·390px에서 스크린샷하고 **직접 열어본 뒤** 아래를 판정:
  - 어느 화면에서도 가로 스크롤이 없는가
  - 한글 줄바꿈이 어절을 쪼개지 않는가 / 한글 제목의 위아래가 잘리지 않는가(행간 1.0 금지)
  - 입력 필드가 배경과 명확히 구분되는가 (이월 항목 — 밑줄형 사양)
  - 빈 영역·맥락 없는 장식이 남아 있지 않은가
  - **Wave 0의 다크 잔재가 남은 화면이 없는가** — 차콜 배경이나 황동색이 보이면 Task 2-1 Step 3 누락
  - 스크린샷 파일 경로 전부를 보고서에 기재
- [ ] **DESIGN.md 준수 확인** — 아래를 하나씩 대조하고 결과를 보고서에 적는다:
  - `grep -rE '#[0-9a-fA-F]{3,8}' frontend/src --include=*.css`로 **CSS에 남은 raw hex가 `index.css`의 토큰 정의부 외에 없는지**. 있으면 토큰으로 교체하거나, 문서에 없는 값이면 보고
  - 시그널 색이 `background`로 쓰인 곳이 없는지 (글자·아이콘·1px 테두리만 허용)
  - 버튼이 전부 `{rounded.full}` 알약이고 그리드·폼은 `{rounded.none}`인지 (둘을 섞지 않는다)
  - `box-shadow`가 없는지 (DESIGN.md는 그림자를 전면 배제한다)
  - 대문자(`text-transform: uppercase`)가 eyebrow·micro-caps 두 역할에만 쓰였는지, 한글에 걸리지 않았는지
- [ ] **Wave 0 이월 9건 체크리스트 확인** — 히어로 줄바꿈 / 1440 빈 영역 / 입력 필드 대비 / 눈금 장식 / `.bb-auth__error` 토큰화 / `Outlet` 전환 / 헤더 로그아웃 / 부트스트랩 refresh / `frontend/README.md`. **9건 각각에 대해 해소 여부와 위치를 보고서에 적는다.**
- [ ] `git diff main --stat`으로 **`backend/` 변경이 0인지** 확인
- [ ] 오케스트레이터에 보고 → 리뷰 통과 시 `feat/front-base`를 main에 머지

---

## 웨이브 마감 (오케스트레이터, 두 브랜치 머지 후)

- [ ] `git merge feat/catalog` → `git merge feat/front-base` (충돌 없어야 정상 — 있으면 파일 소유권 위반이므로 원인부터 규명)
- [ ] main에서 `./gradlew test` + `npm test` 전부 녹색
- [ ] **W1 DoD 확인**: 카테고리 트리·상품 목록/상세·성분 API가 시드 데이터로 응답 + 프론트 상품카드가 MSW mock으로 렌더 (curl 출력 + 스크린샷 둘 다 확인)
- [ ] **계약 정합성 대조** — T1의 실제 `GET /goods` 응답 JSON과 T2의 `types/goods.ts`·mock 핸들러 응답을 **나란히 놓고 필드명·타입을 대조**한다. 두 터미널이 이 계약을 각자 해석했을 수 있고, 어긋난 채 Wave 3에서 실 API를 붙이면 그때 프론트를 다시 쓰게 된다. 어긋난 곳이 있으면 **T1의 실제 응답이 진실**이고 프론트를 맞춘다(CLAUDE.md: 스키마·픽스처가 진실).
- [ ] `docs/plans/2026-07-23-roadmap.md`의 "Wave 0에서 이월된 항목 → Wave 1 T2" 절을 해소 완료로 갱신하고, Wave 2로 넘길 이월 항목을 새로 적는다
- [ ] worktree 정리 — **루트에서** 실행한다. 각 세션은 `EnterWorktree`에 `path`로 진입했으므로 `ExitWorktree`가 worktree를 지우지 않는다(그 경로로 진입한 것은 세션이 만든 것으로 취급되지 않는다). 머지가 끝난 뒤 사람이 정리한다:

```bash
git worktree remove ../BeautyBoy-w1-catalog
git worktree remove ../BeautyBoy-w1-front
git worktree list          # 루트만 남아야 한다
```

---

## 터미널 실행 프롬프트

**운용 방식:** 사람은 **터미널 2개를 프로젝트 루트에서 열고 프롬프트를 붙여넣기만 한다.**
worktree 생성·진입·기점 검증은 각 세션의 오케스트레이터가 프롬프트 지시에 따라 직접 수행한다.
git 명령을 손으로 치지 않으므로 터미널마다 브랜치를 즉흥으로 만들 여지가 없다.

### 0) 사전 조건 (루트에서 한 번만 확인)

- 계획서가 **커밋돼 있어야 한다.** untracked 파일은 새 worktree에 딸려가지 않아 세션이 계획서를 못 읽는다.
  `DESIGN.md`도 마찬가지다 (T2가 이 문서 없이는 아무것도 못 한다).
- 두 터미널 모두 **같은 커밋을 기점으로** 브랜치를 딴다. 아래 프롬프트가 `git worktree add`를
  로컬 HEAD 기준으로 실행하므로, 두 터미널을 열기 전에 루트의 `main`을 더 이상 진행시키지 않는다.

```bash
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" log --oneline -1   # 이 커밋이 두 브랜치의 기점이 된다
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" status --short     # 비어 있어야 한다
```

### 1) 터미널 A (T1 — 카탈로그)

**프로젝트 루트에서** Claude Code를 열고 아래를 그대로 붙여넣는다. worktree는 세션이 만든다.

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.

  git worktree add ../BeautyBoy-w1-catalog -b feat/catalog

를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
(EnterWorktree를 name으로 새로 만들지 마라 — 그러면 브랜치명과 경로를 계획서대로 못 정하고,
기본 설정이 로컬 HEAD가 아닌 origin/main에서 브랜치를 딴다. 지금은 둘이 같지만, 루트에
푸시 안 된 커밋이 생기는 순간 계획서도 DESIGN.md도 없는 워크트리가 만들어진다.)

진입한 뒤 아래를 확인하고, 하나라도 어긋나면 **중단하고 나에게 보고해라**:
  - pwd가 BeautyBoy-w1-catalog인지
  - git log --oneline -1 이 루트에서 본 것과 같은 커밋인지 (브랜치 기점 확인)
  - docs/plans/2026-07-23-wave1-catalog-frontbase.md 가 존재하는지
  - git status가 깨끗한지

[2단계 — 실행]
CLAUDE.md와 docs/plans/2026-07-23-wave1-catalog-frontbase.md를 읽고, 그중 "터미널 T1 — feat/catalog"
섹션의 Task 1-1 ~ 1-8을 순서대로 실행해줘. T2(feat/front-base) 섹션은 다른 터미널 담당이니 건드리지 마.

너는 오케스트레이터다: 태스크마다 서브에이전트(model: sonnet)를 스폰해 TDD로 구현시키고,
태스크 사이마다 (1) 테스트 통과 (2) Files 목록 준수 (3) 계획서의 Interfaces 계약 일치를 리뷰한 뒤
다음으로 넘어가. 특히 아래를 매 리뷰에서 확인해:

- Flyway는 V10~V19 대역만 (V1~V9 수정 금지)
- common/ErrorCode.java는 GOODS_ 접두사 상수 "추가"만. common의 다른 파일과 SecurityConfig는 수정 금지
- catalog가 ingredient 엔티티/리포지토리를 직접 import하지 않는지 (서비스 인터페이스 경유)
- frontend/ 아래 파일 변경이 0인지

Task 1-5 서브에이전트 브리프에는 GoodsQueryService.exists(Long) 인터페이스 추가를 반드시 포함시켜라
(Task 1-6이 이걸 필요로 하는데 catalog 파일을 수정할 수 없다).

전 태스크 완료 후 ./gradlew test 결과와 Task 1-8의 curl 시나리오 출력을 전부 보고해.
```

### 2) 터미널 B (T2 — 프론트 기반)

**프로젝트 루트에서** Claude Code를 열고 아래를 그대로 붙여넣는다. worktree는 세션이 만든다.

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.

  git worktree add ../BeautyBoy-w1-front -b feat/front-base

를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
(EnterWorktree를 name으로 새로 만들지 마라 — 그러면 브랜치명과 경로를 계획서대로 못 정하고,
기본 설정이 로컬 HEAD가 아닌 origin/main에서 브랜치를 딴다. 지금은 둘이 같지만, 루트에
푸시 안 된 커밋이 생기는 순간 계획서도 DESIGN.md도 없는 워크트리가 만들어진다.)

진입한 뒤 아래를 확인하고, 하나라도 어긋나면 **중단하고 나에게 보고해라**:
  - pwd가 BeautyBoy-w1-front인지
  - git log --oneline -1 이 루트에서 본 것과 같은 커밋인지 (브랜치 기점 확인)
  - DESIGN.md 와 docs/plans/2026-07-23-wave1-catalog-frontbase.md 가 존재하는지
    (DESIGN.md가 없으면 이 터미널은 아무것도 시작할 수 없다)
  - git status가 깨끗한지

[2단계 — 실행]
CLAUDE.md, 루트 DESIGN.md, docs/plans/2026-07-23-wave1-catalog-frontbase.md를 읽고, 그중
"터미널 T2 — feat/front-base" 섹션의 Task 2-1 ~ 2-7을 순서대로 실행해줘.
T1(feat/catalog) 섹션은 다른 터미널 담당이니 건드리지 마.
백엔드는 이 터미널에서 켜지 않는다 — 모든 데이터는 MSW mock이다.

DESIGN.md가 시각 언어의 유일한 진실이다. 모든 구현 서브에이전트 브리프에 "CSS를 쓰기 전에
DESIGN.md를 먼저 읽고 토큰 이름을 직접 참조할 것, hex를 손으로 옮겨 적지 말 것"을 포함시켜라.
Wave 0의 다크 차콜+황동 팔레트는 이 웨이브에서 폐기되고 DESIGN.md의 라이트 편집디자인으로 교체된다.
두 시스템이 'ink'라는 이름을 정반대 뜻으로 쓰므로(Wave 0=어두운 배경 / DESIGN.md=검정 글자)
Task 2-1의 매핑표를 반드시 리뷰하고 넘어가라.

너는 오케스트레이터다: 태스크마다 서브에이전트(model: sonnet)를 스폰해 TDD로 구현시키고,
태스크 사이마다 (1) 테스트 통과 (2) Files 목록 준수 (3) 계획서의 Interfaces 계약 일치를 리뷰해.

가장 중요한 규칙 — CLAUDE.md의 화면 검증:
Task 2-2, 2-3, 2-5, 2-6, 2-7은 구현 서브에이전트가 개발서버를 띄우고 담당 화면을 스크린샷으로 찍어
"직접 본 뒤" 그 파일 경로를 보고서에 남겨야 완료다. 너도 그 스크린샷을 열어보고 판정해라.
테스트 통과는 이 요구를 대체하지 못한다. Wave 0에서 유닛테스트 47개가 전부 녹색인 채로
히어로 제목 줄바꿈이 깨져 있었고 사람이 브라우저를 열고 나서야 발견됐다.

그 밖에 매 리뷰에서 확인할 것:
- CSS에 raw hex가 새로 생기지 않았는지 (index.css의 토큰 정의부만 예외)
- 시그널 색을 배경으로 칠한 곳이 없는지, box-shadow가 없는지
- types/goods.ts가 계획서의 GoodsListItem 형태(설계 7장 동결)와 한 글자도 다르지 않은지
- Wave 0의 기존 테스트 6개가 계속 통과하는지
- backend/ 아래 파일 변경이 0인지
- package.json 수정은 Task 2-6의 msw 스크립트 1줄만 (그 외는 보고)
- DESIGN.md에 없는 색·간격·컴포넌트를 지어내지 않았는지 (필요하면 문서 수정을 나에게 보고)

전 태스크 완료 후 npm test / npm run build / npm run lint 결과와 스크린샷 경로 전부,
그리고 Wave 0 이월 9건 체크리스트를 보고해.
```

---

## Self-Review 결과

- **스펙 커버리지**: 로드맵 Wave 1 정의("T1: catalog + ingredient + 시드 데이터", "T2: 프론트 디자인시스템 + 공통 컴포넌트, MSW mock 기반") 대비 누락 없음. W1 DoD 두 항목이 각각 Task 1-8 curl 시나리오와 Task 2-6·2-7 스크린샷으로 검증된다.
- **이월 항목 반영**: 로드맵이 Wave 1 T2로 지정한 9건을 전부 태스크에 배치했다 — 히어로 줄바꿈(2-1 전역 `keep-all` → 2-3 확인)·빈 레이아웃·눈금(2-3), 입력 필드 대비(2-4 밑줄형 사양), `.bb-auth__error` 토큰화(2-1), `Outlet` 전환·로그아웃·부트스트랩 refresh(2-2), `frontend/README.md`(2-7). Task 2-7이 9건 전부를 체크리스트로 재확인한다.
- **DESIGN.md 반영**: 시각 결정을 계획서가 아니라 `DESIGN.md`에 두고 태스크는 그것을 참조만 하게 했다. 계획서에 색·간격을 복사해 두면 문서와 계획서가 갈라지고, 그때 서브에이전트는 어느 쪽을 따를지 또 판단해야 한다. 커머스에 없던 것(시그널 색 5종, `goods-card`, 한글 적용)은 계획서가 아니라 **`DESIGN.md`에 절을 추가**해 해결했다.
- **착수 전 결정 3건 반영**: 셋 다 설계 문서 7장으로 이관됐고, `PageResponse`는 Task 1-4 목록 응답에, `SecurityConfig` 선반영은 "수정 금지 + Task 1-8 무토큰 curl 검증"으로, `GoodsListItem`은 T1 DTO와 T2 `types/goods.ts` 양쪽에 동일 형태로 못 박았다.
- **병렬 안전**: 두 터미널의 Files 교집합 0(`backend/**` vs `frontend/**`). Flyway는 T1 단독 소유 대역. `common`은 `ErrorCode` 상수 추가 3개만이며 T2는 백엔드를 건드리지 않으므로 충돌 불가.
- **계획서가 해소한 모호함 4건**: `goodsNo` 타입(설계 문서 vs 동결 DTO 충돌) / `popular`·`sales` 정렬의 데이터 출처 / `CATEGORY_`·`INGREDIENT_` 접두사 부재 / Flyway 시드와 H2 테스트의 관계 — 넷 다 서브에이전트가 즉흥 판단하면 웨이브 간 재작업을 부르는 지점이라 결정 절에 근거와 함께 못 박았다.
- **플레이스홀더 없음.**

## 이 웨이브에서 Wave 2로 넘기는 것 (마감 시 로드맵에 반영)

- `rating`/`reviewCount`/`wished` 실값 채우기 → Wave 2 T3 (review·wishlist)
- `sales_count` 증가 훅(`GoodsStatCommandService`) → Wave 2 T2 (결제 완료 시점)
- 로드맵의 기존 "Wave 2 착수 전" 2건(테스트 `@Transactional` flush/clear 규약, Flyway DDL Testcontainers 스모크)은 **이 웨이브에서 다루지 않는다** — 로드맵이 Wave 2 착수 전으로 지정했고, 재고 차감·결제가 실제로 그 위험에 노출되는 지점이기 때문이다.
