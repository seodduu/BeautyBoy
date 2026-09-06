"""투 타워 학습(설계 §4, 구현 계획 §4 Task B1-3).

`data/train.csv` + `data/catalog/*.csv` + `data/raw/*.csv`를 읽어 모델 파일 세 개를 만든다.

    python tools/recsys/train.py --train data/train.csv --catalog data/catalog \
        --raw data/raw --out out --version v3.1

산출:
- `out/<version>/item_embeddings.json`  상품 임베딩. 브라우저가 통째로 받는다
- `out/<version>/user_tower.json`       사용자 탑 가중치. 브라우저가 34칸을 32칸으로 옮긴다
- `out/<version>/meta.json`             가격대 경계·손실 기록 등 운영 메타데이터

앞의 두 파일은 저장 직후 Wave A의 JSON Schema로 스스로 검증한다. 하나라도 어긋나면 세 파일을
지우고 종료 코드 1로 죽는다. 스키마에 안 맞는 모델 파일이 디스크에 남으면 다음 단계가 그것을
읽기 때문이다.

두 모델 JSON에는 학습 시각을 넣지 않는다. 같은 시드로 두 번 돌리면 바이트까지 같아야 하고
(게이트 재현성), 시각이 들어가는 순간 그 성질이 깨진다. 시각은 meta.json에만 남는다.
"""

from __future__ import annotations

import argparse
import json
import random
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import pandas as pd
import torch
import torch.nn.functional as F
from jsonschema import Draft202012Validator
from torch import nn

from behavior_input import MODEL_TAG_SLUGS
from features import SKIN_TYPES, ItemFeatureSpace, load_item_features

# ---------------------------------------------------------------------------
# 상수 (구현 계획 §4 Task B1-3 표. 값을 바꾸면 게이트 임계값의 근거가 무너진다)
# ---------------------------------------------------------------------------

SEED = 42                 # torch·random·numpy 셋 다 건다. 하나라도 빠지면 파일이 달라진다
EMB_DIM = 32              # 두 탑의 출력 차원
USER_HIDDEN = 48          # 사용자 탑 34 → 48(relu) → 32(none)
ITEM_HIDDEN = 64          # 상품 탑 입력 → 64(relu) → 32(none)
EPOCHS = 30
PATIENCE = 3              # holdout 손실이 3에폭 연속 오르면 조기 종료
LEARNING_RATE = 1e-3
BATCH_SIZE = 1024
SHRINK_K = 5              # 기록이 적은 상품을 카테고리태그평균 쪽으로 당기는 세기
TAG_CONTRIBUTION_TOP = 4  # item_embeddings.schema.json의 maxProperties와 같아야 한다
ROUND_DIGITS = 6          # 파일 크기와 재현성을 위해 자릿수를 자른다
DEFAULT_VERSION = "v3.1"

MODEL_CONCERNS = [
    "exfoliate", "sebum", "pore", "trouble", "soothe",
    "moisture", "barrier", "bright", "anti-aging",
]
MODEL_TEXTURES = ["fresh", "dewy", "matte"]

# user_tower.schema.json의 inputOrder와 같아야 한다. 4 + 9 + 3 + 18 = 34.
INPUT_ORDER = {
    "skinType": list(SKIN_TYPES),
    "concerns": list(MODEL_CONCERNS),
    "textures": list(MODEL_TEXTURES),
    "behaviorTags": list(MODEL_TAG_SLUGS),
}
USER_INPUT_DIM = sum(len(block) for block in INPUT_ORDER.values())

BEHAVIOR_COLUMNS = [f"b_{slug}" for slug in MODEL_TAG_SLUGS]
SCHEMA_DIR = Path(__file__).resolve().parent / "schema"


def set_seeds(seed: int = SEED) -> None:
    """재현성의 전제. 셋 다 건다."""
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)


# ---------------------------------------------------------------------------
# 입력 행렬
# ---------------------------------------------------------------------------


