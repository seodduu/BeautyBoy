"""모델 파일 픽스처 생성기.

프론트 테스트가 읽는 `frontend/src/features/affinity/fixtures/model-v3.0.json`을 만든다.
손으로 쓰거나 고치지 않는다. 형태를 바꾸려면 이 파일을 고치고 다시 돌린다.

실제 API는 `item_embeddings.json`과 `user_tower.json` 두 파일로 나뉜다. 픽스처만
`{"itemEmbeddings": ..., "userTower": ...}` 하나로 합친다. 프론트에서 import 한 번으로
둘 다 얻기 위해서다. 각 값은 실제 파일의 최상위 객체와 그대로 같으므로, JSON Schema 검증은
합친 객체가 아니라 두 값 각각에 건다.

시드는 `random.Random(0)`으로 고정한다. 두 번 돌려 바이트가 달라지면 재현성이 깨진 것이다.
"""

import argparse
import json
import random
from pathlib import Path

# tag.sort_order 순. TS의 MODEL_TAG_SLUGS, item_embeddings.schema.json의 tagSlugs와 같은 순서다.
TAG_SLUGS = [
    "cleanse", "exfoliate", "sebum", "soothe", "moisture", "uv", "bright", "firm", "anti-aging", "scalp",
    "pore", "trouble", "barrier", "antioxidant",
    "fresh", "dewy", "matte",
    "gentle",
]
SKIN_TYPES = ["DRY", "OILY", "COMBINATION", "SENSITIVE"]
CONCERNS = ["exfoliate", "sebum", "pore", "trouble", "soothe", "moisture", "barrier", "bright", "anti-aging"]
TEXTURES = ["fresh", "dewy", "matte"]

USER_INPUT_DIM = len(SKIN_TYPES) + len(CONCERNS) + len(TEXTURES) + len(TAG_SLUGS)  # 34
HIDDEN_DIM = 48
EMB_DIM = 32

VERSION = "v3.0"
# 학습 시각도 고정한다. 지금 시각을 쓰면 돌릴 때마다 파일이 달라져 재현성 확인이 무의미해진다.
TRAINED_AT = "2026-09-04T00:00:00Z"
# V12 시드 상품 3개.
GOODS_NOS = [1, 2, 4]

REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_OUT = REPO_ROOT / "frontend/src/features/affinity/fixtures/model-v3.0.json"


def _weight(rng: random.Random) -> float:
    """가중치 하나. 소수 6자리로 자른다. 부동소수 표기가 흔들리면 바이트 비교가 깨진다."""
    return round(rng.uniform(-0.1, 0.1), 6)


def _vector(rng: random.Random, size: int) -> list[float]:
    return [_weight(rng) for _ in range(size)]


def _matrix(rng: random.Random, rows: int, cols: int) -> list[list[float]]:
    return [_vector(rng, cols) for _ in range(rows)]


def build(rng: random.Random) -> dict:
    item_embeddings = {
        "version": VERSION,
        "dim": EMB_DIM,
        "trainedAt": TRAINED_AT,
        "tagSlugs": TAG_SLUGS,
        "items": {str(goods_no): _vector(rng, EMB_DIM) for goods_no in GOODS_NOS},
        "tagContribution": {
            str(goods_no): {"moisture": _weight(rng), "soothe": _weight(rng)}
            for goods_no in GOODS_NOS
        },
    }
    user_tower = {
        "version": VERSION,
        "dim": EMB_DIM,
        "inputOrder": {
            "skinType": SKIN_TYPES,
            "concerns": CONCERNS,
            "textures": TEXTURES,
            "behaviorTags": TAG_SLUGS,
        },
        "layers": [
            {"w": _matrix(rng, USER_INPUT_DIM, HIDDEN_DIM), "b": _vector(rng, HIDDEN_DIM), "act": "relu"},
            {"w": _matrix(rng, HIDDEN_DIM, EMB_DIM), "b": _vector(rng, EMB_DIM), "act": "none"},
        ],
        "normalize": True,
    }
    return {"itemEmbeddings": item_embeddings, "userTower": user_tower}


def main() -> None:
    parser = argparse.ArgumentParser(description="모델 파일 픽스처를 생성한다")
    parser.add_argument("out", nargs="?", default=str(DEFAULT_OUT), help="출력 경로")
    args = parser.parse_args()

    fixture = build(random.Random(0))
    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(fixture, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {out_path}")


if __name__ == "__main__":
    main()
