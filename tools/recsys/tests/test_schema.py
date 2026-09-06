"""모델 파일 JSON Schema 검증.

픽스처(make_fixture.py 산출물)가 스키마를 통과하는지 보고, 스키마가 실제로 무언가를
막는지도 본다. 통과만 확인하면 전부 허용하는 스키마도 녹색이 된다.
"""

import copy
import json
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator, ValidationError

REPO_ROOT = Path(__file__).resolve().parents[3]
SCHEMA_DIR = REPO_ROOT / "tools/recsys/schema"
FIXTURE_PATH = REPO_ROOT / "frontend/src/features/affinity/fixtures/model-v3.0.json"


def _load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


@pytest.fixture(scope="module")
def fixture() -> dict:
    return _load(FIXTURE_PATH)


@pytest.fixture(scope="module")
def item_schema() -> dict:
    return _load(SCHEMA_DIR / "item_embeddings.schema.json")


@pytest.fixture(scope="module")
def tower_schema() -> dict:
    return _load(SCHEMA_DIR / "user_tower.schema.json")


def test_fixture_item_embeddings_passes_schema(fixture, item_schema):
    Draft202012Validator(item_schema).validate(fixture["itemEmbeddings"])


def test_fixture_user_tower_passes_schema(fixture, tower_schema):
    Draft202012Validator(tower_schema).validate(fixture["userTower"])


def test_wrong_layer_shape_fails(fixture, tower_schema):
    broken = copy.deepcopy(fixture["userTower"])
    del broken["layers"][0]["w"][0]          # 34행 → 33행

    with pytest.raises(ValidationError):
        Draft202012Validator(tower_schema).validate(broken)


def test_tag_order_mismatch_fails(fixture, item_schema):
    broken = copy.deepcopy(fixture["itemEmbeddings"])
    broken["tagSlugs"][0], broken["tagSlugs"][1] = broken["tagSlugs"][1], broken["tagSlugs"][0]

    with pytest.raises(ValidationError):
        Draft202012Validator(item_schema).validate(broken)