def split_slugs(value: str) -> list[str]:
    """학습표는 파이프로 잇는다. 콤마도 받아 준다."""
    if not value:
        return []
    return [part.strip() for part in str(value).replace("|", ",").split(",") if part.strip()]


def user_matrix(table: pd.DataFrame) -> np.ndarray:
    """학습표 행 → 34칸 사용자 입력. 자리 배치는 INPUT_ORDER 그대로다."""
    skin_at = {name: i for i, name in enumerate(INPUT_ORDER["skinType"])}
    concern_at = {name: i for i, name in enumerate(INPUT_ORDER["concerns"])}
    texture_at = {name: i for i, name in enumerate(INPUT_ORDER["textures"])}

    base_concern = len(INPUT_ORDER["skinType"])
    base_texture = base_concern + len(INPUT_ORDER["concerns"])
    base_behavior = base_texture + len(INPUT_ORDER["textures"])

    matrix = np.zeros((len(table), USER_INPUT_DIM), dtype=np.float32)
    for i, row in enumerate(table.itertuples(index=False)):
        skin = str(getattr(row, "skin_type", "") or "")
        if skin in skin_at:
            matrix[i, skin_at[skin]] = 1.0
        for slug in split_slugs(str(getattr(row, "concerns", "") or "")):
            if slug in concern_at:
                matrix[i, base_concern + concern_at[slug]] = 1.0
        for slug in split_slugs(str(getattr(row, "textures", "") or "")):
            if slug in texture_at:
                matrix[i, base_texture + texture_at[slug]] = 1.0
    behavior = table[BEHAVIOR_COLUMNS].to_numpy(dtype=np.float32)
    matrix[:, base_behavior:base_behavior + len(BEHAVIOR_COLUMNS)] = behavior
    return matrix


# ---------------------------------------------------------------------------
# 두 탑
# ---------------------------------------------------------------------------


class Tower(nn.Module):
    """입력 → hidden(relu) → EMB_DIM(none) → L2 정규화. 두 탑이 같은 모양을 쓴다."""

    def __init__(self, input_dim: int, hidden_dim: int) -> None:
        super().__init__()
        self.fc1 = nn.Linear(input_dim, hidden_dim)
        self.fc2 = nn.Linear(hidden_dim, EMB_DIM)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        hidden = torch.relu(self.fc1(x))
        return F.normalize(self.fc2(hidden), dim=-1, eps=1e-8)


def weighted_bce(user_vec: torch.Tensor, item_vec: torch.Tensor,
                 label: torch.Tensor, weight: torch.Tensor) -> torch.Tensor:
    """weight 가중 BCE. 내적은 -1~1이라 (내적 + 1) / 2로 0~1에 옮겨 확률로 읽는다."""
    score = (user_vec * item_vec).sum(dim=-1)
    prob = ((score + 1.0) / 2.0).clamp(1e-6, 1.0 - 1e-6)
    loss = F.binary_cross_entropy(prob, label, weight=weight, reduction="sum")
    return loss / weight.sum().clamp_min(1e-8)


