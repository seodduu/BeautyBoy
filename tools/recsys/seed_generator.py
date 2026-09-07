"""v3 학습용 시드 생성기.

카탈로그 CSV(data/catalog)만 읽어 학습표 입력이 될 raw CSV 다섯 개, 개발 DB 적재용
seed_v3.sql, 게이트 계약인 planted.json을 만든다. 실행 중 DB에 붙지 않는다(구현 계획 §3.1).

    python tools/recsys/seed_generator.py --catalog data/catalog --out data

난수원은 `random.Random(42)` 하나뿐이다. 재현성이 게이트의 전제라서, 다른 난수원을 섞으면
같은 입력에서 다른 학습표가 나오고 게이트 판정의 근거가 사라진다. 시각도 마찬가지 이유로
"지금"이 아니라 "오늘 자정"을 기준으로 잡는다. 같은 날 두 번 돌리면 같은 파일이 나온다.

심은 패턴 P1~P4는 설계 §9와 로드맵 §3.5를 그대로 옮긴 것이다. 상품 id는 하드코딩하지 않고
카탈로그에서 규칙대로 고른다. 카탈로그가 바뀌면 고른 상품도 같이 바뀐다.
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from collections import Counter, defaultdict
from datetime import datetime, timedelta
from pathlib import Path
from random import Random

# ---------------------------------------------------------------------------
# 상수. frontend/src/features/affinity/profile.ts와 types/model.ts가 원본이다.
# 순서가 어긋나면 계산은 조용히 성공하고 추천만 틀린다.
# ---------------------------------------------------------------------------

SKIN_TYPES = ["DRY", "OILY", "COMBINATION", "SENSITIVE"]

SKIN_TYPE_CONCERNS: dict[str, list[str]] = {
    "DRY": ["moisture", "barrier"],
    "OILY": ["sebum", "pore"],
    "COMBINATION": ["moisture", "sebum"],
    "SENSITIVE": ["soothe", "gentle"],
}

CONCERN_SLUGS = [
    "exfoliate", "sebum", "pore", "trouble", "soothe",
    "moisture", "barrier", "bright", "anti-aging",
]
TEXTURE_SLUGS = ["fresh", "dewy", "matte"]
AGE_BANDS = ["10s", "20s", "30s", "40s", "50s+"]
BEHAVIOR_KINDS = ["VIEW", "WISH", "CART"]
BEHAVIOR_KIND_WEIGHTS = [0.78, 0.10, 0.12]

SEED = 42
CAT3_LENGTH = 7                # category_code 앞 7자가 3단계(leaf) 코드다. features.py와 같은 값
MEMBER_ID_BASE = 1000        # V64 시드 회원 1~3과 겹치지 않게 한다
WINDOW_DAYS = 180              # 전부 오늘로부터 과거 180일 안
ORDERS_PER_MEMBER = 10         # 600명 → 6,000건
BEHAVIOR_PER_MEMBER = 100      # 600명 → 60,000건
REVIEW_SHARE_OF_ORDERS = 0.6   # 6,000건의 60% → 3,600건
CONCERN_PICK_RATE = 0.7        # 기본 패턴: 고민 태그 상품을 70% 확률로 고른다
NOISE_SHARE = 0.15             # P4
P3_SHARE = 0.25                # P3. DRY 회원 풀 안에서 고른다
P2_BUYER_SHARE = 0.4           # P2
P1_LOW_RATING_SHARE = 0.8      # P1
P3_BEHAVIOR_SHARE = 0.8        # P3 행동 로그 중 sebum 태그 비율

# 일반 리뷰 별점 분포. 커머스 리뷰는 5점에 몰린다.
RATING_CHOICES = [5, 4, 3, 2, 1]
RATING_WEIGHTS = [0.45, 0.30, 0.15, 0.07, 0.03]

# P2의 C는 pass 문장이 C001001(토너/스킨)로 못 박아 두어 단계를 옮길 수 없다. 그래서
# 같은 단계로 겹치는 쪽은 P3의 D가 양보한다. 토너 안에서 먼저 찾고, 없으면 스킨케어 대분류,
# 그래도 없으면 전체로 넓힌다. D의 성격은 "피지 태그를 단 앞단계 스킨케어"라서 이 순서가
# 성격을 가장 덜 흔든다.
P3_CATEGORY_LADDER = ["C001001", "C001", ""]

PASS_TEXT = {
    "P1": "DRY 사용자 상위4에 rivalGoodsId 포함률 ≥ 0.7 AND goodsId 포함률 ≤ 0.2",
    "P2": "bright 사용자의 C001001 단계 상위4에 goodsId 포함률 ≥ 0.6",
    "P3": "해당 회원 상위4에 goodsId 포함률 ≥ 0.6",
    "P4": "P1~P3 각 지표가 잡음 0% 학습 대비 5%p 이내",
}


# ---------------------------------------------------------------------------
# 카탈로그
# ---------------------------------------------------------------------------


class Catalog:
    """상품 id → 카테고리·가격·조회수·태그. 이 클래스만 CSV 열 이름을 안다."""

    def __init__(self, directory: Path) -> None:
        self.category: dict[int, str] = {}
        self.brand: dict[int, int] = {}
        self.price: dict[int, int] = {}
        self.status: dict[int, str] = {}
        self.view_count: dict[int, int] = {}
        self.tags: dict[int, set[str]] = defaultdict(set)

        with (directory / "goods.csv").open(encoding="utf-8", newline="") as fp:
            for row in csv.DictReader(fp):
                gid = int(row["goods_id"])
                self.category[gid] = row["category_code"]
                self.brand[gid] = int(row["brand_id"])
                self.price[gid] = int(row["sale_price"])
                self.status[gid] = row["status"]
                self.view_count[gid] = int(row["view_count"])

        with (directory / "goods_tag.csv").open(encoding="utf-8", newline="") as fp:
            for row in csv.DictReader(fp):
                gid = int(row["goods_id"])
                if gid in self.category:
                    self.tags[gid].add(row["tag_slug"])

        if not self.category:
            raise SystemExit("카탈로그가 비었다. export_catalog.py를 먼저 돌려라.")

        self.ids: list[int] = sorted(self.category)
        self.weights: list[int] = [max(1, self.view_count[gid]) for gid in self.ids]

    def with_any_tag(self, slugs: list[str]) -> list[int]:
        wanted = set(slugs)
        return [gid for gid in self.ids if self.tags[gid] & wanted]

    def by_popularity(self, candidates: list[int]) -> list[int]:
        """조회수 내림차순. 동점은 id 오름차순으로 갈라 결정적으로 만든다."""
        return sorted(candidates, key=lambda gid: (-self.view_count[gid], gid))


def pick_p1_pair(catalog: Catalog, blocked: set[str]) -> tuple[int, int]:
    """P1의 배신 쌍 A·B를 같은 cat3(leaf) 안에서 고른다.

    게이트는 상품이 속한 cat3의 후보 풀 안에서 상위 4를 잰다. A와 B가 다른 leaf에 있으면
    둘은 각자 다른 풀에서 재어지고 한 번도 맞대결하지 않는다. 그러면 "같은 태그인데 별점만
    다른 두 상품 중 모델이 어느 쪽을 올리는가"라는 P1의 질문 자체가 성립하지 않는다.
    그래서 moisture 상품을 cat3로 묶고 같은 묶음 안에서 조회수 1·2위를 쓴다.

    `blocked`는 P2·P3이 이미 쓰는 cat3다. 같은 풀에 P2·P3이 심은 강한 신호가 있으면
    그쪽이 B를 밀어내 P1이 또 측정 불능이 된다.
    """
    groups: dict[str, list[int]] = defaultdict(list)
    for gid in catalog.with_any_tag(["moisture"]):
        groups[catalog.category[gid][:CAT3_LENGTH]].append(gid)

    # moisture 상품이 가장 많은 cat3를 쓴다. 동률은 cat3 오름차순으로 갈라 결정적으로 만든다.
    candidates = sorted(
        ((code, gids) for code, gids in groups.items()
         if len(gids) >= 2 and code not in blocked),
        key=lambda item: (-len(item[1]), item[0]),
    )
    if not candidates:
        raise SystemExit(
            "moisture 상품 2개 이상을 가진 cat3가 없다(P2·P3 단계 제외). P1을 심을 수 없다."
        )
    ranked = catalog.by_popularity(candidates[0][1])
    return ranked[0], ranked[1]


def pick_p3_goods(catalog: Catalog, blocked: set[str]) -> int:
    """P3의 상품 D를 고른다. sebum 태그를 달고, `blocked` cat3 밖에 있어야 한다.

    D는 DRY 회원 다수가 별점 5.0을 다는 강한 신호다. 같은 cat3에 P2의 C가 함께 있으면
    게이트가 그 단계의 상위 4를 잴 때 D가 C를 그대로 눌러 P2 측정이 0이 된다. P1에서 본
    것과 같은 구조다. C의 단계는 pass 문장에 C001001로 박혀 있어 못 옮기므로 D가 비킨다.

    카테고리 사다리를 위에서부터 내려가며 처음으로 후보가 나오는 칸을 쓴다. 정렬은
    조회수 내림차순, 동률은 goods_id 오름차순이라 선택이 결정적이다.
    """
    sebum = catalog.with_any_tag(["sebum"])
    for prefix in P3_CATEGORY_LADDER:
        candidates = [
            gid for gid in sebum
            if catalog.category[gid].startswith(prefix)
            and catalog.category[gid][:CAT3_LENGTH] not in blocked
        ]
        if candidates:
            return catalog.by_popularity(candidates)[0]
    raise SystemExit(
        f"sebum 태그 상품이 {sorted(blocked)} 밖에 없다. P3을 P2와 갈라 심을 수 없다."
    )


def pick_planted_goods(catalog: Catalog) -> dict[str, int]:
    """P1~P4가 쓸 상품 A·B·C·D를 카탈로그에서 고른다(구현 계획 §4 Task B1-1).

    로드맵의 #88·#91·#217·#140은 아티팩트 작성 시점의 예시 번호라 실제 카탈로그와
    일치한다는 보장이 없다. 그래서 번호를 적지 않고 규칙만 적는다.

    고르는 순서가 곧 우선순위다. 단계를 못 옮기는 C가 먼저, 그다음 C를 피해 D, 마지막으로
    둘 다 피해 P1 쌍이다. 순서가 반대면 나중에 고른 쪽이 앞서 고른 쪽의 단계로 들어와
    심은 신호를 지운다.
    """
    toners = catalog.by_popularity(
        [gid for gid in catalog.ids if catalog.category[gid].startswith("C001001")]
    )
    if not toners:
        raise SystemExit("C001001 카테고리 상품이 없다. P2를 심을 수 없다.")
    if len(toners) < 18:
        print(
            f"경고: C001001 상품이 {len(toners)}개뿐이라 P2는 18위 대신 마지막 상품을 쓴다.",
            file=sys.stderr,
        )
        c = toners[-1]
    else:
        c = toners[17]

    blocked = {catalog.category[c][:CAT3_LENGTH]}
    d = pick_p3_goods(catalog, blocked)

    blocked.add(catalog.category[d][:CAT3_LENGTH])
    a, b = pick_p1_pair(catalog, blocked)

    picks = {"A": a, "B": b, "C": c, "D": d}
    if len(set(picks.values())) != 4:
        raise SystemExit(f"심은 패턴 상품이 겹쳤다: {picks}. 패턴이 서로를 지운다.")

    steps = {key: catalog.category[gid][:CAT3_LENGTH] for key, gid in picks.items()}
    if steps["A"] != steps["B"]:
        raise SystemExit(f"P1 쌍이 서로 다른 단계에 있다: {steps}. 맞대결이 성립하지 않는다.")
    # P1 쌍이 차지한 단계 하나, P2의 단계 하나, P3의 단계 하나가 셋 다 달라야 한다.
    # 두 패턴이 한 단계에 들어가면 강한 쪽이 약한 쪽을 눌러 약한 쪽 측정이 0이 된다.
    if len({steps["A"], steps["C"], steps["D"]}) != 3:
        raise SystemExit(f"P1·P2·P3 단계가 겹친다: {steps}. 심은 신호가 서로를 지운다.")
    return picks


# ---------------------------------------------------------------------------
# 회원
# ---------------------------------------------------------------------------


class Member:
    __slots__ = ("member_id", "skin_type", "concerns", "textures", "age_band")

    def __init__(self, member_id: int, skin_type: str, concerns: list[str],
                 textures: list[str], age_band: str) -> None:
        self.member_id = member_id
        self.skin_type = skin_type
        self.concerns = concerns
        self.textures = textures
        self.age_band = age_band


def build_members(rng: Random, count: int) -> list[Member]:
    """피부타입 4종을 균등하게 나눠 회원을 만든다.

    고민은 피부타입 파생에서 1~2개 + 9종에서 0~1개다. 프로필이 단순해야 P3(프로필과
    행동이 어긋나는 회원)이 실제로 어긋난 상태가 된다.
    """
    members: list[Member] = []
    for index in range(count):
        skin_type = SKIN_TYPES[index % len(SKIN_TYPES)]
        derived = SKIN_TYPE_CONCERNS[skin_type]
        concerns = rng.sample(derived, rng.randint(1, len(derived)))
        extra = rng.sample(CONCERN_SLUGS, 1) if rng.random() < 0.5 else []
        for slug in extra:
            if slug not in concerns:
                concerns.append(slug)
        textures = rng.sample(TEXTURE_SLUGS, 1) if rng.random() < 0.5 else []
        members.append(
            Member(MEMBER_ID_BASE + index, skin_type, concerns, textures,
                   rng.choice(AGE_BANDS))
        )
    return members


# ---------------------------------------------------------------------------
# 생성
# ---------------------------------------------------------------------------


class Seed:
    def __init__(self, catalog: Catalog, member_count: int, now: datetime) -> None:
        self.rng = Random(SEED)
        self.catalog = catalog
        self.now = now
        self.picks = pick_planted_goods(catalog)

        self.members = build_members(self.rng, member_count)
        self.by_id = {m.member_id: m for m in self.members}

        # 피부타입별 고민 태그 상품 풀. 기본 패턴 70%가 여기서 뽑는다.
        self.concern_pool: dict[str, list[int]] = {}
        for skin_type, slugs in SKIN_TYPE_CONCERNS.items():
            pool = catalog.with_any_tag(slugs)
            self.concern_pool[skin_type] = pool or catalog.ids
        self.sebum_pool = catalog.with_any_tag(["sebum"]) or catalog.ids

        self._assign_patterns()

        self.orders: list[tuple[int, int, datetime]] = []
        self.order_items: list[tuple[int, int, int]] = []
        self.reviews: dict[tuple[int, int], tuple[int, datetime]] = {}
        self.behavior: list[tuple[int, int, str, datetime]] = []

    # -- 패턴 대상 회원 --------------------------------------------------

    def _assign_patterns(self) -> None:
        """P3(행동이 프로필을 이기는 회원)과 P4(잡음 회원)를 고른다.

        P3은 DRY 회원 안에서만 고른다. 프로필이 DRY인데 행동이 sebum이라는 것이
        패턴의 내용이라, 다른 피부타입에서 뽑으면 패턴 자체가 성립하지 않는다.
        P4는 P3과 겹치지 않게 나머지에서 고른다. 겹치면 잡음이 P3을 지운다.
        """
        dry = [m.member_id for m in self.members if m.skin_type == "DRY"]
        p3_count = min(round(len(self.members) * P3_SHARE), len(dry))
        self.p3_members = sorted(self.rng.sample(dry, p3_count))
        p3_set = set(self.p3_members)

        rest = [m.member_id for m in self.members if m.member_id not in p3_set]
        noise_count = min(round(len(self.members) * NOISE_SHARE), len(rest))
        self.noise_members = sorted(self.rng.sample(rest, noise_count))
        self.noise_set = set(self.noise_members)

        bright = [m.member_id for m in self.members if "bright" in m.concerns]
        p2_count = round(len(bright) * P2_BUYER_SHARE)
        self.p2_members = sorted(self.rng.sample(bright, p2_count)) if bright else []

    # -- 시각 -------------------------------------------------------------

    def _moment(self) -> datetime:
        """과거 180일 안 균등. 최근 14일이 자연히 전체의 약 8%(14/180)가 된다."""
        return self.now - timedelta(
            days=self.rng.randrange(WINDOW_DAYS), seconds=self.rng.randrange(86400)
        )

    # -- 상품 고르기 -------------------------------------------------------

    def _pick_goods(self, member: Member) -> int:
        """기본 패턴. 고민 태그 상품을 70%, 나머지 30%는 전체에서 무작위.

        100%로 하면 규칙표와 같아져 v2와 v3의 차이가 0이 된다(설계 §8 "답안지를 보고
        낸 시험"). 30%의 이탈이 모델이 배울 여지를 만든다.
        """
        if member.member_id in self.noise_set:
            return self.rng.choice(self.catalog.ids)
        if self.rng.random() < CONCERN_PICK_RATE:
            return self.rng.choice(self.concern_pool[member.skin_type])
        return self.rng.choices(self.catalog.ids, weights=self.catalog.weights, k=1)[0]

    # -- 주문 -------------------------------------------------------------

    def build_orders(self) -> None:
        total = len(self.members) * ORDERS_PER_MEMBER
        member_ids = [m.member_id for m in self.members]
        per_member: dict[int, list[int]] = defaultdict(list)

        for order_id in range(1, total + 1):
            member_id = self.rng.choice(member_ids)
            member = self.by_id[member_id]
            ordered_at = self._moment()
            self.orders.append((order_id, member_id, ordered_at))
            per_member[member_id].append(order_id)
            for _ in range(self.rng.randint(1, 3)):
                self.order_items.append(
                    (order_id, self._pick_goods(member), self.rng.randint(1, 2))
                )

        self.orders_by_member = per_member
        self.order_at = {oid: at for oid, _, at in self.orders}

        # 심은 구매. P2는 bright 회원의 40%가 C를, P3은 대상 회원 전원이 D를 산다.
        for member_id in self.p2_members:
            self._plant_purchase(member_id, self.picks["C"])
        for member_id in self.p3_members:
            self._plant_purchase(member_id, self.picks["D"])

        self.order_items.sort(key=lambda row: (row[0], row[1]))

    def _plant_purchase(self, member_id: int, goods_id: int) -> None:
        orders = self.orders_by_member.get(member_id)
        if not orders:
            order_id = len(self.orders) + 1
            ordered_at = self._moment()
            self.orders.append((order_id, member_id, ordered_at))
            self.order_at[order_id] = ordered_at
            self.orders_by_member[member_id] = [order_id]
            orders = [order_id]
        order_id = orders[self.rng.randrange(len(orders))]
        self.order_items.append((order_id, goods_id, 1))

    # -- 리뷰 -------------------------------------------------------------

    def build_reviews(self) -> None:
        """주문의 약 60%에 리뷰가 달린다. 나머지 40%는 리뷰 없는 구매(가중치 0.5)다."""
        items_by_order: dict[int, list[int]] = defaultdict(list)
        for order_id, goods_id, _ in self.order_items:
            items_by_order[order_id].append(goods_id)

        for order_id, member_id, ordered_at in self.orders:
            if self.rng.random() >= REVIEW_SHARE_OF_ORDERS:
                continue
            goods_list = items_by_order.get(order_id)
            if not goods_list:
                continue
            goods_id = goods_list[self.rng.randrange(len(goods_list))]
            key = (member_id, goods_id)
            if key in self.reviews:
                continue
            if member_id in self.noise_set:
                rating = self.rng.randint(1, 5)
            else:
                rating = self.rng.choices(RATING_CHOICES, weights=RATING_WEIGHTS, k=1)[0]
            self.reviews[key] = (rating, self._review_moment(ordered_at))

        self._plant_ratings()

    def _review_moment(self, ordered_at: datetime) -> datetime:
        moment = ordered_at + timedelta(
            days=self.rng.randint(1, 14), seconds=self.rng.randrange(86400)
        )
        return min(moment, self.now)

    def _plant_ratings(self) -> None:
        """P1·P2·P3의 별점을 심는다.

        P1은 "샀는데 별점으로 배신하는" 패턴이라 구매 사실만으로는 부족하다. DRY 회원이
        A나 B를 샀으면 리뷰를 반드시 남기게 해서, 60% 표본추출이 배신 비율을 흐리지 않게 한다.
        """
        bought: dict[int, set[int]] = defaultdict(set)
        order_owner = {oid: mid for oid, mid, _ in self.orders}
        for order_id, goods_id, _ in self.order_items:
            bought[order_owner[order_id]].add(goods_id)

        a, b, c, d = (self.picks[k] for k in ("A", "B", "C", "D"))

        for member in self.members:
            if member.skin_type != "DRY":
                continue
            owned = bought.get(member.member_id, set())
            when = self._any_order_moment(member.member_id)
            if a in owned:
                low = self.rng.random() < P1_LOW_RATING_SHARE
                self.reviews[(member.member_id, a)] = (
                    self.rng.randint(1, 2) if low else self.rng.randint(4, 5), when
                )
            if b in owned:
                high = self.rng.random() < P1_LOW_RATING_SHARE
                self.reviews[(member.member_id, b)] = (
                    self.rng.randint(4, 5) if high else self.rng.randint(1, 2), when
                )

        for member_id in self.p2_members:
            self.reviews[(member_id, c)] = (5, self._any_order_moment(member_id))
        for member_id in self.p3_members:
            self.reviews[(member_id, d)] = (5, self._any_order_moment(member_id))

    def _any_order_moment(self, member_id: int) -> datetime:
        orders = self.orders_by_member.get(member_id)
        base = self.order_at[orders[0]] if orders else self._moment()
        return self._review_moment(base)

    # -- 행동 로그 ---------------------------------------------------------

    def build_behavior(self) -> None:
        total = len(self.members) * BEHAVIOR_PER_MEMBER
        member_ids = [m.member_id for m in self.members]
        p3_set = set(self.p3_members)
        for _ in range(total):
            member_id = self.rng.choice(member_ids)
            member = self.by_id[member_id]
            if member_id in p3_set and self.rng.random() < P3_BEHAVIOR_SHARE:
                goods_id = self.rng.choice(self.sebum_pool)
            else:
                goods_id = self._pick_goods(member)
            kind = self.rng.choices(BEHAVIOR_KINDS, weights=BEHAVIOR_KIND_WEIGHTS, k=1)[0]
            self.behavior.append((member_id, goods_id, kind, self._moment()))
        self.behavior.sort(key=lambda row: (row[0], row[3], row[1], row[2]))

    # -- planted -----------------------------------------------------------

    def planted(self) -> list[dict]:
        return [
            {
                "id": "P1",
                "kind": "rating_betrayal",
                "goodsId": self.picks["A"],
                "rivalGoodsId": self.picks["B"],
                "skinType": "DRY",
                "lowRatingShare": P1_LOW_RATING_SHARE,
                "pass": PASS_TEXT["P1"],
            },
            {
                "id": "P2",
                "kind": "off_rulebook_item",
                "goodsId": self.picks["C"],
                "concern": "bright",
                "step": self.catalog.category[self.picks["C"]][:7],
                "buyerShare": P2_BUYER_SHARE,
                "pass": PASS_TEXT["P2"],
            },
            {
                "id": "P3",
                "kind": "behavior_over_profile",
                "goodsId": self.picks["D"],
                "skinType": "DRY",
                "behaviorTag": "sebum",
                "memberCount": len(self.p3_members),
                "pass": PASS_TEXT["P3"],
            },
            {
                "id": "P4",
                "kind": "noise",
                "share": NOISE_SHARE,
                "pass": PASS_TEXT["P4"],
            },
        ]

    def planted_members(self) -> dict:
        """회원 id 목록. planted.schema.json이 additionalProperties: false라 별도 파일로 뺀다.

        스키마는 Wave A 계약이라 고치지 않는다(구현 계획 §4 Task B1-1).
        """
        return {"P3": self.p3_members, "P4": self.noise_members, "P2": self.p2_members}


# ---------------------------------------------------------------------------
# 출력
# ---------------------------------------------------------------------------

STAMP = "%Y-%m-%d %H:%M:%S"


def write_csv(path: Path, header: list[str], rows) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.writer(fp)
        writer.writerow(header)
        writer.writerows(rows)


def write_raw(seed: Seed, out: Path) -> None:
    raw = out / "raw"
    write_csv(
        raw / "members.csv",
        ["member_id", "skin_type", "concerns", "textures"],
        [(m.member_id, m.skin_type, ",".join(m.concerns), ",".join(m.textures))
         for m in seed.members],
    )
    write_csv(
        raw / "orders.csv",
        ["order_id", "member_id", "ordered_at"],
        [(oid, mid, at.strftime(STAMP)) for oid, mid, at in sorted(seed.orders)],
    )
    write_csv(
        raw / "order_items.csv",
        ["order_id", "goods_id", "quantity"],
        seed.order_items,
    )
    write_csv(
        raw / "reviews.csv",
        ["member_id", "goods_id", "rating", "created_at"],
        [(mid, gid, rating, at.strftime(STAMP))
         for (mid, gid), (rating, at) in sorted(seed.reviews.items())],
    )
    write_csv(
        raw / "behavior_events.csv",
        ["member_id", "goods_id", "kind", "created_at"],
        [(mid, gid, kind, at.strftime(STAMP)) for mid, gid, kind, at in seed.behavior],
    )


SQL_HEADER = """-- seed_v3.sql — 개인화 v3 학습 시드(개발용 데이터 적재 전용).
--
-- 이 파일은 Flyway 마이그레이션이 아니다. db/migration에 넣지 않는다. 스키마를 바꾸지 않고
-- 행만 넣는다. Wave D가 실 MySQL에 부어 화면으로 확인할 때만 쓰고, 학습 파이프라인은
-- 이 파일을 읽지 않는다(구현 계획 §3.1).
--
-- 생성기: tools/recsys/seed_generator.py (난수 시드 42)
-- 회원 id는 1000번부터다. V64 시드 회원 1~3과 겹치지 않게 했다.
-- 시드 계정의 password_hash는 실제로 로그인되지 않는 자리표시자다.
"""

DUMMY_HASH = "$2a$10$seedonlyvalue0000000000000000000000000000000000000000"
RECEIVER_ZIPCODE = "06236"
RECEIVER_ADDRESS = "서울특별시 강남구 테헤란로 152"


def sql_text(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def insert_block(table: str, columns: list[str], rows: list[str], chunk: int = 400) -> str:
    if not rows:
        return ""
    out = []
    head = f"INSERT INTO {table} ({', '.join(columns)}) VALUES\n"
    for start in range(0, len(rows), chunk):
        out.append(head + ",\n".join(rows[start:start + chunk]) + ";\n")
    return "".join(out)


def write_seed_sql(seed: Seed, out: Path) -> None:
    catalog = seed.catalog
    parts = [SQL_HEADER, "\n-- 회원\n"]

    parts.append(insert_block(
        "member",
        ["id", "email", "password_hash", "nickname", "grade", "status", "role"],
        [
            "({}, {}, {}, {}, 'BABY', 'ACTIVE', 'USER')".format(
                m.member_id,
                sql_text(f"v3seed{m.member_id}@beautyboy.test"),
                sql_text(DUMMY_HASH),
                sql_text(f"시드회원{m.member_id}"),
            )
            for m in seed.members
        ],
    ))

    parts.append("\n-- 프로필. concerns는 콤마 구분 슬러그 문자열이다(V81 이후).\n"
                 "-- 고민과 선호 사용감이 같은 칸에 섞인다.\n")
    parts.append(insert_block(
        "member_profile",
        ["member_id", "skin_type", "concerns", "age_band"],
        [
            "({}, {}, {}, {})".format(
                m.member_id,
                sql_text(m.skin_type),
                sql_text(",".join(m.concerns + m.textures)),
                sql_text(m.age_band),
            )
            for m in seed.members
        ],
    ))

    line_amount: dict[int, int] = defaultdict(int)
    for order_id, goods_id, quantity in seed.order_items:
        line_amount[order_id] += catalog.price[goods_id] * quantity

    parts.append("\n-- 주문. 테이블 이름은 orders다.\n")
    parts.append(insert_block(
        "orders",
        ["id", "order_no", "member_id", "status", "total_amount", "discount_amount",
         "payable_amount", "receiver_name", "receiver_phone", "zipcode", "address1",
         "delivery_type", "ordered_at", "paid_at"],
        [
            "({}, {}, {}, 'PAID', {}, 0, {}, {}, '010-0000-0000', {}, {}, 'NORMAL', {}, {})".format(
                order_id,
                sql_text(f"V3{at:%Y%m%d}{order_id:07d}"),
                member_id,
                line_amount[order_id],
                line_amount[order_id],
                sql_text(f"시드회원{member_id}"),
                sql_text(RECEIVER_ZIPCODE),
                sql_text(RECEIVER_ADDRESS),
                sql_text(at.strftime(STAMP)),
                sql_text(at.strftime(STAMP)),
            )
            for order_id, member_id, at in sorted(seed.orders)
        ],
    ))

    parts.append("\n-- 주문 상품. unit_price는 카탈로그 CSV의 sale_price를 스냅샷으로 복사한다.\n"
                 "-- goods_name은 카탈로그 CSV에 없는 열이라 자리표시자로 넣고 아래에서\n"
                 "-- goods 테이블의 실제 이름으로 한 번에 채운다.\n")
    parts.append(insert_block(
        "order_item",
        ["order_id", "goods_id", "goods_name", "unit_price", "quantity", "line_amount"],
        [
            "({}, {}, {}, {}, {}, {})".format(
                order_id,
                goods_id,
                sql_text(f"상품{goods_id}"),
                catalog.price[goods_id],
                quantity,
                catalog.price[goods_id] * quantity,
            )
            for order_id, goods_id, quantity in seed.order_items
        ],
    ))
    parts.append(
        "UPDATE order_item oi JOIN goods g ON g.id = oi.goods_id\n"
        "   SET oi.goods_name = g.name\n"
        f" WHERE oi.order_id BETWEEN 1 AND {len(seed.orders)};\n"
    )

    parts.append("\n-- 리뷰. UNIQUE(member_id, goods_id)라 한 회원이 한 상품에 하나뿐이다.\n")
    parts.append(insert_block(
        "review",
        ["member_id", "goods_id", "rating", "content", "skin_type_snapshot", "created_at"],
        [
            "({}, {}, {}, {}, {}, {})".format(
                member_id,
                goods_id,
                rating,
                sql_text(f"시드 리뷰입니다. 별점 {rating}점."),
                sql_text(seed.by_id[member_id].skin_type),
                sql_text(at.strftime(STAMP)),
            )
            for (member_id, goods_id), (rating, at) in sorted(seed.reviews.items())
        ],
    ))

    parts.append("\n-- 행동 로그\n")
    parts.append(insert_block(
        "behavior_event",
        ["member_id", "goods_id", "kind", "created_at"],
        [
            "({}, {}, {}, {})".format(
                member_id, goods_id, sql_text(kind), sql_text(at.strftime(STAMP))
            )
            for member_id, goods_id, kind, at in seed.behavior
        ],
        chunk=800,
    ))

    (out / "seed_v3.sql").write_text("".join(parts), encoding="utf-8")


def write_json(path: Path, payload) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


# ---------------------------------------------------------------------------
# 엔트리포인트
# ---------------------------------------------------------------------------


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="v3 학습 시드를 만든다.")
    parser.add_argument("--catalog", required=True, type=Path, help="카탈로그 CSV 디렉터리")
    parser.add_argument("--out", required=True, type=Path, help="산출물 디렉터리")
    parser.add_argument("--members", type=int, default=600,
                        help="회원 수. 주문·행동 로그는 여기에 비례해 줄어든다")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if args.members < 4:
        raise SystemExit("회원은 최소 4명이다. 피부타입 4종을 균등하게 나눠야 한다.")

    # "지금"이 아니라 "오늘 자정"이 기준이다. 초 단위 현재시각을 쓰면 같은 날 두 번 돌린
    # 결과가 달라져 재현성 테스트가 깨진다.
    now = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)

    catalog = Catalog(args.catalog)
    seed = Seed(catalog, args.members, now)
    seed.build_orders()
    seed.build_reviews()
    seed.build_behavior()

    args.out.mkdir(parents=True, exist_ok=True)
    write_raw(seed, args.out)
    write_seed_sql(seed, args.out)
    write_json(args.out / "planted.json", seed.planted())
    write_json(args.out / "planted_members.json", seed.planted_members())

    kinds = Counter(kind for _, _, kind, _ in seed.behavior)
    print(
        f"회원 {len(seed.members)} / 주문 {len(seed.orders)} / 주문상품 {len(seed.order_items)} / "
        f"리뷰 {len(seed.reviews)} / 행동 {len(seed.behavior)} {dict(kinds)}",
        file=sys.stderr,
    )
    print(f"심은 상품 {seed.picks} / P3 {len(seed.p3_members)}명 / 잡음 {len(seed.noise_members)}명",
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
