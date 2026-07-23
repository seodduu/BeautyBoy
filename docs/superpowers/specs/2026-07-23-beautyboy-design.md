# 뷰티보이(BeautyBoy) 설계 문서

> 올리브영 클론 기반 **남성 화장품 커머스 플랫폼**. 2026-07-23 브레인스토밍 확정본.

## 1. 개요

- **컨셉**: 올리브영의 기본 기능을 모두 갖춘 커머스 + 화장품을 잘 모르는 남성 사용자를 위한 **루틴 가이드**와 **성분 궁합 분석**으로 차별화.
- **목적**: 취업 포트폴리오. "완성도 있는 범위 + 설계 이유 설명 가능"을 최우선 가치로 둔다.
- **스택**: React SPA (Vite + TypeScript) / Spring Boot 모듈러 모놀리스 / MySQL / (선택) Redis.
- **페르소나**: 화장품 종류(토너/로션/세럼)와 사용 순서를 모르는 남성. 탐색보다 랭킹·추천으로 바로 구매하는 성향.

### 비목표 (하지 않는 것)
- 바코드 스캔 (확정 제외)
- 실제 배송/배차, 실결제 (토스페이먼츠 테스트 키만 사용)
- 관리자 화면의 완성도 (최소 CRUD만)
- SEO/SSR (포트폴리오이므로 불필요 — Next.js 미채택 근거)

## 2. 올리브영 실측 분석 요약 (2026-07-23 브라우저 분석)

| 관찰 | 내용 | 우리 설계 반영 |
|---|---|---|
| 하이브리드 아키텍처 | 레거시 Spring MVC(.do, SSR) + 신규 Next.js(PDP, 내부명 lavender) 공존 | 모놀리스로 시작하되 도메인 경계를 명확히 |
| 마이크로서비스 API | `/goods/api/v1/*`, `/claim-front/api/v1/*`, `/search/api/v1/*` 도메인별 분리 | API 경로를 도메인 프리픽스로 설계 |
| PDP 지연 로딩 | 상세설명·추천·Q&A수를 별도 API로 분리 호출 | 동일 패턴 적용 |
| 카테고리 코드 | 계층 인코딩 (대 11자리 → 중 15자리 → 소 19자리, 부모코드가 접두사) | `C001` → `C001001` → `C001001001` 방식 채택 |
| 프로모션 분리 | 상품 카드 배지(세일/쿠폰/증정/1+1)는 프로모션 테이블 조인 파생 | promotion 테이블 분리 |
| 추천 슬롯 | `recommended?type=a202/a003` 슬롯 코드 방식 | 동일 |
| 검색 | 인기검색어 API(`popular-keywords?size=100`), 최근 검색어는 클라이언트 저장 | 동일 |
| 랭킹 | 카테고리 탭 20개 × 100위, 배치 집계 추정 | 스냅샷 배치 방식 채택 |
| 로그인 상태 | 매 페이지 `loginCheckJson.do` AJAX 확인 (세션) | 우리는 JWT로 대체 |
| 행동 추적 | 전 링크 `t_page`/`t_click` + Amplitude | 2차 개인화 입력으로 자체 경량 버전 |

## 3. 범위

### 1차 (MVP — 전부 완성해야 하는 범위)
- 회원: 가입/로그인(JWT), 피부 프로필, 배송지, 마이페이지
- 카탈로그: 카테고리 트리, 상품 목록(필터/정렬/페이지네이션), 상품 상세(옵션/배지/지연로딩)
- 검색: 결과, 자동완성, 인기 검색어, 최근 검색어(localStorage)
- 랭킹: 카테고리별 배치 집계 스냅샷
- 장바구니 → 주문 → 토스페이먼츠 테스트 결제 (서버 재검증 2단계)
- 리뷰: 구매 인증, 평점, 포토, 피부타입 태그 필터, 도움돼요
- 찜 + 최근 본 상품(localStorage)
- 오늘드림 클론: 매장/매장재고, 근거리 판정, 당일배송/픽업 주문, 조건부 재고 차감
- **루틴 가이드**: 피부타입 매칭 템플릿 → 단계별 추천 → 한번에 담기
- **성분·궁합 코어**: 성분/매핑/규칙 테이블, 상세 성분 배지, 루틴·장바구니 궁합 경고
- 포인트: 정률 적립/사용 (쿠폰함은 2차)
- 관리자: 상품/재고/프로모션/루틴 템플릿 최소 CRUD