def train_towers(
    table: pd.DataFrame,
    space: ItemFeatureSpace,
    *,
    epochs: int = EPOCHS,
) -> dict:
    """두 탑을 학습시킨다. 반환값에 에폭별 손실과 학습된 두 탑이 들어 있다."""
    set_seeds()

    index = space.index
    known = table["goods_id"].astype(int).map(lambda gid: gid in index)
    table = table[known].reset_index(drop=True)
    if table.empty:
        raise SystemExit("학습표에 카탈로그와 겹치는 상품이 하나도 없다.")

    users = torch.from_numpy(user_matrix(table))
    items = torch.from_numpy(np.asarray(space.matrix, dtype=np.float32))
    item_rows = torch.from_numpy(
        np.array([index[int(gid)] for gid in table["goods_id"]], dtype=np.int64)
    )
    labels = torch.from_numpy(table["label"].to_numpy(dtype=np.float32))
    weights = torch.from_numpy(table["weight"].to_numpy(dtype=np.float32))
    is_train = torch.from_numpy((table["split"].to_numpy() == "train"))
    is_holdout = ~is_train

    user_tower = Tower(USER_INPUT_DIM, USER_HIDDEN)
    item_tower = Tower(space.dim, ITEM_HIDDEN)
    optimizer = torch.optim.Adam(
        list(user_tower.parameters()) + list(item_tower.parameters()), lr=LEARNING_RATE
    )

    train_idx = torch.nonzero(is_train, as_tuple=False).squeeze(-1)
    holdout_idx = torch.nonzero(is_holdout, as_tuple=False).squeeze(-1)
    if train_idx.numel() == 0:                # holdout만 있는 표는 학습이 불가능하다
        train_idx = torch.arange(len(table))

    generator = torch.Generator().manual_seed(SEED)
    train_losses: list[float] = []
    holdout_losses: list[float] = []
    worse_streak = 0

    def evaluate(rows: torch.Tensor) -> float:
        if rows.numel() == 0:
            return float("nan")
        with torch.no_grad():
            loss = weighted_bce(
                user_tower(users[rows]),
                item_tower(items[item_rows[rows]]),
                labels[rows],
                weights[rows],
            )
        return float(loss)

    for _ in range(epochs):
        order = train_idx[torch.randperm(train_idx.numel(), generator=generator)]
        total = 0.0
        batches = 0
        for start in range(0, order.numel(), BATCH_SIZE):
            rows = order[start:start + BATCH_SIZE]
            optimizer.zero_grad()
            loss = weighted_bce(
                user_tower(users[rows]),
                item_tower(items[item_rows[rows]]),
                labels[rows],
                weights[rows],
            )
            loss.backward()
            optimizer.step()
            total += float(loss)
            batches += 1
        train_losses.append(total / max(batches, 1))
        holdout_losses.append(evaluate(holdout_idx))

        if len(holdout_losses) >= 2 and not np.isnan(holdout_losses[-1]):
            worse_streak = worse_streak + 1 if holdout_losses[-1] > holdout_losses[-2] else 0
            if worse_streak >= PATIENCE:
                break

    with torch.no_grad():
        embeddings = item_tower(items).numpy()

    record_counts: dict[int, int] = defaultdict(int)
    for gid in table["goods_id"].astype(int):
        record_counts[int(gid)] += 1

    return {
        "user_tower": user_tower,
        "item_tower": item_tower,
        "embeddings": embeddings,
        "record_counts": dict(record_counts),
        "train_losses": train_losses,
        "holdout_losses": holdout_losses,
        "rows": int(len(table)),
    }


# ---------------------------------------------------------------------------
# 수축 (기록이 적은 상품은 평균 쪽으로 당긴다)
# ---------------------------------------------------------------------------


def category_tag_means(space: ItemFeatureSpace, embeddings: np.ndarray) -> np.ndarray:
    """상품마다 자기 카테고리태그 그룹의 평균 좌표.

    그룹 열쇠는 (3단계 카테고리, 태그 집합)이다. 그 그룹에 자기밖에 없으면 카테고리로,
    카테고리에도 자기밖에 없으면 전체 평균으로 물러난다.
    """
    exact: dict[tuple, list[int]] = defaultdict(list)
    by_category: dict[str, list[int]] = defaultdict(list)
    for i, gid in enumerate(space.goods_ids):
        exact[(space.cat3[gid], tuple(sorted(space.tags.get(gid, []))))].append(i)
        by_category[space.cat3[gid]].append(i)

    overall = embeddings.mean(axis=0)
    means = np.zeros_like(embeddings)
    for i, gid in enumerate(space.goods_ids):
        group = exact[(space.cat3[gid], tuple(sorted(space.tags.get(gid, []))))]
        if len(group) < 2:
            group = by_category[space.cat3[gid]]
        means[i] = embeddings[group].mean(axis=0) if len(group) >= 2 else overall
    return means


