"""상품 탑 입력(설계 §4).

카탈로그 덤프(`data/catalog/*.csv`)와 시드 raw(`data/raw/{reviews,members}.csv`)에서
상품 하나당 특징 벡터 한 줄을 만든다. 실행 중 DB에 붙지 않는다(구현 계획 §3.1).

블록 순서와 칸 수는 계약이다.

| 블록 | 칸 수 | 만드는 법 |
|---|---|---|
| 카테고리 one-hot | `category_code` 앞 7자의 고유값 수 | 정렬해 고정 |
| 태그 multi-hot | 18 | `MODEL_TAG_SLUGS` 순 |
| 가격대 one-hot | 5 | `sale_price` 분위수 20/40/60/80% |
| 성분 multi-hot | 200 | 등장 상품 수 상위 200개, 동률은 `ingredient_id` 오름차순 |
| 피부타입별 평균 별점 | 4 | 리뷰가 없는 칸은 전체 평균으로 채운다 |

마지막 블록을 0으로 채우지 않는 이유는 "리뷰 없음"과 "별점 0"이 같은 값이 되면 P1
(같은 태그를 단 두 상품이 별점만 다른 패턴)이 안 잡히기 때문이다.

이 벡터는 배포되지 않는다. 상품 탑은 학습 때만 돌고, 나가는 것은 그 출력인
`item_embeddings.json` 뿐이다.
"""

from __future__ import annotations

from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
import pandas as pd

from behavior_input import MODEL_TAG_SLUGS

# ---------------------------------------------------------------------------
# 계약
# ---------------------------------------------------------------------------

CAT3_LENGTH = 7                                   # category_code 앞 7자가 3단계 코드다
PRICE_QUANTILES = [0.2, 0.4, 0.6, 0.8]            # 가격대 경계
PRICE_BANDS = len(PRICE_QUANTILES) + 1            # 5칸
TOP_INGREDIENTS = 200                             # 성분 multi-hot 칸 수
SKIN_TYPES = ["DRY", "OILY", "COMBINATION", "SENSITIVE"]

CATALOG_FILES = ["goods", "goods_tag", "goods_ingredient"]
RAW_FILES = ["members", "reviews"]


@dataclass
class ItemFeatureSpace:
    """상품 특징 행렬과 그 행렬을 되읽는 데 필요한 자리표."""

    goods_ids: list[int]
    matrix: np.ndarray                 # (상품 수, dim)
    categories: list[str]
    price_bounds: list[float]
    ingredient_ids: list[int]
    overall_rating: float
    tag_slugs: list[str] = field(default_factory=lambda: list(MODEL_TAG_SLUGS))
    cat3: dict[int, str] = field(default_factory=dict)
    tags: dict[int, list[str]] = field(default_factory=dict)

    @property
    def dim(self) -> int:
        return int(self.matrix.shape[1])

    @property
    def index(self) -> dict[int, int]:
        """상품 id → 행 번호."""
        return {gid: i for i, gid in enumerate(self.goods_ids)}

    def block(self, name: str) -> tuple[int, int]:
        """블록 이름 → [시작, 끝) 열 범위."""
        category = len(self.categories)
        tag = len(self.tag_slugs)
        offsets = {
            "category": (0, category),
            "tag": (category, category + tag),
            "price": (category + tag, category + tag + PRICE_BANDS),
            "ingredient": (
                category + tag + PRICE_BANDS,
                category + tag + PRICE_BANDS + TOP_INGREDIENTS,
            ),
        }
        offsets["rating"] = (
            offsets["ingredient"][1],
            offsets["ingredient"][1] + len(SKIN_TYPES),
        )
        if name not in offsets:
            raise KeyError(f"모르는 블록이다: {name}")
        return offsets[name]

    def row(self, goods_id: int) -> np.ndarray:
        return self.matrix[self.index[goods_id]]


# ---------------------------------------------------------------------------
# 블록별 계산
# ---------------------------------------------------------------------------


def category_codes(goods: pd.DataFrame) -> list[str]:
    """3단계 카테고리 코드의 고유값. 정렬해 고정 순서로 쓴다."""
    return sorted({str(code)[:CAT3_LENGTH] for code in goods["category_code"]})


def price_bounds(prices: np.ndarray) -> list[float]:
    """가격대 경계 4개. 카탈로그 전체로 계산하고 meta.json에 남긴다."""
    if len(prices) == 0:
        return [0.0] * len(PRICE_QUANTILES)
    return [float(v) for v in np.quantile(prices, PRICE_QUANTILES)]


def price_band(price: float, bounds: list[float]) -> int:
    """가격 → 0~4 구간 번호. 경계값은 위 구간에 넣는다."""
    band = int(np.searchsorted(np.asarray(bounds, dtype=float), float(price), side="right"))
    return min(band, PRICE_BANDS - 1)


def top_ingredients(goods_ingredient: pd.DataFrame, goods_ids: set[int]) -> list[int]:
    """등장 상품 수 상위 200개. 동률은 ingredient_id 오름차순."""
    seen: set[tuple[int, int]] = set()
    counts: Counter[int] = Counter()
    for row in goods_ingredient.itertuples(index=False):
        gid, iid = int(row.goods_id), int(row.ingredient_id)
        if gid not in goods_ids or (gid, iid) in seen:
            continue
        seen.add((gid, iid))
        counts[iid] += 1
    ranked = sorted(counts.items(), key=lambda item: (-item[1], item[0]))
    return [iid for iid, _ in ranked[:TOP_INGREDIENTS]]


