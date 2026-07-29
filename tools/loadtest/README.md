# k6 부하테스트: confirm / browse

Wave 0 "before" 수치를 재는 스크립트 2종이다. 이후 Kafka 후처리 비동기화·Redis 캐싱을 넣은 뒤
**같은 스크립트, 같은 옵션 값**으로 다시 재서 before/after를 비교한다. 그래서 아래 부하 모형
(ramping-vus 단계값, constant-vus 100/2분, 조회 비율 70/20/10, `http_req_failed: rate<0.01`)은
절대 바꾸지 않는다.

- `confirm.js` — 시나리오 ①: 로그인 1회(setup) → 반복마다 주문 생성 → 결제 확정.
- `browse.js` — 시나리오 ②: 랭킹 70% / 상품 목록 20% / 궁합 조회 10%.

### confirm.js의 두 가지 부하 모형(`LOAD_MODEL`)

Task 0.4에서 `confirm.js`에 `LOAD_MODEL` 스위치가 추가됐다(부하 모형 계약 — ramping-vus
단계값/threshold/요청 순서 — 자체는 바꾸지 않았다. 바뀐 건 "어떤 상품을 사는가" 하나뿐이다):

| `LOAD_MODEL` | 무엇을 사는가 | 용도 |
|---|---|---|
| `single` (기본값, 미지정 시) | `GOODS_ID`/`OPTION_ID` 하나 고정 | 기존(`2026-07-29-baseline`) 동작과 정확히 동일 — 단일 SKU 락 경합 진단용 |
| `spread` | `PAIRS_FILE`의 goods/option 쌍 목록에서 반복마다 회전 선택 | 일반적 다품목 트래픽의 확정 지연 측정, Kafka 후처리 비동기화 before/after 기준 |

자세한 준비 절차는 3-1절, 실행 예시는 4절 참고.

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
| `GOODS_A` / `GOODS_B` | `41` / `42` (기본값, `browse.js`에 하드코딩) | 궁합 조회(`GET /api/v1/compat/verdicts?base=&candidates=`)의 기준/후보 상품. 존재하는 goods.id면 되고, 존재하지 않아도 CompatVerdictsController는 200 + 전량 OK로 응답한다. **주의**: 비워 두면(undefined) 쿼리스트링이 `base=undefined&candidates=undefined`가 되어 Spring이 400을 돌려주고 이 트래픽(전체의 10%)이 `http_req_failed` threshold(`rate<0.01`)를 즉시 깬다 — 그래서 기본값을 스크립트에 박아 뒀다. |
| `LOAD_MODEL` | `single`(기본값) 또는 `spread` | confirm.js 전용(Task 0.4). `single`이면 `GOODS_ID`/`OPTION_ID`를 그대로 쓰는 기존 동작, `spread`면 `PAIRS_FILE`의 쌍 목록에서 반복마다 고른다. |
| `PAIRS_FILE` | `./pairs.json`(기본값) | `LOAD_MODEL=spread`일 때만 쓰인다. goods/option 쌍 JSON 배열 파일 경로(k6의 `open()`으로 읽는다). 만드는 SQL은 3-1절. |

## 3. GOODS_ID/OPTION_ID 재고 준비 (중요 — confirm.js 전용)

`PaymentService.confirm`은 **결제 확정 시점에 재고를 차감**한다(주문 생성 시점은 검증만 하고
차감하지 않는다). confirm.js는 200VU로 1~2분간 반복 주문→확정을 돌리므로 시드 재고
(수십~수백 개 수준)는 순식간에 바닥나 `ORDER_OUT_OF_STOCK`(409)이 쏟아진다.

> k6 기본 분류는 **status 200~399 밖을 전부 failed로 집계한다**(`setResponseCallback`으로
> 재정의하지 않는 한 — 이 스크립트는 재정의하지 않았다). 즉 재고 고갈로 409(`ORDER_OUT_OF_STOCK`)가
> 유의미한 비율로 나오면 지연시간만 왜곡되는 게 아니라 **`http_req_failed` threshold(`rate<0.01`) 자체가
> 깨져 k6 실행이 실패로 끝난다.** 재고 사전 보충은 선택이 아니라 이 스크립트가 성립하기 위한 필수
> 전제조건이다 — 매 실행 전 아래 절차를 반드시 거친다.

실행 전 매번 아래 순서를 따른다:

```sql
-- 1) 후보 옵션 확인 (goods_id는 GOODS_ID와 같은 값으로)
SELECT id, goods_id, stock FROM goods_option WHERE goods_id = 42;

-- 2) 위에서 나온 id로 재고를 넉넉히(예: 1000000) 채운다 — 매 baseline 재측정 전 반복
UPDATE goods_option SET stock = 1000000 WHERE id = <위에서 확인한 id>;
```

`mysql -h127.0.0.1 -P13306 -uroot -plocal1234 beautyboy`로 접속해서 실행하면 된다.

### 3-1. `LOAD_MODEL=spread` 전용 준비 — 쌍 목록과 재고 보충

