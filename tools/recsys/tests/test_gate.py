"""게이트 검증.

**진짜 학습을 하지 않는다.** 손으로 만든 작은 모델(2차원 임베딩)과 작은 학습표·카탈로그로만
돌린다. 학습을 부르는 자리(P4 재학습)는 스텁 함수를 주입해 대신한다. 게이트가 판정 규칙을
제대로 적용하는지가 여기서 볼 것이고, 모델이 실제로 잘 배웠는지는 게이트를 실행하는 B1-5가 본다.
"""

import json
import sys
from pathlib import Path

import numpy as np
import pandas as pd

RECSYS_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RECSYS_ROOT))

import gate  # noqa: E402
from train import BEHAVIOR_COLUMNS, INPUT_ORDER, USER_INPUT_DIM  # noqa: E402

STEP = "C001001"

# 상품 여섯 개. 조회수는 id 순으로 떨어진다(1번이 1위).
VIEW_COUNT = {1: 600, 2: 500, 3: 400, 4: 300, 5: 200, 6: 100}
TAGS = {
    1: ["moisture"], 2: ["moisture"], 3: ["bright"],
    4: ["sebum"], 5: ["uv"], 6: [],
}

# 임베딩 두 방향뿐이다. [1, 0]은 DRY 쪽, [0, 1]은 OILY 쪽.
DRY_SIDE = [1.0, 0.0]
OILY_SIDE = [0.0, 1.0]


def make_catalog(view_count=None) -> gate.Catalog:
    counts = dict(view_count or VIEW_COUNT)
    return gate.Catalog(
        cat3={gid: STEP for gid in counts},
        view_count=counts,
        tags={gid: list(TAGS.get(gid, [])) for gid in counts},
    )


def make_model(items: dict[int, list[float]], version: str = "v3.test") -> gate.Model:
    """피부타입 두 칸만 살아 있는 34 → 2 사용자 탑. 학습 없이 손으로 채운다."""
    weight = np.zeros((USER_INPUT_DIM, 2), dtype=float)
    skin_types = INPUT_ORDER["skinType"]
    weight[skin_types.index("DRY")] = [1.0, 0.0]
    weight[skin_types.index("OILY")] = [0.0, 1.0]
    return gate.Model(
        version=version,
        dim=2,
        items={gid: np.asarray(vec, dtype=float) for gid, vec in items.items()},
        layers=[{"w": weight.tolist(), "b": [0.0, 0.0], "act": "none"}],
        normalize=True,
    )


def make_table(rows: list[dict]) -> pd.DataFrame:
    """작은 학습표. 행동 18칸은 인자로 준 것만 채우고 나머지는 0이다."""
    records = []
    for row in rows:
        record = {
            "member_id": int(row["member_id"]),
            "skin_type": row.get("skin_type", "DRY"),
            "concerns": row.get("concerns", ""),
            "textures": row.get("textures", ""),
        }
        record.update({column: 0.0 for column in BEHAVIOR_COLUMNS})
        for slug, value in row.get("behavior", {}).items():
            record[f"b_{slug}"] = float(value)
        record.update({
            "goods_id": int(row["goods_id"]),
            "label": float(row.get("label", 1)),
            "weight": float(row.get("weight", 1.0)),
            "split": row.get("split", "train"),
        })
        records.append(record)
    return pd.DataFrame(records)


PLANTED = [
    {"id": "P1", "kind": "rating_betrayal", "goodsId": 2, "rivalGoodsId": 1,
     "skinType": "DRY", "lowRatingShare": 0.8, "pass": "P1 기준 문장"},
    {"id": "P2", "kind": "off_rulebook_item", "goodsId": 3, "concern": "bright",
     "step": STEP, "buyerShare": 0.4, "pass": "P2 기준 문장"},
    {"id": "P3", "kind": "behavior_over_profile", "goodsId": 4, "skinType": "DRY",
     "behaviorTag": "sebum", "memberCount": 2, "pass": "P3 기준 문장"},
    {"id": "P4", "kind": "noise", "share": 0.15, "pass": "P4 기준 문장"},
]

