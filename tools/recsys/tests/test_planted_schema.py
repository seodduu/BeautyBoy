"""심은 패턴 스키마와 행동 점수 픽스처 형태 검증.

planted.schema.json이 무언가를 실제로 막는지 보고, behavior-cases.json이 프론트에서
넘어온 형태 그대로인지 본다. Python 쪽 behaviorInput 동등 구현은 여기 범위가 아니다
(Wave B-T1 B1-2). 지금은 형태만 본다.
"""

import copy
import json
import re
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator, ValidationError

REPO_ROOT = Path(__file__).resolve().parents[3]
RECSYS_ROOT = REPO_ROOT / "tools/recsys"
PLANTED_SCHEMA_PATH = RECSYS_ROOT / "schema/planted.schema.json"
PLANTED_EXAMPLE_PATH = RECSYS_ROOT / "data/planted.example.json"
BEHAVIOR_CASES_PATH = RECSYS_ROOT / "tests/fixtures/behavior-cases.json"

AGGREGATE_KEY = re.compile(r"^C\d{6}\|[a-z-]+$")
BEHAVIOR_INPUT_LEN = 18


def _load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


@pytest.fixture(scope="module")
def planted_schema() -> dict:
    return _load(PLANTED_SCHEMA_PATH)


@pytest.fixture(scope="module")
def planted_example() -> list:
    return _load(PLANTED_EXAMPLE_PATH)


def test_example_passes_schema(planted_schema, planted_example):
    Draft202012Validator.check_schema(planted_schema)
    Draft202012Validator(planted_schema).validate(planted_example)


def test_unknown_kind_fails(planted_schema, planted_example):
    broken = copy.deepcopy(planted_example)
    broken[3]["kind"] = "vibes"

    with pytest.raises(ValidationError):
        Draft202012Validator(planted_schema).validate(broken)


def test_p1_without_rival_fails(planted_schema, planted_example):
    broken = copy.deepcopy(planted_example)
    assert broken[0]["id"] == "P1"
    del broken[0]["rivalGoodsId"]

    with pytest.raises(ValidationError):
        Draft202012Validator(planted_schema).validate(broken)


def test_behavior_cases_fixture_shape():
    bundle = _load(BEHAVIOR_CASES_PATH)
    cases = bundle["cases"]
    assert len(cases) > 0

    for case in cases:
        vector = case["behaviorInput"]
        assert len(vector) == BEHAVIOR_INPUT_LEN, case["name"]
        assert all(0 <= value <= 1 for value in vector), case["name"]
        for key in case["aggregate"]:
            assert AGGREGATE_KEY.match(key), f'{case["name"]}: {key}'

    # 가드: 생성기 쪽과 같은 조건을 여기서도 본다. 픽스처만 보고 판정하는 쪽은 이 파일이다.
    assert any(max(case["behaviorInput"]) == 1 for case in cases)
    assert any(all(value == 0 for value in case["behaviorInput"]) for case in cases)
