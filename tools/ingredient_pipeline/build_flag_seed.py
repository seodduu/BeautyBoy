#!/usr/bin/env python3
"""식약처 공공데이터 API로부터 성분 규제 플래그 시드(V61)를 생성한다.

설계: 화장품_성분_주의도_점수_설계.md §0. 참조 테이블: V60__ingredient_reg_flag.sql.

무엇을 만드나
  - 식약처 사용제한 원료정보(한국) → BANNED / LIMIT 플래그(+배합한도 원문)
  - 착향제 알레르기 유발물질 25종(고시 별표2) → ALLERGEN 플래그(한글명·CAS는 원료성분 API로 보강)
  - 내부 각질제거 산 목록 → EXFOLIANT_ACID 플래그
  결과: backend/src/main/resources/db/migration/V61__seed_ingredient_reg_flag.sql

왜 스크립트인가
  API는 필터가 없어 전량 페이징 수집(사용제한 63p·원료 44p)해야 하고, 국가 다중 규제라 '한국'만 골라야 한다.
  이 판단을 SQL에 손으로 박으면 재현이 안 되므로 생성 과정을 코드로 고정한다.

실행
  COS_INFO=... python3 tools/ingredient_pipeline/build_flag_seed.py
  (.env의 COS_INFO를 자동으로 읽는다. 원본 JSON은 .cache/에 캐시하며 gitignore 대상이다.)
"""
from __future__ import annotations
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / ".cache"
OUT = ROOT / "backend/src/main/resources/db/migration/V61__seed_ingredient_reg_flag.sql"

RESTRICT_EP = "https://apis.data.go.kr/1471000/CsmtcsUseRstrcInfoService/getCsmtcsUseRstrcInfoService"
INGR_EP = "https://apis.data.go.kr/1471000/CsmtcsIngdCpntInfoService01/getCsmtcsIngdCpntInfoService01"

# 착향제 알레르기 유발물질 25종 — 식약처 「화장품 안전기준 등에 관한 규정」 별표2(2020.1 표시의무 시행).
# 사용제한 API의 한국 데이터에 없으므로(표시지정) 여기에 고정한다. 한글명·CAS는 원료성분 API로 보강한다.
ALLERGEN_25 = [
    "amyl cinnamal", "amylcinnamyl alcohol", "benzyl alcohol", "benzyl salicylate",
    "cinnamal", "cinnamyl alcohol", "citral", "coumarin", "eugenol", "geraniol",
    "hydroxycitronellal", "hydroxyisohexyl 3-cyclohexene carboxaldehyde", "isoeugenol",
    "anise alcohol", "benzyl benzoate", "benzyl cinnamate", "citronellol",
    "farnesol", "hexyl cinnamal", "limonene", "linalool", "methyl 2-octynoate",
    "alpha-isomethyl ionone", "evernia prunastri", "evernia furfuracea",
]
ALLERGEN_KR_MANUAL = {  # 원료성분 API가 학명 표기라 못 잡는 2종만 수기 보강
    "evernia prunastri": ("오크모스추출물", "90028-68-5"),
    "evernia furfuracea": ("트리모스추출물", "90028-67-4"),
    "hydroxyisohexyl 3-cyclohexene carboxaldehyde": ("하이드록시아이소헥실3-사이클로헥센카복스알데하이드", "31906-04-4"),
}

# 내부 각질제거 산 목록 — AHA/BHA. 사용감·자극과 직결돼 종합판정 축에 포함(설계 §0.3).
EXFOLIANT_ACIDS = {
    "salicylic acid": "BHA", "glycolic acid": "AHA", "lactic acid": "AHA",
    "mandelic acid": "AHA", "malic acid": "AHA", "tartaric acid": "AHA",
}


def norm(s: str | None) -> str:
    return re.sub(r"\s+", " ", (s or "").strip().lower())


def load_env_key() -> str:
    key = os.environ.get("COS_INFO")
    if key:
        return key
    env = ROOT / ".env"
    if env.exists():
        for line in env.read_text().splitlines():
            if line.startswith("COS_INFO="):
                return line.split("=", 1)[1].strip()
    sys.exit("COS_INFO(공공데이터 인증키)를 찾지 못했습니다. .env 또는 환경변수에 설정하세요.")


