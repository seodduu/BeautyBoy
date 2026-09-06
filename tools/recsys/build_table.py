"""학습표 빌더.

`data/raw/*.csv`(시드 생성기 산출물)와 `data/catalog/*.csv`(카탈로그 덤프)를 읽어
`data/train.csv` 한 장을 만든다. 실행 중 DB에 붙지 않는다(구현 계획 §3.1).

    python tools/recsys/build_table.py --raw data/raw --catalog data/catalog --out data/train.csv

출력 열의 계약은 `tools/recsys/schema/train.csv.md`다. 이 파일은 그 문서의 순서를 그대로
따르고, 열 이름을 두 곳에 적지 않기 위해 18개 행동 열은 `MODEL_TAG_SLUGS`에서 만든다.

라벨 규칙은 설계 §7의 표를 순서대로 판정한다. 먼저 맞는 것을 쓰고 나머지는 보지 않는다.
"""

from __future__ import annotations

import argparse
from collections import defaultdict
from datetime import datetime, timedelta
from pathlib import Path
from random import Random

import pandas as pd

from behavior_input import MAX_EVENTS, MODEL_TAG_SLUGS, WEIGHT, behavior_input

# ---------------------------------------------------------------------------
# 계약
# ---------------------------------------------------------------------------

BEHAVIOR_COLUMNS = [f"b_{slug}" for slug in MODEL_TAG_SLUGS]
COLUMNS = (
    ["member_id", "skin_type", "concerns", "textures"]
    + BEHAVIOR_COLUMNS
    + ["goods_id", "label", "weight", "split"]
)

SEED = 42                 # 미구매 음성 표본추출. 재현성이 게이트의 전제다
NEGATIVES_PER_MEMBER = 4  # 설계 §7 "미구매 무작위 4개/회원"
HOLDOUT_DAYS = 14         # split=holdout은 최근 14일 주문에서만 나온다
CAT3_LENGTH = 7           # category_code 앞 7자가 3단계 코드다

RAW_FILES = ["members", "orders", "order_items", "reviews", "behavior_events"]
CATALOG_FILES = ["goods", "goods_tag"]


# ---------------------------------------------------------------------------
# 입력 읽기
# ---------------------------------------------------------------------------


def load_frames(directory: Path, names: list[str]) -> dict[str, pd.DataFrame]:
    """`<directory>/<name>.csv`를 전부 읽는다. 없으면 그 자리에서 죽는다."""
    frames: dict[str, pd.DataFrame] = {}
    for name in names:
        path = directory / f"{name}.csv"
        if not path.exists():
            raise SystemExit(f"입력이 없다: {path}. 시드 생성기를 먼저 돌려라.")
        frames[name] = pd.read_csv(path, dtype=str, keep_default_na=False)
    return frames


def split_slugs(value: str) -> list[str]:
    """생성기는 콤마로 잇고 학습표는 파이프로 잇는다. 양쪽 구분자를 모두 받는다."""
    if not value:
        return []
    text = value.replace("|", ",")
    return [part.strip() for part in text.split(",") if part.strip()]


# ---------------------------------------------------------------------------
# 카탈로그
# ---------------------------------------------------------------------------


def catalog_index(goods: pd.DataFrame, goods_tag: pd.DataFrame):
    """상품 id → (cat3, 태그 목록). 태그는 goods_tag.csv의 등장 순서를 지킨다."""
    cat3: dict[int, str] = {}
    for row in goods.itertuples(index=False):
        cat3[int(row.goods_id)] = str(row.category_code)[:CAT3_LENGTH]

    tags: dict[int, list[str]] = defaultdict(list)
    for row in goods_tag.itertuples(index=False):
        gid = int(row.goods_id)
        if gid in cat3 and row.tag_slug not in tags[gid]:
            tags[gid].append(str(row.tag_slug))
    return cat3, tags


# ---------------------------------------------------------------------------
# 행동 18열
# ---------------------------------------------------------------------------


def behavior_vectors(behavior_events: pd.DataFrame, cat3, tags) -> dict[int, list[float]]:
    """회원별 행동 18칸. created_at 오름차순 마지막 MAX_EVENTS건만 본다.

    브라우저는 이벤트 링버퍼에 최근 50건만 담는다(events.ts). 학습이 전체 이력을 보면
    모델과 브라우저가 서로 다른 입력을 쓰게 된다.
    """
    per_member: dict[int, list[tuple[str, int, str]]] = defaultdict(list)
    for row in behavior_events.itertuples(index=False):
        gid = int(row.goods_id)
        if gid not in cat3:
            continue
        per_member[int(row.member_id)].append((str(row.created_at), gid, str(row.kind)))

    vectors: dict[int, list[float]] = {}
    for member_id, rows in per_member.items():
        recent = sorted(rows, key=lambda item: item[0])[-MAX_EVENTS:]
        events = [
            {"cat3": cat3[gid], "tags": tags.get(gid, []), "w": WEIGHT[kind]}
            for _, gid, kind in recent
            if kind in WEIGHT
        ]
        vectors[member_id] = behavior_input(events)
    return vectors


# ---------------------------------------------------------------------------
# 학습표
# ---------------------------------------------------------------------------