### 2차
- **클라이언트 위임 개인화** (핵심 실험): 서버는 스냅샷 JSON + 버전 배포만, 클라이언트가 IndexedDB 캐시 + 추천 정렬·루틴 개인화·궁합 진단 계산. 가중치는 서버 배포 JSON.
- **내 화장대**: 보유 제품 등록(검색-추가), 화장대 궁합 진단, 7일 루틴 자동 생성
- 쿠폰 발급/다운로드, 카카오 소셜 로그인, Elasticsearch 전환(선택)

### 아키텍처 불변 원칙
1. **돈과 재고는 서버, 취향은 클라이언트.** 가격·재고·주문·쿠폰 검증은 항상 서버. 클라이언트 위임은 "틀려도 사고 안 나는" 계산만.
2. **패키지 = 서비스 경계.** 도메인 패키지는 자기 테이블만 접근, 타 도메인은 서비스 인터페이스 경유. (MSA 전환 가능 모놀리스)
3. 주문 시점 데이터는 **스냅샷** (가격, 상품명, 배송지, 리뷰의 피부타입).

## 4. 시스템 구조

```
[브라우저] React SPA (Vite+TS)
  상태: TanStack Query(서버) / Zustand(클라이언트)
  (2차) 개인화 엔진 + IndexedDB
    │ REST /api/v1/*
[Spring Boot 단일 앱 — 도메인 패키지]
  member | catalog | ingredient | search | cart | order
  payment | review | ranking | delivery | routine
    │
[MySQL] + [Redis(선택): 조회수 버퍼·인기검색어]
```

- 인증: JWT 액세스(30분) + 리프레시(2주, httpOnly 쿠키)
- 이미지: 1차 로컬 디스크 (운영 전환 시 S3 명시)

## 5. 도메인 모델

### member
- `member`(email, password_hash, 닉네임, 등급, 상태)
- `member_profile`(피부타입: 건성/지성/복합/민감, 피부고민[다중], 연령대) — 루틴·리뷰 필터 원천
- `address`(기본배송지 플래그)

### catalog
- `category` — 계층 인코딩 코드(`C001`/`C001001`/`C001001001`) + depth. 하위 전체 조회 = `LIKE 'C001%'`
- `goods`(goodsNo `A#########`, brand FK, leaf category FK, 상품명, 정상가, 판매가, 대표이미지, 상태)
- `goods_option`(옵션명, 가격가산, 재고)
- `brand`, `promotion` + `promotion_goods`(세일/쿠폰/증정/1+1 → 배지 파생)

### ingredient (성분·궁합)
- `ingredient`(성분명, 분류: 레티노이드/AHA/BHA/비타민C/나이아신아마이드/…, 자극도 등급, comedogenic 지수, 한줄설명)
- `goods_ingredient`(상품당 핵심 성분 5~10, 시드에 포함)
- `ingredient_rule`(분류쌍 → CONFLICT/CAUTION/SYNERGY + 사유 텍스트, 15~20개 규칙)

### review
- `review`(member, goods, **order_item FK=구매인증**, 평점, 본문, 피부타입 스냅샷)
- `review_photo`, `review_helpful`(member×review 유니크)
- `goods_review_stat`(평점 평균·개수 비정규화, 작성/삭제 시 갱신)

### cart / order / payment
- `cart_item` (비회원은 localStorage → 로그인 병합)
- `orders`(주문번호, 배송지 스냅샷, 배송유형: 일반/오늘드림/픽업, 상태: 결제대기→결제완료→배송준비→배송중→완료/취소)
- `order_item`(상품명·가격 스냅샷)
- `payment`(toss paymentKey, 금액, 상태, 원문 JSON)