**왜 옵션 id만 바꾸면 안 되는가:** 주문 생성 API(`POST /api/v1/orders`)는 `items`에
`{goodsNo, optionNo}`를 **쌍**으로 요구한다(`OrderCreateRequest`). `goodsNo`를 고정한 채
`optionNo`만 다른 옵션으로 바꾸면 그 옵션이 실제로는 다른 상품 소속이라 서비스 레이어가
거부한다(`GOODS_NOT_FOUND` 류) — 그래서 spread 모형은 goods/option **쌍**의 목록이 필요하다.

**왜 쉼표구분 환경변수가 아니라 JSON 파일인가:** 200쌍 이상을 `KEY=1:1,1:2,2:3,...` 식으로
넘기면 한 줄이 수천 자가 되어 셸 인용·CI 변수 길이 제한에 쉽게 걸리고, 커맨드 자체가
읽기 불가능해진다. k6는 `open()`으로 로컬 파일을 읽을 수 있으므로(테스트 실행 전
번들링 시점에 로드됨) JSON 배열 파일 하나로 넘기는 쪽이 가장 단순하다 — 과설계(별도 스키마,
CSV 파서 등)를 피하려고 `[{goodsNo, optionNo}, ...]` 평범한 배열 형태만 썼다.

**1) 쌍 목록 생성 (`tools/loadtest/pairs.json`, gitignore 대상 — SQL로 언제든 재현 가능):**

```bash
docker exec beautyboy-mysql-1 mysql -uroot -plocal1234 beautyboy -N -e "
SELECT JSON_ARRAYAGG(JSON_OBJECT('goodsNo', goods_id, 'optionNo', id))
FROM (
  SELECT go.goods_id, go.id
  FROM goods_option go JOIN goods g ON g.id = go.goods_id
  WHERE g.status = 'ON_SALE'
  ORDER BY go.id
  LIMIT 250
) t;" > tools/loadtest/pairs.json
```

- `g.status = 'ON_SALE'`로 걸러서 `SOLD_OUT`/`HIDDEN` 상품의 옵션은 애초에 목록에 넣지 않는다
  (재고를 아무리 채워도 주문 생성 검증에서 걸릴 수 있는 상품을 배제).
- `LIMIT 250`은 피크 VU(200)보다 여유 있게 커야 한다는 브리프 요건(최소 200개)을 반영한
  값이다 — 옵션 수가 VU 수보다 적으면 `(__VU + __ITER) % length` 회전이 VU 수보다 적은
  행에 다시 몰리게 된다.
- `mysql -N`은 컬럼 헤더를 빼고 값만 출력하므로 결과 한 줄이 그대로 유효한 JSON 배열이 된다.
  (경고 메시지는 stderr로 나가므로 `>` 리다이렉트에는 섞이지 않는다.)

**2) 재고 보충 — spread 모형은 옵션 200개 이상에 나눠 보충해야 한다:**

```sql
UPDATE goods_option go JOIN goods g ON g.id = go.goods_id
SET go.stock = 1000000
WHERE g.status = 'ON_SALE';
```

- 위 쌍 목록 생성 쿼리와 같은 필터(`g.status = 'ON_SALE'`)를 써서, `pairs.json`이 담고 있는
  옵션(그리고 여유분)을 전부 커버하는 상위집합을 한 번에 채운다 — 옵션 id를 일일이 나열할
  필요가 없다.
- 3절(단일 모형)과 마찬가지로 매 baseline 재측정 전 반복 실행한다.

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

### 4-0. 분산 모형(`LOAD_MODEL=spread`) 실행

3-1절로 `pairs.json`을 만들고 재고를 보충한 뒤:

```bash
cd tools/loadtest

BASE_URL=http://localhost:8080 \
LOADTEST_EMAIL=dry@beautyboy.dev LOADTEST_PASSWORD='seed1234!' \
LOAD_MODEL=spread PAIRS_FILE=./pairs.json \
k6 run confirm.js
```

`GOODS_ID`/`OPTION_ID`는 spread 모형에서는 쓰이지 않는다(무시된다) — 대신 `PAIRS_FILE`의
쌍을 반복마다 회전 선택한다.

### 4-1. 스모크 (커밋 전 필수)

본 측정 전 VU 1 / 30초로 짧게 돌려 에러율 0인지 먼저 확인한다:

```bash
k6 run --vus 1 --duration 30s confirm.js   # 위 env 그대로
k6 run --vus 1 --duration 30s browse.js
```

`http_req_failed` rate가 0이면 통과.

`LOAD_MODEL`을 새로 건드렸다면 **먼저 `LOAD_MODEL=single`(또는 미지정)로 이 스모크를 돌려
기존 경로가 안 깨졌는지 회귀 확인부터 한다** — 하위 호환이 계약이기 때문이다. 그 다음
`LOAD_MODEL=spread`로도 같은 스모크를 한 번 돌려본다.

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

집중/분산 두 부하 모형의 결과는 폴더로 분리해 나란히 둔다 — `docs/loadtest/2026-07-29-baseline/`
(집중, `LOAD_MODEL=single`)과 `docs/loadtest/2026-07-29-baseline-spread/`(분산,
`LOAD_MODEL=spread`). 두 모형의 조건 차이와 수치 비교는 후자 폴더의 `conditions.md`에 있다.
