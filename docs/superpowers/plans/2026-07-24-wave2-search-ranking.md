# Wave 2 T1 — 검색(search) + 랭킹(ranking) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: `superpowers:subagent-driven-development`(권장) 또는
> `superpowers:executing-plans`로 태스크 단위 실행. 스텝은 체크박스(`- [ ]`)로 추적한다.

**Goal:** 상품명·브랜드명 검색(자동완성·인기검색어 포함)과 일별 통계 기반 랭킹 스냅샷을 만들어
설계 7장의 공개 엔드포인트 `GET /search` · `/search/autocomplete` · `/search/popular-keywords` · `/rankings`를 채운다.

**Architecture:** 검색은 **리포지토리를 인터페이스로 가르고 구현 2벌**을 둔다 — 운영은 MySQL FULLTEXT(ngram),
테스트(H2)는 LIKE. H2에 FULLTEXT가 없어서 생기는 타협이 아니라, 설계 8장이 명시한 "2차 Elasticsearch 교체 지점"을
지금 만들어 두는 것이다. FULLTEXT 실 쿼리는 `@Tag("integration")`으로 실 MySQL에서만 검증한다.
랭킹은 `goods_daily_stat`(일별 조회·판매·찜)을 원장으로 삼고, 매시 배치가 최근 3일 가중 점수를 계산해
`ranking_snapshot`을 **트랜잭션 안에서 통째로 교체**한다. 조회는 스냅샷만 읽는다.

**Tech Stack:** Spring Boot 3.5, Java 21, JPA + JPQL/네이티브 쿼리(QueryDSL 미도입), Flyway, MySQL 8.4,
테스트는 H2(MySQL 모드) + Testcontainers(통합).

**근거 문서:** 설계 `docs/superpowers/specs/2026-07-23-beautyboy-design.md` 5장(ranking)·7장(공개 API)·8장(검색).
로드맵 `docs/plans/2026-07-23-roadmap.md`(Flyway 대역, 공유 계약, Wave 2 사전 정리).

---

## Global Constraints

모든 태스크에 암묵적으로 포함된다.

- **Flyway 대역은 V20~V29뿐이다.** 이 밖의 번호를 쓰지 않는다. 다른 터미널(T2=V30~V39, T3=V40~V49)의 파일을 만들지 않는다.
- **아래 파일은 열지 않는다** (Wave 2 사전 정리에서 이미 닫힘):
  - `common/ErrorCode.java` — `SEARCH_*` `RANKING_*` 상수가 **이미 들어가 있다**. 추가가 필요하면 중단하고 보고.
  - `config/SecurityConfig.java` — `/api/v1/search/**` · `/api/v1/rankings`는 **이미 공개다**. 확인만 하고 수정하지 않는다.
  - `backend/build.gradle.kts` — 동결. **새 의존성이 필요하면 중단하고 보고한다.** 이 계획은 추가 의존성 0으로 설계됐다.
  - `catalog/**` 전부 — 이번 웨이브에서 catalog는 **T2 소유**다. 조회수 집계가 catalog를 지나가야 할 것 같아도
    (T1-6이 인터셉터로 우회한다) 절대 열지 않는다. 열면 T2와 충돌한다.
  - `common/ApiResponse.java` · `common/PageResponse.java` — 동결.
- **Redis를 쓰지 않는다.** 설계의 "조회수 Redis INCR 후 5분 플러시"는 1차 범위에서 제외됐다(로드맵 2026-07-24 결정).
  조회수는 DB 카운터로 간다. Redis 의존성을 추가하면 동결된 빌드 파일을 여는 것이므로 **중단하고 보고**한다.
- **패키지 = 서비스 경계.** `search`·`ranking`은 자기 테이블만 접근한다. 타 도메인 엔티티/리포지토리를 직접 import하지 않는다.
  판매·찜 수치는 **이미 main에 있는** `ranking.SalesStatProvider` / `ranking.WishStatProvider`로만 받는다.
  이 두 인터페이스의 시그니처는 **확정 계약**이다 — 바꾸면 T2·T3가 동시에 깨진다. 수정하지 말고 보고.
- **응답은 `ApiResponse` 봉투, 목록은 `PageResponse<T>`.** 도메인 전용 페이징 타입을 새로 만들지 않는다.
- **테스트는 H2 + 픽스처만.** 실 MySQL이 필요한 것은 `@Tag("integration")`으로 갈라 `./gradlew integrationTest`로만 돈다.
- **상태 변경을 검증할 때는 재조회 전에 `com.beautyboy.support.TestPersistence.DB_왕복_강제(em)`를 호출한다.**
  클래스 `@Transactional`의 1차 캐시가 DB 왕복을 가려 "쓰지 않았는데 통과하는" 테스트가 만들어진다.
- **`@Scheduled` 배치는 테스트에서 저절로 돌면 안 된다.** 스케줄러 등록 설정에 `@Profile("!test")`를 붙이고,
  테스트는 배치 **메서드를 직접 호출**해 검증한다.
- **커밋 메시지·주석·문서는 한국어.** 태스크 단위 원자적 커밋.
- **모델 배분: 전 태스크 sonnet.** (CLAUDE.md 예외 3종 — 결제·재고차감·궁합엔진 — 해당 없음)
- 명령은 모두 `backend/`에서 실행한다.

---

## 착수 전 확인 (사람 몫)

```bash
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" log --oneline -1   # 0e990a7 (chore(backend): Wave 2 병렬 분기 전 사전 정리) 이후여야 함
git -C "/Users/doo._.hyun/Study/Project/Beauty Boy" status --short     # 비어 있어야 한다
```

---

## 파일 구조 (이 계획이 만들거나 고치는 것)

| 파일 | 책임 | 태스크 |
|---|---|---|
| `resources/db/migration/V20__search.sql` | `search_keyword_log` | T1-1 |
| `resources/db/migration/V21__ranking.sql` | `goods_daily_stat` · `ranking_snapshot` | T1-1 |
| `resources/db/migration/V22__goods_fulltext.sql` | `goods.name` ngram FULLTEXT 인덱스 | T1-1 |
| `test/.../common/FlywayMigrationSmokeTest.java` | 적용 버전 단언에 20·21·22 추가 | T1-1 |
| `search/SearchSort.java` | 검색 정렬 4종 + 파라미터 파싱 | T1-2 |
| `search/GoodsSearchRepository.java` | 검색 질의 인터페이스 (교체 지점) | T1-2 |
| `search/LikeGoodsSearchRepository.java` | LIKE 구현 — 테스트/H2 기본값 | T1-2 |
| `search/SearchService.java` | 검색 오케스트레이션 + 응답 조립 | T1-2 |
| `search/SearchController.java` | `GET /search` · `/autocomplete` · `/popular-keywords` | T1-2·T1-4·T1-5 |
| `search/dto/SearchCondition.java` · `SearchResultItem.java` | 검색 조건/결과 DTO | T1-2 |
| `search/MysqlFulltextGoodsSearchRepository.java` | FULLTEXT(ngram) 구현 — 운영 | T1-3 |
| `search/SearchKeywordLog.java` · `SearchKeywordLogRepository.java` | 검색어 로그 적재·집계 | T1-5 |
| `search/PopularKeywordHolder.java` | 매시 집계 결과 보관(인메모리) | T1-5 |
| `ranking/GoodsDailyStat.java` · `GoodsDailyStatRepository.java` | 일별 통계 원장 | T1-6 |
| `ranking/GoodsViewCountInterceptor.java` · `RankingWebConfig.java` | 상세 조회 시 조회수 +1 (catalog 미접촉) | T1-6 |
| `ranking/RankingSnapshot.java` · `RankingSnapshotRepository.java` | 스냅샷 테이블 | T1-7 |
| `ranking/RankingBatchService.java` | 통계 수집 + 점수 계산 + 스냅샷 통째 교체 | T1-7 |
| `ranking/RankingScheduler.java` | 매시 배치 트리거 (`@Profile("!test")`) | T1-7 |
| `ranking/RankingService.java` · `RankingController.java` · `dto/RankingItem.java` | `GET /rankings` | T1-8 |

**범위 밖(YAGNI):**
- **Redis** — 위 Global Constraints 참조. 조회수는 DB 카운터.
- **Elasticsearch** — `GoodsSearchRepository` 인터페이스가 교체 지점이라는 것만 남기고 구현하지 않는다.
- **검색 결과의 평점·찜 여부** — `GoodsListItem` 계약대로 기본값(`0.0`/`0`/`false`)으로 낸다. Wave 2 T3·Wave 3이 채운다.
- **인기검색어 다중 인스턴스 공유** — 인메모리 보관. 앱 1대 전제이며 그 지점을 코드 주석에 남긴다.

---

## Task 1: Flyway V20~V22 — 검색 로그 · 랭킹 테이블 · FULLTEXT 인덱스

**왜 이게 1번인가:** 이후 모든 태스크가 이 스키마에 엔티티를 맞춘다. 그리고 CLAUDE.md는 "설계 문서와 실제 스키마가
다르면 스키마가 진실"이라고 못 박았으므로, 스키마를 먼저 확정해야 뒤에서 엔티티를 고치는 일이 없다.

**Files:**
- Create: `backend/src/main/resources/db/migration/V20__search.sql`
- Create: `backend/src/main/resources/db/migration/V21__ranking.sql`
- Create: `backend/src/main/resources/db/migration/V22__goods_fulltext.sql`
- Modify: `backend/src/test/java/com/beautyboy/common/FlywayMigrationSmokeTest.java` (적용 버전 단언 1줄)

**Interfaces:**
- Produces: 테이블 `search_keyword_log(id, keyword, member_id, searched_at)` ·
  `goods_daily_stat(goods_id, stat_date, view_count, sales_count, wish_count)` PK=(goods_id, stat_date) ·
  `ranking_snapshot(id, category_code, goods_id, rank_no, score, generated_at)`.
- Produces: `goods` 테이블의 ngram FULLTEXT 인덱스 `ft_goods_name`. T1-3의 `MATCH ... AGAINST`가 이것에 의존한다.

- [ ] **Step 1: `V20__search.sql` 작성**

```sql
-- 검색어 원장. 인기검색어는 이 로그를 24시간 창으로 집계해 만든다(설계 8장).
-- 비로그인도 검색하므로 member_id는 NULL 허용이고 FK를 걸지 않는다 —
-- 로그는 회원이 탈퇴해도 통계로 남아야 하고, FK가 있으면 탈퇴가 로그 삭제를 강요한다.
CREATE TABLE search_keyword_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  keyword VARCHAR(100) NOT NULL,
  member_id BIGINT NULL,
  searched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  -- 집계는 항상 "최근 N시간 × 키워드별 건수"라 이 순서가 곧 커버링 인덱스가 된다.
  INDEX idx_search_log_searched_at_keyword (searched_at, keyword)
);
```

- [ ] **Step 2: `V21__ranking.sql` 작성**

```sql
-- 상품×날짜 일별 통계 원장. 조회수는 상세 조회 때 실시간 증가하고(T1-6),
-- 판매·찜은 매시 배치가 타 도메인 Provider에서 받아 채운다(T1-7).
-- PK를 (goods_id, stat_date) 복합으로 잡아 upsert(ON DUPLICATE KEY UPDATE)가 성립하게 한다.
CREATE TABLE goods_daily_stat (
  goods_id BIGINT NOT NULL,
  stat_date DATE NOT NULL,
  view_count INT NOT NULL DEFAULT 0,
  sales_count INT NOT NULL DEFAULT 0,
  wish_count INT NOT NULL DEFAULT 0,
  PRIMARY KEY (goods_id, stat_date),
  -- 배치가 "최근 3일 전체"를 훑으므로 날짜 선행 인덱스가 필요하다.
  INDEX idx_goods_daily_stat_date (stat_date)
);

-- 매시 배치가 통째로 교체하는 랭킹 결과. 조회는 이 테이블만 읽는다(설계 5장).
-- category_code는 대분류(C001 등)이고, 전체 랭킹은 'ALL'이라는 예약값을 쓴다 —
-- NULL로 두면 "전체"와 "미분류"가 구분되지 않고 인덱스에서도 다루기 번거롭다.
CREATE TABLE ranking_snapshot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_code VARCHAR(12) NOT NULL,
  goods_id BIGINT NOT NULL,
  rank_no INT NOT NULL,
  score DOUBLE NOT NULL,
  generated_at DATETIME NOT NULL,
  UNIQUE KEY uk_ranking_category_rank (category_code, rank_no)
);
```

- [ ] **Step 3: `V22__goods_fulltext.sql` 작성**

```sql
-- 상품명 전문 검색 인덱스. 한글은 공백 단위 토큰이 의미가 없어 ngram 파서를 쓴다(설계 8장).
-- ngram_token_size 기본값이 2이므로 2자 미만 검색어는 어떤 것도 매칭되지 않는다 —
-- 그래서 서비스가 2자 미만을 SEARCH_QUERY_TOO_SHORT(400)로 먼저 끊는다.
--
-- 브랜드명이 이 인덱스에 없는 이유: FULLTEXT는 한 테이블 안에서만 걸린다.
-- 브랜드명 매칭은 조인 후 LIKE로 보완한다(T1-3). 브랜드 수가 수십 개라 LIKE로 충분하다.
ALTER TABLE goods ADD FULLTEXT INDEX ft_goods_name (name) WITH PARSER ngram;
```

- [ ] **Step 4: 스모크 테스트의 적용 버전 단언 갱신**

`FlywayMigrationSmokeTest`의 `모든_마이그레이션이_실_MySQL에서_성공한다()` 안에서 아래 한 줄을 **교체**한다:

```java
        assertThat(적용된_버전).contains("1", "10", "11", "12", "20", "21", "22");
```

- [ ] **Step 5: 실 MySQL에서 마이그레이션 검증**

Run: `./gradlew integrationTest`
Expected: PASS (4 tests). `ALTER TABLE ... WITH PARSER ngram`이 실제 MySQL 8.4에서 실행된다.

실패하면 로그에서 Flyway가 멈춘 버전을 확인한다. H2는 이 DDL을 못 도니 **여기서만** 검증된다 —
`./gradlew test`가 녹색이라는 것은 이 스텝의 증거가 되지 않는다.

