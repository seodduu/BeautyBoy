"""투 타워 학습 스모크.

작은 합성 학습표 200행으로 2에폭만 돌린다. 학습 품질은 게이트(B1-4)가 본다. 여기서 잡는 것은
계약 위반과 "학습이 아예 안 되는 상태" 둘이다.
"""

import hashlib
import json
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import pytest
from jsonschema import Draft202012Validator

RECSYS_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RECSYS_ROOT))

from behavior_input import MODEL_TAG_SLUGS  # noqa: E402
import features as ft  # noqa: E402
import train as tr  # noqa: E402

SCHEMA_DIR = RECSYS_ROOT / "schema"
GOODS_IDS = list(range(1, 21))
MEMBERS = list(range(1, 41))
EPOCHS = 2


# ---------------------------------------------------------------------------
# 픽스처 조립
# ---------------------------------------------------------------------------


def frame(columns, rows) -> pd.DataFrame:
    return pd.DataFrame(list(rows), columns=columns)


def goods_tags(goods_id: int) -> list[str]:
    return [MODEL_TAG_SLUGS[goods_id % len(MODEL_TAG_SLUGS)]]


def catalog_frames(goods_ids=GOODS_IDS):
    goods = frame(
        ["goods_id", "category_code", "brand_id", "sale_price", "status", "view_count"],
        [
            (gid, f"C00100100{1 + gid % 2}", 1, 5000 + gid * 1000, "ON_SALE", 100)
            for gid in goods_ids
        ],
    )
    goods_tag = frame(
        ["goods_id", "tag_slug", "sort_order"],
        [(gid, slug, 1) for gid in goods_ids for slug in goods_tags(gid)],
    )
    goods_ingredient = frame(
        ["goods_id", "ingredient_id"],
        [(gid, iid) for gid in goods_ids for iid in (gid, gid + 100)],
    )
    return goods, goods_tag, goods_ingredient


def member_skin(member_id: int) -> str:
    return ft.SKIN_TYPES[member_id % len(ft.SKIN_TYPES)]


def members_frame(member_ids=MEMBERS) -> pd.DataFrame:
    return frame(
        ["member_id", "skin_type", "concerns", "textures"],
        [
            (mid, member_skin(mid), tr.MODEL_CONCERNS[mid % 9], tr.MODEL_TEXTURES[mid % 3])
            for mid in member_ids
        ],
    )


def reviews_frame() -> pd.DataFrame:
    return frame(
        ["member_id", "goods_id", "rating", "created_at"],
        [
            (mid, gid, 5 if gid % 4 == mid % 4 else 2, "2026-01-01 00:00:00")
            for mid in MEMBERS[:10]
            for gid in GOODS_IDS[:5]
        ],
    )


def item_space(goods_ids=GOODS_IDS, member_ids=MEMBERS) -> ft.ItemFeatureSpace:
    goods, goods_tag, goods_ingredient = catalog_frames(goods_ids)
    return ft.build_item_features(
        goods=goods,
        goods_tag=goods_tag,
        goods_ingredient=goods_ingredient,
        reviews=reviews_frame(),
        members=members_frame(member_ids),
    )


TABLE_COLUMNS = (
    ["member_id", "skin_type", "concerns", "textures"]
    + tr.BEHAVIOR_COLUMNS
    + ["goods_id", "label", "weight", "split"]
)


def table_row(member_id: int, goods_id: int, label: int, weight: float, split: str) -> dict:
    """학습표 한 행. 행동 18칸은 회원 취향과 같은 자리를 밝힌다(학습할 신호가 있어야 한다)."""
    behavior = [0.0] * len(MODEL_TAG_SLUGS)
    behavior[member_id % len(MODEL_TAG_SLUGS)] = 1.0
    behavior[(member_id * 3) % len(MODEL_TAG_SLUGS)] = 0.5
    row = {
        "member_id": member_id,
        "skin_type": member_skin(member_id),
        "concerns": tr.MODEL_CONCERNS[member_id % 9],
        "textures": tr.MODEL_TEXTURES[member_id % 3],
    }
    row.update(dict(zip(tr.BEHAVIOR_COLUMNS, behavior)))
    row.update({"goods_id": goods_id, "label": label, "weight": weight, "split": split})
    return row


