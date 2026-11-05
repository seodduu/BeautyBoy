"""behavior_input.py가 브라우저 구현과 같은 값을 내는지 본다.

정답지는 `tests/fixtures/behavior-cases.json`이다. Wave A의 벤치가 실제 TS 함수를 돌려
찍어낸 출력이라, 값이 틀리면 파이썬 쪽을 고친다. 픽스처를 고쳐 맞추면 동등성 검사가
스스로를 검사하는 꼴이 된다.
"""

import json
import re
import sys
from pathlib import Path

import pytest

RECSYS_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = RECSYS_ROOT.parents[1]
sys.path.insert(0, str(RECSYS_ROOT))

from behavior_input import MODEL_TAG_SLUGS, aggregate, behavior_input  # noqa: E402

FIXTURE_PATH = RECSYS_ROOT / "tests/fixtures/behavior-cases.json"
MODEL_TS = REPO_ROOT / "frontend/src/types/model.ts"
TOLERANCE = 1e-12


@pytest.fixture(scope="module")
def cases() -> list[dict]:
    return json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))["cases"]


def test_픽스처_케이스가_전부_일치한다(cases):
    for case in cases:
        got_aggregate = aggregate(case["events"])
        assert set(got_aggregate) == set(case["aggregate"]), case["name"]
        for key, expected in case["aggregate"].items():
            assert got_aggregate[key] == pytest.approx(expected, abs=TOLERANCE), \
                f"{case['name']} / {key}"

        got_vector = behavior_input(case["events"])
        expected_vector = case["behaviorInput"]
        assert len(got_vector) == len(expected_vector), case["name"]
        for index, expected in enumerate(expected_vector):
            assert got_vector[index] == pytest.approx(expected, abs=TOLERANCE), \
                f"{case['name']} / {MODEL_TAG_SLUGS[index]}"


def test_케이스가_열개다(cases):
    """픽스처가 비면 위 테스트가 공허하게 통과한다."""
    assert len(cases) == 10


def test_최대값이_1인_케이스와_전부_0인_케이스가_둘_다_있다(cases):
    """Wave A 생성기의 가드를 소비 측에서 한 번 더 건다."""
    maxima = [max(case["behaviorInput"]) for case in cases]
    assert any(value == pytest.approx(1.0, abs=TOLERANCE) for value in maxima)
    assert any(all(v == 0 for v in case["behaviorInput"]) for case in cases)


def test_태그순서가_model_ts와_같다():
    """정규식이 조용히 빈 목록을 만들어 통과하는 것을 18개 단언으로 막는다."""
    source = MODEL_TS.read_text(encoding="utf-8")
    block = re.search(r"MODEL_TAG_SLUGS\s*=\s*\[(.*?)\]", source, re.S)
    assert block is not None, "model.ts에서 MODEL_TAG_SLUGS 배열을 찾지 못했다"
    slugs = re.findall(r"'([^']+)'", block.group(1))
    assert len(slugs) == 18, f"정규식이 뽑은 태그가 18개가 아니다: {slugs}"
    assert slugs == MODEL_TAG_SLUGS
