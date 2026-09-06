# 학습표 CSV 스키마 (`tools/recsys/data/train.csv`)

두 탑(사용자·상품)을 학습시키는 표의 유일한 계약이다(로드맵 §3.4). 시드 생성기(B1-1)가 쓰고
학습 스크립트(B1-3)가 읽는다. 열이 하나라도 어긋나면 학습은 조용히 성공하고 추천만 틀린다.

## 열 순서

헤더 행 필수, UTF-8, 쉼표 구분, 따옴표 없다.

| 열 | 값 |
|---|---|
| `member_id` | 정수 |
| `skin_type` | `DRY` \| `OILY` \| `COMBINATION` \| `SENSITIVE` \| `""`(빈 문자열 = 없음) |
| `concerns` | `\|` 구분 슬러그. MODEL_CONCERNS 부분집합. 빈 문자열 허용 |
| `textures` | `\|` 구분. MODEL_TEXTURES 부분집합. 빈 문자열 허용 |
| `b_cleanse` … `b_gentle` | 18열, MODEL_TAG_SLUGS 순, 소수 0~1. `behaviorInput()`과 같은 식 |
| `goods_id` | 정수 |
| `label` | `1` \| `0` |
| `weight` | `1.0` \| `0.5` \| `2.0` \| `0.3` (설계 §7 표. 다른 값 금지) |
| `split` | `train` \| `holdout` (holdout = 학습표 생성 시점 기준 최근 14일 주문) |

18개 행동 열의 이름은 순서대로 다음과 같다. `MODEL_TAG_SLUGS`(frontend/src/types/model.ts) 앞에
`b_`를 붙인 것이다.

```
b_cleanse, b_exfoliate, b_sebum, b_soothe, b_moisture, b_uv, b_bright, b_firm, b_anti-aging, b_scalp,
b_pore, b_trouble, b_barrier, b_antioxidant, b_fresh, b_dewy, b_matte, b_gentle
```

행 수 하한은 없다. 행 하나당 `(member_id, goods_id, split)`은 유일하다.

## weight 값의 뜻

| weight | 뜻 | 전형적인 행 |
|---|---|---|
| `1.0` | 강한 예 | 샀고 별점이 높다 |
| `2.0` | 강한 아니오 | 샀는데 별점이 낮다. 취향에 안 맞았다는 가장 비싼 신호라 제일 무겁게 센다 |
| `0.5` | 약한 예 / 약한 신호 | 샀는데 리뷰가 없다, 장바구니에 담기만 했다 |
| `0.3` | 약한 아니오 | 봤는데 안 샀다 |

## 예시 5행

```csv
member_id,skin_type,concerns,textures,b_cleanse,b_exfoliate,b_sebum,b_soothe,b_moisture,b_uv,b_bright,b_firm,b_anti-aging,b_scalp,b_pore,b_trouble,b_barrier,b_antioxidant,b_fresh,b_dewy,b_matte,b_gentle,goods_id,label,weight,split
101,DRY,moisture|barrier,dewy,0.0,0.0,0.0,0.5,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.75,0.0,0.0,0.5,0.0,0.0,88,1,1.0,train
102,OILY,sebum|pore,matte,0.0,0.25,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.75,0.5,0.0,0.0,0.0,0.0,0.5,0.0,91,0,2.0,train
103,COMBINATION,moisture|sebum,,0.0,0.0,0.5,0.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.25,0.0,0.0,0.0,0.0,0.0,140,1,0.5,train
104,SENSITIVE,soothe,fresh,0.0,0.0,0.0,1.0,0.5,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.5,0.0,0.5,0.0,0.0,0.75,217,1,0.5,holdout
105,,,,0.0,0.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,217,0,0.3,train
```

행별로 읽으면 이렇다.

1. 101번: 건성 회원이 사고 높은 별점을 남겼다(강한 예, `label=1`, `weight=1.0`).
2. 102번: 지성 회원이 샀는데 별점이 낮았다(강한 아니오, `label=0`, `weight=2.0`).
3. 103번: 샀지만 리뷰를 안 남겼다(약한 예, `weight=0.5`).
4. 104번: 장바구니에 담기만 했다(약한 예, `weight=0.5`). 최근 14일 주문이라 `split=holdout`이다.
5. 105번: 상세를 봤는데 안 샀다(약한 아니오, `label=0`, `weight=0.3`). 프로필이 비어 있어
   `skin_type`, `concerns`, `textures`가 전부 빈 문자열이다.
