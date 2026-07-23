# 뷰티보이 (BeautyBoy)

올리브영을 벤치마크한 **남성 화장품 커머스 플랫폼**. 취업 포트폴리오 프로젝트로,
"완성도 있는 범위 + 설계 이유를 설명할 수 있는 것"을 최우선 가치로 둔다.

## 현재 상태

**Wave 0 (Foundation) 완료.** 스캐폴딩, 공통 응답/에러 계약, 회원가입/로그인(JWT), 프론트 셸,
로그인·가입 2스텝 화면까지 동작한다. 상품(catalog)·검색·장바구니·주문·결제·리뷰·랭킹·배송(오늘드림)·
루틴·성분 궁합 등은 아직 구현되지 않았고, 이후 웨이브(Wave 1~4)에서 순차적으로 붙는다.
전체 웨이브 구조는 [`docs/plans/2026-07-23-roadmap.md`](docs/plans/2026-07-23-roadmap.md) 참고.

## 기술 스택

| 영역 | 구성 |
|---|---|
| 백엔드 | Spring Boot 3.5.0, Java 21, Spring Security, Spring Data JPA, Flyway, JJWT 0.12.6, MySQL(운영) / H2(테스트) |
| 프론트엔드 | React 19, TypeScript, Vite 8, React Router 7, TanStack Query 5, Zustand 5, Axios, Vitest + Testing Library + MSW |
| 인프라 | MySQL 8.4 (Docker Compose), (선택) Redis — 이후 웨이브에서 도입 |

## 실행 방법

### 1. MySQL 기동

```bash
docker compose up -d
```

> **알려진 함정**: 호스트에 이미 로컬 MySQL(예: `brew services`로 띄운 mysqld)이 3306 포트를
> 점유하고 있으면 `docker compose up -d`가 포트 바인딩 실패로 죽는다. 이 프로젝트 세팅 중 실제로
> 겪은 문제다. 해결책:
> - 기존 mysqld를 내린다 (`brew services stop mysql` 등), 또는
> - `docker-compose.yml`의 `ports`를 `"3307:3306"`처럼 바꾸고 `application-local.yml`의
>   `datasource.url` 포트도 함께 맞춘다.

### 2. 백엔드 로컬 설정

```bash
cp backend/src/main/resources/application-local.yml.example backend/src/main/resources/application-local.yml
```

`application-local.yml`은 `.gitignore` 대상이라 커밋되지 않는다. 안의 DB 비밀번호(`local1234`)는
`docker-compose.yml`의 로컬 전용 MySQL 컨테이너 값과 짝을 이루는 예시이므로 그대로 써도 된다.

JWT 시크릿은 코드/설정 파일에 넣지 않고 환경변수로 주입한다. `TokenProvider`가 HMAC 키를
`Base64.decode(secret)`로 만들기 때문에 **반드시 Base64로 인코딩된 값**이어야 한다 — 임의의
평문 문자열을 넣으면 컨텍스트 로딩 단계에서 기동이 실패한다:

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
```

### 3. 백엔드 실행

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

기동 시 Flyway가 마이그레이션(`db/migration/V1__member.sql`)을 적용한다.
헬스체크: `curl http://localhost:8080/api/v1/health`

### 4. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

Vite 개발 서버가 `/api` 요청을 `http://localhost:8080`으로 프록시한다(`vite.config.ts`).
기본 주소: `http://localhost:5173`

### 5. 테스트

```bash
# 백엔드 — H2(MySQL 모드)로 동작, 별도 DB 기동 불필요
cd backend && ./gradlew test

# 프론트엔드
cd frontend && npm test
```

## 구현 현황 (Wave 0)

- **인증**: 이메일/비밀번호 로그인, 액세스 토큰(30분)은 메모리(Zustand, `authStore.ts`)에만 보관,
  리프레시 토큰(2주)은 httpOnly 쿠키. 리프레시는 사용 시점에 즉시 폐기 후 재발급하는 1회용 로테이션.
- **회원가입**: 이메일/비밀번호/닉네임 + 피부 프로필(피부타입/고민/연령대) 2스텝 가입.
- **배송지**: 등록/수정/삭제 CRUD, "기본 배송지는 항상 정확히 1개" 불변식을 서버가 보장.
- **프론트 셸**: 헤더/푸터 레이아웃, 라우터, 로그인·가입 화면(MSW 목 기반 테스트).

