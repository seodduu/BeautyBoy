# v3 개인화 학습 파이프라인 (tools/recsys)

루틴 추천 v3의 투 타워 모델을 만드는 파이프라인이다. `make all` 한 번이면 시드 생성부터
게이트 판정까지 끝나고, 브라우저가 받을 모델 파일 두 개가 `out/<버전>/`에 떨어진다.

**이 파이프라인은 실행 중 MySQL에 붙지 않는다.** 카탈로그는 사람이 한 번만 CSV로 덤프해
저장소에 넣고(`export_catalog.py`), 그 뒤로는 아무도 DB를 읽지 않는다. 덕분에 백엔드 작업과
병렬로 돌려도 안전하고, `pytest`를 돌릴 때 살아 있는 DB가 필요 없다.

## 파이프라인

```
export_catalog.py  (한 번만. 사람이 돌린다)   MySQL 13306 → data/catalog/*.csv  (저장소에 커밋)
        │
seed_generator.py  ── data/catalog/*.csv 를 읽어
        ├──→ data/raw/{members,orders,order_items,reviews,behavior_events}.csv   ← build_table.py 입력
        ├──→ data/seed_v3.sql        (Wave D가 실 MySQL에 부어 화면으로 확인할 때만 쓴다)
        └──→ data/planted.json       (게이트 계약)
        │
build_table.py ── data/raw/*.csv + data/catalog/*.csv → data/train.csv
        │
train.py ──→ out/<version>/{item_embeddings.json,user_tower.json}
        │
gate.py ──→ reports/gate-<version>.json
```

`seed_v3.sql`은 파이프라인의 어느 단계도 읽지 않는다. 실 화면을 볼 때만 쓰는 곁가지다.

## 처음 돌리는 사람

### 1. 가상환경 만들기

파이썬은 반드시 이 디렉터리의 `.venv`를 쓴다. 시스템 `python3`는 쓰지 않는다.

