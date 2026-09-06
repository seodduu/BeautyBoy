"""상품 탑 입력 검증.

`data/catalog`의 진짜 파일을 읽지 않는다. 표를 손으로 만들어 넣는다. 덤프가 없어도
블록 순서와 칸 수 계약은 혼자 검증할 수 있어야 한다.
"""

import sys
from pathlib import Path

import pandas as pd
import pytest

RECSYS_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RECSYS_ROOT))

from behavior_input import MODEL_TAG_SLUGS  # noqa: E402
import features as ft  # noqa: E402

GOODS_IDS = list(range(1, 11))
GOODS_TAGS = {
    1: ["moisture"], 2: ["soothe"], 3: ["sebum"], 4: ["barrier"], 5: ["pore"],
    6: ["trouble"], 7: ["bright"], 8: ["firm"], 9: ["uv"], 10: [],
}
PRICES = {gid: gid * 1000 for gid in GOODS_IDS}


# ---------------------------------------------------------------------------
# 픽스처 조립
# ---------------------------------------------------------------------------


def frame(columns, rows) -> pd.DataFrame:
    return pd.DataFrame(list(rows), columns=columns)


def goods_frame() -> pd.DataFrame:
    return frame(
        ["goods_id", "category_code", "brand_id", "sale_price", "status", "view_count"],
        [(gid, "C001001001", 1, PRICES[gid], "ON_SALE", 100) for gid in GOODS_IDS],
    )


def goods_tag_frame() -> pd.DataFrame:
    return frame(
        ["goods_id", "tag_slug", "sort_order"],
        [(gid, slug, 1) for gid in GOODS_IDS for slug in GOODS_TAGS[gid]],
    )


def goods_ingredient_frame() -> pd.DataFrame:
    """성분 900은 열 상품 전부에, 1~240은 상품 1에만 붙인다(동률 240개)."""
    rows = [(gid, 900) for gid in GOODS_IDS]
    rows += [(1, iid) for iid in range(1, 241)]
    return frame(["goods_id", "ingredient_id"], rows)


def members_frame() -> pd.DataFrame:
    return frame(
        ["member_id", "skin_type", "concerns", "textures"],
        [(1, "DRY", "moisture", "dewy"), (2, "OILY", "sebum", "matte")],
    )


def reviews_frame() -> pd.DataFrame:
    """상품 1·2에만 리뷰가 있다. 상품 10은 리뷰가 하나도 없다."""
    return frame(
        ["member_id", "goods_id", "rating", "created_at"],
        [
            (1, 1, 5, "2026-01-01 00:00:00"),
            (1, 1, 3, "2026-01-02 00:00:00"),
            (2, 2, 1, "2026-01-03 00:00:00"),
        ],
    )


@pytest.fixture(scope="module")
def space() -> ft.ItemFeatureSpace:
    return ft.build_item_features(
        goods=goods_frame(),
        goods_tag=goods_tag_frame(),
        goods_ingredient=goods_ingredient_frame(),
        reviews=reviews_frame(),
        members=members_frame(),
    )


# ---------------------------------------------------------------------------
# 검증
# ---------------------------------------------------------------------------


def test_태그칸은_18개_순서가_상수와_같다(space):
    start, end = space.block("tag")
    assert end - start == 18
    assert space.tag_slugs == MODEL_TAG_SLUGS

    for gid, slugs in GOODS_TAGS.items():
        block = space.row(gid)[start:end]
        hit = {MODEL_TAG_SLUGS[i] for i, value in enumerate(block) if value == 1.0}
        assert hit == set(slugs)


def test_가격대는_다섯칸이고_하나만_1이다(space):
    start, end = space.block("price")
    assert end - start == ft.PRICE_BANDS == 5
    assert len(space.price_bounds) == 4

    for gid in GOODS_IDS:
        block = space.row(gid)[start:end]
        assert sorted(block.tolist()) == [0.0, 0.0, 0.0, 0.0, 1.0]

    # 가장 싼 상품과 가장 비싼 상품은 서로 다른 칸에 들어간다
    assert space.row(1)[start:end].argmax() == 0
    assert space.row(10)[start:end].argmax() == ft.PRICE_BANDS - 1


def test_성분은_상위200개_동률은_id순(space):
    start, end = space.block("ingredient")
    assert end - start == ft.TOP_INGREDIENTS == 200
    assert len(space.ingredient_ids) == 200

    # 900은 열 상품 전부에 붙어 등장 상품 수가 가장 많다. 나머지는 전부 1건이라 id 오름차순
    assert space.ingredient_ids[0] == 900
    assert space.ingredient_ids[1:] == list(range(1, 200))

    assert space.row(2)[start:end].tolist() == [1.0] + [0.0] * 199


def test_리뷰없는_피부타입칸은_전체평균이다(space):
    start, end = space.block("rating")
    assert end - start == len(ft.SKIN_TYPES) == 4

    overall = space.overall_rating
    assert overall == pytest.approx((5 + 3 + 1) / 3)
    assert overall != 0

    # 상품 10은 리뷰가 없다. 네 칸이 전부 전체 평균이고 0이 아니다
    assert space.row(10)[start:end].tolist() == pytest.approx([overall] * 4)

    # 상품 1은 DRY 리뷰 두 건의 평균만 자기 값이고 나머지 세 칸은 전체 평균이다
    row = space.row(1)[start:end]
    assert row[ft.SKIN_TYPES.index("DRY")] == pytest.approx(4.0)
    for skin in ("OILY", "COMBINATION", "SENSITIVE"):
        assert row[ft.SKIN_TYPES.index(skin)] == pytest.approx(overall)
