# 2026-07-29 after 부하 측정 조건 (지점 ③ — Kafka 비동기 + Redis 캐싱)

Task C2 측정 파트. `docs/loadtest/2026-07-29-baseline/conditions.md`(집중 모형)와
`docs/loadtest/2026-07-29-baseline-spread/conditions.md`(분산 모형)를 전제로 하고
**다른 점만** 적는다. 하드웨어/OS, k6 버전, `confirm.js`/`browse.js`의 부하 모형 계약
(ramping-vus·constant-vus 단계값, threshold, 요청 순서), `browse.js` 환경변수 등 여기 적지
않은 항목은 전부 baseline 문서들과 동일하다.

## 1. 측정 일시

2026-07-29 (KST) 약 19:21 ~ 19:30.

## 2. 무엇을 재는가 — 지점 ③

현재 HEAD, 커밋 `55b776c496abf5157bad50942735370746e1c7ef`("feat: Kafka에 호스트 전용
리스너(PLAINTEXT_HOST) 추가"). Kafka 후처리 비동기화(아웃박스 릴레이 → 컨슈머 3종: 랭킹
집계/장바구니 비우기/알림) + Redis 캐싱(ranking/goodsList/compat)이 모두 반영된 시점. main
worktree에서 그대로 측정했다(worktree 분리 불필요).

## 3. 백엔드 기동 방식

```bash
cd backend
JWT_SECRET=<.env 참조, Base64> TOSS_SECRET_KEY=<.env 참조> \
KAFKA_BOOTSTRAP=localhost:29092 ORDER_EVENTS=true CACHE_REDIS=true \
  ./gradlew bootRun --args='--spring.profiles.active=local,loadtest'
```

- 활성 프로필: `local,loadtest` (부팅 로그 확인, ERROR 없음).
- `KAFKA_BOOTSTRAP=localhost:29092` — 컨테이너 내부 리스너(`kafka:9092`)가 아니라 방금
  추가된 호스트 전용 리스너(`PLAINTEXT_HOST`) 주소. 호스트 bootRun 백엔드는 컨테이너 이름
  `kafka`를 해석할 수 없으므로 이 주소가 필수다.
- `ORDER_EVENTS=true CACHE_REDIS=true` — 두 플래그 모두 켜짐(기본값은 둘 다 `false`).
- 포트: 8080 (`docker compose stop backend frontend`로 compose backend를 먼저 내림).

## 4. Kafka 스택 구성

- `docker compose up -d kafka`로 이미 떠 있던 `beautyboy-kafka`(healthy) 재사용.
- 실제 컨슈머 그룹은 하나의 논리적 이름(`beautyboy-post-order`)이 아니라 **리스너별로
  분리된 3개 그룹**이었다(`KafkaConsumerConfig`가 리스너마다 `groupId`를 명시해 덮어씀):
  - `beautyboy-post-order.sales-aggregation`
  - `beautyboy-post-order.cart-clear`
  - `beautyboy-post-order.notification`
  브리프에 적힌 `--group beautyboy-post-order` 단일 이름으로는 그룹이 존재하지 않아
  (`Error: Consumer group 'beautyboy-post-order' does not exist.`) 3개 그룹 각각을 5초
  간격으로 조회하는 방식으로 대체했다(§6).
- 토픽: `order-events`(파티션 3개), `order-events.DLT`.

## 5. 재고 보충 범위

- 분산 모형(`confirm-spread`): `UPDATE goods_option go JOIN goods g ... WHERE g.status =
  'ON_SALE' SET stock = 1000000` — baseline-spread 문서 §3과 동일 SQL, 측정 전 1회 실행.
  `pairs.json`은 gitignore 대상이라 저장소에 남아 있던 것이 이전 웨이브 것이었으므로 이번
  측정 전 baseline-spread 문서 §2-2 SQL로 새로 재생성했다(`LIMIT 250`, 옵션 350개 중 상위
  250쌍).
- 집중 모형(`confirm-single`, `GOODS_ID=42 OPTION_ID=51`): `UPDATE goods_option SET stock =
  1000000 WHERE id = 51` — 스모크 실행 직후 및 본 측정 직전 각각 1회씩 재실행(스모크가
  일부 재고를 소진하므로 본 측정 전 재보충).
- `browse.js`: 재고 보충 대상 아님(조회 전용).

## 6. 컨슈머 랙 수집

측정 중(confirm-spread → confirm-single → browse 구간에 걸쳐, 19:21:46 ~ 19:29:51) 5초
간격으로 3개 그룹 각각에 대해 `kafka-consumer-groups.sh --describe`를 실행해
`docs/loadtest/2026-07-29-after/consumer-lag.log`에 타임스탬프와 함께 남겼다.

- 관측된 LAG 값(파티션별)은 대부분 **0**, 드물게 1~3까지 순간 상승 후 곧 0으로 복귀 —
  382건 중 0, 138건 1, 17건 2, 3건 3(파티션 3개 × 3그룹 × 샘플 수 기준 집계).
- 릴레이 배치 크기(`relay-batch-size: 100`)와 지연(`relay-delay-ms: 1000`)이 이 측정의
  주문 발생률(confirm-spread 기준 초당 iteration 약 85회)에 비해 충분히 빨라, 랙이 눈에
  띄게 쌓였다가 풀리는 큰 진폭은 관측되지 않았다 — 작은 규모(0~3)에서 "쌓였다 풀리는"
  패턴만 확인된다.