```sh
cd tools/recsys
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

`torch`는 CPU 휠이라 GPU가 없어도 된다. 설치에 몇 분 걸린다.

### 2. 카탈로그 덤프 (한 번만)

`data/catalog/*.csv`가 이미 저장소에 있으면 이 단계는 건너뛴다. 상품·태그·성분이 바뀌어
다시 뽑아야 할 때만 돌린다. compose로 개발 DB(포트 13306)를 띄운 상태여야 한다.

```sh
.venv/bin/python export_catalog.py --host 127.0.0.1 --port 13306 \
    --user beautyboy --password <비밀번호> --database beautyboy --out data/catalog
```

비밀번호는 인자로만 받는다. 파일이나 커밋에 넣지 않는다.

### 3. 전체 파이프라인

```sh
make all                 # 기본 버전 v3.1
make all VERSION=v3.2    # 버전만 바꿔 다시
```

타깃은 다섯이다. `seed`, `table`, `train`, `gate`, 그리고 넷을 순서대로 도는 `all`이다.
단계 하나만 다시 돌리고 싶으면 그 타깃을 직접 부른다(예: `make gate VERSION=v3.2`).
게이트가 FAIL이면 `make`는 종료 코드 1로 끝난다. 그래도 리포트 파일은 남는다.

### 4. 테스트

```sh
.venv/bin/python -m pytest -q
```

## 산출 파일이 무엇인지

| 파일 | 뜻 |
|---|---|
| `data/catalog/goods.csv` | 상품 기본 정보(카테고리, 브랜드, 가격, 상태, 조회수). 파이프라인의 유일한 실데이터 입력 |
| `data/catalog/goods_tag.csv` | 상품에 붙은 태그 슬러그와 정렬 순서 |
| `data/catalog/goods_ingredient.csv` | 상품에 든 성분 id |
| `data/raw/members.csv` | 생성한 가상 회원의 피부타입·고민·선호 사용감 |
| `data/raw/orders.csv`, `order_items.csv` | 가상 주문 이력. 학습 라벨의 원천이다 |
| `data/raw/reviews.csv` | 가상 리뷰 별점. 상품 탑의 "피부타입별 평균 별점" 입력이 된다 |
| `data/raw/behavior_events.csv` | 가상 행동 로그(조회·장바구니 등). 사용자 탑의 행동 18열 입력 |
| `data/seed_v3.sql` | 위 다섯을 MySQL INSERT로 옮긴 것. 실 DB에 부어 화면으로 확인할 때만 쓴다 |
| `data/planted.json` | 시드에 일부러 심은 패턴 P1~P4의 정답지. 게이트가 이걸로 채점한다 |
| `data/planted_members.json` | 패턴별로 심은 회원 id 목록. P3·P4 채점용 |
| `data/train.csv` | 학습표 한 장. 열 계약은 `schema/train.csv.md`가 진실 |
| `out/<버전>/item_embeddings.json` | 상품 임베딩. 브라우저가 통째로 받는다 |
| `out/<버전>/user_tower.json` | 사용자 탑 가중치. 브라우저가 34칸을 32칸으로 옮긴다 |
| `out/<버전>/meta.json` | 가격대 경계, 손실 기록 등 운영 메타데이터 |
| `reports/gate-<버전>.json` | 게이트 판정. `verdict`가 `PASS`/`FAIL`이고 항목별 측정치가 들어 있다 |

## 게이트가 FAIL일 때 무엇부터 의심할지

게이트는 넷을 잰다. holdout Recall@4의 v3 대 v2 비교, 그리고 심은 패턴 P1~P4의 포함률이다.
**임계값을 낮춰 통과시키지 않는다.** 임계값 자체가 틀렸다고 판단되면 고치지 말고 보고한다.
정직한 FAIL이 아무거나 PASS시키는 것보다 낫다.

| 신호 | 먼저 볼 것 |
|---|---|
| P1 FAIL (같은 태그 두 상품을 못 가른다) | 상품 탑 입력에 "피부타입별 평균 별점"이 실제로 들어갔는지 확인한다. 그다음 `EMB_DIM`을 48로 올린다 |
| P3 FAIL | 학습표의 행동 18열이 전부 0인지부터 본다. 0이면 빌더 버그다 |
| Recall@4 개선이 5% 미만 | 시드의 기본 패턴 확률이 70%가 아니라 100%로 들어갔는지 본다 |
| P4 FAIL (잡음 회원을 빼도 결과가 흔들린다) | 재학습 경로(`gate.py --raw`)가 같은 시드로 도는지, 수축 계수가 너무 약하지 않은지 본다 |

### 지금 상태

현재 `reports/gate-v3.1.json`의 `verdict`는 **FAIL**이다. Recall@4는 v3 0.4524 대 v2 0.4219로
개선 +7.2%라 기준을 넘지만 P1·P2·P4가 걸린다. 원인 후보는 아래 "카탈로그가 픽스처 복사본"이다.

`data/catalog/*.csv` 세 개는 **실 MySQL 덤프가 아니라 `tests/fixtures/catalog_mini/`의 작은
픽스처를 복사한 것**이다(상품 88개 규모). 실 카탈로그(수백 상품)로 다시 뽑으면 후보 풀 구성과
태그 분포가 달라져 판정이 바뀔 수 있다. 게이트 수치를 근거로 삼기 전에 `export_catalog.py`로
실 DB에서 다시 덤프하고 `make all`을 한 번 더 돌린다.

## 커밋하는 파일과 안 하는 파일

**커밋한다** (입력이거나 계약이라 다시 못 만든다, 또는 만들려면 DB가 필요하다):

- 스크립트 전부(`*.py`), `Makefile`, `README.md`, `requirements.txt`
- `data/catalog/*.csv` (DB가 있어야 만들 수 있다)
- `data/planted.json`, `data/planted_members.json` (게이트 채점 계약)
- `schema/`, `tests/`

**커밋하지 않는다** (`make`로 언제든 다시 만들고, 크고, 매 실행마다 바뀐다).
루트 `.gitignore`에 네 줄로 적혀 있다.

```
tools/recsys/data/raw/
tools/recsys/data/seed_v3.sql
tools/recsys/data/train.csv
tools/recsys/out/
```

`.venv/`, `__pycache__/`, `.pytest_cache/`도 같은 이유로 무시한다.
`reports/`는 무시 목록에 없다. 게이트 리포트는 판정 근거라 남길지 말지를 사람이 정한다.
