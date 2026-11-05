"""학습표 빌더 검증.

`data/raw`의 진짜 파일을 읽지 않는다. 표를 손으로 만들어 넣는다. 생성기가 안 돌아도
라벨 규칙과 열 계약은 혼자 검증할 수 있어야 한다.
"""

import re
import sys
from datetime import datetime, timedelta
from pathlib import Path

import pandas as pd
import pytest

RECSYS_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RECSYS_ROOT))

from behavior_input import MODEL_TAG_SLUGS, behavior_input  # noqa: E402
import build_table as bt  # noqa: E402

TRAIN_CSV_MD = RECSYS_ROOT / "schema/train.csv.md"
NOW = datetime(2026, 9, 6, 0, 0, 0)
STAMP = "%Y-%m-%d %H:%M:%S"

# 상품 1~10. 태그는 한 상품에 하나씩만 붙여 행동 벡터를 눈으로 읽을 수 있게 한다.
GOODS_TAGS = {
    1: "moisture", 2: "soothe", 3: "sebum", 4: "barrier", 5: "pore",
    6: "trouble", 7: "bright", 8: "firm", 9: "uv", 10: "scalp",
}
CATEGORY = {gid: "C001001001" for gid in GOODS_TAGS}


# ---------------------------------------------------------------------------
# 픽스처 조립
# ---------------------------------------------------------------------------


def frame(columns: list[str], rows) -> pd.DataFrame:
    return pd.DataFrame(list(rows), columns=columns)


def goods_frame() -> pd.DataFrame:
    return frame(
        ["goods_id", "category_code", "brand_id", "sale_price", "status", "view_count"],
        [(gid, CATEGORY[gid], 1, 10000, "ON_SALE", 100) for gid in sorted(GOODS_TAGS)],
    )


def goods_tag_frame() -> pd.DataFrame:
    return frame(
        ["goods_id", "tag_slug", "sort_order"],
        [(gid, slug, 1) for gid, slug in sorted(GOODS_TAGS.items())],
    )


def members_frame(rows=((1, "DRY", "moisture", "dewy"),)) -> pd.DataFrame:
    return frame(["member_id", "skin_type", "concerns", "textures"], rows)


def orders_frame(rows=()) -> pd.DataFrame:
    return frame(
        ["order_id", "member_id", "ordered_at"],
        [(oid, mid, at.strftime(STAMP)) for oid, mid, at in rows],
    )


def order_items_frame(rows=()) -> pd.DataFrame:
    return frame(["order_id", "goods_id", "quantity"], [(o, g, 1) for o, g in rows])


def reviews_frame(rows=()) -> pd.DataFrame:
    return frame(
        ["member_id", "goods_id", "rating", "created_at"],
        [(mid, gid, rating, NOW.strftime(STAMP)) for mid, gid, rating in rows],
    )


def behavior_frame(rows=()) -> pd.DataFrame:
    return frame(
        ["member_id", "goods_id", "kind", "created_at"],
        [(mid, gid, kind, at.strftime(STAMP)) for mid, gid, kind, at in rows],
    )


def run(**overrides) -> pd.DataFrame:
    inputs = {
        "members": members_frame(),
        "orders": orders_frame(),
        "order_items": order_items_frame(),
        "reviews": reviews_frame(),
        "behavior_events": behavior_frame(),
        "goods": goods_frame(),
        "goods_tag": goods_tag_frame(),
        "now": NOW,
    }
    inputs.update(overrides)
    return bt.build_table(**inputs)


def row_for(table: pd.DataFrame, member_id: int, goods_id: int) -> pd.Series:
    match = table[(table["member_id"] == member_id) & (table["goods_id"] == goods_id)]
    assert len(match) == 1, f"({member_id}, {goods_id}) 행이 {len(match)}개다"
    return match.iloc[0]


def old(days: int) -> datetime:
    return NOW - timedelta(days=days)


# ---------------------------------------------------------------------------
# 라벨 규칙 (설계 §7)
# ---------------------------------------------------------------------------


def test_별점4는_예_가중1():
    table = run(
        orders=orders_frame([(1, 1, old(30))]),
        order_items=order_items_frame([(1, 1)]),
        reviews=reviews_frame([(1, 1, 4)]),
    )
    row = row_for(table, 1, 1)
    assert row["label"] == 1
    assert row["weight"] == pytest.approx(1.0)


def test_재구매는_리뷰없어도_예():
    table = run(
        orders=orders_frame([(1, 1, old(30)), (2, 1, old(20))]),
        order_items=order_items_frame([(1, 1), (2, 1)]),
    )
    row = row_for(table, 1, 1)
    assert row["label"] == 1
    assert row["weight"] == pytest.approx(1.0)


