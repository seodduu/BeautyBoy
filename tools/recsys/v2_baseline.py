"""v2 규칙 점수를 파이썬으로 옮긴 것(구현 계획 §4 Task B1-4).

원본은 `frontend/src/features/affinity/composer.ts`의 `composeStep`이고, 가중치는 그 파일의
`WEIGHTS` 상수다. 값이 갈라지면 `tests/test_v2_baseline.py`가 잡는다.

    concern    2.0 · min(고민 태그 히트 수, 2)
  + behavior   1.5 · (후보 중 최대값으로 정규화한 행동 친화도)
  + flow       1.0 · (전이 규칙 태그를 가지면 1)
  + texture    0.5 · min(선호 사용감 히트 수, 1)
  + popularity 0.3 · ((n - i) / n)        i = 인기순 인덱스, n = 후보 수

**게이트에서는 전이 규칙 항(flow)을 0으로 둔다.** 이유는 두 가지다. 첫째, 전이 규칙은 이전
단계의 픽에 의존하는데 학습표에는 "이 사람이 이 상품을 샀다"만 있고 단계 순서가 없다. 둘째,
v2와 v3를 같은 조건에서 재려면 양쪽 다 못 쓰는 정보를 한쪽만 쓰게 하면 안 된다. 그래서
`score_step`은 `flow_tag`를 받지 않고, 아래 `FLOW_TERM_IN_GATE`가 0으로 고정돼 있다.

후보 풀 크기도 원본 그대로 12개다(`POOL_SIZE`). v3는 30개를 쓴다. 풀 크기는 v3가 바꾸려는
대상 자체라(설계 §3 "풀 12개") 같게 맞추면 개선의 절반을 지우는 셈이다.
"""

from __future__ import annotations

from dataclasses import dataclass, field

# ---------------------------------------------------------------------------
# 계약 (composer.ts와 같아야 한다)
# ---------------------------------------------------------------------------

WEIGHTS: dict[str, float] = {
    "concern": 2.0,
    "behavior": 1.5,
    "flow": 1.0,
    "texture": 0.5,
    "popularity": 0.3,
}

POOL_SIZE = 12          # composer.ts POOL_SIZE. v3의 30과 다른 채로 둔다
CONCERN_HIT_CAP = 2     # min(고민 히트, 2)
TEXTURE_HIT_CAP = 1     # min(사용감 히트, 1)
FLOW_TERM_IN_GATE = 0.0  # 위 모듈 주석의 이유로 게이트에서는 전이 규칙 항이 없다

# profile.ts SKIN_TYPE_CONCERNS. 고민이 비었을 때만 쓴다.
SKIN_TYPE_CONCERNS: dict[str, list[str]] = {
    "DRY": ["moisture", "barrier"],
    "OILY": ["sebum", "pore"],
    "COMBINATION": ["moisture", "sebum"],
    "SENSITIVE": ["soothe", "gentle"],
}

CONCERN_SLUGS = [
    "exfoliate", "sebum", "pore", "trouble", "soothe",
    "moisture", "barrier", "bright", "anti-aging",
]
TEXTURE_SLUGS = ["fresh", "dewy", "matte"]


@dataclass
class Scored:
    """후보 하나의 점수와 정렬에 필요한 자리표."""

    goods_id: int
    index: int                       # 인기순 인덱스. 0이 1위다
    score: float
    concern_hits: list[str] = field(default_factory=list)
    behavior_hits: list[str] = field(default_factory=list)


def effective_concerns(concerns: list[str], skin_type: str | None) -> list[str]:
    """profile.ts effectiveConcerns. 직접 고른 것이 추론한 것을 이긴다."""
    picked = [slug for slug in concerns if slug in CONCERN_SLUGS]
    if picked:
        return picked
    return list(SKIN_TYPE_CONCERNS.get(skin_type or "", []))


def preferred_textures(concerns: list[str]) -> list[str]:
    """profile.ts preferredTextures. 고민과 사용감은 한 칸에 섞여 온다."""
    return [slug for slug in concerns if slug in TEXTURE_SLUGS]


def score_step(
    *,
    candidates: list[int],
    tags_of: dict[int, list[str]],
    concerns: list[str],
    textures: list[str],
    affinity: dict[str, float],
) -> list[Scored]:
    """후보(인기순)마다 v2 점수를 매긴다. 정렬은 하지 않는다.

    `affinity`는 태그 → 행동 가중치다. 브라우저는 `${cat3}|${tag}` 키를 단계 접두사로 걸러
    태그별로 합산하는데, 학습표는 이미 카테고리 축을 접은 18칸을 들고 있으므로 게이트는
    그 18칸을 그대로 태그별 친화도로 쓴다.
    """
    raw = [
        sum(affinity.get(tag, 0.0) for tag in tags_of.get(goods_id, []))
        for goods_id in candidates
    ]
    maximum = max(raw) if raw else 0.0
    n = len(candidates)

    scored: list[Scored] = []
    for i, goods_id in enumerate(candidates):
        slugs = tags_of.get(goods_id, [])
        concern_hits = [slug for slug in slugs if slug in concerns]
        behavior_hits = [slug for slug in slugs if affinity.get(slug, 0.0) > 0]
        texture_hits = [slug for slug in slugs if slug in textures]
        score = (
            WEIGHTS["concern"] * min(len(concern_hits), CONCERN_HIT_CAP)
            + WEIGHTS["behavior"] * ((raw[i] / maximum) if maximum > 0 else 0.0)
            + WEIGHTS["flow"] * FLOW_TERM_IN_GATE
            + WEIGHTS["texture"] * min(len(texture_hits), TEXTURE_HIT_CAP)
            + WEIGHTS["popularity"] * ((n - i) / n if n else 0.0)
        )
        scored.append(
            Scored(
                goods_id=goods_id,
                index=i,
                score=score,
                concern_hits=concern_hits,
                behavior_hits=behavior_hits,
            )
        )
    return scored


def rank_entries(entries: list[Scored]) -> list[Scored]:
    """점수 내림차순, 동점은 인기순 인덱스, 그다음 goods_id. 완전 결정적이다."""
    return sorted(entries, key=lambda e: (-e.score, e.index, e.goods_id))


def rank_step(
    *,
    candidates: list[int],
    tags_of: dict[int, list[str]],
    concerns: list[str],
    textures: list[str],
    affinity: dict[str, float],
) -> list[int]:
    """v2 점수 순으로 정렬한 상품 id 목록."""
    entries = rank_entries(
        score_step(
            candidates=candidates,
            tags_of=tags_of,
            concerns=concerns,
            textures=textures,
            affinity=affinity,
        )
    )
    return [entry.goods_id for entry in entries]