def train_table() -> pd.DataFrame:
    """회원 40명 × 상품 5개 = 200행. 라벨은 '같은 나머지끼리 맞는다'는 단순한 규칙이다."""
    rows = []
    for member_id in MEMBERS:
        for offset in range(5):
            goods_id = GOODS_IDS[(member_id + offset * 4) % len(GOODS_IDS)]
            fits = goods_id % 4 == member_id % 4
            label = 1 if fits else 0
            weight = 1.0 if fits else 0.3
            split = "holdout" if member_id % 10 == 0 else "train"
            rows.append(table_row(member_id, goods_id, label, weight, split))
    return pd.DataFrame(rows, columns=TABLE_COLUMNS)


@pytest.fixture(scope="module")
def trained(tmp_path_factory) -> dict:
    out = tmp_path_factory.mktemp("out")
    return tr.run(train_table(), item_space(), out, version="v3.1", epochs=EPOCHS)


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


# ---------------------------------------------------------------------------
# 검증
# ---------------------------------------------------------------------------


def test_학습표가_200행이다():
    assert len(train_table()) == 200


def test_두_파일이_스키마를_통과한다(trained):
    for name, schema_file in (
        ("item_embeddings", "item_embeddings.schema.json"),
        ("user_tower", "user_tower.schema.json"),
    ):
        schema = json.loads((SCHEMA_DIR / schema_file).read_text(encoding="utf-8"))
        payload = load(trained["paths"][name])
        errors = list(Draft202012Validator(schema).iter_errors(payload))
        assert errors == [], [error.message for error in errors]


def test_두_파일_version이_같다(trained):
    items = load(trained["paths"]["item_embeddings"])
    user = load(trained["paths"]["user_tower"])
    assert items["version"] == user["version"] == "v3.1"


def test_layers_모양이_34x48_48x32다(trained):
    layers = load(trained["paths"]["user_tower"])["layers"]
    assert len(layers) == 2

    first, second = layers
    assert (len(first["w"]), len(first["w"][0]), len(first["b"])) == (34, 48, 48)
    assert first["act"] == "relu"
    assert (len(second["w"]), len(second["w"][0]), len(second["b"])) == (48, 32, 32)
    assert second["act"] == "none"


def test_임베딩이_L2정규화됐다(trained):
    items = load(trained["paths"]["item_embeddings"])["items"]
    assert len(items) == len(GOODS_IDS)
    for goods_no, vector in items.items():
        assert len(vector) == tr.EMB_DIM
        assert np.linalg.norm(vector) == pytest.approx(1.0, abs=1e-5), goods_no


def test_같은_시드는_같은_파일을_만든다(tmp_path):
    table, space = train_table(), item_space()
    first = tr.run(table, space, tmp_path / "a", version="v3.1", epochs=EPOCHS)
    second = tr.run(table, space, tmp_path / "b", version="v3.1", epochs=EPOCHS)
    for name in ("item_embeddings", "user_tower"):
        assert sha256(first["paths"][name]) == sha256(second["paths"][name]), name


def test_손실이_줄어든다(trained):
    losses = trained["train_losses"]
    assert len(losses) == EPOCHS
    assert losses[-1] < losses[0]


def test_기록이_적은_상품은_평균쪽으로_당겨진다(tmp_path):
    """상품 1은 기록 1건, 상품 2는 50건이다. 둘은 같은 카테고리태그 그룹에 있다."""
    # 세 상품의 카테고리와 태그가 같아야 한 그룹이 된다
    goods, goods_tag, goods_ingredient = catalog_frames([1, 2, 3])
    goods["category_code"] = "C001001001"
    goods_tag["tag_slug"] = "moisture"
    space = ft.build_item_features(
        goods=goods,
        goods_tag=goods_tag,
        goods_ingredient=goods_ingredient,
        reviews=reviews_frame(),
        members=members_frame(),
    )

    rows = [table_row(1, 1, 1, 1.0, "train")]
    rows += [table_row(m, 2, m % 2, 1.0, "train") for m in MEMBERS[:40]]
    rows += [table_row(m, 2, m % 2, 0.5, "train") for m in MEMBERS[:10]]
    rows += [table_row(m, 3, 1, 1.0, "train") for m in MEMBERS[:10]]
    table = pd.DataFrame(rows, columns=TABLE_COLUMNS)

    result = tr.run(table, space, tmp_path, version="v3.1", epochs=EPOCHS)
    assert result["record_counts"][1] == 1
    assert result["record_counts"][2] == 50

    index = space.index
    shrunk, means = result["shrunk_embeddings"], result["group_means"]
    rare = np.linalg.norm(shrunk[index[1]] - means[index[1]])
    common = np.linalg.norm(shrunk[index[2]] - means[index[2]])
    assert rare < common