def fetch_all(endpoint: str, tag: str, key: str, total_hint: int) -> list[dict]:
    """전량 페이징 수집(numOfRows=500). 페이지별로 .cache에 저장해 재실행 시 재요청하지 않는다."""
    CACHE.mkdir(exist_ok=True)
    pages = (total_hint + 499) // 500 + 2  # 여유
    rows: list[dict] = []
    for p in range(1, pages + 1):
        cf = CACHE / f"{tag}_p{p}.json"
        if cf.exists() and cf.stat().st_size > 0:
            body = json.loads(cf.read_text())["body"]
        else:
            qs = urllib.parse.urlencode(
                {"serviceKey": key, "pageNo": p, "numOfRows": 500, "type": "json"}
            )
            with urllib.request.urlopen(f"{endpoint}?{qs}", timeout=60) as r:
                raw = r.read().decode("utf-8")
            cf.write_text(raw)
            body = json.loads(raw)["body"]
            time.sleep(0.1)
        items = body.get("items") or []
        rows += items
        if not items or len(rows) >= body.get("totalCount", 0):
            break
    return rows


def sql_str(v: str | None) -> str:
    if v is None:
        return "NULL"
    return "'" + v.replace("\\", "\\\\").replace("'", "''").replace("\n", " ").strip() + "'"


def main() -> None:
    key = load_env_key()
    print("· 사용제한 원료정보 수집…")
    restrict = fetch_all(RESTRICT_EP, "restrict", key, 31191)
    print("· 원료성분정보(마스터) 수집…")
    master = fetch_all(INGR_EP, "ingr", key, 21833)
    print(f"  사용제한 {len(restrict)}행 / 마스터 {len(master)}행")

    # INCI → (한글, CAS) 사전. water는 정제수 우선.
    kor: dict[str, tuple[str, str | None]] = {}
    for x in master:
        e = norm(x.get("INGR_ENG_NAME"))
        if not e:
            continue
        if e == "water" and x.get("INGR_KOR_NAME") != "정제수":
            continue
        kor.setdefault(e, (x.get("INGR_KOR_NAME"), x.get("CAS_NO")))

    # 한국 사용제한만 추출.
    kr = [x for x in restrict if x.get("COUNTRY_NAME") == "한국" and x.get("INGR_ENG_NAME")]

    # (inci, flag_type) 중복 제거하며 행 구성.
    rows: list[tuple] = []
    seen: set[tuple[str, str]] = set()

    MAX_INCI = 255  # 조인 키 상한(V60 컬럼 폭). 초과분은 금지 폴리머의 IUPAC 서술명뿐이라 전성분 조인에 무의미.
    skipped = {"count": 0}

    def add(inci: str, flag: str, source: str, kr_name, cas, ref):
        if len(inci) > MAX_INCI:
            skipped["count"] += 1
            return
        k = (inci, flag)
        if k in seen:
            return
        seen.add(k)
        rows.append((inci, kr_name, cas, flag, source, ref))

    for x in kr:
        inci = norm(x["INGR_ENG_NAME"])
        rt = x.get("REGULATE_TYPE") or ""
        kn, cas = x.get("INGR_STD_NAME"), x.get("CAS_NO")
        if "금지" in rt:
            add(inci, "BANNED", "MFDS_RESTRICT", kn, cas, None)
        if "한도" in rt:
            add(inci, "LIMIT", "MFDS_RESTRICT", kn, cas, x.get("LIMIT_COND"))

    for inci in ALLERGEN_25:
        if inci in ALLERGEN_KR_MANUAL:
            kn, cas = ALLERGEN_KR_MANUAL[inci]
        else:
            kn, cas = kor.get(inci, (None, None))
        add(inci, "ALLERGEN", "MFDS_ALLERGEN_25", kn, cas,
            "식약처 「화장품 안전기준 등에 관한 규정」 별표2 착향제 알레르기 유발물질 25종")

    for inci, cls in EXFOLIANT_ACIDS.items():
        kn, cas = kor.get(inci, (None, None))
        add(inci, "EXFOLIANT_ACID", "INTERNAL_ACID", kn, cas, cls)

    # 통계
    from collections import Counter
    stat = Counter(r[3] for r in rows)
    print(f"· 생성 플래그: 총 {len(rows)}  {dict(stat)}  (조인 무의미 서술명 {skipped['count']}건 제외)")

    # SQL 출력
    lines = [
        "-- V61__seed_ingredient_reg_flag.sql — 성분 규제/주의 플래그 시드.",
        "-- 자동 생성: tools/ingredient_pipeline/build_flag_seed.py (손으로 수정하지 말 것).",
        f"-- 출처: 식약처 사용제한 원료정보(한국) + 착향제 25종 고시 + 내부 각질산. 행수 {len(rows)}.",
        "",
        "INSERT INTO ingredient_reg_flag (inci_name, kr_name, cas_no, flag_type, source, source_ref) VALUES",
    ]
    vals = [
        f"({sql_str(inci)}, {sql_str(kn)}, {sql_str(cas)}, {sql_str(ft)}, {sql_str(src)}, {sql_str(ref)})"
        for (inci, kn, cas, ft, src, ref) in rows
    ]
    lines.append(",\n".join(vals) + ";")
    OUT.write_text("\n".join(lines) + "\n")
    print(f"· 기록: {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