### delivery (오늘드림)
- `store`(위경도, 영업시간), `store_stock`(store×goods×qty)
- 판정: 반경 5km 내 재고 보유 매장 존재 → 배지. Haversine 계산(매장 수십 개 → 공간 인덱스 불필요)
- 차감: 결제완료 시 `UPDATE store_stock SET qty=qty-? WHERE qty>=?` 조건부 차감
- 배송 상태는 스케줄러가 시간 경과로 자동 전이 (데모용)

### ranking
- `goods_daily_stat`(goods×date: 조회/판매/찜) — 조회수는 Redis INCR 후 5분 플러시
- `ranking_snapshot` — 매시 배치: 점수 = 판매×3 + 찜×2 + 조회×1 (최근 3일 가중) → 트랜잭션 내 통째 교체. 조회는 스냅샷만 읽음

### routine
- `routine_template`(이름, 피부타입, 시간대, 설명)
- `routine_step`(순서, 단계명, 초보자용 설명)
- `routine_step_goods`(단계별 추천 상품 — 1차 운영자 큐레이션, 2차 개인화 대체)

### 기타
- `wishlist`, `search_keyword_log`(→ 인기검색어 집계), `point_history`
- 최근 본 상품·최근 검색어: localStorage (2차 개인화 입력)

## 6. 화면 (React Router)

| 라우트 | 화면 |
|---|---|
| `/` | 메인: 배너, 추천 섹션, 랭킹 미리보기, 내 루틴 배너 |
| `/category/:code` | 목록: 브랜드/가격 필터, 정렬 5종, 페이지네이션 |
| `/goods/:goodsNo` | 상세: 옵션, 배지, 성분 배지, 탭(설명/리뷰/Q&A), 오늘드림 판정 |
| `/search?q=` | 검색 결과 + 자동완성 오버레이 |
| `/ranking` | 카테고리 탭 × 100위 |
| `/routine` | 루틴 가이드: 프로필/퀴즈 → 단계 카드 → 궁합 검사 → 전체 담기 |
| `/cart`, `/order`, `/order/complete` | 장바구니 → 주문서(배송유형/포인트) → 토스 → 완료 |
| `/login`, `/signup` | 가입 시 피부 프로필 스텝 |
| `/mypage/*` | 주문내역, 찜, 내 리뷰, 프로필/배송지 |
| `/admin/*` | 최소 CRUD |

공통: 헤더(검색바/카테고리 드로어/장바구니 카운트) + 푸터. 상품 카드 컴포넌트 단일화(배지·찜 포함) 후 전 화면 재사용.

## 7. API 설계 (`/api/v1`)

### 공개
```
GET /categories/tree
GET /goods?categoryCode=&sort=(popular|new|sales|priceAsc|discount)&page=
GET /goods/{goodsNo}                     # 빠른 기본 정보
GET /goods/{goodsNo}/description         # 지연 로딩
GET /goods/{goodsNo}/recommended
GET /goods/{goodsNo}/ingredients         # 성분 + 배지
GET /reviews?goodsNo=&skinType=&photoOnly=&sort=
GET /reviews/stats?goodsNo=
GET /search?q=&sort=&page=
GET /search/autocomplete?q=              # 300ms 디바운스, prefix 10개
GET /search/popular-keywords
GET /rankings?categoryCode=
GET /routines?skinType=&time=
POST /compat/check                       # 상품ID 배열 → 궁합 진단 결과
GET /delivery/stores/nearby?lat=&lng=&goodsNo=
```

### 인증
```
POST /auth/signup|login|refresh|logout
GET|PUT /members/me, /members/me/profile
GET|POST|PATCH|DELETE /cart/items  (+ POST /cart/items/bulk = 루틴 담기)
POST /orders                       # 서버 가격·재고 재검증 → 결제대기
POST /payments/confirm             # 토스 승인 검증 → 결제완료
GET /orders, /orders/{orderNo}
POST|DELETE /wishlist/{goodsNo}
POST /reviews, POST /reviews/{id}/helpful
```