def shrink_embeddings(
    space: ItemFeatureSpace, embeddings: np.ndarray, record_counts: dict[int, int]
) -> tuple[np.ndarray, np.ndarray]:
    """(n·자기 + K·카테고리태그평균) / (n + K) 뒤 다시 L2 정규화한다."""
    means = category_tag_means(space, embeddings)
    counts = np.array(
        [record_counts.get(gid, 0) for gid in space.goods_ids], dtype=np.float32
    ).reshape(-1, 1)
    mixed = (counts * embeddings + SHRINK_K * means) / (counts + SHRINK_K)
    norms = np.linalg.norm(mixed, axis=1, keepdims=True)
    norms[norms < 1e-8] = 1.0
    return mixed / norms, means


# ---------------------------------------------------------------------------
# 산출 JSON
# ---------------------------------------------------------------------------


def rounded(values) -> list:
    return [round(float(v), ROUND_DIGITS) for v in values]


def tag_contribution(space: ItemFeatureSpace, item_tower: Tower) -> dict[str, dict[str, float]]:
    """상품 탑 첫 층의 태그 18칸 가중치 × 그 상품의 태그 multi-hot. 상위 4개만 싣는다."""
    start, end = space.block("tag")
    with torch.no_grad():
        columns = item_tower.fc1.weight[:, start:end].numpy()   # (hidden, 18)
    strength = np.linalg.norm(columns, axis=0)                  # 태그별 기여 크기
    out: dict[str, dict[str, float]] = {}
    for gid in space.goods_ids:
        scores = [
            (slug, float(strength[i]))
            for i, slug in enumerate(MODEL_TAG_SLUGS)
            if slug in space.tags.get(gid, [])
        ]
        scores.sort(key=lambda item: (-item[1], item[0]))
        top = scores[:TAG_CONTRIBUTION_TOP]
        if top:
            out[str(gid)] = {slug: round(value, ROUND_DIGITS) for slug, value in top}
    return out


def item_payload(space: ItemFeatureSpace, embeddings: np.ndarray,
                 item_tower: Tower, version: str) -> dict:
    return {
        "version": version,
        "dim": EMB_DIM,
        "tagSlugs": list(MODEL_TAG_SLUGS),
        "items": {
            str(gid): rounded(embeddings[i]) for i, gid in enumerate(space.goods_ids)
        },
        "tagContribution": tag_contribution(space, item_tower),
    }


def user_payload(user_tower: Tower, version: str) -> dict:
    with torch.no_grad():
        w1 = user_tower.fc1.weight.numpy().T      # [in][out] 배치가 계약이다
        b1 = user_tower.fc1.bias.numpy()
        w2 = user_tower.fc2.weight.numpy().T
        b2 = user_tower.fc2.bias.numpy()
    return {
        "version": version,
        "dim": EMB_DIM,
        "inputOrder": {key: list(value) for key, value in INPUT_ORDER.items()},
        "layers": [
            {"w": [rounded(row) for row in w1], "b": rounded(b1), "act": "relu"},
            {"w": [rounded(row) for row in w2], "b": rounded(b2), "act": "none"},
        ],
        "normalize": True,
    }


def validate(payload: dict, schema_name: str) -> list[str]:
    """Wave A 스키마로 검증한다. 어긋난 자리를 사람이 읽을 문장으로 돌려준다."""
    schema = json.loads((SCHEMA_DIR / schema_name).read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema)
    return [
        f"{'/'.join(str(p) for p in error.path) or '<root>'}: {error.message}"
        for error in sorted(validator.iter_errors(payload), key=lambda e: list(e.path))
    ]


def write_json(path: Path, payload: dict) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")


