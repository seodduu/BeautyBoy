# 2026-07-29 baseline 부하 측정 조건

Wave 0 마지막 태스크(0.3)의 "before" 수치. Kafka 후처리 비동기화 + Redis 캐싱을 얹은 뒤
**이 문서의 조건을 그대로 재현**해 같은 스크립트로 재측정하고 before/after를 비교한다.

## 1. 측정 일시

- 2026-07-29 (KST), 로컬 시각 약 14:59 ~ 15:05 사이(백엔드 기동 14:58:59, confirm 실측 완료 후
  browse 실측까지 연속 진행).

## 2. 하드웨어 / OS

```
$ sysctl -n machdep.cpu.brand_string hw.ncpu hw.memsize
Apple M5 Pro
15
51539607552   # 48GiB

$ sw_vers
ProductName:    macOS
ProductVersion: 26.5.1
BuildVersion:   25F80
```

- Java: OpenJDK 21.0.10 (Homebrew)
- k6: v2.1.0 (darwin/arm64)

## 3. 백엔드 기동 방식

**compose가 아니라 gradle bootRun으로 직접 기동했다** — compose backend가 8080을 점유하고 있어서
먼저 내려야 했다(§6 참고).

```bash
cd backend
JWT_SECRET=<.env 참조, Base64 문자열> \
  ./gradlew bootRun --args='--spring.profiles.active=local,loadtest'
```

- 활성 프로필: `local,loadtest` (부팅 로그 확인: `The following 2 profiles are active: "local", "loadtest"`)
- `loadtest` 프로필이 `StubPaymentGateway`(Task 0.1)를 활성화 — 토스 승인을 100ms 지연 후 요청
  금액 그대로 승인. 실제 토스 API 호출 없음.
- `application-local.yml`은 이미 리포에 존재(gitignore 대상, `jdbc:mysql://localhost:13306/beautyboy`,
  `root`/`local1234`).
- `JWT_SECRET`은 `.env`에 저장된 기존 값을 그대로 사용(Base64, 값은 시크릿이라 마스킹).
- 포트: 8080 (기본값).
- 부팅 로그에 `ERROR` 없음(측정 전·중·후 전체 로그 grep 확인, `RepositoryConfigurationExtensionSupport`
  INFO 노이즈 제외).

## 4. DB / Redis 구성

- `docker compose`로 뜬 컨테이너 그대로 사용, mysql·redis만 계속 켜둠:
  - `beautyboy-mysql-1` — 이미지 `mysql:8.4`, 호스트 포트 13306 (2주 전부터 기동 중, healthy)
  - `beautyboy-redis` — 이미지 `redis:7-alpine`, 호스트 포트 6379 (2달 전부터 기동 중, healthy)
    - `browse.js`/`confirm.js` 시나리오 자체는 Redis를 쓰지 않음(`VIEW_COUNT_REDIS=false` 기본값,
      조회수는 DB 즉시 증가). Wave 0 이후 캐싱을 얹은 재측정 때를 위해 미리 띄워만 둔 상태.
- Flyway: 마이그레이션 34개, 스키마 버전 84, "up to date" (재측정 시에도 동일해야 함).

## 5. 재고 보충 (confirm.js 전용)

`PaymentService.confirm`이 결제 확정 시점에 재고를 차감하므로, 시드 재고(수십~수백)로는
200VU 반복에 순식간에 바닥나 409(`ORDER_OUT_OF_STOCK`)가 쏟아진다. 측정 전 실행한 SQL과 결과:

```sql
-- 1) GOODS_ID=42의 옵션 확인
SELECT id, goods_id, stock FROM goods_option WHERE goods_id = 42;
-- 결과: id=50 stock=67 / id=51 stock=98

-- 2) OPTION_ID로 쓸 id=51의 재고를 100만으로 보충
UPDATE goods_option SET stock = 1000000 WHERE id = 51;
```

- 측정에 사용한 `GOODS_ID=42`, `OPTION_ID=51`.
- confirm.js 본 측정(1164 iterations, 각 1개 수량 주문) 종료 후 잔여 재고 확인: `998596`
  (100만 - 1164 ≈ 998836과 근사, 스모크 실행분 소진분 포함 — 재측정 전 반드시 §5 절차를 다시
  실행해 재보충할 것).

## 6. 측정 중 스택 상태

- `docker compose stop backend frontend` 로 compose의 backend/frontend를 내리고 시작(포트 8080
  충돌 회피). mysql/redis는 계속 기동 상태 유지.
- 측정 대상 백엔드는 §3의 gradle bootRun 인스턴스(포트 8080)이며, compose backend가 아니다.
- 측정 종료 후 `docker compose start backend frontend`로 원복(본 문서 작성 시점 기준 완료 —
  §9 참고).

## 7. 사용한 환경변수 (스크립트별)

### confirm.js 스모크 + 본 측정 공통

