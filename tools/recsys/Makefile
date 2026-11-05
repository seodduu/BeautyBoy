# v3 개인화 학습 파이프라인.
# 사용법은 README.md 참고. 모든 타깃은 이 디렉터리(tools/recsys) 기준 상대경로로 돈다.
#
#   make all                 # 기본 버전(v3.1)으로 시드 → 학습표 → 학습 → 게이트
#   make all VERSION=v3.2    # 버전만 바꿔 다시
#
# 파이썬은 반드시 worktree 안의 가상환경을 쓴다. 시스템 python3는 쓰지 않는다.

VERSION ?= v3.1
PYTHON  := .venv/bin/python

CATALOG := data/catalog
RAW     := data/raw
TABLE   := data/train.csv
PLANTED := data/planted.json
OUT     := out
REPORTS := reports

.PHONY: all seed table train gate
# 네 단계는 앞 단계의 산출물을 읽으므로 순서가 곧 계약이다. make -j로도 섞이지 않게 못 박는다.
.NOTPARALLEL:

## seed: 카탈로그 CSV를 읽어 시드(raw CSV 5종, seed_v3.sql, planted.json)를 만든다
seed:
	$(PYTHON) seed_generator.py --catalog $(CATALOG) --out data

## table: 시드와 카탈로그를 합쳐 학습표 한 장을 만든다
table:
	$(PYTHON) build_table.py --raw $(RAW) --catalog $(CATALOG) --out $(TABLE)

## train: 투 타워를 학습시켜 out/$(VERSION)에 모델 파일 3개를 쓴다
train:
	$(PYTHON) train.py --train $(TABLE) --catalog $(CATALOG) --raw $(RAW) \
		--out $(OUT) --version $(VERSION)

## gate: 게이트를 돌려 reports/gate-$(VERSION).json을 쓴다. FAIL이면 종료 코드 1
gate:
	$(PYTHON) gate.py --model $(OUT)/$(VERSION) --table $(TABLE) --planted $(PLANTED) \
		--out $(REPORTS) --catalog $(CATALOG) --raw $(RAW)

## all: 네 단계를 순서대로. 게이트가 FAIL이면 여기서 종료 코드 1로 멈춘다
all: seed table train gate