- [ ] **Step 6: 유닛테스트 회귀 확인**

Run: `./gradlew test`
Expected: PASS (91 tests). 테스트 프로필은 Flyway가 꺼져 있으므로 새 마이그레이션의 영향을 받지 않는다.

- [ ] **Step 7: 커밋**

```bash
git add src/main/resources/db/migration src/test/java/com/beautyboy/common/FlywayMigrationSmokeTest.java
git commit -m "feat(search,ranking): V20~V22 — 검색 로그·일별 통계·랭킹 스냅샷 + ngram FULLTEXT 인덱스"
```

---

## Task 2: 검색 질의 인터페이스 분리 + LIKE 구현 + `GET /search`

**근거:** 설계 8장 — "검색 모듈 인터페이스 분리 → 2차 Elasticsearch 교체 지점 명시". 그리고 현실 제약:
유닛테스트는 H2라 `MATCH ... AGAINST`를 실행할 수 없다. 두 요구가 같은 답을 가리킨다 — **질의를 인터페이스로 가른다.**

**Files:**
- Create: `backend/src/main/java/com/beautyboy/search/SearchSort.java`
- Create: `backend/src/main/java/com/beautyboy/search/GoodsSearchRepository.java`
- Create: `backend/src/main/java/com/beautyboy/search/LikeGoodsSearchRepository.java`
- Create: `backend/src/main/java/com/beautyboy/search/dto/SearchCondition.java`
- Create: `backend/src/main/java/com/beautyboy/search/dto/SearchResultItem.java`
- Create: `backend/src/main/java/com/beautyboy/search/SearchService.java`
- Create: `backend/src/main/java/com/beautyboy/search/SearchController.java`
- Test: `backend/src/test/java/com/beautyboy/search/SearchApiTest.java`

**Interfaces:**
- Produces: `GoodsSearchRepository` — `List<SearchRow> search(SearchCondition)` · `long count(SearchCondition)` ·
  `List<String> autocomplete(String prefix, int limit)`. T1-3의 FULLTEXT 구현과 T1-4가 이 시그니처에 의존한다.
- Produces: `SearchRow(Long goodsId, String brandName, String name, String thumbnailUrl, int listPrice, int salePrice)`
  — `catalog`의 `GoodsQueryRepository.GoodsRow`와 필드가 같지만 **재사용하지 않는다**. 타 도메인 타입을 import하면
  패키지 경계가 무너지고, catalog는 이번 웨이브에서 T2 소유라 손댈 수도 없다.
- Produces: 응답 `PageResponse<SearchResultItem>` — `GET /api/v1/search?q=&sort=&page=&size=`.

