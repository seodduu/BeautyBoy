# k6 부하테스트: confirm / browse

Wave 0 "before" 수치를 재는 스크립트 2종이다. 이후 Kafka 후처리 비동기화·Redis 캐싱을 넣은 뒤
**같은 스크립트, 같은 옵션 값**으로 다시 재서 before/after를 비교한다. 그래서 아래 부하 모형
(ramping-vus 단계값, constant-vus 100/2분, 조회 비율 70/20/10, `http_req_failed: rate<0.01`)은
절대 바꾸지 않는다.

- `confirm.js` — 시나리오 ①: 로그인 1회(setup) → 반복마다 주문 생성 → 결제 확정.
- `browse.js` — 시나리오 ②: 랭킹 70% / 상품 목록 20% / 궁합 조회 10%.

## 1. 사전 조건

### 1-1. MySQL 13306 + Redis

리포 루트에서:

```bash
docker compose up -d mysql redis
```

- MySQL은 호스트 13306으로 노출된다(로컬 mysqld의 3306 점유를 피하려고 compose가 고정한 값).
- Redis는 호스트 6379로 노출된다. `loadtest` 시나리오 자체는 Redis를 쓰지 않지만(조회수 버퍼는
  `VIEW_COUNT_REDIS=false`가 기본이라 로컬 실행에선 DB 즉시 증가로 동작한다), Wave 0 이후
  캐싱을 얹은 뒤 같은 스크립트로 재측정할 때 필요해지므로 미리 띄워 둔다.
- `backend/src/main/resources/application-local.yml`이 이미 13306/root/local1234로 맞춰져 있어야
  한다(없다면 그 파일 상단 주석대로 example을 복사).

### 1-2. 백엔드를 `local,loadtest` 프로필로 bootRun

```bash
cd backend
JWT_SECRET=$(openssl rand -base64 32) \
  ./gradlew bootRun --args='--spring.profiles.active=local,loadtest'
```

- `loadtest` 프로필은 `StubPaymentGateway`(Task 0.1)를 활성화한다 — 토스 승인을 100ms 지연 후
  요청 금액 그대로 승인하므로 `paymentKey`는 아무 값이나 통과한다(스크립트는 `stub-<orderNo>`를 보낸다).
- `JWT_SECRET`은 Base64 문자열이어야 한다(설계 문서 관례 — `curl-smoke-recipe` 참고).
- 백엔드는 기본 8080 포트로 뜬다.

## 2. 환경변수

