"""게이트(구현 계획 §4 Task B1-4).

학습된 모델과 학습표·심은 패턴을 읽어 넷을 재고 `reports/gate-<version>.json`을 남긴다.

    python tools/recsys/gate.py --model out/v3.1 --table data/train.csv \
        --planted data/planted.json --out reports

재는 것 넷:

1. holdout Recall@4 (v3): holdout 행의 회원마다 그 상품의 3단계 카테고리(앞 7자)를 단계로 보고,
   그 단계의 후보 30개(인기순 28 + 무작위 2, 시드는 카테고리 + 고정 날짜)를 v3 점수로 정렬한다.
   실제 산 상품이 상위 4개 안에 있으면 적중이다.
2. holdout Recall@4 (v2): 같은 회원·같은 단계에 후보 12개를 v2 점수로 정렬해 같은 방식.
3. P1~P3 포함률: `planted.json`의 `pass` 문장이 정의한 그대로.
4. P4: 잡음 회원을 뺀 학습표로 한 번 더 학습해 P1~P3을 다시 재고, 원래 값과의 차가 5%p 이내인지.

`verdict`가 FAIL이면 종료 코드 1로 죽는다. 리포트는 어느 쪽이든 쓴다.

**holdout 행 중 label=1만 센다.** 별점 1~2로 산 상품은 라벨이 0이다(설계 §7). "샀지만 싫어한
상품"을 상위 4개에 올리는 것은 적중이 아니라 실패이므로 분모에서 뺀다.

**심은 패턴을 잴 때는 대상 상품을 후보 풀에 강제로 넣는다.** P1~P3이 묻는 것은 "그 상품이
상위 4개에 올라오는가"이지 "무작위 2칸에 뽑혔는가"가 아니다. Recall@4에는 이 강제가 없다.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from random import Random

import numpy as np
import pandas as pd

import train
import v2_baseline
from features import CAT3_LENGTH
from train import BEHAVIOR_COLUMNS, DEFAULT_VERSION, user_matrix

# ---------------------------------------------------------------------------
# 계약 (값을 바꾸면 판정 기준이 바뀐다)
# ---------------------------------------------------------------------------

TOP_K = 4                     # Recall@4와 포함률 모두 상위 4개를 본다
V3_POOL_SIZE = 30             # 설계 §3. v3가 넓히려는 대상
V3_POOL_POPULAR = 28          # 인기순 28 + 무작위 2
POOL_DATE = "2026-09-06"      # 풀 난수의 고정 날짜. 같은 카테고리면 항상 같은 풀이 나온다

IMPROVEMENT_MIN = 0.20        # v3가 v2보다 20% 이상 나아야 한다
P1_RIVAL_MIN = 0.7            # planted.json P1 pass 문장
P1_GOODS_MAX = 0.2
P2_SHARE_MIN = 0.6            # planted.json P2 pass 문장
P3_SHARE_MIN = 0.6            # planted.json P3 pass 문장
P4_MAX_DELTA_POINTS = 0.05    # planted.json P4 pass 문장 (5%p)

ROUND_DIGITS = 4
PROFILE_COLUMNS = ["member_id", "skin_type", "concerns", "textures"] + BEHAVIOR_COLUMNS


# ---------------------------------------------------------------------------
# 카탈로그
# ---------------------------------------------------------------------------


@dataclass
class Catalog:
    """후보 풀을 뽑는 데 필요한 만큼만 담는다."""

    cat3: dict[int, str]
    view_count: dict[int, int]
    tags: dict[int, list[str]] = field(default_factory=dict)

    def in_step(self, step: str) -> list[int]:
        return [gid for gid, code in self.cat3.items() if code.startswith(step)]

    def by_popularity(self, candidates: list[int]) -> list[int]:
        """조회수 내림차순. 동점은 id 오름차순으로 갈라 결정적으로 만든다."""
        return sorted(candidates, key=lambda gid: (-self.view_count.get(gid, 0), gid))


def load_catalog(catalog_dir: Path) -> Catalog:
    goods = pd.read_csv(catalog_dir / "goods.csv", dtype=str, keep_default_na=False)
    goods_tag = pd.read_csv(catalog_dir / "goods_tag.csv", dtype=str, keep_default_na=False)

    cat3: dict[int, str] = {}
    view_count: dict[int, int] = {}
    for row in goods.itertuples(index=False):
        gid = int(row.goods_id)
        cat3[gid] = str(row.category_code)[:CAT3_LENGTH]
        view_count[gid] = int(row.view_count or 0)

    tags: dict[int, list[str]] = {gid: [] for gid in cat3}
    for row in goods_tag.itertuples(index=False):
        gid = int(row.goods_id)
        slug = str(row.tag_slug)
        if gid in tags and slug not in tags[gid]:
            tags[gid].append(slug)
    return Catalog(cat3=cat3, view_count=view_count, tags=tags)


def pool_seed(step: str) -> int:
    """카테고리 + 고정 날짜. 같은 단계면 언제 돌려도 같은 무작위 2칸이 나온다."""
    digest = hashlib.sha256(f"{step}|{POOL_DATE}".encode("utf-8")).hexdigest()
    return int(digest[:12], 16)


def candidate_pool(
    catalog: Catalog,
    step: str,
    *,
    size: int = V3_POOL_SIZE,
    popular: int = V3_POOL_POPULAR,
    must_include: tuple[int, ...] = (),
) -> list[int]:
    """단계 후보 풀. 인기순 `popular`개 + 나머지에서 무작위 `size - popular`개.

    반환 순서는 인기순이다. v2의 인기 항이 이 순서를 인덱스로 읽는다.
    """
    ranked = catalog.by_popularity(catalog.in_step(step))
    pool = list(ranked[:popular])
    rest = ranked[popular:]
    extra = size - popular
    if rest and extra > 0:
        pool += Random(pool_seed(step)).sample(rest, min(extra, len(rest)))
    for gid in must_include:
        if gid not in pool and gid in catalog.cat3:
            pool.append(gid)
    return catalog.by_popularity(pool)


# ---------------------------------------------------------------------------
# 모델
# ---------------------------------------------------------------------------


@dataclass
class Model:
    """배포되는 두 파일이 담는 것 전부. 게이트는 이 둘만으로 점수를 낸다."""

    version: str
    dim: int
    items: dict[int, np.ndarray]
    layers: list[dict]
    normalize: bool = True


def load_model(model_dir: Path) -> Model:
    items_raw = json.loads((model_dir / "item_embeddings.json").read_text(encoding="utf-8"))
    user_raw = json.loads((model_dir / "user_tower.json").read_text(encoding="utf-8"))
    return Model(
        version=str(items_raw["version"]),
        dim=int(items_raw["dim"]),
        items={
            int(gid): np.asarray(vec, dtype=np.float64)
            for gid, vec in items_raw["items"].items()
        },
        layers=list(user_raw["layers"]),
        normalize=bool(user_raw.get("normalize", True)),
    )


def model_from_payloads(items: dict, user: dict) -> Model:
    """train.run()이 돌려준 payload 두 개로 모델을 만든다. 파일을 되읽지 않는다."""
    return Model(
        version=str(items["version"]),
        dim=int(items["dim"]),
        items={int(gid): np.asarray(vec, dtype=np.float64) for gid, vec in items["items"].items()},
        layers=list(user["layers"]),
        normalize=bool(user.get("normalize", True)),
    )


def user_embeddings(model: Model, inputs: np.ndarray) -> np.ndarray:
    """34칸 입력 → 임베딩. user_tower.json의 layers를 그대로 따라간다."""
    hidden = np.asarray(inputs, dtype=np.float64)
    for layer in model.layers:
        weight = np.asarray(layer["w"], dtype=np.float64)   # [in][out]
        bias = np.asarray(layer["b"], dtype=np.float64)
        # numpy 2.x의 matmul은 결과가 멀쩡해도 BLAS 경로에서 부동소수 플래그를 올려
        # "divide by zero" 경고를 뿜는다. 경고는 끄고 유한성은 아래에서 직접 확인한다.
        with np.errstate(all="ignore"):
            hidden = hidden @ weight + bias
        if layer.get("act") == "relu":
            hidden = np.maximum(hidden, 0.0)
    if not np.isfinite(hidden).all():
        raise SystemExit("사용자 임베딩에 유한하지 않은 값이 있다. 모델 파일을 의심해라.")
    if model.normalize:
        norms = np.linalg.norm(hidden, axis=-1, keepdims=True)
        norms[norms < 1e-8] = 1.0
        hidden = hidden / norms
    return hidden


def rank_v3(model: Model, user_vec: np.ndarray, candidates: list[int]) -> list[int]:
    """v3 점수(내적) 내림차순. 동점은 인기순 인덱스, 그다음 goods_id."""
    scored = []
    for i, gid in enumerate(candidates):
        embedding = model.items.get(gid)
        if embedding is None:
            continue                       # 학습에 없던 상품은 점수를 낼 수 없다
        scored.append((-float(user_vec @ embedding), i, gid))
    return [gid for _, _, gid in sorted(scored)]


# ---------------------------------------------------------------------------
# 사용자 입력
# ---------------------------------------------------------------------------


def member_profiles(table: pd.DataFrame) -> pd.DataFrame:
    """회원당 한 줄. 프로필과 행동 18칸은 회원 안에서 같은 값이라 첫 줄을 쓴다."""
    profiles = table.drop_duplicates(subset="member_id")[PROFILE_COLUMNS]
    return profiles.reset_index(drop=True)


def profile_records(profiles: pd.DataFrame) -> dict[int, dict]:
    """회원 id → 그 회원의 프로필 한 줄(딕셔너리).

    `itertuples`를 쓰지 않는 이유는 `b_anti-aging`처럼 하이픈이 든 열 이름이 `_12` 같은
    자리 번호로 바뀌어 행동 18칸을 이름으로 못 읽게 되기 때문이다.
    """
    return {int(record["member_id"]): record for record in profiles.to_dict("records")}


def member_vectors(model: Model, profiles: pd.DataFrame) -> dict[int, np.ndarray]:
    matrix = user_embeddings(model, user_matrix(profiles))
    return {
        int(member_id): matrix[i]
        for i, member_id in enumerate(profiles["member_id"].astype(int))
    }


def affinity_of(record: dict) -> dict[str, float]:
    """학습표의 b_* 18칸 → v2가 쓰는 태그별 행동 친화도."""
    return {
        column[2:]: float(record.get(column, 0.0))
        for column in BEHAVIOR_COLUMNS
        if float(record.get(column, 0.0)) > 0
    }


def slugs_of(value: object) -> list[str]:
    text = str(value or "")
    if not text:
        return []
    return [part.strip() for part in text.replace("|", ",").split(",") if part.strip()]


# ---------------------------------------------------------------------------
# 1·2. Recall@4
# ---------------------------------------------------------------------------


def holdout_rows(table: pd.DataFrame) -> pd.DataFrame:
    positive = table["label"].astype(float) >= 1.0
    return table[(table["split"] == "holdout") & positive]


def recall_at_k(
    *,
    table: pd.DataFrame,
    model: Model,
    catalog: Catalog,
    profiles: pd.DataFrame,
    vectors: dict[int, np.ndarray],
    engine: str,
    k: int = TOP_K,
) -> float:
    """holdout 회원·상품마다 상위 k 안에 실제 산 상품이 있는지 센다."""
    by_member = profile_records(profiles)
    hits = 0
    total = 0
    for row in holdout_rows(table).itertuples(index=False):
        member_id = int(row.member_id)
        goods_id = int(row.goods_id)
        step = catalog.cat3.get(goods_id)
        if step is None or member_id not in by_member:
            continue
        profile = by_member[member_id]
        total += 1
        if engine == "v3":
            pool = candidate_pool(catalog, step)
            vector = vectors.get(member_id)
            ordered = rank_v3(model, vector, pool) if vector is not None else []
        else:
            pool = candidate_pool(
                catalog, step, size=v2_baseline.POOL_SIZE, popular=v2_baseline.POOL_SIZE
            )
            concerns = v2_baseline.effective_concerns(
                slugs_of(profile["concerns"]), str(profile["skin_type"])
            )
            ordered = v2_baseline.rank_step(
                candidates=pool,
                tags_of=catalog.tags,
                concerns=concerns,
                textures=v2_baseline.preferred_textures(
                    slugs_of(profile["textures"]) + slugs_of(profile["concerns"])
                ),
                affinity=affinity_of(profile),
            )
        if goods_id in ordered[:k]:
            hits += 1
    return hits / total if total else 0.0


def improvement(recall_v3: float, recall_v2: float) -> float:
    """(v3 - v2) / v2. v2가 0이면 비율이 정의되지 않으니 v3가 잡은 만큼을 1.0으로 적는다."""
    if recall_v2 > 0:
        return (recall_v3 - recall_v2) / recall_v2
    return 0.0 if recall_v3 <= 0 else 1.0


# ---------------------------------------------------------------------------
# 3. 심은 패턴 포함률
# ---------------------------------------------------------------------------


def top4_share(
    *,
    model: Model,
    catalog: Catalog,
    vectors: dict[int, np.ndarray],
    member_ids: list[int],
    step: str,
    goods_id: int,
    k: int = TOP_K,
) -> float:
    """회원들의 상위 k 안에 goods_id가 든 비율."""
    if not member_ids:
        return 0.0
    pool = candidate_pool(catalog, step, must_include=(goods_id,))
    hits = 0
    counted = 0
    for member_id in member_ids:
        vector = vectors.get(member_id)
        if vector is None:
            continue
        counted += 1
        if goods_id in rank_v3(model, vector, pool)[:k]:
            hits += 1
    return hits / counted if counted else 0.0


def entry_of(planted: list[dict], pattern_id: str) -> dict:
    for entry in planted:
        if entry.get("id") == pattern_id:
            return entry
    raise SystemExit(f"planted.json에 {pattern_id}이 없다.")


def measure_p1_p3(
    *,
    model: Model,
    catalog: Catalog,
    profiles: pd.DataFrame,
    vectors: dict[int, np.ndarray],
    planted: list[dict],
    planted_members: dict[str, list[int]],
) -> dict[str, dict[str, float]]:
    """P1~P3 포함률. planted.json의 pass 문장이 정의한 그대로 잰다."""
    p1 = entry_of(planted, "P1")
    p2 = entry_of(planted, "P2")
    p3 = entry_of(planted, "P3")

    records = profile_records(profiles)
    skin = {mid: str(record["skin_type"]) for mid, record in records.items()}
    concerns = {mid: slugs_of(record["concerns"]) for mid, record in records.items()}

    dry_members = [mid for mid, value in skin.items() if value == p1["skinType"]]
    bright_members = [mid for mid, value in concerns.items() if p2["concern"] in value]
    p3_members = [mid for mid in planted_members.get("P3", []) if mid in vectors]

    rival_step = catalog.cat3.get(int(p1["rivalGoodsId"]), "")
    goods_step = catalog.cat3.get(int(p1["goodsId"]), "")
    p3_step = catalog.cat3.get(int(p3["goodsId"]), "")

    return {
        "P1": {
            "rivalShare": top4_share(
                model=model, catalog=catalog, vectors=vectors, member_ids=dry_members,
                step=rival_step, goods_id=int(p1["rivalGoodsId"]),
            ),
            "goodsShare": top4_share(
                model=model, catalog=catalog, vectors=vectors, member_ids=dry_members,
                step=goods_step, goods_id=int(p1["goodsId"]),
            ),
        },
        "P2": {
            "share": top4_share(
                model=model, catalog=catalog, vectors=vectors, member_ids=bright_members,
                step=str(p2["step"]), goods_id=int(p2["goodsId"]),
            ),
        },
        "P3": {
            "share": top4_share(
                model=model, catalog=catalog, vectors=vectors, member_ids=p3_members,
                step=p3_step, goods_id=int(p3["goodsId"]),
            ),
        },
    }


def max_delta_points(before: dict[str, dict[str, float]], after: dict[str, dict[str, float]]) -> float:
    """P1~P3 지표를 하나씩 맞대 가장 큰 차를 돌려준다."""
    worst = 0.0
    for pattern_id, values in before.items():
        for key, value in values.items():
            other = after.get(pattern_id, {}).get(key)
            if other is None:
                continue
            worst = max(worst, abs(float(value) - float(other)))
    return worst


# ---------------------------------------------------------------------------
# 판정과 리포트
# ---------------------------------------------------------------------------


def planted_pass(pattern_id: str, measured: dict[str, float]) -> bool:
    """planted.json의 pass 문장을 코드로 옮긴 것. 조건이 여럿이면 전부 봐야 한다."""
    if pattern_id == "P1":
        return (
            float(measured.get("rivalShare", 0.0)) >= P1_RIVAL_MIN
            and float(measured.get("goodsShare", 1.0)) <= P1_GOODS_MAX
        )
    if pattern_id == "P2":
        return float(measured.get("share", 0.0)) >= P2_SHARE_MIN
    if pattern_id == "P3":
        return float(measured.get("share", 0.0)) >= P3_SHARE_MIN
    if pattern_id == "P4":
        return float(measured.get("maxDeltaPoints", 1.0)) <= P4_MAX_DELTA_POINTS
    raise SystemExit(f"모르는 패턴이다: {pattern_id}")


def decide_verdict(improvement_value: float, entries: list[dict]) -> str:
    """넷 다 pass이고 improvement >= 0.20이면 PASS."""
    if improvement_value < IMPROVEMENT_MIN:
        return "FAIL"
    return "PASS" if all(entry["pass"] for entry in entries) else "FAIL"


def build_report(
    *,
    version: str,
    recall_v3: float,
    recall_v2: float,
    planted: list[dict],
    measured: dict[str, dict[str, float]],
    now: datetime | None = None,
) -> dict:
    stamp = (now or datetime.now(timezone.utc)).astimezone(timezone.utc)
    entries = []
    for entry in planted:
        pattern_id = str(entry["id"])
        values = measured.get(pattern_id, {})
        entries.append({
            "id": pattern_id,
            "pass": planted_pass(pattern_id, values),
            "measured": {key: round(float(v), ROUND_DIGITS) for key, v in values.items()},
        })
    improvement_value = improvement(recall_v3, recall_v2)
    return {
        "version": version,
        "generatedAt": stamp.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "recall4": {
            "v3": round(recall_v3, ROUND_DIGITS),
            "v2": round(recall_v2, ROUND_DIGITS),
            "improvement": round(improvement_value, ROUND_DIGITS),
        },
        "planted": entries,
        "verdict": decide_verdict(improvement_value, entries),
    }


def emit(report: dict, out_dir: Path) -> int:
    """리포트를 쓰고 종료 코드를 돌려준다. FAIL이어도 파일은 남긴다."""
    out_dir.mkdir(parents=True, exist_ok=True)
    path = out_dir / f"gate-{report['version']}.json"
    path.write_text(json.dumps(report, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"리포트: {path}")
    return 0 if report["verdict"] == "PASS" else 1


# ---------------------------------------------------------------------------
# 파이프라인
# ---------------------------------------------------------------------------


def retrain_without_noise(
    *,
    table: pd.DataFrame,
    noise_members: list[int],
    catalog_dir: Path,
    raw_dir: Path,
    version: str,
    out_dir: Path,
) -> Model:
    """잡음 회원을 뺀 학습표로 다시 학습한다(P4). train.py를 함수로 부른다."""
    noise = {int(mid) for mid in noise_members}
    clean = table[~table["member_id"].astype(int).isin(noise)].reset_index(drop=True)
    space = train.load_item_features(catalog_dir, raw_dir)
    result = train.run(clean, space, out_dir, version=f"{version}-nonoise")
    return model_from_payloads(result["item_payload"], result["user_payload"])


def run_gate(
    *,
    model: Model,
    table: pd.DataFrame,
    catalog: Catalog,
    planted: list[dict],
    planted_members: dict[str, list[int]],
    retrain=None,
    now: datetime | None = None,
) -> dict:
    """넷을 다 재고 리포트 딕셔너리를 만든다. 파일은 쓰지 않는다."""
    profiles = member_profiles(table)
    vectors = member_vectors(model, profiles)

    recall_v3 = recall_at_k(
        table=table, model=model, catalog=catalog, profiles=profiles,
        vectors=vectors, engine="v3",
    )
    recall_v2 = recall_at_k(
        table=table, model=model, catalog=catalog, profiles=profiles,
        vectors=vectors, engine="v2",
    )
    measured = measure_p1_p3(
        model=model, catalog=catalog, profiles=profiles, vectors=vectors,
        planted=planted, planted_members=planted_members,
    )

    delta = 1.0
    if retrain is not None:
        clean_model = retrain(planted_members.get("P4", []))
        clean_profiles = member_profiles(table)
        clean_vectors = member_vectors(clean_model, clean_profiles)
        clean_measured = measure_p1_p3(
            model=clean_model, catalog=catalog, profiles=clean_profiles,
            vectors=clean_vectors, planted=planted, planted_members=planted_members,
        )
        delta = max_delta_points(measured, clean_measured)
    measured["P4"] = {"maxDeltaPoints": delta}

    return build_report(
        version=model.version, recall_v3=recall_v3, recall_v2=recall_v2,
        planted=planted, measured=measured, now=now,
    )


def load_planted_members(planted_path: Path) -> dict[str, list[int]]:
    """`planted_members.json`은 planted.json 옆에 있다(스키마가 회원 id를 안 받는다)."""
    path = planted_path.parent / "planted_members.json"
    if not path.exists():
        raise SystemExit(f"회원 목록이 없다: {path}. 시드 생성기를 먼저 돌려라.")
    raw = json.loads(path.read_text(encoding="utf-8"))
    return {key: [int(v) for v in value] for key, value in raw.items()}


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="v3 게이트를 돌린다.")
    parser.add_argument("--model", required=True, type=Path, help="모델 디렉터리(out/<version>)")
    parser.add_argument("--table", required=True, type=Path, help="학습표 CSV")
    parser.add_argument("--planted", required=True, type=Path, help="planted.json 경로")
    parser.add_argument("--out", required=True, type=Path, help="리포트 디렉터리")
    parser.add_argument("--catalog", default=Path("data/catalog"), type=Path,
                        help="카탈로그 CSV 디렉터리(기본 data/catalog)")
    parser.add_argument("--raw", default=Path("data/raw"), type=Path,
                        help="시드 raw CSV 디렉터리(기본 data/raw). P4 재학습에 쓴다")
    parser.add_argument("--skip-p4", action="store_true",
                        help="P4 재학습을 건너뛴다. 리포트의 P4는 실패로 남는다")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    model = load_model(args.model)
    table = train.load_table(args.table)
    catalog = load_catalog(args.catalog)
    planted = json.loads(args.planted.read_text(encoding="utf-8"))
    planted_members = load_planted_members(args.planted)

    retrain = None
    if not args.skip_p4:
        def retrain(noise_members):
            return retrain_without_noise(
                table=table, noise_members=noise_members, catalog_dir=args.catalog,
                raw_dir=args.raw, version=model.version or DEFAULT_VERSION,
                out_dir=args.model.parent,
            )

    report = run_gate(
        model=model, table=table, catalog=catalog, planted=planted,
        planted_members=planted_members, retrain=retrain,
    )
    recall = report["recall4"]
    print(
        f"Recall@4 v3 {recall['v3']} / v2 {recall['v2']} / 개선 {recall['improvement']}"
    )
    for entry in report["planted"]:
        print(f"{entry['id']} {'pass' if entry['pass'] else 'FAIL'} {entry['measured']}")
    sys.stdout.flush()      # FAIL 줄은 stderr로 나가므로 순서가 섞이지 않게 먼저 비운다
    print(
        f"판정: {report['verdict']}",
        file=sys.stderr if report["verdict"] == "FAIL" else sys.stdout,
    )
    return emit(report, args.out)


if __name__ == "__main__":
    raise SystemExit(main())