| 변수 | 값 |
|---|---|
| `BASE_URL` | `http://localhost:8080` |
| `LOADTEST_EMAIL` | `dry@beautyboy.dev` |
| `LOADTEST_PASSWORD` | `seed1234!` |
| `GOODS_ID` | `42` |
| `OPTION_ID` | `51` |

### browse.js 스모크 + 본 측정 공통

| 변수 | 값 |
|---|---|
| `BASE_URL` | `http://localhost:8080` |
| `CATEGORY_CODE` | `C002001001` |
| `GOODS_A` | `41` |
| `GOODS_B` | `42` |

### 백엔드 기동

| 변수 | 값 |
|---|---|
| `JWT_SECRET` | (.env 참조, Base64) |
| `spring.profiles.active` | `local,loadtest` |

## 8. 스크립트 커밋 SHA

`tools/loadtest/confirm.js`, `tools/loadtest/browse.js`, `tools/loadtest/README.md`를 포함한
측정 시점 HEAD: `b6837f944c368b37a6f2b8048004883654fc0a7d`
("fix k6 loadtest doc/script findings from review")

## 9. compose 원복

측정 완료 후 `docker compose start backend frontend` 실행, `docker compose ps`로 두 컨테이너
모두 `Up`/`healthy` 재확인 완료.

## 10. 이 측정의 한계

- **토스 결제 게이트웨이가 스텁으로 치환되어 있다.** `StubPaymentGateway`가 실제 토스 API 호출
  없이 100ms 고정 지연 후 승인 응답을 돌려준다. 즉 이 수치는 **외부 결제 API 왕복 시간을 제외한,
  우리 서버(Spring + MySQL) 자체의 처리 능력을 측정한 것**이다. 실제 운영에서 토스 API 호출이
  추가되면 confirm 경로의 지연시간은 이보다 커진다.
- 백엔드는 compose 이미지가 아니라 로컬 gradle bootRun으로 떴다 — JVM 워밍업(JIT)이 compose의
  장기 기동 컨테이너보다 덜 되어 있을 수 있다. 다만 before/after 모두 같은 방식(bootRun)으로
  재기동해 재측정하므로 비교 조건 자체는 일관된다.
- MySQL/Redis는 단일 로컬 도커 컨테이너로, 커넥션 풀·리소스 설정은 운영 환경과 다르다(로컬
  랩탑 1대에서의 상대 비교 목적).
- confirm.js 시나리오의 병목(§11 참고, p95 15.5s)은 이 태스크의 조사 대상이 아니다 — Wave A/B/C에서
  Kafka 비동기화로 개선하는 것이 바로 이 구간이라는 가설의 근거 데이터로 남긴다.

## 11. 측정 결과 요약

### confirm.js — 시나리오 ① 주문 생성→결제 확정 (ramping-vus 10→50→200→0, 총 2분)

| 지표 | 값 |
|---|---|
| 총 요청 수 | 2,329 (`http_reqs`) |
| 완료 iteration | 1,164 |
| RPS (평균) | 18.23 req/s |
| p50 (med) | 4,255.9 ms |
| p90 | 13,367.9 ms |
| p95 | 15,456.4 ms |
| p99 | (k6 기본 summary는 p90/p95만 export — 위 README·스크립트를 수정하지 않는 제약상 p99는 미수집. max=19,377.3 ms로 상한 참고) |
| max | 19,377.3 ms |
| 에러율 (`http_req_failed`) | 0.00% |
| threshold `rate<0.01` | **PASS** |

지연시간이 매우 크다(200VU 근처에서 p95 15초대) — 재고 문제나 스크립트 결함이 아니라
(에러율 0%, 재고 998k+ 잔존 확인) **주문 생성→결제 확정 동기 처리 경로 자체가 200 동시 요청에서
느려짐**을 보여주는 것으로 보인다. 이것이 이번 개선(Kafka 비동기화)의 대상 구간이라는 가설과
일치하는 결과라 그대로 기록한다.

### browse.js — 시나리오 ② 조회 혼합 70/20/10 (constant-vus 100, 2분)

| 지표 | 값 |
|---|---|
| 총 요청 수 | 293,970 |
| RPS (평균) | 2,448.95 req/s |
| p50 (med) | 40.47 ms |
| p90 | 44.49 ms |
| p95 | 75.58 ms |
| p99 | (위와 동일한 이유로 미수집. max=218.42 ms로 상한 참고) |
| max | 218.42 ms |
| 에러율 (`http_req_failed`) | 0.00% |
| threshold `rate<0.01` | **PASS** |

조회 경로는 200ms 이내로 빠르고 안정적 — Redis 캐싱 도입 후 랭킹/목록 조회 구간이 실제로
개선되는지 확인할 기준선이다.

## 12. 원본 JSON

- `docs/loadtest/2026-07-29-baseline/confirm-summary.json`
- `docs/loadtest/2026-07-29-baseline/browse-summary.json`

(k6 `--summary-export` 원본, 위 표는 이 파일들에서 발췌.)