ALL_PASS = {
    "P1": {"rivalShare": 0.78, "goodsShare": 0.11},
    "P2": {"share": 0.64},
    "P3": {"share": 0.66},
    "P4": {"maxDeltaPoints": 0.03},
}


def measured(**overrides) -> dict:
    values = {key: dict(value) for key, value in ALL_PASS.items()}
    for key, value in overrides.items():
        values[key] = value
    return values


def scored_model(bought_is_top: bool) -> gate.Model:
    """산 상품(1번)이 사용자와 같은 방향이거나 반대 방향인 모델 둘."""
    if bought_is_top:
        items = {1: DRY_SIDE, 2: OILY_SIDE, 3: OILY_SIDE,
                 4: OILY_SIDE, 5: OILY_SIDE, 6: OILY_SIDE}
    else:
        items = {1: [-1.0, 0.0], 2: DRY_SIDE, 3: DRY_SIDE,
                 4: DRY_SIDE, 5: DRY_SIDE, 6: DRY_SIDE}
    return make_model(items)


def recall_of(model: gate.Model, table: pd.DataFrame, engine: str = "v3") -> float:
    catalog = make_catalog()
    profiles = gate.member_profiles(table)
    vectors = gate.member_vectors(model, profiles)
    return gate.recall_at_k(
        table=table, model=model, catalog=catalog, profiles=profiles,
        vectors=vectors, engine=engine,
    )


# ---------------------------------------------------------------------------
# 1. Recall@4
# ---------------------------------------------------------------------------


def test_recall4는_상위4안에_있으면_적중이다():
    table = make_table([
        {"member_id": 1000, "skin_type": "DRY", "goods_id": 1, "split": "holdout"},
    ])
    assert recall_of(scored_model(bought_is_top=True), table) == 1.0
    # 산 상품이 꼴찌로 밀리면 상위 4개 밖이라 적중이 아니다.
    assert recall_of(scored_model(bought_is_top=False), table) == 0.0


def test_holdout이_아니거나_라벨이_0이면_세지_않는다():
    model = scored_model(bought_is_top=False)
    train_only = make_table([
        {"member_id": 1000, "skin_type": "DRY", "goods_id": 1, "split": "train"},
    ])
    assert recall_of(model, train_only) == 0.0          # 분모가 0이면 0으로 본다

    mixed = make_table([
        {"member_id": 1000, "skin_type": "DRY", "goods_id": 1, "split": "holdout", "label": 0},
        {"member_id": 1000, "skin_type": "DRY", "goods_id": 2, "split": "holdout", "label": 1},
    ])
    # 라벨 0(별점 1~2로 산 상품)은 분모에서 빠지고, 라벨 1인 2번만 센다.
    assert recall_of(model, mixed) == 1.0


def test_v2는_후보_12개만_본다():
    counts = {gid: 1000 - gid for gid in range(1, 41)}
    catalog = make_catalog(counts)
    pool_v2 = gate.candidate_pool(catalog, STEP, size=12, popular=12)
    pool_v3 = gate.candidate_pool(catalog, STEP)
    assert len(pool_v2) == 12
    assert len(pool_v3) == gate.V3_POOL_SIZE
    assert pool_v3[:12] == pool_v2                      # 둘 다 인기순이다


def test_후보풀은_같은_단계면_항상_같다():
    catalog = make_catalog({gid: 1000 - gid for gid in range(1, 41)})
    assert gate.candidate_pool(catalog, STEP) == gate.candidate_pool(catalog, STEP)