### API 엔드포인트

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/v1/health` | - | 헬스체크 |
| POST | `/api/v1/auth/signup` | - | 회원가입 |
| POST | `/api/v1/auth/login` | - | 로그인 (액세스 토큰 응답 + 리프레시 쿠키 발급) |
| POST | `/api/v1/auth/refresh` | 리프레시 쿠키 | 액세스 토큰 재발급 + 리프레시 로테이션 |
| POST | `/api/v1/auth/logout` | 리프레시 쿠키 | 리프레시 토큰 폐기 |
| GET | `/api/v1/members/me` | 액세스 토큰 | 내 정보 조회 |
| PUT | `/api/v1/members/me/profile` | 액세스 토큰 | 피부 프로필 수정 |
| GET | `/api/v1/members/me/addresses` | 액세스 토큰 | 배송지 목록 |
| POST | `/api/v1/members/me/addresses` | 액세스 토큰 | 배송지 등록 |
| PUT | `/api/v1/members/me/addresses/{id}` | 액세스 토큰 | 배송지 수정 |
| DELETE | `/api/v1/members/me/addresses/{id}` | 액세스 토큰 | 배송지 삭제 |

## 설계 판단

- **모듈러 모놀리스 + 패키지=서비스 경계**: `member`/`auth`처럼 도메인 패키지가 자기 테이블만
  접근하고, 타 도메인은 서비스 인터페이스를 경유한다. 이후 웨이브에서 도메인이 늘어나도(catalog,
  cart, order, ...) 서로 엔티티/리포지토리를 직접 참조하지 않도록 강제해, 필요 시 MSA로 쪼갤 수
  있는 경계를 미리 만든다.
- **액세스 토큰을 localStorage에 두지 않는다**: XSS로 스크립트가 실행되면 localStorage는 그대로
  읽히지만, 메모리 변수는 페이지를 새로고침하면 사라진다. 대신 httpOnly 쿠키(JS로 접근 불가)에
  담은 리프레시 토큰으로 새로고침 시 세션을 복구한다. 리프레시는 사용될 때마다 폐기·재발급하는
  1회용 로테이션이라, 탈취된 리프레시 토큰이 재사용되면 이미 폐기된 토큰이라 실패한다.
- **공통 응답/에러 계약**: 성공은 `{code, message, data}`(`ApiResponse`), 실패는
  `{code, message, detail}`(`ErrorResponse`)로 고정. 프론트가 매 API마다 응답 형태를 추측하지
  않게 하고, `GlobalExceptionHandler` 한 곳에서 `ErrorCode`를 HTTP 상태로 매핑한다.
- **"돈과 재고는 서버, 취향은 클라이언트"**: 가격·재고·주문·쿠폰 검증은 항상 서버에서 재검증한다.
  클라이언트가 계산해도 되는 건 추천 정렬·루틴 개인화·성분 궁합 진단처럼 "틀려도 사고 안 나는" 것뿐.
  Wave 0에는 아직 돈/재고 도메인이 없지만, 이 원칙이 이후 order/payment/delivery 설계의 전제다.
- **주문 시점 데이터 스냅샷** (Wave 2 이후 적용 예정): 가격·상품명·배송지·리뷰의 피부타입은 참조가
  아니라 스냅샷으로 저장해, 나중에 원본 값이 바뀌어도 과거 주문/리뷰 기록이 흔들리지 않게 한다.
- **스키마가 진실**: 설계 문서와 실제 Flyway 스키마가 다르면 스키마 쪽을 따른다. 엔티티는 스키마에
  맞춰 작성한다(예: `member`는 `grade`/`status`/`role`을 각각 별도 컬럼으로 가짐).

## 프로젝트 구조

```
backend/
  src/main/java/com/beautyboy/
    common/        # ApiResponse, ErrorCode, BusinessException, GlobalExceptionHandler
    config/        # SecurityConfig, JwtProperties
    auth/          # 로그인/리프레시/로그아웃, JWT 발급·검증
    member/        # 회원가입, 내 정보, 피부 프로필, 배송지
  src/main/resources/
    application.yml, application-local.yml.example
    db/migration/  # Flyway (V1__member.sql)
frontend/
  src/
    api/           # axios 클라이언트, auth API
    stores/        # authStore (Zustand)
    components/    # layout(Header/Footer), signup(SkinProfileStep)
    pages/         # Home, Login, Signup
    mocks/         # MSW 핸들러
docs/
  superpowers/specs/2026-07-23-beautyboy-design.md  # 설계 문서(도메인·API·범위)
  plans/2026-07-23-roadmap.md                       # 구현 로드맵(웨이브 구조)
```

## 문서

- 설계 문서: [`docs/superpowers/specs/2026-07-23-beautyboy-design.md`](docs/superpowers/specs/2026-07-23-beautyboy-design.md)
- 구현 로드맵: [`docs/plans/2026-07-23-roadmap.md`](docs/plans/2026-07-23-roadmap.md)
- 실행 모델·모델 배분·공통 규칙: [`CLAUDE.md`](CLAUDE.md)
