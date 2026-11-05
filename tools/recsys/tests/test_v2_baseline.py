"""v2 기준선 검증.

원본 `composer.ts`와 값이 갈라지면 게이트가 재는 "v2 대비 개선"이 통째로 거짓말이 된다.
그래서 가중치는 TS 파일을 실제로 읽어 대조한다.
"""

import re
import sys
from pathlib import Path

import pytest

RECSYS_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = RECSYS_ROOT.parents[1]
sys.path.insert(0, str(RECSYS_ROOT))

import v2_baseline as v2  # noqa: E402

COMPOSER_TS = REPO_ROOT / "frontend" / "src" / "features" / "affinity" / "composer.ts"

# 후보 세 개: 태그만 다르고 인기순은 넣은 순서다.
TAGS = {
    10: ["moisture", "barrier", "soothe"],   # 고민 히트 3개
    20: ["moisture", "barrier"],             # 고민 히트 2개
    30: ["uv"],                              # 히트 없음
}
CONCERNS = ["moisture", "barrier", "soothe"]


def score_of(entries, goods_id):
    return next(entry.score for entry in entries if entry.goods_id == goods_id)


def test_가중치가_composer_ts와_같다():
    source = COMPOSER_TS.read_text(encoding="utf-8")
    block = re.search(r"export const WEIGHTS = \{(.*?)\}", source, re.S)
    assert block, "composer.ts에서 WEIGHTS 블록을 못 찾았다"
    found = {
        name: float(value)
        for name, value in re.findall(r"(\w+):\s*([0-9.]+)", block.group(1))
    }
    assert len(found) == 5, f"가중치가 다섯 개가 아니다: {found}"
    assert found == v2.WEIGHTS


def test_고민히트는_두개에서_멈춘다():
    entries = v2.score_step(
        candidates=[10, 20], tags_of=TAGS, concerns=CONCERNS, textures=[], affinity={}
    )
    # 인기 항만큼은 차이가 나므로 고민 항 자체를 떼어 비교한다.
    n = 2
    popularity = [v2.WEIGHTS["popularity"] * ((n - i) / n) for i in range(n)]
    assert score_of(entries, 10) - popularity[0] == pytest.approx(
        score_of(entries, 20) - popularity[1]
    )
    assert score_of(entries, 10) - popularity[0] == pytest.approx(v2.WEIGHTS["concern"] * 2)


def test_행동친화도는_후보중_최대값정규화다():
    affinity = {"moisture": 4.0, "barrier": 2.0, "uv": 1.5}
    entries = v2.score_step(
        candidates=[10, 20, 30], tags_of=TAGS, concerns=[], textures=[], affinity=affinity
    )
    n = 3
    without_popularity = [
        score_of(entries, gid) - v2.WEIGHTS["popularity"] * ((n - i) / n)
        for i, gid in enumerate([10, 20, 30])
    ]
    raw = [4.0 + 2.0 + 0.0, 4.0 + 2.0, 1.5]      # soothe는 친화도가 없다
    maximum = max(raw)
    for actual, value in zip(without_popularity, raw):
        assert actual == pytest.approx(v2.WEIGHTS["behavior"] * (value / maximum))
    # 최대값을 가진 후보는 행동 항이 만점이다.
    assert without_popularity[0] == pytest.approx(v2.WEIGHTS["behavior"])


def test_행동친화도가_전부_0이면_행동항도_0이다():
    entries = v2.score_step(
        candidates=[10, 30], tags_of=TAGS, concerns=[], textures=[], affinity={}
    )
    n = 2
    for i, gid in enumerate([10, 30]):
        assert score_of(entries, gid) == pytest.approx(v2.WEIGHTS["popularity"] * ((n - i) / n))


def test_인기항은_1위가_최대_꼴찌가_최소다():
    candidates = [10, 20, 30]
    entries = v2.score_step(
        candidates=candidates, tags_of={gid: [] for gid in candidates},
        concerns=[], textures=[], affinity={},
    )
    scores = [score_of(entries, gid) for gid in candidates]
    assert scores[0] == pytest.approx(v2.WEIGHTS["popularity"])      # (n - 0) / n = 1
    assert scores[-1] == pytest.approx(v2.WEIGHTS["popularity"] * (1 / 3))  # 꼴찌
    assert scores == sorted(scores, reverse=True)


def test_동점은_인기순_그다음_goods_id로_갈린다():
    # 같은 점수 + 같은 인기순 인덱스면 goods_id가 작은 쪽이 앞이다.
    same_index = [
        v2.Scored(goods_id=77, index=0, score=1.0),
        v2.Scored(goods_id=55, index=0, score=1.0),
    ]
    assert [e.goods_id for e in v2.rank_entries(same_index)] == [55, 77]

    # 점수가 같고 인덱스가 다르면 인기순이 이긴다(goods_id가 커도).
    same_score = [
        v2.Scored(goods_id=99, index=0, score=1.0),
        v2.Scored(goods_id=11, index=1, score=1.0),
    ]
    assert [e.goods_id for e in v2.rank_entries(same_score)] == [99, 11]


def test_사용감히트는_한개에서_멈춘다():
    tags = {1: ["fresh", "dewy"], 2: ["fresh"]}
    entries = v2.score_step(
        candidates=[1, 2], tags_of=tags, concerns=[], textures=["fresh", "dewy"], affinity={}
    )
    n = 2
    popularity = [v2.WEIGHTS["popularity"] * ((n - i) / n) for i in range(n)]
    assert score_of(entries, 1) - popularity[0] == pytest.approx(v2.WEIGHTS["texture"])
    assert score_of(entries, 2) - popularity[1] == pytest.approx(v2.WEIGHTS["texture"])


def test_전이규칙_항은_게이트에서_0이다():
    assert v2.FLOW_TERM_IN_GATE == 0.0
    entries = v2.score_step(
        candidates=[30], tags_of=TAGS, concerns=[], textures=[], affinity={}
    )
    assert score_of(entries, 30) == pytest.approx(v2.WEIGHTS["popularity"])


def test_고민이_비면_피부타입에서_파생한다():
    assert v2.effective_concerns([], "DRY") == ["moisture", "barrier"]
    assert v2.effective_concerns(["bright"], "DRY") == ["bright"]
    assert v2.preferred_textures(["bright", "fresh"]) == ["fresh"]