def rating_by_skin_type(
    reviews: pd.DataFrame, members: pd.DataFrame
) -> tuple[dict[tuple[int, str], float], float]:
    """(상품, 피부타입) → 평균 별점, 그리고 전체 평균."""
    skin_of = {
        int(row.member_id): str(row.skin_type)
        for row in members.itertuples(index=False)
    }
    total = 0.0
    count = 0
    sums: dict[tuple[int, str], float] = defaultdict(float)
    hits: dict[tuple[int, str], int] = defaultdict(int)
    for row in reviews.itertuples(index=False):
        rating = float(row.rating)
        total += rating
        count += 1
        skin = skin_of.get(int(row.member_id), "")
        if skin not in SKIN_TYPES:
            continue
        key = (int(row.goods_id), skin)
        sums[key] += rating
        hits[key] += 1
    overall = total / count if count else 0.0
    means = {key: sums[key] / hits[key] for key in hits}
    return means, overall


# ---------------------------------------------------------------------------
# 조립
# ---------------------------------------------------------------------------


def build_item_features(
    *,
    goods: pd.DataFrame,
    goods_tag: pd.DataFrame,
    goods_ingredient: pd.DataFrame,
    reviews: pd.DataFrame,
    members: pd.DataFrame,
) -> ItemFeatureSpace:
    """카탈로그와 리뷰에서 상품 특징 행렬을 만든다."""
    ids = sorted({int(gid) for gid in goods["goods_id"]})
    id_set = set(ids)

    cat3 = {
        int(row.goods_id): str(row.category_code)[:CAT3_LENGTH]
        for row in goods.itertuples(index=False)
    }
    prices = {int(row.goods_id): float(row.sale_price) for row in goods.itertuples(index=False)}

    tags: dict[int, list[str]] = defaultdict(list)
    for row in goods_tag.itertuples(index=False):
        gid, slug = int(row.goods_id), str(row.tag_slug)
        if gid in id_set and slug in MODEL_TAG_SLUGS and slug not in tags[gid]:
            tags[gid].append(slug)

    ingredients: dict[int, set[int]] = defaultdict(set)
    for row in goods_ingredient.itertuples(index=False):
        gid = int(row.goods_id)
        if gid in id_set:
            ingredients[gid].add(int(row.ingredient_id))

    categories = category_codes(goods)
    bounds = price_bounds(np.array([prices[gid] for gid in ids], dtype=float))
    ingredient_ids = top_ingredients(goods_ingredient, id_set)
    means, overall = rating_by_skin_type(reviews, members)

    category_at = {code: i for i, code in enumerate(categories)}
    tag_at = {slug: i for i, slug in enumerate(MODEL_TAG_SLUGS)}
    ingredient_at = {iid: i for i, iid in enumerate(ingredient_ids)}

    width = (
        len(categories) + len(MODEL_TAG_SLUGS) + PRICE_BANDS
        + TOP_INGREDIENTS + len(SKIN_TYPES)
    )
    matrix = np.zeros((len(ids), width), dtype=np.float32)

    base_tag = len(categories)
    base_price = base_tag + len(MODEL_TAG_SLUGS)
    base_ingredient = base_price + PRICE_BANDS
    base_rating = base_ingredient + TOP_INGREDIENTS

    for i, gid in enumerate(ids):
        matrix[i, category_at[cat3[gid]]] = 1.0
        for slug in tags.get(gid, []):
            matrix[i, base_tag + tag_at[slug]] = 1.0
        matrix[i, base_price + price_band(prices[gid], bounds)] = 1.0
        for iid in ingredients.get(gid, ()):
            at = ingredient_at.get(iid)
            if at is not None:
                matrix[i, base_ingredient + at] = 1.0
        for j, skin in enumerate(SKIN_TYPES):
            matrix[i, base_rating + j] = means.get((gid, skin), overall)

    return ItemFeatureSpace(
        goods_ids=ids,
        matrix=matrix,
        categories=categories,
        price_bounds=bounds,
        ingredient_ids=ingredient_ids,
        overall_rating=overall,
        cat3={gid: cat3[gid] for gid in ids},
        tags={gid: list(tags.get(gid, [])) for gid in ids},
    )


def load_frames(directory: Path, names: list[str]) -> dict[str, pd.DataFrame]:
    """`<directory>/<name>.csv`를 전부 읽는다. 없으면 그 자리에서 죽는다."""
    frames: dict[str, pd.DataFrame] = {}
    for name in names:
        path = directory / f"{name}.csv"
        if not path.exists():
            raise SystemExit(f"입력이 없다: {path}")
        frames[name] = pd.read_csv(path, dtype=str, keep_default_na=False)
    return frames


def load_item_features(catalog_dir: Path, raw_dir: Path) -> ItemFeatureSpace:
    """디렉터리 두 개에서 상품 특징 행렬을 만든다."""
    catalog = load_frames(catalog_dir, CATALOG_FILES)
    raw = load_frames(raw_dir, RAW_FILES)
    return build_item_features(
        goods=catalog["goods"],
        goods_tag=catalog["goods_tag"],
        goods_ingredient=catalog["goods_ingredient"],
        reviews=raw["reviews"],
        members=raw["members"],
    )