def test_improvement가_20퍼센트미만이면_FAIL():
    assert gate.improvement(0.23, 0.20) < gate.IMPROVEMENT_MIN
    fail = gate.build_report(
        version="v3.test", recall_v3=0.23, recall_v2=0.20,
        planted=PLANTED, measured=measured(),
    )
    assert fail["recall4"]["improvement"] == 0.15
    assert all(entry["pass"] for entry in fail["planted"])
    assert fail["verdict"] == "FAIL"

    ok = gate.build_report(
        version="v3.test", recall_v3=0.25, recall_v2=0.20,
        planted=PLANTED, measured=measured(),
    )
    assert ok["recall4"]["improvement"] == 0.25
    assert ok["verdict"] == "PASS"


# ---------------------------------------------------------------------------
# 3. 심은 패턴
# ---------------------------------------------------------------------------


def test_P1은_두_조건을_모두_봐야_통과다():
    assert gate.planted_pass("P1", {"rivalShare": 0.78, "goodsShare": 0.11})
    # rival 포함률만 높고 goods 포함률도 높으면 별점 배신을 못 배운 것이다.
    assert not gate.planted_pass("P1", {"rivalShare": 0.78, "goodsShare": 0.55})
    # goods를 잘 걸렀어도 rival을 못 올리면 통과가 아니다.
    assert not gate.planted_pass("P1", {"rivalShare": 0.40, "goodsShare": 0.05})


def test_planted가_하나라도_실패하면_verdict가_FAIL():
    report = gate.build_report(
        version="v3.test", recall_v3=0.41, recall_v2=0.29,
        planted=PLANTED, measured=measured(P3={"share": 0.52}),
    )
    assert report["recall4"]["improvement"] >= gate.IMPROVEMENT_MIN
    assert [entry["pass"] for entry in report["planted"]] == [True, True, False, True]
    assert report["verdict"] == "FAIL"


def test_P4는_5퍼센트포인트를_넘으면_실패다():
    assert gate.planted_pass("P4", {"maxDeltaPoints": 0.05})
    assert not gate.planted_pass("P4", {"maxDeltaPoints": 0.06})
    before = {"P1": {"rivalShare": 0.80, "goodsShare": 0.10}, "P2": {"share": 0.60}}
    after = {"P1": {"rivalShare": 0.74, "goodsShare": 0.10}, "P2": {"share": 0.62}}
    assert round(gate.max_delta_points(before, after), 4) == 0.06


def test_포함률은_대상상품을_풀에_넣고_잰다():
    # 6번은 인기 꼴찌라 무작위 2칸에 안 들어도 강제로 풀에 들어간다.
    model = make_model({gid: (DRY_SIDE if gid == 6 else OILY_SIDE) for gid in VIEW_COUNT})
    table = make_table([
        {"member_id": 1000, "skin_type": "DRY", "goods_id": 1},
        {"member_id": 1001, "skin_type": "OILY", "goods_id": 1},
    ])
    profiles = gate.member_profiles(table)
    vectors = gate.member_vectors(model, profiles)
    share = gate.top4_share(
        model=model, catalog=make_catalog(), vectors=vectors,
        member_ids=[1000, 1001], step=STEP, goods_id=6,
    )
    assert share == 0.5      # DRY 회원만 6번을 상위 4개에 올린다


# ---------------------------------------------------------------------------
# 리포트
# ---------------------------------------------------------------------------


def test_리포트에_네_패턴이_모두_들어간다():
    report = gate.build_report(
        version="v3.test", recall_v3=0.41, recall_v2=0.29,
        planted=PLANTED, measured=measured(),
    )
    assert [entry["id"] for entry in report["planted"]] == ["P1", "P2", "P3", "P4"]
    assert report["planted"][0]["measured"] == {"rivalShare": 0.78, "goodsShare": 0.11}
    assert report["planted"][3]["measured"] == {"maxDeltaPoints": 0.03}
    assert set(report) == {"version", "generatedAt", "recall4", "planted", "verdict"}
    assert report["generatedAt"].endswith("Z")


