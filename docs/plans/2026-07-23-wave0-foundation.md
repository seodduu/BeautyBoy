# Wave 0: Foundation 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development (권장) 또는 superpowers:executing-plans 로 태스크 단위 실행. 스텝은 체크박스로 추적한다.
> 실행 모델·모델 배분·공통 규칙은 `CLAUDE.md`, 웨이브 구조·공유 계약은 `docs/plans/2026-07-23-roadmap.md` 참조. 이 웨이브의 모든 서브에이전트는 **sonnet**.

**Goal:** backend/frontend 스캐폴딩 + 공통 계약 + 회원/JWT 인증까지 — 이후 모든 웨이브가 딛고 설 기반을 완성한다.

**Architecture:** Spring Boot 3.5(Java 21) 모놀리스를 도메인 패키지로 분리, `common` 패키지가 응답/에러 계약 제공. 프론트는 Vite+React+TS SPA, axios 인터셉터가 JWT 갱신을 담당. 테스트는 H2(MySQL 모드)로 병렬 안전.

**Tech Stack:** Spring Boot 3.5, Spring Security, Spring Data JPA, Flyway, MySQL 8 / H2(test), jjwt 0.12, Gradle(Kotlin DSL) / React 18, Vite 5, TypeScript, React Router 6, TanStack Query 5, Zustand, axios, Vitest

## Global Constraints (CLAUDE.md·설계 문서에서 발췌 — 모든 태스크에 적용)

- 패키지 = 서비스 경계. 타 도메인 엔티티/리포지토리 직접 import 금지
- 시크릿은 `.env.local` / `application-local.yml`(gitignore)로만 주입
- API 응답은 항상 `ApiResponse<T>` 포맷 `{ code, message, data }`, 에러는 `{ code, message, detail }`
- JWT: 액세스 30분(Authorization: Bearer), 리프레시 2주(httpOnly 쿠키 `refresh_token`)
- 단위/슬라이스 테스트는 H2, Flyway는 test 프로필에서 off
- 커밋 메시지 한국어, 태스크 단위 원자 커밋
- Flyway는 V1~V9 대역만 사용 (Wave 0 소유 대역)

## 디렉터리 구조 (이 웨이브의 산출물)

```
backend/
  build.gradle.kts, settings.gradle.kts
  src/main/java/com/beautyboy/
    BeautyboyApplication.java
    common/ (ApiResponse, ErrorCode, BusinessException, GlobalExceptionHandler)
    config/ (SecurityConfig, JwtProperties)
    member/ (Member, MemberProfile, Address 엔티티·리포지토리·서비스·컨트롤러)
    auth/   (TokenProvider, JwtAuthenticationFilter, AuthService, AuthController, RefreshToken)
  src/main/resources/ (application.yml, application-local.yml.example, db/migration/V1__member.sql)
  src/test/... (+ resources/application-test.yml)
frontend/
  package.json, vite.config.ts, tsconfig.json
  src/
    main.tsx, App.tsx, router.tsx
    api/client.ts            # axios + 401 리프레시 인터셉터
    api/auth.ts
    stores/authStore.ts      # Zustand
    components/layout/{Header,Footer,Layout}.tsx
    pages/{Home,Login,Signup}.tsx
docker-compose.yml           # 로컬 MySQL 8
```

---

### Task 1: 백엔드 스캐폴딩 + 헬스체크

**Files:**
- Create: `backend/build.gradle.kts`, `backend/settings.gradle.kts`, `backend/src/main/java/com/beautyboy/BeautyboyApplication.java`, `backend/src/main/resources/application.yml`, `backend/src/test/resources/application-test.yml`, `backend/src/main/java/com/beautyboy/common/HealthController.java`
- Test: `backend/src/test/java/com/beautyboy/common/HealthControllerTest.java`

**Interfaces:**
- Produces: `GET /api/v1/health` → 200 `{"status":"UP"}`. 이후 모든 태스크가 이 Gradle 설정과 test 프로필을 사용.

- [ ] **Step 1: Gradle 프로젝트 작성**

```kotlin
// backend/build.gradle.kts
plugins {
    java
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}
group = "com.beautyboy"
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
repositories { mavenCentral() }
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    runtimeOnly("com.mysql:mysql-connector-j")
    testRuntimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}
tasks.withType<Test> { useJUnitPlatform() }
```