### 결제 2단계 (핵심 설계)
1. `POST /orders`: 장바구니를 서버가 재계산(가격 위변조 차단), "결제대기" 주문 + 결제 예정 금액 반환
2. 프론트: 토스 결제창 → 성공 시 paymentKey 수신
3. `POST /payments/confirm`: 서버가 토스 승인 API 호출, **금액 일치 검증** 후 결제완료 전환. 검증 실패 시 토스 취소 API 호출 후 주문 실패 처리

## 8. 핵심 기능 로직

### 루틴 가이드
1. 회원=프로필, 비회원=3문항 퀴즈(localStorage, 가입 시 승격)
2. 템플릿 매칭(1차: 피부타입×시간대 단순 룩업 — 2차 개인화로 교체할 자리)
3. 단계 카드(순서+초보자 설명+추천 2~3개, 기본 선택) → 궁합 검사 → "루틴 전체 담기"
4. 담기 전 `POST /compat/check`로 조합 검사, CONFLICT 시 경고+대체 제안

### 성분 궁합
- 규칙은 성분 "분류 쌍" 단위 (개별 성분 쌍이 아님 → 규칙 수 통제)
- 적용 지점: ① 상품 상세(자극도/comedogenic 배지) ② 루틴 가이드 ③ 장바구니 경고 배너
- 예: (레티노이드, AHA)→CONFLICT "자극 중첩", (비타민C, 레티노이드)→CAUTION "시간대 분리 권장", (레티노이드, 나이아신아마이드)→SYNERGY

### 검색
- MySQL FULLTEXT + ngram 파서 (상품명+브랜드명), 자동완성은 prefix LIKE
- 검색 로그 → 매시 24시간 집계 → 인기검색어 캐시
- 검색 모듈 인터페이스 분리 → 2차 Elasticsearch 교체 지점 명시

## 9. 에러 처리

- 전역 `@RestControllerAdvice` → `{code, message, detail}` 통일. 도메인 에러코드 체계(STOCK_SHORTAGE, PAYMENT_AMOUNT_MISMATCH, REVIEW_NOT_PURCHASED …)
- 결제 실패 복구: 승인 실패→결제대기 유지(재시도 가능) / 승인 후 검증 실패→토스 취소 후 주문 실패 (과금만 되는 상태 금지)
- 프론트: Query 공통 에러 바운더리 + 토스트, 401→리프레시→실패 시 로그인

## 10. 테스트 전략

- 백엔드: 도메인 서비스 단위 테스트 중심. **필수 통합 테스트**: 주문-결제 흐름, 재고 조건부 차감 동시성(멀티스레드), 리뷰 구매인증
- 프론트: Playwright E2E는 핵심 플로우(탐색→담기→주문)만 소수. 컴포넌트 테스트 생략(과투자)

## 11. 결정 기록 (면접 대비 요약)

| 결정 | 이유 |
|---|---|
| React SPA (Next.js 미채택) | 포트폴리오라 SEO 불필요 + 2차 클라이언트 위임(CSR 철학)과 궁합 |
| 모듈러 모놀리스 (MSA 미채택) | 1인 완성도 우선, 패키지 경계로 전환 가능성 확보 |
| 결제 2단계 + 서버 재검증 | 클라이언트 금액 신뢰 금지 |
| 랭킹 스냅샷 배치 | 조회 부하와 집계 분리 (올리브영 동일 추정) |
| 궁합 규칙 = 분류 쌍 | 규칙 폭발 방지, 데이터로 관리 |
| 시드 데이터 자작 | 저작권/크롤링 리스크 제거, 성분 데이터 확보 문제 해결 |
| 성분 스키마 1차 포함 | 시드 재작성 비용 방지 (스키마는 처음부터) |
