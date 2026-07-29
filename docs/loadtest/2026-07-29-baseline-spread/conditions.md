# 2026-07-29 baseline-spread 부하 측정 조건 (분산 모형)

Task 0.4. **이 문서는 `docs/loadtest/2026-07-29-baseline/conditions.md`(집중 모형, 이하
"집중 문서")를 전제로 하고 다른 점만 적는다.** 하드웨어/OS, MySQL·Redis 구성, 백엔드 기동
방식(`local,loadtest` bootRun, JWT_SECRET, 포트 8080), compose stop/start 절차, `browse.js`
조건 등 여기 적지 않은 항목은 전부 집중 문서와 동일하다.

## 0. 왜 두 번째 모형이 필요했나

집중 문서 §11의 원인 분석(재확인함, 아래 참고): confirm p95 15.3초의 지배 원인은 동기 결제
경로 일반의 느림이 아니라 **200 VU 전원이 `OPTION_ID=51` 단 하나를 사서 생긴 InnoDB 행 락
직렬화**였다. 세트 A(Kafka 후처리 비동기화)는 커밋 이후 부수 작업만 옮기므로 이 락 보유 구간
자체를 줄이지 못한다 — 그래서 "일반적인 다품목 트래픽"을 대표하는 별도 모형이 필요했다.
이 측정이 그 모형이다.

## 1. 측정 일시

2026-07-29 (KST) 약 15:11(스모크) ~ 15:49(본 측정 완료). 집중 문서와 같은 세션에서
이어서 측정했다(백엔드 재기동 없이 동일 bootRun 인스턴스, mysql/redis도 동일 컨테이너).

## 2. `confirm.js`의 `LOAD_MODEL` 스위치 (이번 태스크의 변경 본체)

`tools/loadtest/confirm.js`에 환경변수 `LOAD_MODEL`을 추가했다:

- `single`(기본값, 미지정 시): 기존과 정확히 동일 — `GOODS_ID`/`OPTION_ID` 하나만 산다.
- `spread`: `PAIRS_FILE`(기본 `./pairs.json`)의 goods/option 쌍 목록에서
  `(__VU + __ITER) % length`로 반복마다 회전 선택한다.

부하 모형 계약(ramping-vus 10→30s 50→1m 200→30s 0, `http_req_failed: rate<0.01`, 주문 생성→확정
순서)은 전혀 바꾸지 않았다 — 바뀐 건 "어떤 상품을 사는가" 하나뿐이다.

### 2-1. 회귀 확인 (하위 호환)

본 측정 전 `LOAD_MODEL=single`(명시 지정)로 VU 1 / 30초 스모크를 돌려 기존 동작이 그대로인지
확인했다:

```
✓ 'rate<0.01' rate=0.00%
checks_succeeded...: 100.00% 500 out of 500
http_reqs..........: 501    16.605691/s
iterations.........: 250    8.286273/s
```

집중 문서의 본 측정과 별개로, 이 스모크만으로도 `single` 경로가 정상 동작함(에러율 0%)을
확인했다 — 회귀 없음.

### 2-2. goodsNo/optionNo 쌍 목록 구성 방식과 근거

주문 생성 API가 `items`에 `{goodsNo, optionNo}`를 **쌍**으로 요구하므로(`OrderCreateRequest`),
옵션만 바꾸고 상품을 고정하면 요청이 깨진다. **쉼표구분 환경변수가 아니라 k6 `open()`으로
JSON 파일(`pairs.json`)을 읽는 방식을 택했다** — 200쌍 이상을 환경변수 한 줄로 넘기면 셸
인용/길이 문제로 비실용적이고, `open()`이 가장 단순한 대안이었기 때문이다(과설계 회피 —
별도 스키마나 CSV 파서 없이 평범한 `[{goodsNo, optionNo}, ...]` 배열 하나).

`pairs.json`은 `.gitignore`에 추가해 커밋하지 않는다(SQL로 언제든 재현 가능하므로). 생성 SQL과
재고 보충 SQL은 `tools/loadtest/README.md` §3-1에 있다 — 요지만 적으면:

```sql
SELECT JSON_ARRAYAGG(JSON_OBJECT('goodsNo', goods_id, 'optionNo', id))
FROM (
  SELECT go.goods_id, go.id
  FROM goods_option go JOIN goods g ON g.id = go.goods_id
  WHERE g.status = 'ON_SALE'
  ORDER BY go.id
  LIMIT 250
) t;
```

이번 측정에 쓴 `pairs.json`은 goods/option 쌍 **250개**(옵션 id 1~258 범위 내, `SOLD_OUT`/
`HIDDEN` 상품 제외 후 상위 250행)였다 — 브리프 요건(피크 VU 200보다 많은 최소 200개)을
충족한다.

## 3. 재고 보충 범위 (집중 문서 §5와 다른 점)

집중 모형은 옵션 **하나**(`id=51`)만 100만으로 채웠지만, 분산 모형은 `pairs.json`에 담긴
250개 옵션이 전부 반복적으로 소진되므로 **범위 전체**를 보충해야 한다:

```sql
UPDATE goods_option go JOIN goods g ON g.id = go.goods_id
SET go.stock = 1000000
WHERE g.status = 'ON_SALE';
```

`g.status = 'ON_SALE'` 조건의 옵션 수는 350개(측정 시점 `goods_option` 전체 359개 중)로,
`pairs.json`의 250개를 포함하는 상위집합이다 — 옵션 id를 일일이 나열하지 않고 한 번에
채웠다.

## 4. 측정 결과 요약 — confirm.js 분산 모형

`k6 run --summary-trend-stats="avg,min,med,max,p(90),p(95),p(99)" LOAD_MODEL=spread
PAIRS_FILE=./pairs.json confirm.js` (ramping-vus 10→50→200→0, 총 2분, 그 밖 env는
`BASE_URL/LOADTEST_EMAIL/LOADTEST_PASSWORD`만 — `GOODS_ID`/`OPTION_ID`는 spread 모형에서
쓰이지 않는다):

| 지표 | 값 |
|---|---|
| 총 요청 수 | 21,303 (`http_reqs`) |
| 완료 iteration | 10,651 |
| RPS (평균) | 177.28 req/s |
| p50 (med) | 522.6 ms |
| p90 | 1,023.9 ms |
| p95 | 1,101.2 ms |
| **p99** | **1,229.9 ms** |
| max | 3,272.6 ms |
| 에러율 (`http_req_failed`) | 0.00% |
| threshold `rate<0.01` | **PASS** (CLI: `✓ 'rate<0.01' rate=0.00%`) |

### 4-1. `--summary-export` JSON의 `thresholds` 필드 — 정정: 버그가 아니라 정상 표기

(최초 작성 시 이 필드를 "k6 v2.1.0의 직렬화 버그"로 잘못 결론지었다. 아래는 사실 확인 후
정정한 내용이다.)

k6 CLI 실행 화면은 `THRESHOLDS` 섹션에 `✓ 'rate<0.01' rate=0.00%`로 명확히 PASS를 표시했고
`checks_succeeded`도 100%였다. 같은 실행에서 `--summary-export`로 저장한 JSON의
`metrics.http_req_failed.thresholds`는 `{"rate<0.01": false}`로 찍혀 있다(값 자체는
`"value": 0`으로 정상).

**이 필드는 threshold의 실패 여부(`LastFailed`)를 담는다 — `false`는 "실패하지 않음", 즉
PASS라는 정상 의미다.** 로컬에서 최소 재현으로 검증했다: k6 스크립트 두 개(항상 통과하는
check, 항상 실패하는 check 각각에 `thresholds: { checks: ['rate>0.99'] }`)를 5회 반복으로
돌려 `--summary-export`를 비교했다.

```
$ k6 run --summary-export=fail-summary.json fail.js   # threshold가 실제로 깨지는 케이스
...THRESHOLDS  ✗ 'rate>0.99' rate=0.00%   (exit code 99)
$ python3 -c "import json; print(json.load(open('fail-summary.json'))['metrics']['checks']['thresholds'])"
{'rate>0.99': True}

$ k6 run --summary-export=pass-summary.json pass.js   # threshold가 통과하는 케이스
...THRESHOLDS  ✓ 'rate>0.99' rate=100.00%   (exit code 0)
$ python3 -c "import json; print(json.load(open('pass-summary.json'))['metrics']['checks']['thresholds'])"
{'rate>0.99': False}
```

CLI가 FAIL(✗, exit 99)을 보인 실행에서만 JSON의 `thresholds` 값이 `true`였고, PASS(✓, exit 0)
실행에서는 `false`였다 — 즉 이 필드는 "threshold가 깨졌는가"를 뜻하며 버그가 아니다.

방증도 있다: 같은 `{"rate<0.01": false}`가 집중 모형 JSON(`docs/loadtest/2026-07-29-baseline/
browse-summary.json`, `confirm-summary.json`)에도 똑같이 있다 — "분산 측정에서 새로 발견된
이상"이라는 최초 서술 자체가 성립하지 않았다. 이 필드는 두 모형 모두에서 정상 PASS 표기였다.

### 4-2. 분산 검증 (재고 감소가 여러 행에 퍼졌는가)

측정 종료 후 `pairs.json`에 등장하는 250개 옵션 id 기준으로 재고 감소를 확인했다:

```sql
SELECT COUNT(*) FROM goods_option WHERE id IN (<250개 id>) AND stock < 1000000;
-- 결과: 224 / 250
SELECT MIN(1000000-stock), MAX(1000000-stock) FROM goods_option WHERE id IN (<250개 id>);
-- 결과: min 1, max 342
```

224/250개 행이 감소했고, 감소량도 1~342 범위로 고르게 퍼져 있다 — 특정 한 행에 몰리지
않았다. (누적치는 이번 본 측정 이전의 스모크·시행착오 실행분을 포함한다.) 집중 모형(단일
옵션 1개만 감소)과 뚜렷이 대비된다.