```yaml
# backend/src/test/resources/application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:beautyboy;MODE=MySQL;DATABASE_TO_LOWER=TRUE
  jpa:
    hibernate: { ddl-auto: create-drop }
  flyway: { enabled: false }
jwt:
  secret: dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=
  access-exp-minutes: 30
  refresh-exp-days: 14
```

- [ ] **Step 2: 실패 테스트 작성**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthControllerTest {
    @Autowired TestRestTemplate rest;
    @Test void 헬스체크는_200과_UP을_반환한다() {
        var res = rest.getForEntity("/api/v1/health", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("UP");
    }
}
```

- [ ] **Step 3: 실행해 실패 확인** — `cd backend && ./gradlew test` → 컴파일 실패(HealthController 없음)
- [ ] **Step 4: 구현** — `@RestController` `@GetMapping("/api/v1/health")` → `Map.of("status","UP")`. 이 시점엔 Security 기본설정이 401을 주므로 임시로 `application.yml`에 `spring.autoconfigure.exclude: SecurityAutoConfiguration` (Task 4에서 제거)
- [ ] **Step 5: 테스트 통과 확인** — `./gradlew test` → PASS
- [ ] **Step 6: 커밋** — `git add backend && git commit -m "chore: Spring Boot 스캐폴딩 + 헬스체크"`

---

### Task 2: 공통 계약 (ApiResponse / ErrorCode / 전역 예외)

**Files:**
- Create: `backend/src/main/java/com/beautyboy/common/{ApiResponse,ErrorCode,BusinessException,GlobalExceptionHandler}.java`
- Test: `backend/src/test/java/com/beautyboy/common/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces (전 웨이브 공유 계약 — 이후 동결):
  - `ApiResponse.ok(T data)` → `{ "code":"OK", "message":"성공", "data":{...} }`
  - `ErrorCode` enum: `OK, INVALID_INPUT, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, MEMBER_EMAIL_DUPLICATED, MEMBER_LOGIN_FAILED` (+HttpStatus 필드). 이후 도메인은 접두사 규칙으로 상수 **추가만** 가능
  - `BusinessException(ErrorCode)` 던지면 핸들러가 `{code,message,detail}` + 매핑된 HTTP 상태로 응답

- [ ] **Step 1: 실패 테스트** — 테스트 전용 `@RestController`가 `BusinessException(MEMBER_EMAIL_DUPLICATED)`을 던지면 409와 `code=MEMBER_EMAIL_DUPLICATED`를 응답하는지 MockMvc로 검증. `@Valid` 실패 시 400 `INVALID_INPUT` 검증 포함

```java
@Test void 비즈니스_예외는_에러코드와_상태로_변환된다() throws Exception {
    mockMvc.perform(get("/test/dup"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MEMBER_EMAIL_DUPLICATED"));
}
```

- [ ] **Step 2: 실패 확인** → 컴파일 실패
- [ ] **Step 3: 구현**

```java
public record ApiResponse<T>(String code, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>("OK", "성공", data); }
}

public enum ErrorCode {
    OK(HttpStatus.OK, "성공"),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다"),
    MEMBER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다"),
    MEMBER_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다");
    private final HttpStatus status; private final String message;
    // 생성자·게터
}
```

`GlobalExceptionHandler`: `@RestControllerAdvice`, `BusinessException` → `ErrorResponse(code, message, detail=null)`, `MethodArgumentNotValidException` → `INVALID_INPUT` + 필드 오류를 detail에.
- [ ] **Step 4: 통과 확인** → PASS
- [ ] **Step 5: 커밋** — `git commit -m "feat(common): 응답·에러 공통 계약"`

---

### Task 3: Flyway V1 + 로컬 인프라

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__member.sql`, `docker-compose.yml`, `backend/src/main/resources/application-local.yml.example`, `.gitignore`

**Interfaces:**
- Produces: `member`, `member_profile`, `address`, `refresh_token` 테이블 (아래 DDL이 전 웨이브의 스키마 진실)

- [ ] **Step 1: DDL 작성**

```sql
-- V1__member.sql
CREATE TABLE member (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(100) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  nickname VARCHAR(30) NOT NULL,
  grade VARCHAR(20) NOT NULL DEFAULT 'BABY',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE member_profile (
  member_id BIGINT PRIMARY KEY,
  skin_type VARCHAR(20) NULL,          -- DRY|OILY|COMBINATION|SENSITIVE
  concerns VARCHAR(200) NULL,          -- 콤마 구분: PORE,TROUBLE,WRINKLE,DARK_SPOT
  age_band VARCHAR(10) NULL,           -- 10s|20s|30s|40s|50s+
  CONSTRAINT fk_profile_member FOREIGN KEY (member_id) REFERENCES member(id)
);
CREATE TABLE address (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL,
  receiver VARCHAR(30) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  zipcode VARCHAR(10) NOT NULL,
  address1 VARCHAR(200) NOT NULL,
  address2 VARCHAR(200) NULL,
  latitude DECIMAL(10,7) NULL,
  longitude DECIMAL(10,7) NULL,
  is_default TINYINT(1) NOT NULL DEFAULT 0,
  CONSTRAINT fk_address_member FOREIGN KEY (member_id) REFERENCES member(id)
);
CREATE TABLE refresh_token (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expires_at DATETIME NOT NULL,
  CONSTRAINT fk_rt_member FOREIGN KEY (member_id) REFERENCES member(id)
);
```

```yaml
# docker-compose.yml
services:
  mysql:
    image: mysql:8.4
    environment: { MYSQL_DATABASE: beautyboy, MYSQL_ROOT_PASSWORD: local1234 }
    ports: ["3306:3306"]
    volumes: [mysql-data:/var/lib/mysql]
volumes: { mysql-data: }
```

- [ ] **Step 2: 검증** — `docker compose up -d` 후 `./gradlew bootRun --args='--spring.profiles.active=local'` → 로그에 `Successfully applied 1 migration` 확인 (application-local.yml은 example 복사)
- [ ] **Step 3: `.gitignore`에 `application-local.yml`, `.env.local`, `node_modules`, `build` 추가 후 커밋 — `git commit -m "feat(db): member 스키마 V1 + 로컬 MySQL"`

---

### Task 4: member 도메인 — 가입

**Files:**
- Create: `backend/src/main/java/com/beautyboy/member/{Member,MemberProfile,MemberRepository,MemberService,MemberController,dto/SignupRequest,dto/MemberResponse}.java`, `backend/src/main/java/com/beautyboy/config/SecurityConfig.java`
- Modify: `application.yml` (Task 1의 SecurityAutoConfiguration exclude 제거)
- Test: `backend/src/test/java/com/beautyboy/member/MemberServiceTest.java`

**Interfaces:**
- Produces: `POST /api/v1/auth/signup` `{email, password, nickname, skinType?, concerns?, ageBand?}` → 201 `MemberResponse{id, email, nickname, grade}`. `MemberRepository.findByEmail(String)`. `SecurityConfig`: `/api/v1/auth/**`, `/api/v1/health` permitAll, 그 외 인증 필요, 세션 STATELESS, `PasswordEncoder` 빈=BCrypt

- [ ] **Step 1: 실패 테스트**

```java
@Test void 가입하면_비밀번호가_해시로_저장된다() {
    var res = memberService.signup(new SignupRequest("a@b.com", "pw123456", "민수", "OILY", List.of("TROUBLE"), "20s"));
    var saved = memberRepository.findByEmail("a@b.com").orElseThrow();
    assertThat(saved.getPasswordHash()).isNotEqualTo("pw123456");
    assertThat(passwordEncoder.matches("pw123456", saved.getPasswordHash())).isTrue();
}
@Test void 중복_이메일이면_MEMBER_EMAIL_DUPLICATED() {
    memberService.signup(req("a@b.com"));
    assertThatThrownBy(() -> memberService.signup(req("a@b.com")))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_EMAIL_DUPLICATED);
}
```

- [ ] **Step 2: 실패 확인** → 컴파일 실패
- [ ] **Step 3: 구현** — `Member` 엔티티(email 유니크, grade/status/role enum-string), `MemberProfile` `@OneToOne(cascade=ALL)` — 프로필 값이 오면 함께 저장. 서비스: `findByEmail` 존재 시 예외, `passwordEncoder.encode`. 컨트롤러 201 + `ApiResponse.ok`
- [ ] **Step 4: 통과 확인**, MockMvc 슬라이스로 `@Valid`(이메일 형식, 비번 8자+) 400도 확인
- [ ] **Step 5: 커밋** — `git commit -m "feat(member): 회원가입 + 피부 프로필"`

---

### Task 5: auth 도메인 — 로그인 / JWT / 리프레시

**Files:**
- Create: `backend/src/main/java/com/beautyboy/auth/{TokenProvider,JwtAuthenticationFilter,AuthService,AuthController,RefreshToken,RefreshTokenRepository,dto/LoginRequest,dto/TokenResponse}.java`, `backend/src/main/java/com/beautyboy/config/JwtProperties.java`
- Modify: `SecurityConfig`(필터 등록)
- Test: `backend/src/test/java/com/beautyboy/auth/{TokenProviderTest,AuthApiTest}.java`

**Interfaces:**
- Produces:
  - `POST /api/v1/auth/login` `{email,password}` → `TokenResponse{accessToken}` + `Set-Cookie: refresh_token=...; HttpOnly; Path=/api/v1/auth`
  - `POST /api/v1/auth/refresh` (쿠키) → 새 accessToken + 리프레시 로테이션(기존 토큰 폐기·재발급)
  - `POST /api/v1/auth/logout` → 리프레시 삭제 + 쿠키 만료
  - `TokenProvider.createAccessToken(Long memberId, String role)` / `parse(String)` → memberId·role. 이후 전 웨이브 컨트롤러는 `@AuthenticationPrincipal Long memberId` 사용

- [ ] **Step 1: TokenProvider 실패 테스트** — 발급한 토큰 parse 시 memberId/role 복원, 만료 토큰은 `UNAUTHORIZED` BusinessException
- [ ] **Step 2: 실패 확인 → 구현** — jjwt 0.12 API(`Jwts.builder().subject(...).claim("role",...).expiration(...).signWith(key)`), 리프레시는 랜덤 UUID를 SHA-256 해시로 DB 저장
- [ ] **Step 3: AuthApiTest 실패 테스트** — 가입→로그인→`GET /api/v1/members/me`(임시로 이 태스크에서 401 확인만)→refresh→재사용된 구 리프레시는 401
- [ ] **Step 4: 필터 구현** — `OncePerRequestFilter`: Bearer 파싱 성공 시 `UsernamePasswordAuthenticationToken(memberId, null, ROLE_x)` 세팅. SecurityConfig에 `addFilterBefore`
- [ ] **Step 5: 전체 통과 확인** — `./gradlew test` PASS
- [ ] **Step 6: 커밋** — `git commit -m "feat(auth): JWT 로그인·리프레시 로테이션"`

---

### Task 6: member 도메인 — 내 정보 / 프로필 수정 / 배송지

**Files:**
- Create: `backend/src/main/java/com/beautyboy/member/{Address,AddressRepository,AddressService,dto/ProfileRequest,dto/AddressRequest,dto/AddressResponse}.java`
- Modify: `MemberController`, `MemberService`
- Test: `backend/src/test/java/com/beautyboy/member/MemberApiTest.java`

**Interfaces:**
- Produces: `GET /api/v1/members/me`, `PUT /api/v1/members/me/profile`, `GET|POST|PUT|DELETE /api/v1/members/me/addresses[/{id}]`. 기본배송지는 1개 보장(새 기본 지정 시 기존 해제). `AddressRepository.findByMemberIdAndIsDefaultTrue(Long)` — Wave 3 오늘드림이 좌표 조회에 사용

- [ ] **Step 1: 실패 테스트** — 인증 사용자만 접근(무토큰 401), 기본배송지 교체 로직, 타인 배송지 삭제 시 `FORBIDDEN`
- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인**
- [ ] **Step 3: 커밋** — `git commit -m "feat(member): 내정보·프로필·배송지"`

---

### Task 7: 프론트 스캐폴딩 + API 클라이언트

**Files:**
- Create: `frontend/package.json`, `frontend/vite.config.ts`, `frontend/src/{main.tsx,App.tsx,router.tsx}`, `frontend/src/api/client.ts`, `frontend/src/stores/authStore.ts`, `frontend/src/components/layout/{Layout,Header,Footer}.tsx`, `frontend/src/pages/Home.tsx`
- Test: `frontend/src/api/client.test.ts` (Vitest)

**Interfaces:**
- Produces: `api` axios 인스턴스(`baseURL:'/api/v1'`, vite proxy→`http://localhost:8080`). 요청 인터셉터가 `authStore.accessToken`을 Bearer로 첨부, 401 응답 시 `/auth/refresh` 1회 재시도 후 원요청 재실행, 실패 시 스토어 clear. `authStore{accessToken, member, setAuth, clear}` (액세스 토큰은 메모리만 — localStorage 저장 금지, 리프레시는 httpOnly 쿠키가 담당)

- [ ] **Step 1: 스캐폴드** — `npm create vite@latest frontend -- --template react-ts`, 의존성: `react-router-dom @tanstack/react-query zustand axios`, dev: `vitest @testing-library/react jsdom msw`
- [ ] **Step 2: 실패 테스트** — msw로 첫 요청 401→`/auth/refresh` 200→원요청 재시도 성공을 검증

```ts
it('401이면 refresh 후 원요청을 재시도한다', async () => {
  server.use(
    http.get('/api/v1/members/me', () =>
      firstCall++ === 0 ? new HttpResponse(null, { status: 401 })
                        : HttpResponse.json({ code: 'OK', data: { nickname: '민수' } })),
    http.post('/api/v1/auth/refresh', () =>
      HttpResponse.json({ code: 'OK', data: { accessToken: 'new-token' } })),
  );
  const res = await api.get('/members/me');
  expect(res.data.data.nickname).toBe('민수');
  expect(useAuthStore.getState().accessToken).toBe('new-token');
});
```

- [ ] **Step 3: 실패 확인** — `npm test` FAIL
- [ ] **Step 4: 구현** — client.ts 인터셉터(재시도 플래그 `_retried`로 무한루프 방지), authStore, Layout(올리브영식 헤더: 로고/검색바 자리/장바구니·로그인 링크 + 푸터), router(`/`, `/login`, `/signup`)
- [ ] **Step 5: 통과 확인** — `npm test` PASS, `npm run dev`로 셸 렌더 확인
- [ ] **Step 6: 커밋** — `git commit -m "feat(front): 스캐폴딩 + JWT 갱신 클라이언트"`

---

### Task 8: 로그인 / 가입 화면 (피부 프로필 스텝 포함)

**Files:**
- Create: `frontend/src/api/auth.ts`, `frontend/src/pages/{Login,Signup}.tsx`, `frontend/src/components/signup/SkinProfileStep.tsx`
- Test: `frontend/src/pages/Signup.test.tsx`

**Interfaces:**
- Consumes: Task 4·5의 `/auth/signup`, `/auth/login` — 요청/응답 형태는 해당 태스크 Interfaces와 동일
- Produces: `signup(SignupPayload)`, `login({email,password})` API 함수. 가입 2스텝: ① 계정(이메일/비번/닉네임) → ② 피부 프로필(피부타입 4택1, 고민 다중, 연령대 — **건너뛰기 가능**). 성공 시 자동 로그인 후 `/` 이동

- [ ] **Step 1: 실패 테스트** — 렌더 후 1스텝 입력→다음→2스텝에서 "건너뛰기" 클릭 시 msw로 signup 호출 body에 skinType이 없음을 검증
- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인** — 폼 검증은 HTML5 + 간단한 상태(라이브러리 불필요, YAGNI)
- [ ] **Step 3: 백엔드 동시 기동 후 수동 확인** — 가입→로그인→Header에 닉네임 표시
- [ ] **Step 4: 커밋** — `git commit -m "feat(front): 로그인·가입(피부 프로필 스텝)"`

---

### Task 9: 웨이브 마감

- [ ] `cd backend && ./gradlew test` 전체 녹색 / `cd frontend && npm test` 녹색
- [ ] curl 시나리오 재현: signup→login(쿠키 저장)→me→refresh→logout
- [ ] `README.md` 작성: 실행법(docker compose, gradlew bootRun, npm run dev), 프로젝트 한줄 소개, 문서 링크
- [ ] 커밋 후 오케스트레이터에 보고 → 리뷰 통과 시 `feat/foundation`을 main에 머지(Wave 0은 단일 터미널이므로 충돌 없음)

## Self-Review 결과

- 스펙 커버리지: Wave 0 목표(스캐폴딩·공통계약·member/auth·프론트 셸) 대비 누락 없음. 등급 산정 로직은 설계상 "표시만"이므로 grade 기본값 BABY로 충분 (승급은 범위 외)
- 타입 일관성: `TokenResponse{accessToken}`·쿠키명 `refresh_token`·`ApiResponse{code,message,data}`를 Task 5/7/8이 동일하게 참조함을 확인
- 플레이스홀더 없음