- [ ] **Step 1: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/search/SearchApiTest.java`

```java
package com.beautyboy.search;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 검색 API 테스트.
 *
 * <p>여기서 도는 구현은 LIKE 쪽이다(H2에 FULLTEXT가 없다). 그래서 이 테스트가 검증하는 것은
 * <b>서비스 계약</b>(파라미터 검증·정렬·페이징·응답 형태)이지 FULLTEXT 질의의 정확성이 아니다.
 * FULLTEXT 자체는 MysqlFulltextSearchIntegrationTest(@Tag("integration"))가 실 MySQL에서 본다.
 *
 * <p>픽스처가 catalog 엔티티를 쓰는 것은 의도적이다 — 검색 대상이 상품이므로 테스트에는 상품이 있어야 한다.
 * 운영 코드(search 패키지)는 catalog 타입을 import하지 않는다는 규칙과 별개다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SearchApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    @Test
    void 상품명에_검색어가_들어간_상품을_찾는다() throws Exception {
        Brand brand = brandRepository.save(new Brand("이니스프리", null));
        goodsRepository.save(new Goods(brand, "C001001001", "그린티 수분 토너", null, "https://img/1.jpg", 20000, 16000));
        goodsRepository.save(new Goods(brand, "C001003001", "퍼펙트 로션", null, "https://img/2.jpg", 30000, 30000));

        mockMvc.perform(get("/api/v1/search").param("q", "토너"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("그린티 수분 토너"));
    }

    @Test
    void 브랜드명으로도_검색된다() throws Exception {
        Brand brand = brandRepository.save(new Brand("닥터지", null));
        goodsRepository.save(new Goods(brand, "C001003001", "레드블레미쉬 크림", null, "https://img/3.jpg", 30000, 24000));

        mockMvc.perform(get("/api/v1/search").param("q", "닥터지"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].brandName").value("닥터지"));
    }

    @Test
    void 숨김_상품은_검색되지_않는다() throws Exception {
        Brand brand = brandRepository.save(new Brand("브랜드", null));
        Goods hidden = new Goods(brand, "C001001001", "숨김 토너", null, "https://img/4.jpg", 10000, 10000);
        hidden.hide();
        goodsRepository.save(hidden);

        mockMvc.perform(get("/api/v1/search").param("q", "토너"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 검색어가_2자_미만이면_400과_SEARCH_QUERY_TOO_SHORT() throws Exception {
        // ngram_token_size=2라 1자 검색어는 FULLTEXT에서 어차피 아무것도 매칭되지 않는다.
        // 빈 결과를 주는 대신 이유를 알려주고 끊는다.
        mockMvc.perform(get("/api/v1/search").param("q", "토"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_QUERY_TOO_SHORT"));
    }

    @Test
    void 지원하지_않는_정렬이면_400과_SEARCH_INVALID_SORT() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "토너").param("sort", "없는정렬"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_INVALID_SORT"));
    }

    @Test
    void 가격오름차순_정렬이_동작한다() throws Exception {
        Brand brand = brandRepository.save(new Brand("브랜드", null));
        goodsRepository.save(new Goods(brand, "C001001001", "비싼 토너", null, "https://img/5.jpg", 50000, 40000));
        goodsRepository.save(new Goods(brand, "C001001001", "싼 토너", null, "https://img/6.jpg", 10000, 8000));

        mockMvc.perform(get("/api/v1/search").param("q", "토너").param("sort", "priceAsc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("싼 토너"))
                .andExpect(jsonPath("$.data.content[1].name").value("비싼 토너"));
    }

    @Test
    void 페이징_정보가_PageResponse_계약대로_나온다() throws Exception {
        Brand brand = brandRepository.save(new Brand("브랜드", null));
        for (int i = 1; i <= 3; i++) {
            goodsRepository.save(new Goods(brand, "C001001001", "토너 " + i, null, "https://img/x.jpg", 10000, 10000));
        }

        mockMvc.perform(get("/api/v1/search").param("q", "토너").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*SearchApiTest*'`
Expected: FAIL — 모든 테스트가 404(핸들러 없음). `SearchController`가 아직 없다.

- [ ] **Step 3: 정렬 enum 구현** — `search/SearchSort.java`

```java
package com.beautyboy.search;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;

/**
 * 검색 결과 정렬.
 *
 * <p>catalog의 {@code GoodsSort}를 재사용하지 않는 이유가 둘이다.
 * (1) 검색에만 있는 ACCURACY(관련도)가 필요하고, catalog에는 그 개념이 없다.
 * (2) catalog는 이번 웨이브에서 다른 터미널 소유라 손댈 수 없다.
 *
 * <p>모든 정렬에 2차 키 {@code g.id desc}를 붙인다 — 동점이 흔한 정렬(관련도·판매량)에서
 * offset 페이징 경계의 상품이 중복되거나 누락되는 것을 막는다.
 */
public enum SearchSort {

    /** 관련도. 구현체마다 의미가 다르다(FULLTEXT=MATCH 점수, LIKE=이름 일치 우선). */
    ACCURACY("accuracy"),
    POPULAR("popular"),
    NEW("new"),
    PRICE_ASC("priceAsc");

    private final String param;

    SearchSort(String param) {
        this.param = param;
    }

    public static SearchSort fromParam(String param) {
        for (SearchSort sort : values()) {
            if (sort.param.equals(param)) {
                return sort;
            }
        }
        throw new BusinessException(ErrorCode.SEARCH_INVALID_SORT);
    }
}
```

- [ ] **Step 4: 조건·결과 DTO 구현**

`search/dto/SearchCondition.java`:

```java
package com.beautyboy.search.dto;

import com.beautyboy.search.SearchSort;

/**
 * 검증이 끝난 검색 조건. 검색어 길이 검사와 size 클램프는 컨트롤러가 마치고,
 * 이 레코드에는 이미 안전한 값만 담긴다(catalog의 GoodsSearchCondition과 같은 규약).
 */
public record SearchCondition(String keyword, SearchSort sort, int page, int size) {
}
```

`search/dto/SearchResultItem.java`:

```java
package com.beautyboy.search.dto;

import java.util.List;

/**
 * 검색 결과 카드 1건.
 *
 * <p>catalog의 {@code GoodsListItem}과 필드가 같지만 그 타입을 import하지 않는다 —
 * 패키지 경계 규칙(타 도메인 타입 직접 참조 금지)이고, catalog는 이번 웨이브에서 T2 소유다.
 * 프론트 입장에서 형태가 같으므로 카드 컴포넌트는 그대로 재사용된다.
 *
 * <p>rating/reviewCount/wished/todayDreamAvailable은 이 웨이브가 채울 수 없어 기본값으로 낸다
 * (설계 7장 동결 계약 — 미래 필드를 지금 형태에 포함해 두면 값이 채워질 때 프론트를 고치지 않는다).
 */
public record SearchResultItem(
        Long goodsNo,
        String brandName,
        String name,
        String thumbnailUrl,
        int listPrice,
        int salePrice,
        int discountRate,
        List<String> badges,
        double rating,
        int reviewCount,
        boolean wished,
        boolean todayDreamAvailable) {
}
```

- [ ] **Step 5: 리포지토리 인터페이스 구현** — `search/GoodsSearchRepository.java`

```java
package com.beautyboy.search;

import com.beautyboy.search.dto.SearchCondition;

import java.util.List;

/**
 * 검색 질의의 교체 지점.
 *
 * <p>설계 8장이 "검색 모듈 인터페이스 분리 → 2차 Elasticsearch 교체 지점"을 요구한다.
 * 동시에 현실 제약이 같은 답을 가리킨다: 유닛테스트는 H2이고 H2에는 FULLTEXT가 없다.
 * 그래서 구현을 둘 둔다 — 운영은 {@link MysqlFulltextGoodsSearchRepository},
 * 테스트/H2는 {@link LikeGoodsSearchRepository}.
 *
 * <p>어느 구현이 뜰지는 프로필로 정한다. 이 인터페이스 밖에는 SQL이 없어야 한다 —
 * 서비스가 MATCH 문법을 알게 되는 순간 교체 지점이 사라진다.
 */
public interface GoodsSearchRepository {

    /** 조건에 맞는 상품 한 페이지. 숨김(HIDDEN) 상품은 어떤 구현에서도 제외한다. */
    List<SearchRow> search(SearchCondition condition);

    /** 조건에 맞는 전체 건수. {@code PageResponse.of}에 그대로 들어간다. */
    long count(SearchCondition condition);

    /**
     * 자동완성 후보 상품명. prefix 일치 상위 {@code limit}개.
     *
     * @return 상품명 목록. 중복은 제거된 상태로 반환한다.
     */
    List<String> autocomplete(String prefix, int limit);

    /**
     * 검색 결과 1행. 목록 화면에 필요한 컬럼만 담는다 —
     * description(TEXT)이 없으므로 검색 경로에서는 애초에 조회되지 않는다.
     */
    record SearchRow(
            Long goodsId,
            String brandName,
            String name,
            String thumbnailUrl,
            int listPrice,
            int salePrice) {
    }
}
```

- [ ] **Step 6: LIKE 구현** — `search/LikeGoodsSearchRepository.java`

```java
package com.beautyboy.search;

import com.beautyboy.search.dto.SearchCondition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * LIKE 기반 검색 구현.
 *
 * <p>테스트 프로필(H2)의 기본 구현이다. H2에는 MySQL FULLTEXT가 없어
 * {@code MATCH ... AGAINST}를 실행할 수 없기 때문이다.
 *
 * <p>운영에서 이 구현을 쓰지 않는 이유: {@code like '%키워드%'}는 선행 와일드카드라
 * 인덱스를 타지 못해 항상 풀스캔이다. 상품 150개인 MVP에서는 문제가 없지만,
 * 그 사실이 "괜찮은 설계"라는 뜻은 아니라서 운영 경로는 FULLTEXT로 간다.
 *
 * <p>ACCURACY(관련도)는 LIKE로는 점수를 낼 수 없어 "상품명 일치를 브랜드명 일치보다 앞"으로만 근사한다.
 * 관련도의 진짜 정의는 FULLTEXT 구현에 있다.
 */
@Repository
@Profile("!mysql-search")
public class LikeGoodsSearchRepository implements GoodsSearchRepository {

    private static final String HIDDEN = "HIDDEN";

    private final EntityManager em;

    public LikeGoodsSearchRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<SearchRow> search(SearchCondition condition) {
        String jpql = "select g.id, b.name, g.name, g.thumbnailUrl, g.listPrice, g.salePrice "
                + "from Goods g join g.brand b "
                + "where g.status <> :hidden and (g.name like :pattern or b.name like :pattern) "
                + "order by " + orderBy(condition);

        TypedQuery<Object[]> query = em.createQuery(jpql, Object[].class)
                .setParameter("hidden", HIDDEN)
                .setParameter("pattern", "%" + condition.keyword() + "%");
        query.setFirstResult(condition.page() * condition.size());
        query.setMaxResults(condition.size());

        return query.getResultList().stream()
                .map(row -> new SearchRow(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        (Integer) row[4],
                        (Integer) row[5]))
                .toList();
    }

    @Override
    public long count(SearchCondition condition) {
        return em.createQuery(
                        "select count(g) from Goods g join g.brand b "
                                + "where g.status <> :hidden and (g.name like :pattern or b.name like :pattern)",
                        Long.class)
                .setParameter("hidden", HIDDEN)
                .setParameter("pattern", "%" + condition.keyword() + "%")
                .getSingleResult();
    }

    @Override
    public List<String> autocomplete(String prefix, int limit) {
        return em.createQuery(
                        "select distinct g.name from Goods g "
                                + "where g.status <> :hidden and g.name like :prefix order by g.name asc",
                        String.class)
                .setParameter("hidden", HIDDEN)
                .setParameter("prefix", prefix + "%")
                .setMaxResults(limit)
                .getResultList();
    }

    private String orderBy(SearchCondition condition) {
        return switch (condition.sort()) {
            // 상품명에 걸린 것을 브랜드명만 걸린 것보다 앞에 둔다 — LIKE로 낼 수 있는 최선의 관련도 근사.
            case ACCURACY -> "case when g.name like :pattern then 0 else 1 end asc, g.id desc";
            case POPULAR -> "g.viewCount desc, g.id desc";
            case NEW -> "g.createdAt desc, g.id desc";
            case PRICE_ASC -> "g.salePrice asc, g.id desc";
        };
    }
}
```

- [ ] **Step 7: 서비스 구현** — `search/SearchService.java`

```java
package com.beautyboy.search;

import com.beautyboy.common.PageResponse;
import com.beautyboy.search.dto.SearchCondition;
import com.beautyboy.search.dto.SearchResultItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 검색 오케스트레이션.
 *
 * <p>이 클래스에는 SQL이 한 줄도 없다 — 질의는 전부 {@link GoodsSearchRepository} 뒤에 있다.
 * 그래야 2차에서 Elasticsearch로 갈아끼울 때 서비스와 컨트롤러를 건드리지 않는다(설계 8장).
 */
@Service
public class SearchService {

    private final GoodsSearchRepository goodsSearchRepository;

    public SearchService(GoodsSearchRepository goodsSearchRepository) {
        this.goodsSearchRepository = goodsSearchRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<SearchResultItem> search(SearchCondition condition) {
        List<GoodsSearchRepository.SearchRow> rows = goodsSearchRepository.search(condition);
        long totalElements = goodsSearchRepository.count(condition);

        List<SearchResultItem> items = rows.stream().map(this::toItem).toList();

        return PageResponse.of(items, condition.page(), condition.size(), totalElements);
    }

    private SearchResultItem toItem(GoodsSearchRepository.SearchRow row) {
        return new SearchResultItem(
                row.goodsId(),
                row.brandName(),
                row.name(),
                row.thumbnailUrl(),
                row.listPrice(),
                row.salePrice(),
                discountRate(row.listPrice(), row.salePrice()),
                // 배지는 promotion(catalog 소유) 조인이 필요한데 이번 웨이브에서 catalog는 T2 소유다.
                // 빈 목록으로 두고 Wave 4 통합에서 채운다 — 계약 형태는 이미 맞다.
                List.of(),
                0.0,
                0,
                false,
                false);
    }

    private int discountRate(int listPrice, int salePrice) {
        if (listPrice == 0) {
            return 0;
        }
        return (listPrice - salePrice) * 100 / listPrice;
    }
}
```

- [ ] **Step 8: 컨트롤러 구현** — `search/SearchController.java`

```java
package com.beautyboy.search;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.common.PageResponse;
import com.beautyboy.search.dto.SearchCondition;
import com.beautyboy.search.dto.SearchResultItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    private static final int MAX_PAGE_SIZE = 100;
    /** ngram_token_size 기본값이 2다. 1자 검색어는 FULLTEXT에서 아무것도 매칭시키지 못한다. */
    private static final int MIN_KEYWORD_LENGTH = 2;

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/v1/search")
    public ResponseEntity<ApiResponse<PageResponse<SearchResultItem>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "accuracy") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String keyword = q.trim();
        if (keyword.length() < MIN_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.SEARCH_QUERY_TOO_SHORT);
        }

        SearchCondition condition = new SearchCondition(
                keyword, SearchSort.fromParam(sort), page, Math.min(size, MAX_PAGE_SIZE));

        return ResponseEntity.ok(ApiResponse.ok(searchService.search(condition)));
    }
}
```

- [ ] **Step 9: green 확인**

Run: `./gradlew test --tests '*SearchApiTest*'`
Expected: PASS (7 tests)

`가격오름차순_정렬이_동작한다`가 실패하면 `orderBy`의 `PRICE_ASC` 절을 확인한다.
`검색어가_2자_미만이면...`이 400 대신 500이면 `ErrorCode.SEARCH_QUERY_TOO_SHORT`가
`GlobalExceptionHandler`를 타지 않은 것이다 — `BusinessException`으로 던졌는지 확인한다.

- [ ] **Step 10: 전체 회귀 + 커밋**

Run: `./gradlew test`
Expected: PASS (98 tests)

```bash
git add src/main/java/com/beautyboy/search src/test/java/com/beautyboy/search
git commit -m "feat(search): GET /search — 질의 인터페이스 분리 + LIKE 구현

설계 8장의 Elasticsearch 교체 지점을 인터페이스로 먼저 만든다.
H2에 FULLTEXT가 없어 유닛테스트가 실 질의를 못 도는 제약과 같은 답이다."
```

---

## Task 3: MySQL FULLTEXT(ngram) 구현 + 실 MySQL 통합 테스트

**근거:** T1-2가 만든 교체 지점의 운영 구현. LIKE는 선행 와일드카드라 항상 풀스캔이고, 한글 형태소를
공백 토큰으로 다루지 못한다. 설계 8장이 지정한 것은 FULLTEXT + ngram이다.

**Files:**
- Create: `backend/src/main/java/com/beautyboy/search/MysqlFulltextGoodsSearchRepository.java`
- Test: `backend/src/test/java/com/beautyboy/search/MysqlFulltextSearchIntegrationTest.java`

**Interfaces:**
- Consumes: `GoodsSearchRepository`(T1-2)의 시그니처 · V22의 인덱스 `ft_goods_name`(T1-1).
- Produces: 프로필 `mysql-search`가 활성일 때 뜨는 구현. 운영 프로필이 이 값을 켠다.

- [ ] **Step 1: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/search/MysqlFulltextSearchIntegrationTest.java`

```java
package com.beautyboy.search;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.search.dto.SearchCondition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FULLTEXT 구현의 유일한 검증 지점.
 *
 * <p>{@code SearchApiTest}는 H2라서 LIKE 구현만 돈다 — 그 테스트가 녹색이어도
 * {@code MATCH ... AGAINST} 문법 오류나 ngram 인덱스 누락은 전혀 드러나지 않는다.
 * 실 MySQL이 아니면 검증 자체가 불가능하므로 {@code @Tag("integration")}으로 분리한다.
 *
 * <p>실행: {@code ./gradlew integrationTest}
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles({"test", "mysql-search"})
@Testcontainers
class MysqlFulltextSearchIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @DynamicPropertySource
    static void 실_MySQL로_바꿔_끼운다(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    GoodsSearchRepository goodsSearchRepository;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    @Test
    void 주입된_구현이_FULLTEXT_구현이다() {
        // 프로필이 어긋나 LIKE 구현이 주입되면 아래 테스트들이 통과해도 아무 의미가 없다.
        assertThat(goodsSearchRepository).isInstanceOf(MysqlFulltextGoodsSearchRepository.class);
    }

    @Test
    void 한글_부분어절로_상품명이_매칭된다() {
        Brand brand = brandRepository.save(new Brand("테스트브랜드", null));
        goodsRepository.save(new Goods(brand, "C001001001", "그린티 수분 토너", null, "https://img/1.jpg", 20000, 16000));
        goodsRepository.flush();

        // ngram 파서라 "수분"처럼 어절 일부로도 걸린다. 공백 토큰 방식이면 여기서 0건이 나온다.
        List<GoodsSearchRepository.SearchRow> rows =
                goodsSearchRepository.search(new SearchCondition("수분", SearchSort.ACCURACY, 0, 20));

        assertThat(rows).extracting(GoodsSearchRepository.SearchRow::name).contains("그린티 수분 토너");
    }

    @Test
    void 브랜드명은_FULLTEXT가_아니라_조인_LIKE로_걸린다() {
        // FULLTEXT는 한 테이블 안에서만 걸린다. 브랜드명 매칭이 죽지 않았는지 확인한다.
        Brand brand = brandRepository.save(new Brand("닥터지", null));
        goodsRepository.save(new Goods(brand, "C001003001", "레드블레미쉬 크림", null, "https://img/2.jpg", 30000, 24000));
        goodsRepository.flush();

        List<GoodsSearchRepository.SearchRow> rows =
                goodsSearchRepository.search(new SearchCondition("닥터지", SearchSort.ACCURACY, 0, 20));

        assertThat(rows).extracting(GoodsSearchRepository.SearchRow::brandName).contains("닥터지");
    }

    @Test
    void count가_search와_같은_조건을_센다() {
        Brand brand = brandRepository.save(new Brand("브랜드", null));
        goodsRepository.save(new Goods(brand, "C001001001", "수분 토너 하나", null, "https://img/3.jpg", 10000, 10000));
        goodsRepository.save(new Goods(brand, "C001001001", "수분 토너 둘", null, "https://img/4.jpg", 10000, 10000));
        goodsRepository.flush();

        SearchCondition condition = new SearchCondition("수분 토너", SearchSort.ACCURACY, 0, 20);

        // 조건이 어긋나면 "총 5개"인데 2개만 나오는 페이징 버그가 된다.
        assertThat(goodsSearchRepository.count(condition))
                .isEqualTo(goodsSearchRepository.search(condition).size());
    }

    @Test
    void 자동완성은_접두사로_매칭된다() {
        Brand brand = brandRepository.save(new Brand("브랜드", null));
        goodsRepository.save(new Goods(brand, "C001001001", "수분폭탄 토너", null, "https://img/5.jpg", 10000, 10000));
        goodsRepository.flush();

        assertThat(goodsSearchRepository.autocomplete("수분", 10)).contains("수분폭탄 토너");
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew integrationTest --tests '*MysqlFulltextSearchIntegrationTest*'`
Expected: FAIL — `MysqlFulltextGoodsSearchRepository` 클래스가 없어 컴파일 에러.

- [ ] **Step 3: FULLTEXT 구현** — `search/MysqlFulltextGoodsSearchRepository.java`

```java
package com.beautyboy.search;

import com.beautyboy.search.dto.SearchCondition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MySQL FULLTEXT(ngram) 기반 검색 구현 — 운영 경로.
 *
 * <p>JPQL에는 {@code MATCH ... AGAINST}가 없으므로 네이티브 쿼리를 쓴다.
 * 그래서 이 클래스만 컬럼명(snake_case)을 직접 안다 — 스키마 변경 시 여기가 같이 바뀐다.
 *
 * <p>브랜드명이 OR LIKE인 이유: FULLTEXT 인덱스는 한 테이블 안에서만 걸린다.
 * goods와 brand에 각각 인덱스를 두고 UNION하는 방법도 있지만, 브랜드가 수십 개 규모라
 * 조인 후 LIKE가 더 단순하고 충분히 빠르다.
 *
 * <p>BOOLEAN MODE를 쓰는 이유: 자연어 모드는 전체 행의 50% 이상에 나타나는 토큰을 통째로 버린다.
 * 상품 150개 규모에서 "토너"처럼 흔한 단어가 그 문턱을 넘기 쉬워, 검색이 조용히 0건을 내는 사고가 난다.
 */
@Repository
@Profile("mysql-search")
public class MysqlFulltextGoodsSearchRepository implements GoodsSearchRepository {

    private static final String HIDDEN = "HIDDEN";

    private static final String FROM_WHERE = """
             from goods g join brand b on g.brand_id = b.id
             where g.status <> :hidden
               and (match(g.name) against (:booleanQuery in boolean mode) or b.name like :likePattern)
            """;

    private final EntityManager em;

    public MysqlFulltextGoodsSearchRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<SearchRow> search(SearchCondition condition) {
        String sql = "select g.id, b.name, g.name, g.thumbnail_url, g.list_price, g.sale_price"
                + FROM_WHERE
                + " order by " + orderBy(condition.sort())
                + " limit :size offset :offset";

        Query query = em.createNativeQuery(sql);
        bind(query, condition);
        query.setParameter("size", condition.size());
        query.setParameter("offset", condition.page() * condition.size());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream()
                .map(row -> new SearchRow(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        ((Number) row[4]).intValue(),
                        ((Number) row[5]).intValue()))
                .toList();
    }

    @Override
    public long count(SearchCondition condition) {
        Query query = em.createNativeQuery("select count(*)" + FROM_WHERE);
        bind(query, condition);
        return ((Number) query.getSingleResult()).longValue();
    }

    @Override
    public List<String> autocomplete(String prefix, int limit) {
        // 자동완성은 관련도가 아니라 "빨리 뜨는 접두사 후보"라 prefix LIKE가 맞다(설계 7장).
        // 뒤쪽 와일드카드만 있으므로 name 인덱스를 탄다.
        Query query = em.createNativeQuery(
                "select distinct g.name from goods g "
                        + "where g.status <> :hidden and g.name like :prefix "
                        + "order by g.name asc limit :limit");
        query.setParameter("hidden", HIDDEN);
        query.setParameter("prefix", prefix + "%");
        query.setParameter("limit", limit);

        @SuppressWarnings("unchecked")
        List<String> names = query.getResultList();
        return names;
    }

    private void bind(Query query, SearchCondition condition) {
        query.setParameter("hidden", HIDDEN);
        query.setParameter("booleanQuery", toBooleanQuery(condition.keyword()));
        query.setParameter("likePattern", "%" + condition.keyword() + "%");
    }

    /**
     * 사용자 입력을 BOOLEAN MODE 질의로 바꾼다.
     *
     * <p>연산자 문자(+ - &gt; &lt; ( ) ~ * " @)를 제거하는 이유가 둘이다.
     * (1) 사용자가 무심코 넣은 하이픈이 "제외" 연산자로 해석돼 결과가 사라지는 것을 막는다.
     * (2) 질의 문법 오류로 500이 나가는 것을 막는다.
     * 그 다음 각 토큰에 {@code +}를 붙여 전부 포함(AND)으로 만든다 — OR면 한 글자만 겹쳐도 걸려 노이즈가 커진다.
     */
    private String toBooleanQuery(String keyword) {
        String sanitized = keyword.replaceAll("[+\\-><()~*\"@]", " ").trim();
        String[] tokens = sanitized.split("\\s+");

        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (!token.isBlank()) {
                builder.append('+').append(token).append(' ');
            }
        }
        return builder.toString().trim();
    }

    private String orderBy(SearchSort sort) {
        return switch (sort) {
            // MATCH를 select 절에 또 쓰지 않고 order by에서 직접 점수를 쓴다 —
            // MySQL이 같은 MATCH 표현식을 재사용하므로 추가 비용이 없다.
            case ACCURACY -> "match(g.name) against (:booleanQuery in boolean mode) desc, g.id desc";
            case POPULAR -> "g.view_count desc, g.id desc";
            case NEW -> "g.created_at desc, g.id desc";
            case PRICE_ASC -> "g.sale_price asc, g.id desc";
        };
    }
}
```

- [ ] **Step 4: 운영 프로필에 `mysql-search` 켜기** — `backend/src/main/resources/application.yml`

`spring:` 블록 안, `flyway` 항목 아래에 아래를 추가한다:

```yaml
  profiles:
    include: mysql-search
```

> 이 파일은 Wave 0 산출물이지만 **동결 대상 목록(`common`·루트 빌드 설정·`SecurityConfig`)에는 없다.**
> 다만 T2도 토스 설정을 여기에 추가하므로 **충돌 가능성이 있다** — 라인이 겹치지 않게 `profiles`는
> `flyway` 바로 아래에만 넣고, 다른 줄은 건드리지 않는다. 충돌이 나면 양쪽 블록을 모두 남기면 된다.

- [ ] **Step 5: green 확인**

Run: `./gradlew integrationTest --tests '*MysqlFulltextSearchIntegrationTest*'`
Expected: PASS (5 tests)

`주입된_구현이_FULLTEXT_구현이다`가 실패하면 `@ActiveProfiles({"test", "mysql-search"})`와
두 구현의 `@Profile` 값이 정확히 반대인지 확인한다(`!mysql-search` / `mysql-search`).

- [ ] **Step 6: 유닛테스트가 여전히 LIKE로 도는지 확인**

Run: `./gradlew test`
Expected: PASS (98 tests). `SearchApiTest`는 `mysql-search` 프로필이 없으므로 LIKE 구현이 주입된다.
여기서 FULLTEXT 구현이 뜨면 H2가 `MATCH`를 못 알아들어 전부 깨진다 — 깨지면 `@Profile` 조건을 다시 본다.

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/beautyboy/search/MysqlFulltextGoodsSearchRepository.java \
        src/test/java/com/beautyboy/search/MysqlFulltextSearchIntegrationTest.java \
        src/main/resources/application.yml
git commit -m "feat(search): MySQL FULLTEXT(ngram) 검색 구현 + 실 MySQL 통합 테스트

BOOLEAN MODE를 쓴 이유는 자연어 모드의 50% 문턱 때문이다 —
상품 150개 규모에서 흔한 단어가 조용히 0건이 되는 사고를 막는다."
```

---

## Task 4: `GET /search/autocomplete`

**근거:** 설계 7장 — "300ms 디바운스, prefix 10개". 디바운스는 프론트 몫이고, 서버는 prefix 상위 10개를 준다.

**Files:**
- Modify: `backend/src/main/java/com/beautyboy/search/SearchService.java`
- Modify: `backend/src/main/java/com/beautyboy/search/SearchController.java`
- Test: `backend/src/test/java/com/beautyboy/search/AutocompleteApiTest.java`

**Interfaces:**
- Consumes: `GoodsSearchRepository.autocomplete(String, int)`(T1-2 계약, T1-3 구현).
- Produces: `GET /api/v1/search/autocomplete?q=` → `ApiResponse<List<String>>`. 최대 10건.

- [ ] **Step 1: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/search/AutocompleteApiTest.java`

```java
package com.beautyboy.search;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AutocompleteApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    @Test
    void 접두사로_시작하는_상품명을_준다() throws Exception {
        Brand brand = brandRepository.save(new Brand("브랜드", null));
        goodsRepository.save(new Goods(brand, "C001001001", "수분폭탄 토너", null, "https://img/1.jpg", 10000, 10000));
        goodsRepository.save(new Goods(brand, "C001003001", "영양 크림", null, "https://img/2.jpg", 10000, 10000));

        mockMvc.perform(get("/api/v1/search/autocomplete").param("q", "수분"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0]").value("수분폭탄 토너"));
    }

    @Test
    void 최대_10건까지만_준다() throws Exception {
        Brand brand = brandRepository.save(new Brand("브랜드", null));
        for (int i = 1; i <= 12; i++) {
            goodsRepository.save(new Goods(brand, "C001001001", "토너 " + i, null, "https://img/x.jpg", 10000, 10000));
        }

        mockMvc.perform(get("/api/v1/search/autocomplete").param("q", "토너"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(10));
    }

    @Test
    void 검색어가_2자_미만이면_에러가_아니라_빈_목록이다() throws Exception {
        // 자동완성은 타이핑 중에 매 글자 호출된다. 첫 글자마다 400을 뱉으면
        // 프론트 콘솔이 에러로 뒤덮이고 정상 흐름과 장애를 구분할 수 없게 된다.
        // 그래서 /search와 달리 조용히 빈 목록을 준다.
        mockMvc.perform(get("/api/v1/search/autocomplete").param("q", "토"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*AutocompleteApiTest*'`
Expected: FAIL — 404 (핸들러 없음)

- [ ] **Step 3: 서비스에 메서드 추가** — `SearchService`의 `search(...)` 아래에 삽입

```java
    /**
     * 자동완성 후보.
     *
     * <p>검색어가 짧으면 예외 대신 빈 목록을 준다 — 이 엔드포인트는 타이핑 중 매 글자 호출되므로,
     * 정상적인 입력 과정을 에러로 취급하면 프론트가 진짜 장애를 구분하지 못한다.
     */
    @Transactional(readOnly = true)
    public List<String> autocomplete(String keyword, int minLength, int limit) {
        String trimmed = keyword.trim();
        if (trimmed.length() < minLength) {
            return List.of();
        }
        return goodsSearchRepository.autocomplete(trimmed, limit);
    }
```

- [ ] **Step 4: 컨트롤러에 핸들러 추가** — `SearchController`의 `search(...)` 아래에 삽입

```java
    /** 프론트가 300ms 디바운스로 호출한다(설계 7장). 서버는 상위 10건만 준다. */
    private static final int AUTOCOMPLETE_LIMIT = 10;

    @GetMapping("/api/v1/search/autocomplete")
    public ResponseEntity<ApiResponse<List<String>>> autocomplete(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.ok(
                searchService.autocomplete(q, MIN_KEYWORD_LENGTH, AUTOCOMPLETE_LIMIT)));
    }
```

`SearchController` 상단에 `import java.util.List;`를 추가한다.

> 상수 선언(`AUTOCOMPLETE_LIMIT`)이 메서드 사이에 오면 체크스타일이 없더라도 읽기 나쁘다 —
> 파일 상단의 다른 `private static final` 옆으로 옮겨 놓아라.

- [ ] **Step 5: green 확인**

Run: `./gradlew test --tests '*AutocompleteApiTest*'`
Expected: PASS (3 tests)

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/beautyboy/search src/test/java/com/beautyboy/search/AutocompleteApiTest.java
git commit -m "feat(search): GET /search/autocomplete — prefix 상위 10건

짧은 입력에 400 대신 빈 목록을 주는 이유는 타이핑 중 매 글자 호출되는 엔드포인트이기 때문이다."
```

---

## Task 5: 검색어 로그 적재 + `GET /search/popular-keywords`

**근거:** 설계 8장 — "검색 로그 → 매시 24시간 집계 → 인기검색어 캐시".

**Files:**
- Create: `backend/src/main/java/com/beautyboy/search/SearchKeywordLog.java`
- Create: `backend/src/main/java/com/beautyboy/search/SearchKeywordLogRepository.java`
- Create: `backend/src/main/java/com/beautyboy/search/PopularKeywordHolder.java`
- Modify: `backend/src/main/java/com/beautyboy/search/SearchService.java`
- Modify: `backend/src/main/java/com/beautyboy/search/SearchController.java`
- Test: `backend/src/test/java/com/beautyboy/search/PopularKeywordTest.java`

**Interfaces:**
- Consumes: V20의 `search_keyword_log`(T1-1).
- Produces: `PopularKeywordHolder.refresh()` — T1-7의 스케줄러가 매시 호출한다. 시그니처를 바꾸면 그쪽이 깨진다.
- Produces: `GET /api/v1/search/popular-keywords` → `ApiResponse<List<String>>` 최대 10건.

- [ ] **Step 1: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/search/PopularKeywordTest.java`

```java
package com.beautyboy.search;

import com.beautyboy.support.TestPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PopularKeywordTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    SearchKeywordLogRepository searchKeywordLogRepository;
    @Autowired
    PopularKeywordHolder popularKeywordHolder;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    void 검색하면_검색어가_로그에_남는다() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "토너")).andExpect(status().isOk());

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(searchKeywordLogRepository.findAll())
                .extracting(SearchKeywordLog::getKeyword)
                .containsExactly("토너");
    }

    @Test
    void 집계는_최근_24시간만_세고_많이_검색된_순으로_정렬한다() {
        LocalDateTime now = LocalDateTime.now();
        검색로그_저장("토너", now.minusHours(1));
        검색로그_저장("토너", now.minusHours(2));
        검색로그_저장("크림", now.minusHours(3));
        // 25시간 전 — 창 밖이라 세면 안 된다. 이게 새면 "어제 유행"이 오늘 1위로 남는다.
        검색로그_저장("선크림", now.minusHours(25));
        검색로그_저장("선크림", now.minusHours(26));
        검색로그_저장("선크림", now.minusHours(27));

        TestPersistence.DB_왕복_강제(entityManager);

        popularKeywordHolder.refresh();

        assertThat(popularKeywordHolder.current()).containsExactly("토너", "크림");
    }

    @Test
    void 집계_전에는_빈_목록을_준다() throws Exception {
        // 배치가 한 번도 안 돈 부팅 직후에도 500이 아니라 빈 목록이어야 한다.
        popularKeywordHolder.reset();

        mockMvc.perform(get("/api/v1/search/popular-keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private void 검색로그_저장(String keyword, LocalDateTime searchedAt) {
        searchKeywordLogRepository.save(new SearchKeywordLog(keyword, null, searchedAt));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*PopularKeywordTest*'`
Expected: FAIL — `SearchKeywordLog` 등이 없어 컴파일 에러.

- [ ] **Step 3: 엔티티 구현** — `search/SearchKeywordLog.java`

```java
package com.beautyboy.search;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 검색어 1건의 기록. 인기검색어 집계의 원장이다(설계 8장).
 *
 * <p>{@code memberId}를 FK 없는 스칼라로 들고 있는 이유: member는 타 도메인이라
 * 엔티티를 직접 참조할 수 없고(패키지 = 서비스 경계), 로그는 회원이 탈퇴해도 통계로 남아야 한다.
 * 비로그인 검색도 기록하므로 null을 허용한다.
 */
@Entity
@Table(name = "search_keyword_log")
public class SearchKeywordLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String keyword;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    protected SearchKeywordLog() {
    }

    public SearchKeywordLog(String keyword, Long memberId, LocalDateTime searchedAt) {
        this.keyword = keyword;
        this.memberId = memberId;
        this.searchedAt = searchedAt;
    }

    public Long getId() {
        return id;
    }

    public String getKeyword() {
        return keyword;
    }

    public Long getMemberId() {
        return memberId;
    }

    public LocalDateTime getSearchedAt() {
        return searchedAt;
    }
}
```

- [ ] **Step 4: 리포지토리 구현** — `search/SearchKeywordLogRepository.java`

```java
package com.beautyboy.search;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SearchKeywordLogRepository extends JpaRepository<SearchKeywordLog, Long> {

    /**
     * 기준 시각 이후 검색어를 건수 내림차순으로. 상위 N건만 필요하므로 Pageable로 자른다.
     *
     * <p>동점일 때 키워드 오름차순을 2차 키로 붙인다 — 없으면 집계할 때마다 순서가 흔들려
     * "인기검색어가 새로고침마다 바뀐다"는 버그로 보인다.
     */
    @Query("select l.keyword from SearchKeywordLog l "
            + "where l.searchedAt >= :from group by l.keyword order by count(l) desc, l.keyword asc")
    List<String> findTopKeywordsSince(@Param("from") LocalDateTime from, Pageable pageable);
}
```

- [ ] **Step 5: 인기검색어 보관소 구현** — `search/PopularKeywordHolder.java`

```java
package com.beautyboy.search;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 인기검색어 집계 결과 보관소.
 *
 * <p>조회할 때마다 집계하지 않는 이유: 인기검색어는 메인·검색창에서 거의 모든 방문자에게 노출되는데
 * 그때마다 24시간치 로그를 group by 하면 가장 흔한 요청이 가장 무거운 쿼리가 된다.
 * 설계 8장이 "매시 집계 → 캐시"로 정한 이유가 그것이다.
 *
 * <p><b>인메모리 보관이라 앱 1대 전제다.</b> 다중화하면 인스턴스마다 다른 목록을 보여주게 되므로,
 * 그 시점에 Redis로 옮긴다 — 이 클래스가 그 교체 지점이다.
 * (Redis는 1차 범위 밖: 로드맵 2026-07-24 결정)
 *
 * <p>{@link AtomicReference}에 불변 리스트를 통째로 갈아끼운다. 부분 갱신이 없으므로 락이 필요 없고,
 * 읽는 쪽은 항상 일관된 스냅샷을 본다(갱신 중 절반만 채워진 목록을 보는 일이 없다).
 */
@Component
public class PopularKeywordHolder {

    private static final int TOP_N = 10;
    private static final int WINDOW_HOURS = 24;

    private final SearchKeywordLogRepository searchKeywordLogRepository;
    private final AtomicReference<List<String>> keywords = new AtomicReference<>(List.of());

    public PopularKeywordHolder(SearchKeywordLogRepository searchKeywordLogRepository) {
        this.searchKeywordLogRepository = searchKeywordLogRepository;
    }

    /** 최근 24시간 로그를 집계해 보관값을 교체한다. 스케줄러(T1-7)가 매시 호출한다. */
    @Transactional(readOnly = true)
    public void refresh() {
        List<String> top = searchKeywordLogRepository.findTopKeywordsSince(
                LocalDateTime.now().minusHours(WINDOW_HOURS), PageRequest.of(0, TOP_N));
        keywords.set(List.copyOf(top));
    }

    /** 마지막 집계 결과. 한 번도 집계되지 않았으면 빈 목록(널이 아니다). */
    public List<String> current() {
        return keywords.get();
    }

    /** 테스트가 부팅 직후 상태를 재현할 때 쓴다. 운영 경로에서는 호출하지 않는다. */
    public void reset() {
        keywords.set(List.of());
    }
}
```

- [ ] **Step 6: 검색 시 로그 적재 + 조회 메서드** — `SearchService` 수정

생성자와 필드를 아래로 **교체**한다:

```java
    private final GoodsSearchRepository goodsSearchRepository;
    private final SearchKeywordLogRepository searchKeywordLogRepository;
    private final PopularKeywordHolder popularKeywordHolder;

    public SearchService(GoodsSearchRepository goodsSearchRepository,
                         SearchKeywordLogRepository searchKeywordLogRepository,
                         PopularKeywordHolder popularKeywordHolder) {
        this.goodsSearchRepository = goodsSearchRepository;
        this.searchKeywordLogRepository = searchKeywordLogRepository;
        this.popularKeywordHolder = popularKeywordHolder;
    }
```

`search(...)`의 `@Transactional(readOnly = true)`를 **`@Transactional`로 바꾸고**(로그를 쓰므로 읽기 전용이 아니다),
메서드 첫 줄에 로그 적재를 추가한다:

```java
    @Transactional
    public PageResponse<SearchResultItem> search(SearchCondition condition, Long memberId) {
        // 로그를 먼저 남긴다 — 결과가 0건인 검색어야말로 "찾는데 없는 것"이라 가장 알고 싶은 데이터다.
        searchKeywordLogRepository.save(
                new SearchKeywordLog(condition.keyword(), memberId, LocalDateTime.now()));

        List<GoodsSearchRepository.SearchRow> rows = goodsSearchRepository.search(condition);
        long totalElements = goodsSearchRepository.count(condition);

        List<SearchResultItem> items = rows.stream().map(this::toItem).toList();

        return PageResponse.of(items, condition.page(), condition.size(), totalElements);
    }

    /** 매시 집계된 인기검색어. 집계 전이면 빈 목록. */
    public List<String> popularKeywords() {
        return popularKeywordHolder.current();
    }
```

상단에 `import java.time.LocalDateTime;`를 추가한다.

- [ ] **Step 7: 컨트롤러 수정** — `SearchController`

`search(...)` 시그니처에 인증 주체를 더하고(공개 엔드포인트라 비로그인은 null이 들어온다),
호출부에 넘긴다. 그리고 인기검색어 핸들러를 추가한다:

```java
    @GetMapping("/api/v1/search")
    public ResponseEntity<ApiResponse<PageResponse<SearchResultItem>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "accuracy") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            // 공개 엔드포인트다. 비로그인이면 null이 들어오고, 그대로 로그에 남는다.
            @AuthenticationPrincipal Long memberId) {

        String keyword = q.trim();
        if (keyword.length() < MIN_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.SEARCH_QUERY_TOO_SHORT);
        }

        SearchCondition condition = new SearchCondition(
                keyword, SearchSort.fromParam(sort), page, Math.min(size, MAX_PAGE_SIZE));

        return ResponseEntity.ok(ApiResponse.ok(searchService.search(condition, memberId)));
    }

    @GetMapping("/api/v1/search/popular-keywords")
    public ResponseEntity<ApiResponse<List<String>>> popularKeywords() {
        return ResponseEntity.ok(ApiResponse.ok(searchService.popularKeywords()));
    }
```

상단에 `import org.springframework.security.core.annotation.AuthenticationPrincipal;`를 추가한다.

- [ ] **Step 8: green 확인**

Run: `./gradlew test --tests '*PopularKeywordTest*' --tests '*SearchApiTest*'`
Expected: PASS (10 tests)

`집계는_최근_24시간만...`이 `선크림`을 포함해 실패하면 `findTopKeywordsSince`의 `>=` 조건과
`WINDOW_HOURS`를 확인한다. `검색하면_검색어가_로그에_남는다`가 0건이면
`DB_왕복_강제` 없이 1차 캐시를 본 것이 아니라 **실제로 저장하지 않은 것**이다 —
`search`의 `@Transactional`이 `readOnly = true`로 남아 있는지 확인한다.

- [ ] **Step 9: 전체 회귀 + 커밋**

Run: `./gradlew test`
Expected: PASS (101 tests)

```bash
git add src/main/java/com/beautyboy/search src/test/java/com/beautyboy/search/PopularKeywordTest.java
git commit -m "feat(search): 검색어 로그 적재 + 인기검색어 매시 집계 캐시

조회 시 집계하지 않는 이유는 가장 흔한 요청이 가장 무거운 쿼리가 되기 때문이다.
인메모리 보관이라 앱 1대 전제이며, 다중화 시 Redis로 옮기는 지점을 주석에 남겼다."
```

---

## Task 6: 조회수 집계 — 상세 조회 인터셉터 (catalog 미접촉)

**근거:** 설계 5장 — `goods_daily_stat`의 조회수. 랭킹 점수의 세 항 중 하나다.

**왜 인터셉터인가 (중요):** 조회수를 늘리는 자연스러운 자리는 `catalog.GoodsService.detail()`이다.
그런데 **이번 웨이브에서 catalog는 T2 소유**다 — T2가 주문 가격 재검증 때문에 `GoodsQueryService`와
`GoodsService`를 고친다. T1이 같은 파일을 건드리면 머지에서 충돌한다.
더 중요한 것은 경계 자체다: **조회수 집계는 catalog의 관심사가 아니라 ranking의 관심사다.**
ranking이 자기 인터셉터로 자기 테이블을 채우면 catalog는 랭킹의 존재조차 몰라도 된다.

**Files:**
- Create: `backend/src/main/java/com/beautyboy/ranking/GoodsDailyStat.java`
- Create: `backend/src/main/java/com/beautyboy/ranking/GoodsDailyStatRepository.java`
- Create: `backend/src/main/java/com/beautyboy/ranking/GoodsViewCountInterceptor.java`
- Create: `backend/src/main/java/com/beautyboy/ranking/RankingWebConfig.java`
- Test: `backend/src/test/java/com/beautyboy/ranking/GoodsViewCountTest.java`

**Interfaces:**
- Consumes: V21의 `goods_daily_stat`(T1-1).
- Produces: `GoodsDailyStatRepository.upsertViewCount(Long goodsId, LocalDate date, int delta)` ·
  `upsertSalesAndWish(...)` — T1-7의 배치가 후자를 쓴다.

- [ ] **Step 1: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/ranking/GoodsViewCountTest.java`

```java
package com.beautyboy.ranking;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.support.TestPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GoodsViewCountTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    GoodsDailyStatRepository goodsDailyStatRepository;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    void 상품_상세를_보면_오늘자_조회수가_증가한다() throws Exception {
        Long goodsId = 상품_저장();

        mockMvc.perform(get("/api/v1/goods/" + goodsId)).andExpect(status().isOk());

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(조회수(goodsId)).isEqualTo(1);
    }

    @Test
    void 두_번_보면_같은_행이_누적된다() throws Exception {
        // PK가 (goods_id, stat_date) 복합이라 upsert가 성립해야 한다.
        // 여기서 실패하면 두 번째 조회가 PK 중복으로 터지거나 행이 2개 생긴다.
        Long goodsId = 상품_저장();

        mockMvc.perform(get("/api/v1/goods/" + goodsId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/goods/" + goodsId)).andExpect(status().isOk());

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(조회수(goodsId)).isEqualTo(2);
        assertThat(goodsDailyStatRepository.findAll()).hasSize(1);
    }

    @Test
    void 목록_조회로는_조회수가_오르지_않는다() throws Exception {
        // 목록에 노출된 것과 상세를 연 것은 다른 사건이다.
        // 목록까지 세면 첫 페이지에 있다는 이유만으로 랭킹이 오르는 되먹임이 생긴다.
        상품_저장();

        mockMvc.perform(get("/api/v1/goods")).andExpect(status().isOk());

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(goodsDailyStatRepository.findAll()).isEmpty();
    }

    @Test
    void 없는_상품을_조회하면_통계를_남기지_않는다() throws Exception {
        // 404 응답에 통계를 남기면 존재하지 않는 goods_id로 원장이 오염된다.
        mockMvc.perform(get("/api/v1/goods/999999")).andExpect(status().isNotFound());

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(goodsDailyStatRepository.findAll()).isEmpty();
    }

    private Long 상품_저장() {
        Brand brand = brandRepository.save(new Brand("브랜드", null));
        Goods goods = goodsRepository.save(
                new Goods(brand, "C001001001", "토너", null, "https://img/1.jpg", 10000, 10000));
        return goods.getId();
    }

    private int 조회수(Long goodsId) {
        return goodsDailyStatRepository.findById(new GoodsDailyStat.Key(goodsId, LocalDate.now()))
                .map(GoodsDailyStat::getViewCount)
                .orElse(0);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*GoodsViewCountTest*'`
Expected: FAIL — `GoodsDailyStat` 등이 없어 컴파일 에러.

- [ ] **Step 3: 엔티티 구현** — `ranking/GoodsDailyStat.java`

```java
package com.beautyboy.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 상품×날짜 일별 통계. 랭킹 점수의 원장이다(설계 5장).
 *
 * <p>{@code goodsId}가 Goods 엔티티 참조가 아니라 스칼라인 이유: catalog는 타 도메인이라
 * 엔티티를 직접 참조할 수 없다(패키지 = 서비스 경계). FK도 걸지 않는다 — 통계는 상품이
 * 숨겨지거나 정리돼도 과거 기록으로 남아야 한다.
 *
 * <p>복합 PK (goods_id, stat_date)를 쓰는 이유: 조회수 증가가 "있으면 더하고 없으면 만들기"라
 * upsert(ON DUPLICATE KEY UPDATE)로 한 방에 끝나야 하기 때문이다.
 * 대리키 + unique 조합으로도 되지만 그러면 upsert가 unique 인덱스에 의존해 의도가 흐려진다.
 */
@Entity
@Table(name = "goods_daily_stat")
@IdClass(GoodsDailyStat.Key.class)
public class GoodsDailyStat {

    @Id
    @Column(name = "goods_id")
    private Long goodsId;

    @Id
    @Column(name = "stat_date")
    private LocalDate statDate;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "sales_count", nullable = false)
    private int salesCount;

    @Column(name = "wish_count", nullable = false)
    private int wishCount;

    protected GoodsDailyStat() {
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public LocalDate getStatDate() {
        return statDate;
    }

    public int getViewCount() {
        return viewCount;
    }

    public int getSalesCount() {
        return salesCount;
    }

    public int getWishCount() {
        return wishCount;
    }

    /** 복합 PK. JPA가 요구하는 대로 equals/hashCode와 기본 생성자를 갖춘다. */
    public static class Key implements Serializable {

        private Long goodsId;
        private LocalDate statDate;

        public Key() {
        }

        public Key(Long goodsId, LocalDate statDate) {
            this.goodsId = goodsId;
            this.statDate = statDate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key key)) {
                return false;
            }
            return Objects.equals(goodsId, key.goodsId) && Objects.equals(statDate, key.statDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(goodsId, statDate);
        }
    }
}
```

- [ ] **Step 4: 리포지토리 구현** — `ranking/GoodsDailyStatRepository.java`

```java
package com.beautyboy.ranking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface GoodsDailyStatRepository extends JpaRepository<GoodsDailyStat, GoodsDailyStat.Key> {

    /**
     * 조회수 upsert. 행이 없으면 만들고 있으면 더한다.
     *
     * <p>"조회 후 없으면 insert"로 짜면 동시 요청 2개가 동시에 '없음'을 보고 둘 다 insert해
     * PK 중복으로 하나가 500이 된다. DB에 원자적으로 맡긴다.
     *
     * <p>H2도 MySQL 모드에서 ON DUPLICATE KEY UPDATE를 지원하므로 테스트에서 같은 경로가 돈다.
     */
    @Modifying
    @Query(value = "insert into goods_daily_stat (goods_id, stat_date, view_count, sales_count, wish_count) "
            + "values (:goodsId, :statDate, :delta, 0, 0) "
            + "on duplicate key update view_count = view_count + :delta", nativeQuery = true)
    void upsertViewCount(@Param("goodsId") Long goodsId,
                         @Param("statDate") LocalDate statDate,
                         @Param("delta") int delta);

    /**
     * 판매·찜 수치 덮어쓰기. 배치가 Provider에서 받은 값을 그대로 반영한다(T1-7).
     *
     * <p>조회수와 달리 더하지 않고 **대입**한다 — Provider가 주는 값은 이미 그 날의 합계라
     * 배치가 두 번 돌면 더하기 방식은 값이 두 배가 된다(배치는 매시 도는데 날짜는 하루짜리다).
     */
    @Modifying
    @Query(value = "insert into goods_daily_stat (goods_id, stat_date, view_count, sales_count, wish_count) "
            + "values (:goodsId, :statDate, 0, :salesCount, :wishCount) "
            + "on duplicate key update sales_count = :salesCount, wish_count = :wishCount", nativeQuery = true)
    void upsertSalesAndWish(@Param("goodsId") Long goodsId,
                            @Param("statDate") LocalDate statDate,
                            @Param("salesCount") int salesCount,
                            @Param("wishCount") int wishCount);

    /** 기준일 이후 통계 전체. 배치가 최근 3일치를 한 번에 읽는다. */
    List<GoodsDailyStat> findByStatDateGreaterThanEqual(LocalDate from);
}
```

- [ ] **Step 5: 인터셉터 구현** — `ranking/GoodsViewCountInterceptor.java`

```java
package com.beautyboy.ranking;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 상품 상세 조회를 세는 인터셉터.
 *
 * <p>catalog의 서비스에 카운트 코드를 넣지 않은 이유가 둘이다.
 * (1) 경계: 조회수 집계는 랭킹의 관심사다. catalog가 ranking의 존재를 알 이유가 없다.
 * (2) 병렬: 이번 웨이브에서 catalog 파일은 T2(주문) 소유라 T1이 열면 충돌한다.
 *
 * <p>{@code afterCompletion}에서 세는 이유: 응답 상태를 봐야 한다.
 * 404(없는 상품)에 통계를 남기면 존재하지 않는 goods_id로 원장이 오염된다.
 *
 * <p>봇·새로고침 어뷰징은 걸러내지 않는다. MVP 범위이고, 랭킹 점수에서 조회는 가중치 1로
 * 판매(3)·찜(2)보다 낮게 잡혀 있어 단독으로 순위를 뒤집기 어렵다.
 */
@Component
public class GoodsViewCountInterceptor implements HandlerInterceptor {

    /** /api/v1/goods/{숫자} 만 센다. /description, /recommended, /ingredients는 상세 조회가 아니다. */
    private static final Pattern GOODS_DETAIL = Pattern.compile("^/api/v1/goods/(\\d+)$");

    private final GoodsDailyStatRepository goodsDailyStatRepository;

    public GoodsViewCountInterceptor(GoodsDailyStatRepository goodsDailyStatRepository) {
        this.goodsDailyStatRepository = goodsDailyStatRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (!HttpStatus.valueOf(response.getStatus()).is2xxSuccessful()) {
            return;
        }

        Matcher matcher = GOODS_DETAIL.matcher(request.getRequestURI());
        if (!matcher.matches()) {
            return;
        }

        goodsDailyStatRepository.upsertViewCount(
                Long.valueOf(matcher.group(1)), LocalDate.now(), 1);
    }
}
```

> `REQUIRES_NEW`인 이유: `afterCompletion`은 컨트롤러 트랜잭션이 이미 끝난 뒤에 돈다.
> 새 트랜잭션을 열지 않으면 `@Modifying` 쿼리가 트랜잭션 없이 실행돼 실패한다.

- [ ] **Step 6: 인터셉터 등록** — `ranking/RankingWebConfig.java`

```java
package com.beautyboy.ranking;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ranking이 자기 인터셉터를 스스로 등록한다.
 *
 * <p>공용 WebMvc 설정 파일을 만들지 않는 이유: 그런 파일이 생기는 순간 다음 웨이브의 여러 터미널이
 * 같은 파일에 인터셉터를 추가하려 들어 충돌 지점이 된다. 도메인이 자기 설정을 들고 있으면 그럴 일이 없다.
 */
@Configuration
public class RankingWebConfig implements WebMvcConfigurer {

    private final GoodsViewCountInterceptor goodsViewCountInterceptor;

    public RankingWebConfig(GoodsViewCountInterceptor goodsViewCountInterceptor) {
        this.goodsViewCountInterceptor = goodsViewCountInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(goodsViewCountInterceptor).addPathPatterns("/api/v1/goods/*");
    }
}
```

- [ ] **Step 7: green 확인**

Run: `./gradlew test --tests '*GoodsViewCountTest*'`
Expected: PASS (4 tests)

`두_번_보면_같은_행이_누적된다`가 PK 중복 예외로 실패하면 H2가 `on duplicate key update`를
못 알아들은 것이다 — 테스트 datasource URL에 `MODE=MySQL`이 살아 있는지 확인한다
(`application-test.yml`은 공유 계약이 아니지만 이 값은 건드리지 않는다).

- [ ] **Step 8: 전체 회귀 + 커밋**

Run: `./gradlew test`
Expected: PASS (105 tests)

```bash
git add src/main/java/com/beautyboy/ranking src/test/java/com/beautyboy/ranking/GoodsViewCountTest.java
git commit -m "feat(ranking): 상품 상세 조회수 집계 — 인터셉터 + goods_daily_stat upsert

catalog 서비스에 카운트를 심지 않은 이유는 경계(집계는 랭킹의 관심사)와
병렬(이번 웨이브에서 catalog는 T2 소유) 둘 다다."
```

---

## Task 7: 랭킹 배치 — 점수 계산 + 스냅샷 통째 교체

**근거:** 설계 5장 — "매시 배치: 점수 = 판매×3 + 찜×2 + 조회×1 (최근 3일 가중) → 트랜잭션 내 통째 교체.
조회는 스냅샷만 읽음."

**Files:**
- Create: `backend/src/main/java/com/beautyboy/ranking/RankingSnapshot.java`
- Create: `backend/src/main/java/com/beautyboy/ranking/RankingSnapshotRepository.java`
- Create: `backend/src/main/java/com/beautyboy/ranking/RankingBatchService.java`
- Create: `backend/src/main/java/com/beautyboy/ranking/RankingScheduler.java`
- Test: `backend/src/test/java/com/beautyboy/ranking/RankingBatchServiceTest.java`

**Interfaces:**
- Consumes: `SalesStatProvider.salesQuantityByGoods(LocalDate)` · `WishStatProvider.wishCountByGoods(LocalDate)`
  (**main에 이미 있는 확정 계약** — 수정 금지) · `GoodsDailyStatRepository`(T1-6).
- Produces: `RankingBatchService.rebuild()` — T1-8의 조회가 이 결과를 읽는다. 테스트가 직접 호출한다.

- [ ] **Step 1: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/ranking/RankingBatchServiceTest.java`

```java
package com.beautyboy.ranking;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.support.TestPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 랭킹 배치 테스트.
 *
 * <p>판매·찜은 타 도메인(order/wishlist)이 아직 구현하지 않았으므로 가짜 Provider를 주입한다.
 * 이것이 인터페이스로 가른 이유 그 자체다 — T2·T3를 기다리지 않고 랭킹을 완성할 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RankingBatchServiceTest {

    private static final LocalDate 오늘 = LocalDate.now();

    /**
     * goods_id 1은 오늘 3개 팔리고 5명이 찜했다. goods_id 2는 판매·찜이 없다.
     * 실 구현이 주입되면 폴백/이 가짜가 밀려나므로, 테스트는 항상 이 값을 본다.
     */
    @TestConfiguration
    static class 가짜_통계_공급자 {
        @Bean
        SalesStatProvider fakeSalesStatProvider() {
            return date -> date.equals(오늘) ? Map.of(1L, 3) : Map.of();
        }

        @Bean
        WishStatProvider fakeWishStatProvider() {
            return date -> date.equals(오늘) ? Map.of(1L, 5) : Map.of();
        }
    }

    @Autowired
    RankingBatchService rankingBatchService;
    @Autowired
    RankingSnapshotRepository rankingSnapshotRepository;
    @Autowired
    GoodsDailyStatRepository goodsDailyStatRepository;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    void 배치가_Provider_수치를_일별통계에_반영한다() {
        상품_저장("C001001001");

        rankingBatchService.rebuild();

        TestPersistence.DB_왕복_강제(entityManager);

        GoodsDailyStat stat = goodsDailyStatRepository
                .findById(new GoodsDailyStat.Key(1L, 오늘)).orElseThrow();
        assertThat(stat.getSalesCount()).isEqualTo(3);
        assertThat(stat.getWishCount()).isEqualTo(5);
    }

    @Test
    void 점수는_판매3_찜2_조회1_가중합이다() {
        상품_저장("C001001001");
        goodsDailyStatRepository.upsertViewCount(1L, 오늘, 10);

        TestPersistence.DB_왕복_강제(entityManager);

        rankingBatchService.rebuild();

        TestPersistence.DB_왕복_강제(entityManager);

        // 오늘 가중치 1.0 × (판매3×3 + 찜5×2 + 조회10×1) = 29.0
        RankingSnapshot snapshot = rankingSnapshotRepository
                .findByCategoryCodeOrderByRankNoAsc("ALL").get(0);
        assertThat(snapshot.getGoodsId()).isEqualTo(1L);
        assertThat(snapshot.getScore()).isEqualTo(29.0);
    }

    @Test
    void 오래된_날짜일수록_가중치가_낮다() {
        상품_저장("C001001001");
        // 어제 조회 10 → 가중치 0.6 → 6.0. 오늘 것(판매·찜)과 합산된다.
        goodsDailyStatRepository.upsertViewCount(1L, 오늘.minusDays(1), 10);

        TestPersistence.DB_왕복_강제(entityManager);

        rankingBatchService.rebuild();

        TestPersistence.DB_왕복_강제(entityManager);

        // 오늘 1.0×(3×3 + 5×2 + 0) = 19.0, 어제 0.6×(0 + 0 + 10) = 6.0 → 25.0
        assertThat(rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc("ALL").get(0).getScore())
                .isEqualTo(25.0);
    }

    @Test
    void 3일보다_오래된_통계는_점수에_들어가지_않는다() {
        상품_저장("C001001001");
        goodsDailyStatRepository.upsertViewCount(1L, 오늘.minusDays(5), 1000);

        TestPersistence.DB_왕복_강제(entityManager);

        rankingBatchService.rebuild();

        TestPersistence.DB_왕복_강제(entityManager);

        // 1000이 새어 들어오면 19.0이 아니다. "한 번 뜬 상품이 영원히 1위"를 막는 창이다.
        assertThat(rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc("ALL").get(0).getScore())
                .isEqualTo(19.0);
    }

    @Test
    void 대분류별_랭킹이_따로_만들어진다() {
        상품_저장("C001001001");   // 스킨케어(C001)
        상품_저장("C002001001");   // 클렌징(C002)

        rankingBatchService.rebuild();

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc("C001")).hasSize(1);
        assertThat(rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc("C002")).hasSize(1);
        assertThat(rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc("ALL")).hasSize(2);
    }

    @Test
    void 순위는_1부터_빈틈없이_매겨진다() {
        상품_저장("C001001001");
        상품_저장("C001001001");

        rankingBatchService.rebuild();

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc("ALL"))
                .extracting(RankingSnapshot::getRankNo)
                .containsExactly(1, 2);
    }

    @Test
    void 다시_돌리면_이전_스냅샷이_남지_않고_통째로_교체된다() {
        상품_저장("C001001001");

        rankingBatchService.rebuild();
        rankingBatchService.rebuild();

        TestPersistence.DB_왕복_강제(entityManager);

        // 누적되면 (category_code, rank_no) 유니크 제약에 걸리거나 순위가 중복된다.
        assertThat(rankingSnapshotRepository.findAll()).hasSize(2);   // ALL 1건 + C001 1건
    }

    @Test
    void 숨김_상품은_랭킹에_오르지_않는다() {
        Brand brand = brandRepository.save(new Brand("브랜드", null));
        Goods hidden = new Goods(brand, "C001001001", "숨김", null, "https://img/x.jpg", 10000, 10000);
        hidden.hide();
        goodsRepository.save(hidden);

        rankingBatchService.rebuild();

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(rankingSnapshotRepository.findAll()).isEmpty();
    }

    private void 상품_저장(String categoryCode) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        goodsRepository.save(new Goods(brand, categoryCode, "상품", null, "https://img/x.jpg", 10000, 10000));
    }
}
```

> **주의:** `점수는_판매3_찜2_조회1_가중합이다`와 그 뒤 테스트들은 가짜 Provider가 `goodsId=1`을 가리키므로
> **테스트 DB에서 첫 상품의 id가 1이어야** 성립한다. `@Transactional` 롤백은 auto_increment를 되돌리지 않으므로
> 클래스 안에서 id가 계속 커진다. **구현 후 이 테스트가 "찾을 수 없음"으로 실패하면 가짜 Provider를
> 고정 id가 아니라 "그 시점의 모든 상품 id"를 반환하도록 바꾸지 말고, 각 테스트가 저장한 상품의 실제 id를
> 읽어 단언하도록 고쳐라** — 가짜를 넓히면 무엇을 검증하는지가 흐려진다.
>
> 구체적으로: `상품_저장(...)`이 `Long`을 반환하게 바꾸고, 가짜 Provider를 `@TestConfiguration` 대신
> 테스트별 `Map`을 참조하는 가변 홀더로 두는 방식이 가장 단순하다. 이 조정은 Step 5에서 실제 실패를 보고 결정한다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*RankingBatchServiceTest*'`
Expected: FAIL — `RankingSnapshot` 등이 없어 컴파일 에러.

- [ ] **Step 3: 스냅샷 엔티티 구현** — `ranking/RankingSnapshot.java`

```java
package com.beautyboy.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 매시 배치가 만드는 랭킹 결과 1행. 조회는 이 테이블만 읽는다(설계 5장).
 *
 * <p>조회 시점에 점수를 계산하지 않는 이유: 랭킹은 메인에서 모든 방문자에게 노출되는데
 * 매 요청마다 3일치 통계를 집계·정렬하면 가장 흔한 요청이 가장 무거워진다.
 * 미리 계산해 두고 읽기만 한다.
 *
 * <p>{@code categoryCode}의 {@code "ALL"}은 전체 랭킹을 뜻하는 예약값이다.
 */
@Entity
@Table(name = "ranking_snapshot")
public class RankingSnapshot {

    /** 전체 랭킹을 가리키는 예약 카테고리 코드. 실제 카테고리 코드는 'C'로 시작하므로 충돌하지 않는다. */
    public static final String CATEGORY_ALL = "ALL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_code", nullable = false, length = 12)
    private String categoryCode;

    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    @Column(nullable = false)
    private double score;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    protected RankingSnapshot() {
    }

    public RankingSnapshot(String categoryCode, Long goodsId, int rankNo, double score, LocalDateTime generatedAt) {
        this.categoryCode = categoryCode;
        this.goodsId = goodsId;
        this.rankNo = rankNo;
        this.score = score;
        this.generatedAt = generatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public int getRankNo() {
        return rankNo;
    }

    public double getScore() {
        return score;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }
}
```

- [ ] **Step 4: 스냅샷 리포지토리 구현** — `ranking/RankingSnapshotRepository.java`

```java
package com.beautyboy.ranking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RankingSnapshotRepository extends JpaRepository<RankingSnapshot, Long> {

    List<RankingSnapshot> findByCategoryCodeOrderByRankNoAsc(String categoryCode);
}
```

- [ ] **Step 5: 배치 서비스 구현** — `ranking/RankingBatchService.java`

```java
package com.beautyboy.ranking;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 랭킹 스냅샷 재생성 배치.
 *
 * <p>순서: (1) 타 도메인 Provider에서 오늘의 판매·찜을 받아 일별 통계에 반영 →
 * (2) 최근 3일 통계를 읽어 가중 점수 계산 → (3) 스냅샷을 트랜잭션 안에서 통째 교체.
 *
 * <p>(3)이 한 트랜잭션인 것이 핵심이다. 지우고 커밋한 뒤 새로 넣으면 그 사이에 들어온 조회 요청이
 * "랭킹 없음"을 본다. 매시 몇 초씩 랭킹이 사라지는 것은 장애로 보인다.
 */
@Service
public class RankingBatchService {

    /** 설계 5장: 판매×3 + 찜×2 + 조회×1. */
    private static final int SALES_WEIGHT = 3;
    private static final int WISH_WEIGHT = 2;
    private static final int VIEW_WEIGHT = 1;

    /**
     * 최근 3일 가중치(오늘 → 그저께). 오늘 일어난 일을 가장 크게 본다.
     *
     * <p>이 값이 랭킹의 성격을 정한다 — 평평하게 두면 3일 내내 같은 순위가 굳고,
     * 너무 가파르면 하루 반짝한 상품이 계속 1위를 갈아치운다. 1.0 / 0.6 / 0.3은
     * "어제 것이 오늘 것의 절반보다 조금 더" 정도의 감쇠다.
     */
    private static final double[] DAY_WEIGHTS = {1.0, 0.6, 0.3};

    /** 카테고리당 보관하는 최대 순위. 랭킹 화면이 그 이상 보여주지 않는다. */
    private static final int MAX_RANK = 50;

    private final GoodsDailyStatRepository goodsDailyStatRepository;
    private final RankingSnapshotRepository rankingSnapshotRepository;
    private final SalesStatProvider salesStatProvider;
    private final WishStatProvider wishStatProvider;
    private final EntityManager em;

    public RankingBatchService(GoodsDailyStatRepository goodsDailyStatRepository,
                               RankingSnapshotRepository rankingSnapshotRepository,
                               SalesStatProvider salesStatProvider,
                               WishStatProvider wishStatProvider,
                               EntityManager em) {
        this.goodsDailyStatRepository = goodsDailyStatRepository;
        this.rankingSnapshotRepository = rankingSnapshotRepository;
        this.salesStatProvider = salesStatProvider;
        this.wishStatProvider = wishStatProvider;
        this.em = em;
    }

    @Transactional
    public void rebuild() {
        LocalDate today = LocalDate.now();

        수집_판매와_찜(today);
        Map<Long, Double> scoreByGoodsId = 점수_계산(today);
        스냅샷_통째_교체(scoreByGoodsId, LocalDateTime.now());
    }

    /** Provider가 준 오늘 수치를 일별 통계에 대입한다. 조회수는 인터셉터가 이미 실시간으로 채워 놨다. */
    private void 수집_판매와_찜(LocalDate today) {
        Map<Long, Integer> sales = salesStatProvider.salesQuantityByGoods(today);
        Map<Long, Integer> wishes = wishStatProvider.wishCountByGoods(today);

        for (Long goodsId : union(sales.keySet(), wishes.keySet())) {
            goodsDailyStatRepository.upsertSalesAndWish(
                    goodsId, today, sales.getOrDefault(goodsId, 0), wishes.getOrDefault(goodsId, 0));
        }
        // upsert는 네이티브 쿼리라 영속성 컨텍스트를 우회한다.
        // 바로 아래에서 같은 행을 읽으므로 캐시를 비워 DB 값을 보게 한다.
        em.flush();
        em.clear();
    }

    private Map<Long, Double> 점수_계산(LocalDate today) {
        LocalDate from = today.minusDays(DAY_WEIGHTS.length - 1L);
        List<GoodsDailyStat> stats = goodsDailyStatRepository.findByStatDateGreaterThanEqual(from);

        Map<Long, Double> scoreByGoodsId = new HashMap<>();
        for (GoodsDailyStat stat : stats) {
            int daysAgo = (int) (today.toEpochDay() - stat.getStatDate().toEpochDay());
            // 미래 날짜(시계 오차)나 창 밖은 건너뛴다.
            if (daysAgo < 0 || daysAgo >= DAY_WEIGHTS.length) {
                continue;
            }
            double weighted = DAY_WEIGHTS[daysAgo] * (
                    stat.getSalesCount() * SALES_WEIGHT
                            + stat.getWishCount() * WISH_WEIGHT
                            + stat.getViewCount() * VIEW_WEIGHT);
            scoreByGoodsId.merge(stat.getGoodsId(), weighted, Double::sum);
        }
        return scoreByGoodsId;
    }

    /**
     * 스냅샷 교체.
     *
     * <p>상품의 카테고리는 catalog 소유라 엔티티로 읽을 수 없다 — 필요한 것은 goods_id → category_code
     * 매핑 하나뿐이므로 네이티브 쿼리로 최소한만 읽는다. 이것이 타 도메인 리포지토리를 import하는 것보다
     * 결합이 얕다(테이블 이름 하나만 안다).
     */
    private void 스냅샷_통째_교체(Map<Long, Double> scoreByGoodsId, LocalDateTime generatedAt) {
        rankingSnapshotRepository.deleteAllInBatch();

        if (scoreByGoodsId.isEmpty()) {
            return;
        }

        Map<Long, String> categoryByGoodsId = 노출중인_상품의_대분류();

        // 카테고리별 버킷. 'ALL'은 전 상품이 들어간다.
        Map<String, List<Map.Entry<Long, Double>>> bucketByCategory = new LinkedHashMap<>();
        for (Map.Entry<Long, Double> entry : scoreByGoodsId.entrySet()) {
            String category = categoryByGoodsId.get(entry.getKey());
            // 숨김·삭제된 상품은 매핑에 없다 — 랭킹에 올리지 않는다.
            if (category == null) {
                continue;
            }
            bucketByCategory.computeIfAbsent(RankingSnapshot.CATEGORY_ALL, k -> new ArrayList<>()).add(entry);
            bucketByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(entry);
        }

        List<RankingSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, List<Map.Entry<Long, Double>>> bucket : bucketByCategory.entrySet()) {
            List<Map.Entry<Long, Double>> sorted = new ArrayList<>(bucket.getValue());
            // 점수 내림차순, 동점이면 goodsId 오름차순 — 2차 키가 없으면 배치마다 순위가 흔들린다.
            sorted.sort(Comparator.<Map.Entry<Long, Double>>comparingDouble(Map.Entry::getValue).reversed()
                    .thenComparing(Map.Entry::getKey));

            int rank = 1;
            for (Map.Entry<Long, Double> entry : sorted) {
                if (rank > MAX_RANK) {
                    break;
                }
                snapshots.add(new RankingSnapshot(
                        bucket.getKey(), entry.getKey(), rank++, entry.getValue(), generatedAt));
            }
        }

        rankingSnapshotRepository.saveAll(snapshots);
    }

    private Map<Long, String> 노출중인_상품의_대분류() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "select id, substring(category_code, 1, 4) from goods where status <> 'HIDDEN'")
                .getResultList();

        Map<Long, String> categoryByGoodsId = new HashMap<>();
        for (Object[] row : rows) {
            categoryByGoodsId.put(((Number) row[0]).longValue(), (String) row[1]);
        }
        return categoryByGoodsId;
    }

    private List<Long> union(java.util.Set<Long> a, java.util.Set<Long> b) {
        java.util.Set<Long> merged = new java.util.LinkedHashSet<>(a);
        merged.addAll(b);
        return List.copyOf(merged);
    }
}
```

- [ ] **Step 6: 스케줄러 구현** — `ranking/RankingScheduler.java`

```java
package com.beautyboy.ranking;

import com.beautyboy.search.PopularKeywordHolder;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매시 배치 트리거.
 *
 * <p>{@code @Profile("!test")}인 이유: 테스트에서 스케줄러가 저절로 돌면 테스트가 만든 픽스처를
 * 배치가 덮어쓰거나, 테스트가 검증하려던 스냅샷을 지워 간헐적으로 실패한다.
 * 테스트는 배치 메서드를 직접 호출해 검증한다.
 *
 * <p>{@code @EnableScheduling}이 여기 붙어 있는 것은 앱 전체 스케줄링을 켠다는 뜻이다 —
 * 인기검색어 집계도 이 설정에 얹혀 돈다. 스케줄 대상이 늘면 이 클래스에 메서드를 추가한다.
 *
 * <p>두 집계를 한 클래스에 둔 이유: 배치가 늘 때마다 도메인마다 스케줄러 클래스를 만들면
 * {@code @EnableScheduling}이 어디 붙어 있는지 추적하기 어려워진다. 트리거는 한 곳, 로직은 각 도메인.
 */
@Component
@Profile("!test")
@EnableScheduling
public class RankingScheduler {

    private final RankingBatchService rankingBatchService;
    private final PopularKeywordHolder popularKeywordHolder;

    public RankingScheduler(RankingBatchService rankingBatchService,
                            PopularKeywordHolder popularKeywordHolder) {
        this.rankingBatchService = rankingBatchService;
        this.popularKeywordHolder = popularKeywordHolder;
    }

    /** 매시 정각. 초기 지연 없이 부팅 직후 한 번 돌리지 않는 이유: 부팅 시점에 통계가 비어 있어도 정상이다. */
    @Scheduled(cron = "0 0 * * * *")
    public void 랭킹_재생성() {
        rankingBatchService.rebuild();
    }

    /** 인기검색어도 매시 갱신한다(설계 8장). 랭킹과 5분 어긋나게 둬 DB 부하가 겹치지 않게 한다. */
    @Scheduled(cron = "0 5 * * * *")
    public void 인기검색어_갱신() {
        popularKeywordHolder.refresh();
    }
}
```

- [ ] **Step 7: green 확인**

Run: `./gradlew test --tests '*RankingBatchServiceTest*'`
Expected: PASS (8 tests)

Step 1의 주의를 읽어라 — `goodsId=1` 전제로 실패하면 거기 적힌 방향으로 테스트를 조정한다.
`다시_돌리면_이전_스냅샷이...`가 유니크 제약 위반으로 실패하면 `deleteAllInBatch()`가
같은 트랜잭션 안에서 flush되지 않은 것이다. `deleteAllInBatch()` 뒤에 `em.flush()`를 추가한다.

- [ ] **Step 8: 전체 회귀 + 커밋**

Run: `./gradlew test`
Expected: PASS (113 tests)

```bash
git add src/main/java/com/beautyboy/ranking src/test/java/com/beautyboy/ranking/RankingBatchServiceTest.java
git commit -m "feat(ranking): 매시 배치 — 최근 3일 가중 점수 + 스냅샷 통째 교체

교체가 한 트랜잭션인 이유는 지우고 커밋한 뒤 넣으면 그 사이 조회가
'랭킹 없음'을 보기 때문이다. 매시 몇 초씩 랭킹이 사라지면 장애로 보인다."
```

---

## Task 8: `GET /rankings` + 최종 검증

**근거:** 설계 7장 공개 목록 — `GET /rankings?categoryCode=`.

**Files:**
- Create: `backend/src/main/java/com/beautyboy/ranking/dto/RankingItem.java`
- Create: `backend/src/main/java/com/beautyboy/ranking/RankingService.java`
- Create: `backend/src/main/java/com/beautyboy/ranking/RankingController.java`
- Test: `backend/src/test/java/com/beautyboy/ranking/RankingApiTest.java`

**Interfaces:**
- Consumes: `RankingSnapshotRepository`(T1-7).
- Produces: `GET /api/v1/rankings?categoryCode=` → `ApiResponse<List<RankingItem>>`.
  `categoryCode` 생략 시 전체(`ALL`).

- [ ] **Step 1: 실패 테스트 작성** — `backend/src/test/java/com/beautyboy/ranking/RankingApiTest.java`

```java
package com.beautyboy.ranking;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RankingApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    RankingSnapshotRepository rankingSnapshotRepository;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    @Test
    void 스냅샷_순서대로_상품_정보와_함께_준다() throws Exception {
        Long 일위 = 상품_저장("1위 토너");
        Long 이위 = 상품_저장("2위 토너");
        스냅샷_저장("ALL", 일위, 1, 100.0);
        스냅샷_저장("ALL", 이위, 2, 50.0);

        mockMvc.perform(get("/api/v1/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].name").value("1위 토너"))
                .andExpect(jsonPath("$.data[1].rank").value(2));
    }

    @Test
    void categoryCode로_해당_카테고리_랭킹만_준다() throws Exception {
        Long 스킨케어 = 상품_저장("스킨케어 상품");
        스냅샷_저장("C001", 스킨케어, 1, 10.0);
        스냅샷_저장("ALL", 스킨케어, 1, 10.0);

        mockMvc.perform(get("/api/v1/rankings").param("categoryCode", "C001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("스킨케어 상품"));
    }

    @Test
    void 아직_집계된_랭킹이_없으면_빈_목록이다() throws Exception {
        // 부팅 직후 배치가 한 번도 안 돈 상태. 500이나 404가 아니라 빈 목록이어야
        // 프론트가 "아직 없음" 화면을 자연스럽게 그린다.
        mockMvc.perform(get("/api/v1/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 비로그인도_조회할_수_있다() throws Exception {
        // 설계 7장 공개 목록. 토큰 없이 200이어야 한다.
        mockMvc.perform(get("/api/v1/rankings")).andExpect(status().isOk());
    }

    private Long 상품_저장(String name) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        return goodsRepository.save(
                new Goods(brand, "C001001001", name, null, "https://img/x.jpg", 20000, 16000)).getId();
    }

    private void 스냅샷_저장(String categoryCode, Long goodsId, int rankNo, double score) {
        rankingSnapshotRepository.save(
                new RankingSnapshot(categoryCode, goodsId, rankNo, score, LocalDateTime.now()));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*RankingApiTest*'`
Expected: FAIL — 404 (핸들러 없음)

- [ ] **Step 3: DTO 구현** — `ranking/dto/RankingItem.java`

```java
package com.beautyboy.ranking.dto;

/**
 * 랭킹 1행. 카드에 필요한 상품 정보를 함께 담는다 —
 * 프론트가 순위 목록을 받고 상품마다 상세를 또 호출하면 N+1 요청이 된다.
 */
public record RankingItem(
        int rank,
        Long goodsNo,
        String brandName,
        String name,
        String thumbnailUrl,
        int listPrice,
        int salePrice,
        int discountRate,
        double score) {
}
```

- [ ] **Step 4: 서비스 구현** — `ranking/RankingService.java`

```java
package com.beautyboy.ranking;

import com.beautyboy.ranking.dto.RankingItem;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 랭킹 조회. 스냅샷만 읽고 점수를 다시 계산하지 않는다(설계 5장).
 *
 * <p>상품 정보를 붙이는 방식: 스냅샷에서 goods_id 목록을 뽑아 <b>1쿼리로 일괄 조회</b>한 뒤
 * 메모리에서 합친다. 순위마다 상품을 조회하면 50번 왕복하는 N+1이 된다.
 * catalog 리포지토리를 import하지 않기 위해 필요한 컬럼만 네이티브로 읽는다.
 */
@Service
public class RankingService {

    private final RankingSnapshotRepository rankingSnapshotRepository;
    private final EntityManager em;

    public RankingService(RankingSnapshotRepository rankingSnapshotRepository, EntityManager em) {
        this.rankingSnapshotRepository = rankingSnapshotRepository;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public List<RankingItem> rankings(String categoryCode) {
        String category = (categoryCode == null || categoryCode.isBlank())
                ? RankingSnapshot.CATEGORY_ALL
                : categoryCode;

        List<RankingSnapshot> snapshots =
                rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc(category);
        if (snapshots.isEmpty()) {
            return List.of();
        }

        Map<Long, Object[]> goodsById = 상품_일괄_조회(
                snapshots.stream().map(RankingSnapshot::getGoodsId).toList());

        List<RankingItem> items = new ArrayList<>();
        for (RankingSnapshot snapshot : snapshots) {
            Object[] goods = goodsById.get(snapshot.getGoodsId());
            // 배치 이후 숨겨진 상품은 스냅샷에 남아 있을 수 있다. 순위를 비우지 않고 그 행만 건너뛴다
            // (재계산은 다음 배치의 몫이다 — 조회 요청이 랭킹을 고치기 시작하면 읽기 경로가 무거워진다).
            if (goods == null) {
                continue;
            }
            int listPrice = ((Number) goods[3]).intValue();
            int salePrice = ((Number) goods[4]).intValue();
            items.add(new RankingItem(
                    snapshot.getRankNo(),
                    snapshot.getGoodsId(),
                    (String) goods[0],
                    (String) goods[1],
                    (String) goods[2],
                    listPrice,
                    salePrice,
                    discountRate(listPrice, salePrice),
                    snapshot.getScore()));
        }
        return items;
    }

    private Map<Long, Object[]> 상품_일괄_조회(List<Long> goodsIds) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "select g.id, b.name, g.name, g.thumbnail_url, g.list_price, g.sale_price "
                                + "from goods g join brand b on g.brand_id = b.id "
                                + "where g.id in (:goodsIds) and g.status <> 'HIDDEN'")
                .setParameter("goodsIds", goodsIds)
                .getResultList();

        Map<Long, Object[]> goodsById = new HashMap<>();
        for (Object[] row : rows) {
            goodsById.put(((Number) row[0]).longValue(),
                    new Object[]{row[1], row[2], row[3], row[4], row[5]});
        }
        return goodsById;
    }

    private int discountRate(int listPrice, int salePrice) {
        if (listPrice == 0) {
            return 0;
        }
        return (listPrice - salePrice) * 100 / listPrice;
    }
}
```

- [ ] **Step 5: 컨트롤러 구현** — `ranking/RankingController.java`

```java
package com.beautyboy.ranking;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.ranking.dto.RankingItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    /** categoryCode 생략 시 전체 랭킹. 설계 7장 공개 목록이라 인증이 필요 없다. */
    @GetMapping("/api/v1/rankings")
    public ResponseEntity<ApiResponse<List<RankingItem>>> rankings(
            @RequestParam(required = false) String categoryCode) {
        return ResponseEntity.ok(ApiResponse.ok(rankingService.rankings(categoryCode)));
    }
}
```

- [ ] **Step 6: green 확인**

Run: `./gradlew test --tests '*RankingApiTest*'`
Expected: PASS (4 tests)

- [ ] **Step 7: 전체 검증**

```bash
./gradlew test
./gradlew integrationTest
```

Expected: `test` 117개 · `integrationTest` 9개(스모크 4 + FULLTEXT 5) 전부 통과.

- [ ] **Step 8: 공개 엔드포인트가 실제로 비로그인 접근 가능한지 확인**

`SecurityConfig`는 수정 대상이 아니지만, 선반영된 경로가 실제 우리 URL과 맞는지는 확인해야 한다.

Run: `./gradlew test --tests '*SecurityErrorHandlingTest*'`
Expected: PASS (13 tests, 회귀 없음)

`RankingApiTest.비로그인도_조회할_수_있다`와 `SearchApiTest`가 401을 받으면
경로가 `SecurityConfig`의 선반영 목록과 어긋난 것이다 — **파일을 고치지 말고 보고한다**
(설계 7장을 먼저 고쳐야 하는 사안이다).

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/beautyboy/ranking src/test/java/com/beautyboy/ranking/RankingApiTest.java
git commit -m "feat(ranking): GET /rankings — 스냅샷 조회 + 상품 정보 일괄 결합"
```

- [ ] **Step 10: 보고**

오케스트레이터 리뷰용 보고서에 아래를 남긴다:

- `./gradlew test` · `./gradlew integrationTest` 출력 (건수 포함)
- 만든 Flyway 버전(V20·V21·V22)과 각 테이블
- **알려진 미완 사항 명시:**
  1. 검색 결과의 `badges`가 항상 빈 목록 — promotion 조인이 catalog 소유라 이번 웨이브에서 못 붙였다. Wave 4 통합 몫.
  2. `rating`/`reviewCount`/`wished`는 계약상 기본값 — Wave 2 T3(review·wishlist) 머지 후 채워진다.
  3. 인기검색어가 인메모리라 앱 1대 전제 — 다중화 시 `PopularKeywordHolder`가 교체 지점.
  4. `SalesStatProvider`/`WishStatProvider`가 **폴백(빈 맵)으로 도는 중** — T2·T3 머지 전까지 랭킹은
     사실상 조회수 랭킹이다. **통합 시 실 구현이 주입되는지 반드시 확인할 것.**

---

## 통합 마무리 (오케스트레이터)

- [ ] T1~T8 전 태스크 리뷰 통과(테스트 green + Files 목록 준수) 후 `feat/search-ranking`을 main에 머지.
- [ ] worktree 정리: `git worktree remove ../BeautyBoy-w2-search`
- [ ] 로드맵의 Flyway 대역 표에 실제 사용 버전(V20·V21·V22) 기록.
- [ ] **T2·T3 머지 후** `RankingStatFallbackAutoConfiguration`이 더 이상 선택되지 않는지 확인 —
      조용히 폴백이 남으면 랭킹이 영원히 조회수 랭킹이 된다.

---

## 실행 프롬프트 (터미널에 그대로 붙여넣기)

프로젝트 루트에서 새 터미널을 열고 Claude Code를 실행한 뒤, 아래를 **그대로** 붙여넣는다.

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.

  git worktree add ../BeautyBoy-w2-search -b feat/search-ranking

를 실행한 뒤 EnterWorktree 도구에 path로 그 경로(../BeautyBoy-w2-search)를 넘겨 세션을 그 안으로 옮겨라.
EnterWorktree를 name으로 새로 만들지 마라 — origin에서 브랜치를 따서 계획서도 참조 문서도 없는 worktree가 생긴다.

진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 .../BeautyBoy-w2-search 인지
  - git log --oneline -1 이 0e990a7 (chore(backend): Wave 2 병렬 분기 전 사전 정리) 이후인지
  - ls docs/superpowers/plans/2026-07-24-wave2-search-ranking.md CLAUDE.md 가 성공하는지
  - ls backend/src/main/java/com/beautyboy/ranking/SalesStatProvider.java 가 성공하는지
    (없으면 기점이 틀린 것이다 — 반드시 중단하고 보고해라)
  - git status 가 깨끗한지
  - cd backend && ./gradlew test 가 91개 통과로 green인지

[2단계 — 실행]

CLAUDE.md와 docs/superpowers/plans/2026-07-24-wave2-search-ranking.md를 읽고, 그 계획서의
Task 1부터 Task 8까지를 순서대로 실행해라.

너는 오케스트레이터다. 직접 구현하지 말고, 태스크마다 서브에이전트(model: sonnet)를 스폰해
TDD로 구현시켜라. 태스크 사이마다 아래를 리뷰한 뒤 다음으로 넘어가라:
  - 해당 태스크의 테스트가 실제로 통과하는가 (출력을 눈으로 확인)
  - 그 태스크의 Files 목록 밖 파일을 건드리지 않았는가
  - 특히 catalog/**, common/ErrorCode.java, config/SecurityConfig.java, build.gradle.kts를
    건드렸다면 즉시 되돌려라 — 전부 다른 터미널과 공유하는 계약이다

계획서의 Global Constraints를 모든 서브에이전트 프롬프트에 그대로 포함시켜라.
특히 "Redis 금지", "catalog 미접촉", "SalesStatProvider/WishStatProvider 시그니처 수정 금지"는
어기면 다른 터미널이 깨지므로 반드시 전달해라.

Task 3과 Task 8의 통합 테스트는 Docker가 필요하다(./gradlew integrationTest).
Docker가 없으면 그 스텝에서 중단하고 보고해라 — 유닛테스트 통과로 대체하지 마라.

전 태스크 완료 후 ./gradlew test 와 ./gradlew integrationTest 결과, 그리고 계획서 Task 8 Step 10의
"알려진 미완 사항" 4가지를 보고해라.
```

---

## Self-Review (계획 대 spec)

**1. Spec 커버리지** — 설계 7장 공개 목록 중 이 계획의 몫과 8장 검색 요구를 매핑:

| 사양 항목 | 태스크 |
|---|---|
| `GET /search?q=&sort=&page=` | T1-2 |
| `GET /search/autocomplete?q=` (prefix 10개) | T1-4 |
| `GET /search/popular-keywords` | T1-5 |
| `GET /rankings?categoryCode=` | T1-8 |
| MySQL FULLTEXT + ngram 파서 | T1-1(인덱스) · T1-3(질의) |
| 자동완성은 prefix LIKE | T1-3 `autocomplete` |
| 검색 로그 → 매시 24시간 집계 → 인기검색어 캐시 | T1-5 · T1-7(스케줄) |
| 검색 모듈 인터페이스 분리(Elasticsearch 교체 지점) | T1-2 `GoodsSearchRepository` |
| `goods_daily_stat`(조회/판매/찜) | T1-1(스키마) · T1-6(조회) · T1-7(판매·찜) |
| 매시 배치 점수 = 판매×3+찜×2+조회×1, 최근 3일 가중 | T1-7 |
| 트랜잭션 내 통째 교체, 조회는 스냅샷만 | T1-7 · T1-8 |
| `PageResponse<T>` 공통 페이징 | T1-2 |
| 조회수 Redis INCR | **의도적 제외** — 로드맵 2026-07-24 결정(병렬 안전). DB 카운터로 대체, T1-6. |

**사양에 없었으나 추가한 것:** T1-3의 `mysql-search` 프로필 분기. 스코프 확장이 아니라 **테스트 가능성의 대가**다 —
H2에 FULLTEXT가 없어 구현을 하나만 두면 유닛테스트 전체가 실 MySQL을 요구하게 되고, 그러면 터미널 병렬이 깨진다
(로드맵 §5 "단위/슬라이스는 H2 — 터미널 병렬 안전").

**2. 플레이스홀더 스캔** — TBD·"적절히 처리"·"위와 유사" 없음. 모든 코드 스텝이 완전한 코드 블록을 포함한다.
T1-7 Step 1의 `goodsId=1` 전제는 실패 가능성을 **미리 적고 대응 방향까지 지정**했다(모호한 지시가 아니라
"실패를 보고 Step 5에서 결정"이라는 명시적 분기).

**3. 타입 일관성** — 교차 확인:
- `GoodsSearchRepository.SearchRow`의 6필드를 T1-2(LIKE)·T1-3(FULLTEXT)이 같은 순서·타입으로 만들고 T1-2 서비스가 소비한다.
- `SearchCondition(keyword, sort, page, size)`를 컨트롤러가 만들고 두 리포지토리 구현이 같은 접근자로 읽는다.
- `SearchSort`의 4상수를 두 구현의 `switch`가 모두 빠짐없이 다룬다(enum switch라 누락 시 컴파일 경고).
- `GoodsDailyStat.Key(goodsId, statDate)`를 T1-6 테스트와 T1-7 배치가 같은 형태로 쓴다.
- `SalesStatProvider.salesQuantityByGoods(LocalDate)` / `WishStatProvider.wishCountByGoods(LocalDate)` —
  **main에 이미 있는 시그니처를 그대로** T1-7이 호출한다. 계획 안에서 새로 정의하지 않았다.
- `RankingSnapshot.CATEGORY_ALL`을 T1-7이 쓰고 T1-8이 기본값으로 읽는다 — 문자열 `"ALL"`을 따로 적어둔 곳이 없다.
- `PopularKeywordHolder.refresh()`를 T1-5가 정의하고 T1-7 스케줄러가 호출한다.

**4. 태스크 경계** — 각 태스크가 독립적으로 테스트 가능하고, 리뷰어가 이웃을 통과시키면서 하나만 거절할 수 있다.
T1-4·T1-5가 T1-2의 파일(`SearchService`·`SearchController`)을 수정하는데, 이는 같은 터미널의 순차 작업이라
충돌 대상이 아니며 각 태스크가 자기 테스트로 회귀를 잡는다.