def test_FAIL이면_종료코드1이고_리포트는_남는다(tmp_path):
    report = gate.build_report(
        version="v3.test", recall_v3=0.30, recall_v2=0.29,
        planted=PLANTED, measured=measured(),
    )
    assert report["verdict"] == "FAIL"
    assert gate.emit(report, tmp_path / "reports") == 1

    path = tmp_path / "reports" / "gate-v3.test.json"
    assert path.exists()
    assert json.loads(path.read_text(encoding="utf-8"))["verdict"] == "FAIL"


def test_PASS면_종료코드0이다(tmp_path):
    report = gate.build_report(
        version="v3.test", recall_v3=0.41, recall_v2=0.29,
        planted=PLANTED, measured=measured(),
    )
    assert report["verdict"] == "PASS"
    assert gate.emit(report, tmp_path) == 0


# ---------------------------------------------------------------------------
# 전 구간 (학습은 스텁이다)
# ---------------------------------------------------------------------------


def test_run_gate는_학습없이_네_지표를_모두_채운다():
    # 2번(P1의 goodsId, 별점 배신 상품)만 반대 방향이라 상위 4개 밖으로 밀린다.
    model = make_model({
        1: DRY_SIDE, 2: [-1.0, 0.0], 3: DRY_SIDE, 4: DRY_SIDE, 5: OILY_SIDE, 6: OILY_SIDE,
    })
    table = make_table([
        {"member_id": 1000, "skin_type": "DRY", "concerns": "bright",
         "goods_id": 1, "split": "holdout"},
        {"member_id": 1001, "skin_type": "DRY", "concerns": "bright",
         "goods_id": 4, "split": "holdout"},
        {"member_id": 1002, "skin_type": "OILY", "goods_id": 5},
    ])
    calls: list[list[int]] = []

    def retrain(noise_members):
        calls.append(list(noise_members))
        return model            # 잡음을 빼도 같은 모델이면 차이는 0이다

    report = gate.run_gate(
        model=model, table=table, catalog=make_catalog(), planted=PLANTED,
        planted_members={"P3": [1000, 1001], "P4": [1002]}, retrain=retrain,
    )
    assert calls == [[1002]]
    assert report["planted"][3]["measured"] == {"maxDeltaPoints": 0.0}
    assert report["recall4"]["v3"] == 1.0          # 둘 다 상위 4개 안이다
    ids = [entry["id"] for entry in report["planted"]]
    assert ids == ["P1", "P2", "P3", "P4"]
    # P1: rival(1번)은 DRY 쪽, goods(2번)는 OILY 쪽이라 rival만 올라온다.
    assert report["planted"][0]["measured"]["rivalShare"] == 1.0
    assert report["planted"][0]["measured"]["goodsShare"] == 0.0


def test_retrain을_주지_않으면_P4는_실패로_남는다():
    model = make_model({gid: DRY_SIDE for gid in VIEW_COUNT})
    table = make_table([{"member_id": 1000, "skin_type": "DRY", "goods_id": 1}])
    report = gate.run_gate(
        model=model, table=table, catalog=make_catalog(), planted=PLANTED,
        planted_members={"P3": [1000], "P4": []}, retrain=None,
    )
    p4 = report["planted"][3]
    assert p4["pass"] is False
    assert p4["measured"] == {"maxDeltaPoints": 1.0}
    assert report["verdict"] == "FAIL"


def test_사용자임베딩은_경고없이_유한하다(recwarn):
    model = make_model({gid: DRY_SIDE for gid in VIEW_COUNT})
    table = make_table([
        {"member_id": 1000, "skin_type": "DRY", "goods_id": 1},
        {"member_id": 1001, "skin_type": "OILY", "goods_id": 2},
    ])
    vectors = gate.member_vectors(model, gate.member_profiles(table))
    assert all(np.isfinite(vec).all() for vec in vectors.values())
    assert [w for w in recwarn.list if issubclass(w.category, RuntimeWarning)] == []
