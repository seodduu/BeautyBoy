# 2026-07-29 baseline 부하 측정 조건

Wave 0 마지막 태스크(0.3)의 "before" 수치. Kafka 후처리 비동기화 + Redis 캐싱을 얹은 뒤
**이 문서의 조건을 그대로 재현**해 같은 스크립트로 재측정하고 before/after를 비교한다.

> **개정 이력**: 최초 측정(2026-07-29 15:0x) 후 리뷰에서 3건이 지적되어 재측정 및 문서 정정을
> 거쳤다 — (1) 커밋된 `confirm-summary.json`의 `setup_data.token`에 살아있는 JWT가 그대로
> 들어가 있었음(스크럽 누락), (2) p99가 k6 기본 summary에 없어 미수집이었음(스크립트를 건드리지
> 않고 CLI 플래그로 해결 가능했음), (3) confirm p95 15초의 원인 진단이 "동기 경로 일반의 느림"으로
> 잘못 서술됨(정정: 단일 SKU 락 직렬화, §11 참고). 아래는 정정 후 최종본이다.

## 1. 측정 일시

- 1차: 2026-07-29 (KST) 약 14:59 ~ 15:05.
- 재측정(p99 수집 + 재검증): 2026-07-29 (KST) 약 15:11 ~ 15:19. 조건은 §2~§10 전부 동일,
  차이는 오직 k6 CLI에 `--summary-trend-stats` 플래그 추가(§7-1).

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
먼저 내려야 했다(§6 참고). 1차·재측정 모두 동일하게 매번 새로 기동했다.

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
- 부팅 로그에 `ERROR` 없음(1차·재측정 전체 로그 grep 확인, `RepositoryConfigurationExtensionSupport`
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
200VU 반복에 순식간에 바닥나 409(`ORDER_OUT_OF_STOCK`)가 쏟아진다. 매 실행(1차·재측정 모두) 전
아래 SQL을 반복 실행했다:

```sql
-- 1) GOODS_ID=42의 옵션 확인
SELECT id, goods_id, stock FROM goods_option WHERE goods_id = 42;
-- 결과: id=50 stock=67(고정) / id=51 stock은 매번 이전 실행이 깎아먹은 잔량

-- 2) OPTION_ID로 쓸 id=51의 재고를 100만으로 재보충
UPDATE goods_option SET stock = 1000000 WHERE id = 51;
```

- 측정에 사용한 `GOODS_ID=42`, `OPTION_ID=51` — **1차·재측정 모두 이 하나의 옵션 행만 산다**
  (스크립트 계약, §11의 원인 분석에서 핵심 전제).
- 1차 confirm.js 본 측정(1164 iterations) 종료 후 잔여 재고: 998,596.
- 재측정 전 §5 SQL로 1,000,000까지 재보충 후 confirm.js(1148 iterations) 실행, 종료 후 잔여
  재고: 998,852. 재측정 시에도 재고 고갈에 의한 실패는 없었다(에러율 0%).

## 6. 측정 중 스택 상태

- `docker compose stop backend frontend` 로 compose의 backend/frontend를 내리고 시작(포트 8080
  충돌 회피). mysql/redis는 계속 기동 상태 유지. 1차·재측정 모두 동일하게 수행.
- 측정 대상 백엔드는 §3의 gradle bootRun 인스턴스(포트 8080)이며, compose backend가 아니다.
- 측정 종료 후 매번 `docker compose start backend frontend`로 원복, `docker compose ps`로
  `Up`/`healthy` 확인(§9).

## 7. 사용한 환경변수 (스크립트별)

### confirm.js 본 측정 공통

| 변수 | 값 |
|---|---|
| `BASE_URL` | `http://localhost:8080` |
| `LOADTEST_EMAIL` | `dry@beautyboy.dev` |
| `LOADTEST_PASSWORD` | `tools/loadtest/README.md` §2 표 참조 (시드 전용 값, 이미 리포 여러 곳에 공개돼 있어 새 노출은 아니지만 여기서 다시 적지 않는다) |
| `GOODS_ID` | `42` |
| `OPTION_ID` | `51` |

### browse.js 본 측정 공통

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

### 7-1. p99 수집용 k6 CLI 옵션 (재측정에서 추가)

부하 모형(`options` 객체의 VU·duration·비율·threshold)은 스크립트 안에 계약으로 박혀 있어
건드리지 않았다. p99는 **k6 실행 시 CLI 플래그로 별도 지정**했다 — 이는 리포팅 옵션이지
부하 모형이 아니므로 계약 위반이 아니다:

```bash
k6 run --summary-trend-stats="avg,min,med,max,p(90),p(95),p(99)" \
  --summary-export=<...>.json confirm.js   # browse.js도 동일 플래그
```

## 8. 스크립트 커밋 SHA

`tools/loadtest/confirm.js`, `tools/loadtest/browse.js`, `tools/loadtest/README.md`를 포함한
측정 시점 HEAD: `b6837f944c368b37a6f2b8048004883654fc0a7d`
("fix k6 loadtest doc/script findings from review")

## 9. compose 원복

측정 완료 후(1차·재측정 각각) `docker compose start backend frontend` 실행, `docker compose ps`로
두 컨테이너 모두 `Up`/`healthy` 재확인 완료.

## 9-1. summary JSON 커밋 전 스크럽 절차 (필수)

`confirm.js`는 `setup()`에서 로그인해 액세스 토큰을 반환하고, k6는 이 반환값을
`--summary-export` JSON의 `setup_data`에 그대로 직렬화한다. **즉 커밋 직전 JSON에는 재사용
가능한 살아있는 JWT 베어러 토큰이 들어 있다.** 1차 측정 때 이를 놓치고 그대로 커밋했다가
리뷰에서 지적받아 정정했다. **이후 모든 baseline/after 재측정에서 아래 절차를 커밋 전에
반드시 거친다:**

```bash
python3 -c "
import json
p = 'docs/loadtest/<날짜>-.../confirm-summary.json'
d = json.load(open(p))
if isinstance(d.get('setup_data'), dict) and 'token' in d['setup_data']:
    d['setup_data']['token'] = '(redacted)'
with open(p, 'w') as f:
    json.dump(d, f, indent=2); f.write('\n')
"
```

`browse.js`는 `setup()`이 없어 `setup_data`가 애초에 없다(None) — 스크럽 대상 아님.

## 10. 이 측정의 한계

- **토스 결제 게이트웨이가 스텁으로 치환되어 있다.** `StubPaymentGateway`가 실제 토스 API 호출
  없이 100ms 고정 지연 후 승인 응답을 돌려준다. 즉 이 수치는 **외부 결제 API 왕복 시간을 제외한,
  우리 서버(Spring + MySQL) 자체의 처리 능력을 측정한 것**이다.
- 백엔드는 compose 이미지가 아니라 로컬 gradle bootRun으로 떴다 — JVM 워밍업(JIT)이 compose의
  장기 기동 컨테이너보다 덜 되어 있을 수 있다. 다만 before/after 모두 같은 방식(bootRun)으로
  재기동해 재측정하므로 비교 조건 자체는 일관된다.
- MySQL/Redis는 단일 로컬 도커 컨테이너로, 커넥션 풀·리소스 설정은 운영 환경과 다르다(로컬
  랩탑 1대에서의 상대 비교 목적).
- **confirm.js는 "모든 구매가 단 하나의 SKU(옵션)에 집중되는" 극단적인 부하 모형이다.**
  200VU 전원이 `OPTION_ID=51` 딱 하나를 산다(§5). 이는 일반적인 다품목 쇼핑 트래픽(수십~수백
  종 상품에 분산된 구매)의 결제 지연을 대표하지 않는다 — **단일 인기 상품 폭주(타임세일, 한정
  수량 이벤트 등) 시나리오에 가깝다.** 아래 §11의 원인 분석이 이 한계와 직결된다.

## 11. 측정 결과 요약

### confirm.js — 시나리오 ① 주문 생성→결제 확정 (ramping-vus 10→50→200→0, 총 2분)

재측정(p99 포함) 수치:

| 지표 | 값 |
|---|---|
| 총 요청 수 | 2,297 (`http_reqs`) |
| 완료 iteration | 1,148 |
| RPS (평균) | 17.99 req/s |
| p50 (med) | 4,470 ms |
| p90 | 13,010 ms |
| p95 | 15,300 ms |
| **p99** | **17,470 ms** |
| max | 18,610 ms |
| 에러율 (`http_req_failed`) | 0.00% |
| threshold `rate<0.01` | **PASS** |

(참고: 1차 측정 수치는 p50 4,255.9ms / p90 13,367.9ms / p95 15,456.4ms / max 19,377.3ms,
2,329 요청 — 재측정과 오차범위 내로 일치. 아래 원인 분석은 재측정 수치 기준.)

#### 원인 분석 (정정판 — 1차 리포트의 "동기 경로 일반이 느리다"는 진단은 부정확했다)

`backend/src/main/java/com/beautyboy/payment/PaymentService.java`의 `confirm()`을 코드로
확인한 결과, 지배적 원인은 **단일 공유 재고 행에 대한 InnoDB 행 락 직렬화**다:

1. `confirm()`은 하나의 `@Transactional` 안에서 순서대로 실행된다: (a) `orderConfirmPort
   .lockPendingOrder` — 주문 행 락, (b) `stockCommandService.deductAll` — `goods_option.stock`을
   깎는 `UPDATE`, (c) `paymentGateway.confirm` — `loadtest` 프로필에서는 `StubPaymentGateway`가
   **100ms 지연 후** 승인, (d) `markPaid` + `Payment` 저장, 그리고 트랜잭션 커밋.
2. (b)의 `UPDATE ... SET stock = stock - ? WHERE id = ?`는 InnoDB 행 배타 락을 걸고, 이 락은
   **트랜잭션이 커밋될 때까지 유지된다** — 즉 (c)의 100ms 대기 전체가 락 보유 구간 안에 들어간다.
3. `confirm.js`의 모든 VU는 **같은 `OPTION_ID=51` 하나**만 구매한다(§5). 따라서 피크의 200개
   동시 트랜잭션이 **같은 행 하나의 배타 락을 놓고 직렬로 줄을 선다** — 각자 최소 100ms(스텁
   지연)씩 그 행을 붙잡는다.
4. 산술로 검증: 200개가 하나의 락을 순서대로 100ms씩 붙잡고 지나간다면 마지막 요청의 대기는
   최악 약 200 × 100ms = 20초. 관측된 **max 18.6s / p99 17.47s / p95 15.3s**는 이 산술과 정확히
   같은 자릿수·형태(선형 증가하는 대기 행렬)로 맞아떨어진다.
5. 이것은 "동기 처리 경로가 일반적으로 느리다"는 것과 **다른 현상**이다 — 애플리케이션이 개별
   요청을 느리게 처리하는 게 아니라, 좁은 공유 자원(하나의 재고 행)에 대한 락 대기 행렬이
   쌓이는 것이다.

**이번 개선(Kafka 후처리 비동기화)이 이 수치를 크게 개선하지 못할 것으로 예상된다.**
계획된 Kafka 비동기화는 **트랜잭션 커밋 이후의 부수 작업**(알림, 랭킹 집계 등)을 큐로 옮기는
것이라, 커밋 이전에 일어나는 (b)의 재고 차감 락 보유 구간 자체를 줄이지 못한다. 이 예측은
after 측정으로 검증되어야 한다 — 만약 after에서도 confirm p95/p99가 비슷하게 높게 남는다면
이 예측이 맞았다는 뜻이고, 그 경우 실제 처방은 Kafka 비동기화가 아니라 (예: 재고 차감을
토스 호출보다 뒤로 미루거나, 낙관적 락/버전 컬럼으로 락 보유 시간을 줄이거나, 재고를 Redis
카운터로 옮기는 등) 별도로 검토해야 한다는 뜻이다. 이 예측을 미리 기록해 두는 이유는, after
결과가 나왔을 때 "그럴 줄 알았다"는 사후 합리화가 아니라 사전에 적어 둔 가설과 실측을
정직하게 대조하기 위해서다.

### browse.js — 시나리오 ② 조회 혼합 70/20/10 (constant-vus 100, 2분)

재측정(p99 포함) 수치:

| 지표 | 값 |
|---|---|
| 총 요청 수 | 298,127 |
| RPS (평균) | 2,483.61 req/s |
| p50 (med) | 39.84 ms |
| p90 | 44.12 ms |
| p95 | 74.32 ms |
| **p99** | **79.31 ms** |
| max | 183.05 ms |
| 에러율 (`http_req_failed`) | 0.00% |
| threshold `rate<0.01` | **PASS** |

(참고: 1차 측정 수치는 p50 40.47ms / p90 44.49ms / p95 75.58ms / max 218.42ms, 293,970 요청 —
재측정과 오차범위 내로 일치.)

조회 경로는 100ms 이내(p99 기준)로 빠르고 안정적이며, confirm.js와 달리 여러 상품에 걸친
분산 조회(랭킹 70%/목록 20%/궁합 10%)라 단일 행 락 같은 병목이 없다. Redis 캐싱 도입 후
랭킹/목록 조회 구간이 실제로 개선되는지 확인할 기준선이다.

## 12. 원본 JSON

- `docs/loadtest/2026-07-29-baseline/confirm-summary.json` — `--summary-trend-stats` 포함
  재측정본. `setup_data.token`은 커밋 전 `(redacted)`로 스크럽됨(§9-1).
- `docs/loadtest/2026-07-29-baseline/browse-summary.json` — 재측정본. `setup_data` 없음.

(k6 `--summary-export` 원본, §11의 표는 이 파일들에서 발췌.)