## 5. 집중 vs 분산 비교표와 해석

| 지표 | 집중(`2026-07-29-baseline`, 재측정본) | 분산(`2026-07-29-baseline-spread`) | 변화 |
|---|---|---|---|
| 총 요청 수 | 2,297 | 21,303 | 9.3배 |
| 완료 iteration | 1,148 | 10,651 | 9.3배 |
| RPS (평균) | 17.99 req/s | 177.28 req/s | 9.9배 |
| p50 | 4,470 ms | 522.6 ms | **−88.3%** |
| p90 | 13,010 ms | 1,023.9 ms | **−92.1%** |
| p95 | 15,300 ms | 1,101.2 ms | **−92.8%** |
| p99 | 17,470 ms | 1,229.9 ms | **−93.0%** |
| max | 18,610 ms | 3,272.6 ms | **−82.4%** |
| 에러율 | 0.00% | 0.00% | 동일 |
| threshold | PASS | PASS | 동일 |

### 해석

**분산 모형에서 지연이 크게 떨어졌다 — 락 직렬화 가설이 검증됐다.** 같은 2분, 같은
ramping-vus 200 피크, 같은 백엔드/DB 구성에서 유일하게 바꾼 변수(구매 대상을 옵션 1개 →
250개로 분산)만으로 p95가 15.3초 → 1.1초로 92.8% 줄었고, 처리량(iteration 수)은 오히려
9.3배 늘었다. 이는 집중 문서 §11의 예측 — "confirm 경로 자체가 느린 게 아니라 단일 공유
행에 대한 락 대기 행렬이 원인이며, 구매가 여러 행에 분산되면 대기 행렬이 사라져야 한다" —
와 정확히 일치한다. 즉:

- 세트 A(Kafka 후처리 비동기화)가 이 분산 시나리오의 지연을 더 개선할 여지는 제한적이다
  (이미 p95 1.1초 수준이라 병목이 아니다). Kafka 비동기화의 효과는 오히려 **집중
  시나리오(타임세일/한정수량 폭주)** 쪽에서 최종 응답을 이루는 부수 작업 처리를 줄여 커밋
  이후 지연을 줄이는 형태로 나타날 가능성이 높다 — 다만 이 예측도 after 측정으로 검증돼야
  한다.
- 분산 모형의 남은 p95 1.1초/p99 1.23초는 여전히 존재하며, 이는 스텁 토스의 100ms 고정
  지연 + 정상적인 DB 왕복 + HikariCP 풀 대기가 섞인 수치로 보인다(원인을 더 파고들 필요가
  있다면 별도 태스크). 이 문서의 목적은 어느 쪽이 맞는지 확정하는 게 아니라, Kafka
  비동기화 이후 재측정 시 비교할 **before 기준선을 남기는 것**이다.

## 6. 스크립트 커밋 SHA

측정에 사용한 `confirm.js`/`README.md`는 이 문서를 커밋하는 것과 같은 커밋에 포함된다(§2-1
참고 — 별도 스모크 재측정 없이 같은 워킹트리에서 이어 측정했다).

## 7. compose 원복

집중 문서 §6과 동일하게 측정 전 `docker compose stop backend frontend`(mysql/redis는
유지), 측정 완료 후 `docker compose start backend frontend` 실행 및 `docker compose ps`로
`Up`/`healthy` 재확인 완료.

## 8. summary JSON 스크럽 확인

`docs/loadtest/2026-07-29-baseline-spread/confirm-summary.json`의 `setup_data.token`은
커밋 전 집중 문서 §9-1과 동일한 절차로 `(redacted)`로 치환했다. `grep -c eyJ` 결과 0으로
JWT 흔적이 없음을 확인했다.

## 9. 이 측정의 한계

집중 문서 §10의 한계(스텁 토스, bootRun의 JIT 워밍업 차이, 로컬 단일 컨테이너 MySQL/Redis)는
분산 모형에도 동일하게 적용된다. 추가로:

- `pairs.json`은 `.gitignore` 대상이라 리포에 커밋되지 않는다 — 재현하려면 §2-2 SQL을
  다시 실행해야 하며, `goods_option`/`goods` 데이터가 이후 마이그레이션으로 바뀌면 정확히
  같은 250개 쌍이 나오지 않을 수 있다(상위집합 조건은 동일하게 유지되므로 재현성 자체는
  보장된다).
- §4-1에서 정리했듯, `--summary-export` JSON의 `thresholds` 필드(`false`)는 threshold PASS를
  뜻하는 정상 표기다(버그 아님). 다만 이 필드만 보고 판단하지 말고 CLI의 실시간 THRESHOLDS
  출력도 함께 확인하는 습관은 유지하는 편이 안전하다(exit code로도 확인 가능).

## 10. 원본 JSON

- `docs/loadtest/2026-07-29-baseline-spread/confirm-summary.json` —
  `--summary-trend-stats` 포함, `setup_data.token`은 `(redacted)`로 스크럽됨(§8).
