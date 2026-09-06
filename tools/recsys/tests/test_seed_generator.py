"""시드 생성기 검증.

생성기를 CLI로 두 번 돌려 산출물을 본다. 함수를 직접 부르지 않고 서브프로세스로 도는 이유는
`make all`이 부르는 것과 같은 경로를 재는 것이 목적이기 때문이다. 인자 파싱이나 경로 조립이
깨지면 함수 호출 테스트는 통과하고 파이프라인만 죽는다.

카탈로그는 `tests/fixtures/catalog_mini/`의 작은 3파일을 쓰고 회원 수를 줄여 돌린다.
"""

import csv
import hashlib
import json
import subprocess
import sys
from collections import Counter, defaultdict
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator

RECSYS_ROOT = Path(__file__).resolve().parents[1]
GENERATOR = RECSYS_ROOT / "seed_generator.py"
CATALOG = RECSYS_ROOT / "tests/fixtures/catalog_mini"
PLANTED_SCHEMA_PATH = RECSYS_ROOT / "schema/planted.schema.json"

MEMBERS = 40
RAW_FILES = [
    "members.csv",
    "orders.csv",
    "order_items.csv",
    "reviews.csv",
    "behavior_events.csv",
]

SKIN_TYPE_CONCERNS = {
    "DRY": {"moisture", "barrier"},
    "OILY": {"sebum", "pore"},
    "COMBINATION": {"moisture", "sebum"},
    "SENSITIVE": {"soothe", "gentle"},
}


def run_generator(out: Path) -> Path:
    result = subprocess.run(
        [sys.executable, str(GENERATOR), "--catalog", str(CATALOG),
         "--out", str(out), "--members", str(MEMBERS)],
        capture_output=True,
        text=True,
        check=False,
    )
    assert result.returncode == 0, result.stderr
    return out


def read_rows(out: Path, name: str) -> list[dict]:
    with (out / "raw" / name).open(encoding="utf-8", newline="") as fp:
        return list(csv.DictReader(fp))


def catalog_tags() -> dict[int, set[str]]:
    tags: dict[int, set[str]] = defaultdict(set)
    with (CATALOG / "goods_tag.csv").open(encoding="utf-8", newline="") as fp:
        for row in csv.DictReader(fp):
            tags[int(row["goods_id"])].add(row["tag_slug"])
    return tags


@pytest.fixture(scope="module")
def out(tmp_path_factory) -> Path:
    return run_generator(tmp_path_factory.mktemp("seed"))


@pytest.fixture(scope="module")
def planted(out: Path) -> dict:
    return {entry["id"]: entry for entry in json.loads((out / "planted.json").read_text("utf-8"))}


@pytest.fixture(scope="module")
def planted_members(out: Path) -> dict:
    return json.loads((out / "planted_members.json").read_text("utf-8"))


def test_같은_시드는_같은_파일을_만든다(tmp_path_factory):
    first = run_generator(tmp_path_factory.mktemp("a"))
    second = run_generator(tmp_path_factory.mktemp("b"))
    for name in RAW_FILES:
        digest = [
            hashlib.sha256((base / "raw" / name).read_bytes()).hexdigest()
            for base in (first, second)
        ]
        assert digest[0] == digest[1], name


def test_회원_피부타입이_균등하다(out: Path):
    counts = Counter(row["skin_type"] for row in read_rows(out, "members.csv"))
    assert set(counts) == set(SKIN_TYPE_CONCERNS)
    assert max(counts.values()) - min(counts.values()) <= 1


def test_기본패턴_구매비율이_70퍼센트대다(out: Path, planted_members: dict):
    tags = catalog_tags()
    skin = {int(r["member_id"]): r["skin_type"] for r in read_rows(out, "members.csv")}
    owner = {int(r["order_id"]): int(r["member_id"]) for r in read_rows(out, "orders.csv")}
    noise = set(planted_members["P4"])

    hit = total = 0
    for row in read_rows(out, "order_items.csv"):
        member_id = owner[int(row["order_id"])]
        if member_id in noise:
            continue
        total += 1
        if tags[int(row["goods_id"])] & SKIN_TYPE_CONCERNS[skin[member_id]]:
            hit += 1

    assert total > 0
    assert 0.6 <= hit / total <= 0.8


def test_planted가_스키마를_통과한다(out: Path):
    schema = json.loads(PLANTED_SCHEMA_PATH.read_text("utf-8"))
    Draft202012Validator.check_schema(schema)
    Draft202012Validator(schema).validate(json.loads((out / "planted.json").read_text("utf-8")))


def test_planted_상품_넷이_서로_다르다(planted: dict):
    picked = [
        planted["P1"]["goodsId"],
        planted["P1"]["rivalGoodsId"],
        planted["P2"]["goodsId"],
        planted["P3"]["goodsId"],
    ]
    assert len(set(picked)) == 4


def test_P1은_별점배신을_실제로_넣는다(out: Path, planted: dict):
    skin = {int(r["member_id"]): r["skin_type"] for r in read_rows(out, "members.csv")}
    goods_id = planted["P1"]["goodsId"]
    rival_id = planted["P1"]["rivalGoodsId"]

    low, low_total, high, high_total = 0, 0, 0, 0
    for row in read_rows(out, "reviews.csv"):
        member_id = int(row["member_id"])
        if skin[member_id] != "DRY":
            continue
        rating = int(row["rating"])
        if int(row["goods_id"]) == goods_id:
            low_total += 1
            low += rating <= 2
        if int(row["goods_id"]) == rival_id:
            high_total += 1
            high += rating >= 4

    assert low_total > 0 and high_total > 0
    assert low / low_total >= 0.7
    assert high / high_total >= 0.7


def test_P3_회원의_행동이_프로필과_어긋난다(out: Path, planted_members: dict):
    tags = catalog_tags()
    skin = {int(r["member_id"]): r["skin_type"] for r in read_rows(out, "members.csv")}
    targets = set(planted_members["P3"])
    assert targets

    sebum = total = 0
    for row in read_rows(out, "behavior_events.csv"):
        if int(row["member_id"]) not in targets:
            continue
        total += 1
        sebum += "sebum" in tags[int(row["goods_id"])]

    assert total > 0
    assert sebum / total >= 0.7
    assert all(skin[member_id] == "DRY" for member_id in targets)


def test_seed_sql은_실제_열_이름을_쓴다(out: Path):
    sql = (out / "seed_v3.sql").read_text("utf-8")
    assert "INSERT INTO orders (" in sql
    assert "INSERT INTO order (" not in sql
    assert "INSERT INTO member_profile (member_id, skin_type, concerns, age_band)" in sql


def test_행동로그는_세_종류뿐이다(out: Path):
    kinds = {row["kind"] for row in read_rows(out, "behavior_events.csv")}
    assert kinds == {"VIEW", "WISH", "CART"}