def test_별점2는_아니오_가중2():
    table = run(
        orders=orders_frame([(1, 1, old(30))]),
        order_items=order_items_frame([(1, 1)]),
        reviews=reviews_frame([(1, 1, 2)]),
    )
    row = row_for(table, 1, 1)
    assert row["label"] == 0
    assert row["weight"] == pytest.approx(2.0)


def test_별점3은_행이_없다():
    table = run(
        orders=orders_frame([(1, 1, old(30))]),
        order_items=order_items_frame([(1, 1)]),
        reviews=reviews_frame([(1, 1, 3)]),
    )
    assert table[(table["member_id"] == 1) & (table["goods_id"] == 1)].empty


def test_리뷰없는_구매는_예_가중0_5():
    table = run(
        orders=orders_frame([(1, 1, old(30))]),
        order_items=order_items_frame([(1, 1)]),
    )
    row = row_for(table, 1, 1)
    assert row["label"] == 1
    assert row["weight"] == pytest.approx(0.5)


def test_담기만_한_것은_예_가중0_5():
    table = run(behavior_events=behavior_frame([(1, 2, "CART", old(5))]))
    row = row_for(table, 1, 2)
    assert row["label"] == 1
    assert row["weight"] == pytest.approx(0.5)
    assert row["split"] == "train"


def test_미구매_음성은_사용자당_4행():
    table = run(
        members=members_frame([(1, "DRY", "moisture", "dewy"), (2, "OILY", "sebum", "")]),
        orders=orders_frame([(1, 1, old(30))]),
        order_items=order_items_frame([(1, 1)]),
    )
    for member_id in (1, 2):
        mine = table[(table["member_id"] == member_id) & (table["weight"] == 0.3)]
        assert len(mine) == 4
        assert set(mine["label"]) == {0}
        assert set(mine["split"]) == {"train"}
    # 산 상품은 음성 후보에서 빠진다
    assert 1 not in set(table[(table["member_id"] == 1) & (table["weight"] == 0.3)]["goods_id"])


# ---------------------------------------------------------------------------
# 행동 18열
# ---------------------------------------------------------------------------


def test_행동점수는_events_ts와_같다():
    events = [(1, 1, "VIEW", old(9)), (1, 1, "CART", old(8)), (1, 2, "WISH", old(7))]
    table = run(behavior_events=behavior_frame(events))
    expected = behavior_input([
        {"cat3": CATEGORY[gid][:7], "tags": [GOODS_TAGS[gid]], "w": bt.WEIGHT[kind]}
        for _, gid, kind, _ in events
    ])
    row = table[table["member_id"] == 1].iloc[0]
    got = [row[f"b_{slug}"] for slug in MODEL_TAG_SLUGS]
    assert got == pytest.approx(expected, abs=1e-12)


def test_행동은_최근50건만_본다():
    """이벤트 60건. 앞 10건에만 있는 태그의 칸이 0이어야 한다."""
    events = [(1, 10, "VIEW", old(100) + timedelta(hours=i)) for i in range(10)]
    events += [(1, 1, "VIEW", old(50) + timedelta(hours=i)) for i in range(50)]
    table = run(behavior_events=behavior_frame(events))
    row = table[table["member_id"] == 1].iloc[0]
    assert row["b_scalp"] == pytest.approx(0.0)      # 상품 10의 태그. 앞 10건에만 있다
    assert row["b_moisture"] == pytest.approx(1.0)   # 상품 1의 태그


# ---------------------------------------------------------------------------
# split
# ---------------------------------------------------------------------------


def test_holdout은_최근14일_주문만():
    table = run(
        orders=orders_frame([(1, 1, old(15)), (2, 1, old(3))]),
        order_items=order_items_frame([(1, 1), (2, 2)]),
    )
    assert row_for(table, 1, 1)["split"] == "train"
    assert row_for(table, 1, 2)["split"] == "holdout"


# ---------------------------------------------------------------------------
# 열 계약
# ---------------------------------------------------------------------------


def test_열_순서가_train_csv_md와_같다():
    text = TRAIN_CSV_MD.read_text(encoding="utf-8")
    block = re.search(r"```\n(b_cleanse.*?)\n```", text, re.S)
    assert block is not None, "train.csv.md에서 b_ 열 이름 블록을 찾지 못했다"
    documented = [name.strip() for name in block.group(1).replace("\n", " ").split(",")]
    documented = [name for name in documented if name]
    assert len(documented) == 18, f"문서에서 뽑은 b_ 열이 18개가 아니다: {documented}"

    header = list(run().columns)
    assert [name for name in header if name.startswith("b_")] == documented
    assert header == (
        ["member_id", "skin_type", "concerns", "textures"]
        + documented
        + ["goods_id", "label", "weight", "split"]
    )