def save_model(out_dir: Path, version: str, items: dict, user: dict, meta: dict) -> dict[str, Path]:
    """세 파일을 쓰고 두 모델 파일을 스키마로 검증한다. 실패하면 전부 지우고 죽는다."""
    target = out_dir / version
    target.mkdir(parents=True, exist_ok=True)
    paths = {
        "item_embeddings": target / "item_embeddings.json",
        "user_tower": target / "user_tower.json",
        "meta": target / "meta.json",
    }
    write_json(paths["item_embeddings"], items)
    write_json(paths["user_tower"], user)
    write_json(paths["meta"], meta)

    problems = (
        [f"item_embeddings.json {line}" for line in validate(items, "item_embeddings.schema.json")]
        + [f"user_tower.json {line}" for line in validate(user, "user_tower.schema.json")]
    )
    if items["version"] != user["version"]:
        problems.append(f"두 파일의 version이 다르다: {items['version']} != {user['version']}")

    if problems:
        for path in paths.values():
            path.unlink(missing_ok=True)
        for line in problems:
            print(f"스키마 위반: {line}")
        raise SystemExit(1)
    return paths


# ---------------------------------------------------------------------------
# 파이프라인
# ---------------------------------------------------------------------------


def run(
    table: pd.DataFrame,
    space: ItemFeatureSpace,
    out_dir: Path,
    *,
    version: str = DEFAULT_VERSION,
    epochs: int = EPOCHS,
) -> dict:
    """학습부터 저장·검증까지 한 번에 돌린다. 테스트도 이 함수를 쓴다."""
    result = train_towers(table, space, epochs=epochs)
    shrunk, means = shrink_embeddings(space, result["embeddings"], result["record_counts"])

    items = item_payload(space, shrunk, result["item_tower"], version)
    user = user_payload(result["user_tower"], version)
    meta = {
        "version": version,
        "dim": EMB_DIM,
        "trainedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "seed": SEED,
        "shrinkK": SHRINK_K,
        "epochs": len(result["train_losses"]),
        "rows": result["rows"],
        "items": len(space.goods_ids),
        "featureDim": space.dim,
        "priceBounds": [round(v, ROUND_DIGITS) for v in space.price_bounds],
        "categories": space.categories,
        "ingredientIds": space.ingredient_ids,
        "overallRating": round(space.overall_rating, ROUND_DIGITS),
        "trainLosses": [round(v, ROUND_DIGITS) for v in result["train_losses"]],
        "holdoutLosses": [
            None if np.isnan(v) else round(v, ROUND_DIGITS) for v in result["holdout_losses"]
        ],
    }
    paths = save_model(out_dir, version, items, user, meta)

    return {
        **result,
        "raw_embeddings": result["embeddings"],
        "group_means": means,
        "shrunk_embeddings": shrunk,
        "item_payload": items,
        "user_payload": user,
        "meta": meta,
        "paths": paths,
    }


def load_table(path: Path) -> pd.DataFrame:
    if not path.exists():
        raise SystemExit(f"학습표가 없다: {path}. build_table.py를 먼저 돌려라.")
    table = pd.read_csv(path, dtype=str, keep_default_na=False)
    table["goods_id"] = table["goods_id"].astype(int)
    table["label"] = table["label"].astype(float)
    table["weight"] = table["weight"].astype(float)
    for column in BEHAVIOR_COLUMNS:
        table[column] = table[column].astype(float)
    return table


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="v3 투 타워를 학습시킨다.")
    parser.add_argument("--train", required=True, type=Path, help="학습표 CSV")
    parser.add_argument("--catalog", required=True, type=Path, help="카탈로그 CSV 디렉터리")
    parser.add_argument("--raw", required=True, type=Path, help="시드 raw CSV 디렉터리")
    parser.add_argument("--out", required=True, type=Path, help="산출 디렉터리")
    parser.add_argument("--version", default=DEFAULT_VERSION, help="모델 버전(기본 v3.1)")
    parser.add_argument("--epochs", default=EPOCHS, type=int, help="최대 에폭 수")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    table = load_table(args.train)
    space = load_item_features(args.catalog, args.raw)
    result = run(table, space, args.out, version=args.version, epochs=args.epochs)

    losses = result["train_losses"]
    print(
        f"{result['rows']}행 / 상품 {len(space.goods_ids)}개 / 입력 {space.dim}칸 / "
        f"{len(losses)}에폭 / 손실 {losses[0]:.4f} → {losses[-1]:.4f}"
    )
    print(f"저장: {result['paths']['item_embeddings'].parent}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
