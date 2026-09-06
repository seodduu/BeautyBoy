"""브라우저의 aggregate()·behaviorInput()을 파이썬으로 옮긴 것.

TS 원본: frontend/src/features/affinity/profile.ts, features/affinity/behaviorInput.ts
동등성은 tests/test_behavior_input.py가 tests/fixtures/behavior-cases.json으로 고정한다.
그 픽스처의 기댓값은 실제 TS 함수의 출력이다. 손으로 계산해 적은 값이 아니다.
"""

# tag.sort_order 순. frontend/src/types/model.ts의 MODEL_TAG_SLUGS와 같아야 한다.
MODEL_TAG_SLUGS = [
    "cleanse", "exfoliate", "sebum", "soothe", "moisture", "uv", "bright", "firm", "anti-aging", "scalp",
    "pore", "trouble", "barrier", "antioxidant",
    "fresh", "dewy", "matte",
    "gentle",
]

MAX_EVENTS = 50                       # events.ts MAX_EVENTS
WEIGHT = {"VIEW": 1, "WISH": 2, "CART": 3}   # events.ts WEIGHT


def aggregate(events):
    """(cat3, tag) 단위 가중치 합. 키는 f"{cat3}|{tag}". profile.ts aggregate()와 같다."""
    scores = {}
    for event in events:
        for tag in event["tags"]:
            key = f"{event['cat3']}|{tag}"
            scores[key] = scores.get(key, 0) + event["w"]
    return scores


def behavior_input(events):
    """행동 이벤트 → 18칸 벡터. behaviorInput.ts와 같은 식이다.

    카테고리 축을 접고 태그별로 합산한 뒤 최대값으로 나눈다. 전부 0이면 0을 돌려준다
    (0으로 나누지 않는다).
    """
    by_tag = {}
    for key, weight in aggregate(events).items():
        tag = key[key.index("|") + 1:]
        by_tag[tag] = by_tag.get(tag, 0) + weight
    maximum = max([0, *by_tag.values()])
    if maximum <= 0:
        return [0.0] * len(MODEL_TAG_SLUGS)
    return [by_tag.get(slug, 0) / maximum for slug in MODEL_TAG_SLUGS]
