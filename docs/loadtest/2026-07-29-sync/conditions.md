# 2026-07-29 sync 부하 측정 조건 (지점 ② 전용 — 확정 후처리 동기 구현)

Task C2 측정 파트. 이 문서는 `docs/loadtest/2026-07-29-baseline-spread/conditions.md`(분산
모형, 이하 "분산 baseline 문서")를 전제로 하고 **다른 점만** 적는다. 하드웨어/OS, k6 버전,
`confirm.js`의 `LOAD_MODEL=spread` 부하 모형 계약(ramping-vus 10→50→200→0, 총 2분,
`http_req_failed: rate<0.01`), `pairs.json` 생성/재고 보충 절차, summary JSON 스크럽 절차 등
여기 적지 않은 항목은 전부 분산 baseline 문서와 동일하다.

## 1. 측정 일시

2026-07-29 (KST) 약 19:14 ~ 19:20.

## 2. 무엇을 재는가 — 지점 ②

Kafka 후처리 비동기화 **이전**, 확정 후처리 3종(랭킹 집계/장바구니 비우기/알림)이 **동기로**
구현된 시점. 커밋 `d9ce1938b172555fdf86c1ac84c06a0f5ab73562`("확정 후처리 3종을 동기로 구현
(A4b — 비동기 비교 기준선)"). main worktree는 HEAD(`55b776c`)에 그대로 두고, 별도 git
worktree(`.claude/worktrees/측정-sync`, `git worktree add ... d9ce1938... --detach`)를 만들어
그 안에서 백엔드를 띄웠다. 측정 완료 후 `git worktree remove .claude/worktrees/측정-sync`로
정리했다 — main worktree는 이 태스크 내내 HEAD `55b776c`에서 움직이지 않았다.

## 3. Flyway / DB 호환성 확인

`d9ce193`과 현재 HEAD(`55b776c`)의 `backend/src/main/resources/db/migration/` 디렉터리를
비교한 결과 마이그레이션 파일 목록이 완전히 동일했다(V92까지, `notification`/
`processed event`/`outbox event` 포함). 즉 두 지점이 **같은 스키마**를 공유하므로 같은 MySQL
컨테이너(`beautyboy-mysql-1`, 13306)를 그대로 재사용해도 안전했다 — 별도 DB 초기화나 스키마
충돌 없음.

- 이 커밋 시점에는 Redis 캐싱 기능 자체가 아직 없다(`application.yml`에 `CACHE_REDIS` 키
  없음, `beautyboy.view-count.redis`만 존재). 따라서 `CACHE_REDIS=true`를 넘겨도 무시된다 —
  브리프의 "지점 ②·③ 양쪽 다 켠다"는 지시는 `ORDER_EVENTS`에는 적용했지만, `CACHE_REDIS`는
  이 커밋에 아직 대응하는 설정이 없어 실질적으로 적용 대상이 아니었다(사실 기록, 판단
  아님 — 서사는 다음 태스크 몫).

## 4. 백엔드 기동 방식

```bash
cd .claude/worktrees/측정-sync/backend
JWT_SECRET=<.env 참조, Base64> TOSS_SECRET_KEY=<.env 참조> \
KAFKA_BOOTSTRAP=localhost:29092 ORDER_EVENTS=true \
  ./gradlew bootRun --args='--spring.profiles.active=local,loadtest'
```

- 활성 프로필: `local,loadtest` (부팅 로그 확인).
- `application-local.yml`은 이 worktree에 없어(gitignore 대상) main worktree의 파일을
  그대로 복사해 넣었다(`jdbc:mysql://localhost:13306/beautyboy`, `root`/`local1234` 동일).
- `KAFKA_BOOTSTRAP=localhost:29092` — 호스트 전용 리스너(방금 추가됨). `ORDER_EVENTS=true`로
  아웃박스 릴레이·컨슈머 활성화.
- 포트: 8080 (compose backend를 먼저 `docker compose stop backend frontend`로 내림).
- 부팅 로그에 ERROR 없음(grep 확인).

## 5. 재고 보충

분산 baseline 문서 §3의 SQL(`UPDATE goods_option go JOIN goods g ... WHERE g.status =
'ON_SALE'`, `stock = 1000000`)을 그대로 실행했다. `pairs.json`은 gitignore 대상이라 저장소에
남아 있던 것이 이전 웨이브의 것이었으므로, 이번 측정 전 §2-2 SQL로 **새로 재생성**했다
(`LIMIT 250`, `ON_SALE` 옵션 상위 250쌍 — 결과는 goods 1~수십 범위, 이전 baseline-spread
문서의 표본과 시드 데이터 자체는 동일해 실질적으로 같은 250쌍이었다).

## 6. 실행 커맨드와 결과

```bash
cd tools/loadtest
BASE_URL=http://localhost:8080 LOADTEST_EMAIL=dry@beautyboy.dev LOADTEST_PASSWORD='seed1234!' \
LOAD_MODEL=spread PAIRS_FILE=./pairs.json \
k6 run --summary-trend-stats="avg,min,med,max,p(90),p(95),p(99)" \
  --summary-export=../../docs/loadtest/2026-07-29-sync/confirm-spread-summary.json \
  confirm.js
```

본 측정 전 `--vus 1 --duration 15s` 스모크(에러율 0%) 통과 확인.

| 지표 | 값 |
|---|---|
| 총 요청 수 (`http_reqs`) | 20,463 |
| 완료 iteration | 10,231 |
| RPS (평균) | 170.40 req/s |
| p50 (med) | 537.23 ms |
| p90 | 1,049.47 ms |
| p95 | 1,150.63 ms |
| **p99** | **1,308.83 ms** |
| max | 2,291.71 ms |
| 에러율 (`http_req_failed`) | 0.00% |
| threshold `rate<0.01` | **PASS** (CLI: `✓ 'rate<0.01' rate=0.00%`, exit code 0) |

(summary JSON의 `thresholds` 필드는 분산 baseline 문서 §4-1에서 확인된 대로 `false`=PASS 의미.)

## 7. summary JSON 스크럽 확인

`docs/loadtest/2026-07-29-sync/confirm-spread-summary.json`의 `setup_data.token`을
`(redacted)`로 치환 후 `grep -c "eyJ" ...` 결과 **0** 확인.

## 8. worktree 정리

측정 종료 후 백엔드 프로세스 종료(`pkill -f "spring.profiles.active=local,loadtest"`) →
`git worktree remove .claude/worktrees/측정-sync` 실행, `git worktree list`로 제거 확인.

## 9. 원본 JSON

- `docs/loadtest/2026-07-29-sync/confirm-spread-summary.json` — `--summary-trend-stats` 포함,
  `setup_data.token`은 `(redacted)`로 스크럽됨(§7).