| 변수 | 값 | 고르는 법 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` (기본값) | bootRun한 백엔드 주소. compose 이미지는 낡을 수 있으니 이 스크립트 대상은 반드시 방금 띄운 bootRun 인스턴스여야 한다. |
| `LOADTEST_EMAIL` | `dry@beautyboy.dev` | `V64__seed_member.sql`의 시드 회원. |
| `LOADTEST_PASSWORD` | `seed1234!` | 같은 시드의 평문 비밀번호(해시는 BCrypt로 고정 저장돼 있다). |
| `GOODS_ID` | 예: `42` | `V65__seed_goods_bulk.sql`에 명시적 PK로 박힌 상품(41~190 중 하나). 아래 3절 순서로 옵션 재고를 먼저 채워야 한다. |
| `OPTION_ID` | 아래 3절에서 조회한 값 | `goods_option.id`는 auto-increment라 마이그레이션 실행 순서에 의존한다 — 하드코딩하지 말고 직접 SELECT해서 넣는다. |
| `CATEGORY_CODE` | `C002001001` (기본값, 클렌징폼 하위 leaf) | `V10__catalog.sql`/`V12__seed_catalog.sql`의 category leaf 코드. 접두사 매칭이라 `C002`처럼 상위 코드도 동작한다. |
| `GOODS_A` / `GOODS_B` | 예: `41` / `42` | 궁합 조회(`GET /api/v1/compat/verdicts?base=&candidates=`)의 기준/후보 상품. 존재하는 goods.id면 되고, 존재하지 않아도 CompatVerdictsController는 200 + 전량 OK로 응답한다(에러율에 영향 없음). |

## 3. GOODS_ID/OPTION_ID 재고 준비 (중요 — confirm.js 전용)

`PaymentService.confirm`은 **결제 확정 시점에 재고를 차감**한다(주문 생성 시점은 검증만 하고
차감하지 않는다). confirm.js는 200VU로 1~2분간 반복 주문→확정을 돌리므로 시드 재고
(수십~수백 개 수준)는 순식간에 바닥나 `ORDER_OUT_OF_STOCK`(409)이 쏟아진다.

> `http_req_failed` 기본 정의는 네트워크/연결 실패만 집계하고 4xx/5xx 애플리케이션 에러는
> 포함하지 않는다 — 그래서 재고 고갈이 threshold(`rate<0.01`) 자체를 깨뜨리지는 않는다. 하지만
> 재고가 바닥난 뒤로는 `checks`(`order created`/`confirmed`)가 대량 실패해 측정한 지연시간이
> "정상 처리 경로"를 반영하지 않게 된다 — 재고를 반드시 넉넉하게 채워야 유효한 baseline이 된다.

실행 전 매번 아래 순서를 따른다:

```sql
-- 1) 후보 옵션 확인 (goods_id는 GOODS_ID와 같은 값으로)
SELECT id, goods_id, stock FROM goods_option WHERE goods_id = 42;

-- 2) 위에서 나온 id로 재고를 넉넉히(예: 1000000) 채운다 — 매 baseline 재측정 전 반복
UPDATE goods_option SET stock = 1000000 WHERE id = <위에서 확인한 id>;
```

`mysql -h127.0.0.1 -P13306 -uroot -plocal1234 beautyboy`로 접속해서 실행하면 된다.

## 4. 실행 커맨드

k6가 로컬에 없다면 4-2절을 먼저 본다.

```bash
cd tools/loadtest

# 시나리오 ① 주문 생성→확정
BASE_URL=http://localhost:8080 \
LOADTEST_EMAIL=dry@beautyboy.dev LOADTEST_PASSWORD='seed1234!' \
GOODS_ID=42 OPTION_ID=<3절에서 확인한 id> \
k6 run confirm.js

# 시나리오 ② 조회 혼합 70/20/10
BASE_URL=http://localhost:8080 \
CATEGORY_CODE=C002001001 GOODS_A=41 GOODS_B=42 \
k6 run browse.js
```

### 4-1. 스모크 (커밋 전 필수)

본 측정 전 VU 1 / 30초로 짧게 돌려 에러율 0인지 먼저 확인한다:

```bash
k6 run --vus 1 --duration 30s confirm.js   # 위 env 그대로
k6 run --vus 1 --duration 30s browse.js
```

`http_req_failed` rate가 0이면 통과.

### 4-2. k6 설치

- macOS: `brew install k6`
- 그 외: https://grafana.com/docs/k6/latest/set-up/install-k6/

k6가 없으면 대신 문법만 검증한다(스크립트가 ESM `import` 문법을 쓰므로 `--input-type=module` 필요):

```bash
node --input-type=module --check < confirm.js
node --input-type=module --check < browse.js
```

## 5. 결과 저장 위치

k6 결과(요약 JSON/텍스트)는 `docs/loadtest/<날짜>-baseline/`에 저장한다. 예:

```bash
mkdir -p ../../docs/loadtest/2026-07-29-baseline
k6 run --summary-export=../../docs/loadtest/2026-07-29-baseline/confirm.json confirm.js
k6 run --summary-export=../../docs/loadtest/2026-07-29-baseline/browse.json browse.js
```

Kafka/Redis 개선 이후 재측정할 때는 같은 규약으로 `docs/loadtest/<날짜>-after-kafka-redis/` 등
디렉터리명만 바꿔 나란히 비교한다.