## 7. 캐시 히트율

browse.js 측정(1,909,313 iteration, constant-vus 100, 2분) 종료 직후 수집:

```
cache.gets{result=hit}  = 1,931,836
cache.gets{result=miss} = 3
```

`docs/loadtest/2026-07-29-after/cache-hits.txt`에 원본 저장. 캐시 대상은
`ranking`/`goodsList`/`compat` 3종(`CATEGORY_CODE=C002001001`, `GOODS_A=41`, `GOODS_B=42`로
고정된 조회라 캐시 키 공간이 매우 좁음 — 히트율이 사실상 100%에 수렴한 것은 이 조건에서
당연한 결과다. 미스 3건은 캐시가 비어 있던 최초 요청 3개(ranking/goodsList/compat 각 1회)로
추정된다(진단은 다음 태스크 몫이라 여기서는 수치만).

## 8. 측정 결과 — 4회 수치 표

| 시나리오 | 지점 | 총 요청 수 | 완료 iteration | RPS | p50(med) | p90 | p95 | p99 | max | 에러율 | threshold |
|---|---|---|---|---|---|---|---|---|---|---|---|
| confirm spread | ②(sync) | 20,463 | 10,231 | 170.40 req/s | 537.23 ms | 1,049.47 ms | 1,150.63 ms | 1,308.83 ms | 2,291.71 ms | 0.00% | PASS |
| confirm spread | ③(after) | 20,569 | 10,284 | 171.23 req/s | 543.29 ms | 1,040.34 ms | 1,145.44 ms | 1,321.08 ms | 4,417.19 ms | 0.00% | PASS |
| confirm single | ③(after) | 2,281 | 1,140 | 17.82 req/s | 4,256.77 ms | 13,785.05 ms | 16,419.78 ms | 18,982.77 ms | 20,429.96 ms | 0.00% | PASS |
| browse | ③(after) | 1,909,313 | 1,909,313 | 15,910.27 req/s | 6.235 ms | 7.481 ms | 8.023 ms | 9.951 ms | 39.569 ms | 0.00% | PASS |

(threshold 판정은 CLI 실시간 출력의 `✓ 'rate<0.01' rate=0.00%`과 k6 종료 코드 0을 근거로
했다 — summary JSON의 `metrics.http_req_failed.thresholds`는 4건 모두 `{"rate<0.01": false}`이며,
이는 baseline-spread 문서 §4-1에서 확인된 대로 PASS를 뜻하는 정상 표기다.)

참고 — 지점 ①(baseline, 이미 측정 완료)의 같은 시나리오 수치:

| 시나리오 | 지점 | p95 | p99 | RPS |
|---|---|---|---|---|
| confirm spread | ①(baseline-spread) | 1,101.2 ms | 1,229.9 ms | 177.28 req/s |
| confirm single | ①(baseline) | 15,300 ms | 17,470 ms | 17.99 req/s |
| browse | ①(baseline) | 74.32 ms | 79.31 ms | 2,483.61 req/s |

## 9. compose 원복

측정 완료 후 `pkill -f "spring.profiles.active=local,loadtest"`로 bootRun 프로세스 종료 →
`docker compose start backend frontend` → `docker compose ps`로 `beautyboy-backend-1`,
`beautyboy-frontend-1` 모두 `Up`/`healthy` 재확인 완료.

## 10. summary JSON 스크럽 확인

세 confirm 계열 summary JSON(`confirm-spread-summary.json`, `confirm-single-summary.json`)
모두 `setup_data.token`을 `(redacted)`로 치환 후 `grep -c "eyJ" <파일>` 결과 **0** 확인.
`browse-summary.json`은 `setup_data`가 원래 없음(스크럽 대상 아님).

## 11. 이 측정의 한계

baseline/baseline-spread 문서의 한계(스텁 토스, bootRun의 JIT 워밍업, 로컬 단일 컨테이너
MySQL/Redis/Kafka)가 동일하게 적용된다. 추가로:

- 컨슈머 그룹 이름이 브리프가 가정한 단일 이름과 달리 리스너별 3개로 분리돼 있었다(§4) —
  측정 자체에는 영향 없으나 랙 수집 커맨드를 그 자리에서 조정했다.
- `pairs.json`은 gitignore 대상이라 커밋되지 않는다 — 재현하려면 baseline-spread 문서
  §2-2 SQL을 다시 실행해야 한다.
- 캐시 히트율(§7)은 CATEGORY_CODE/GOODS_A/GOODS_B가 고정된 이 스크립트 특유의 좁은 키
  공간에서 나온 수치이며, 실제 운영 트래픽(다양한 카테고리·상품 조합)의 히트율을 그대로
  대표하지 않는다.

## 12. 원본 JSON / 로그

- `docs/loadtest/2026-07-29-after/confirm-spread-summary.json`
- `docs/loadtest/2026-07-29-after/confirm-single-summary.json`
- `docs/loadtest/2026-07-29-after/browse-summary.json`
- `docs/loadtest/2026-07-29-after/consumer-lag.log`
- `docs/loadtest/2026-07-29-after/cache-hits.txt`
- `docs/loadtest/2026-07-29-sync/confirm-spread-summary.json` (지점 ②, 별도 폴더)