def build_table(
    *,
    members: pd.DataFrame,
    orders: pd.DataFrame,
    order_items: pd.DataFrame,
    reviews: pd.DataFrame,
    behavior_events: pd.DataFrame,
    goods: pd.DataFrame,
    goods_tag: pd.DataFrame,
    now: datetime | None = None,
) -> pd.DataFrame:
    """설계 §7의 라벨 규칙으로 학습표를 만든다."""
    now = now or datetime.now()
    holdout_from = now - timedelta(days=HOLDOUT_DAYS)

    cat3, tags = catalog_index(goods, goods_tag)
    vectors = behavior_vectors(behavior_events, cat3, tags)
    zero = [0.0] * len(MODEL_TAG_SLUGS)

    order_owner: dict[int, int] = {}
    order_at: dict[int, datetime] = {}
    for row in orders.itertuples(index=False):
        oid = int(row.order_id)
        order_owner[oid] = int(row.member_id)
        order_at[oid] = pd.to_datetime(row.ordered_at).to_pydatetime()

    # (회원, 상품) → 구매 건수와 가장 최근 구매 시각
    bought_count: dict[tuple[int, int], int] = defaultdict(int)
    bought_at: dict[tuple[int, int], datetime] = {}
    bought_by_member: dict[int, set[int]] = defaultdict(set)
    for row in order_items.itertuples(index=False):
        oid = int(row.order_id)
        if oid not in order_owner:
            continue
        key = (order_owner[oid], int(row.goods_id))
        bought_count[key] += 1
        bought_by_member[key[0]].add(key[1])
        when = order_at[oid]
        if key not in bought_at or when > bought_at[key]:
            bought_at[key] = when

    rating: dict[tuple[int, int], int] = {}
    for row in reviews.itertuples(index=False):
        rating[(int(row.member_id), int(row.goods_id))] = int(row.rating)

    # 구매하지 않았어도 찜·담기는 약한 예다. 미구매 음성 후보에서도 빠진다.
    wished: dict[int, set[int]] = defaultdict(set)
    for row in behavior_events.itertuples(index=False):
        gid = int(row.goods_id)
        if gid not in cat3:
            continue
        if str(row.kind) in ("WISH", "CART"):
            wished[int(row.member_id)].add(gid)

    all_goods = sorted(cat3)
    rng = Random(SEED)
    rows: list[dict] = []

    member_rows = sorted(members.itertuples(index=False), key=lambda m: int(m.member_id))
    for member in member_rows:
        member_id = int(member.member_id)
        profile = {
            "member_id": member_id,
            "skin_type": str(member.skin_type),
            "concerns": "|".join(split_slugs(str(member.concerns))),
            "textures": "|".join(split_slugs(str(member.textures))),
        }
        vector = vectors.get(member_id, zero)
        behavior = dict(zip(BEHAVIOR_COLUMNS, vector))

        def emit(goods_id: int, label: int, weight: float, split: str) -> None:
            rows.append({
                **profile, **behavior,
                "goods_id": goods_id, "label": label, "weight": weight, "split": split,
            })

        purchased = sorted(bought_by_member[member_id])
        for goods_id in purchased:
            key = (member_id, goods_id)
            split = "holdout" if bought_at[key] >= holdout_from else "train"
            score = rating.get(key)
            if bought_count[key] >= 2:
                emit(goods_id, 1, 1.0, split)
            elif score is not None and score >= 4:
                emit(goods_id, 1, 1.0, split)
            elif score is not None and score <= 2:
                emit(goods_id, 0, 2.0, split)
            elif score is not None:
                continue          # 별점 3은 행을 만들지 않는다. 취향 신호가 아니다
            else:
                emit(goods_id, 1, 0.5, split)

        purchased_set = set(purchased)
        # 구매하지 않은 찜·담기. 구매 행이 이미 있으면 그쪽 판정이 이긴다
        for goods_id in sorted(wished[member_id] - purchased_set):
            emit(goods_id, 1, 0.5, "train")

        # 미구매 음성. 사지도 찜하지도 담지도 않은 상품에서만 고른다
        excluded = purchased_set | wished[member_id]
        candidates = [gid for gid in all_goods if gid not in excluded]
        picked = rng.sample(candidates, min(NEGATIVES_PER_MEMBER, len(candidates)))
        for goods_id in sorted(picked):
            emit(goods_id, 0, 0.3, "train")

    return pd.DataFrame(rows, columns=COLUMNS)


# ---------------------------------------------------------------------------
# 엔트리포인트
# ---------------------------------------------------------------------------


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="v3 학습표를 만든다.")
    parser.add_argument("--raw", required=True, type=Path, help="시드 raw CSV 디렉터리")
    parser.add_argument("--catalog", required=True, type=Path, help="카탈로그 CSV 디렉터리")
    parser.add_argument("--out", required=True, type=Path, help="학습표 CSV 경로")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    raw = load_frames(args.raw, RAW_FILES)
    catalog = load_frames(args.catalog, CATALOG_FILES)

    table = build_table(
        members=raw["members"],
        orders=raw["orders"],
        order_items=raw["order_items"],
        reviews=raw["reviews"],
        behavior_events=raw["behavior_events"],
        goods=catalog["goods"],
        goods_tag=catalog["goods_tag"],
    )

    args.out.parent.mkdir(parents=True, exist_ok=True)
    table.to_csv(args.out, index=False)

    counts = table["split"].value_counts().to_dict()
    print(f"{len(table)}행 / split {counts} / 회원 {table['member_id'].nunique()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
