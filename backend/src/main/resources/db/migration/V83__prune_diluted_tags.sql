-- V83__prune_diluted_tags.sql
-- 태그 희석화 해소 — 설계: docs/superpowers/specs/2026-07-27-tag-dilution-design.md
--
-- 배경: V72가 태그 파생에서 is_key 조건을 없애 보조 성분까지 태그를 만들게 됐다. 그 결과
-- 상품당 평균 태그가 5.18개가 되고 moisture는 190개 중 127개(67%)가 보유해, tag= 필터가
-- 카탈로그를 거의 걸러내지 못한다. 메인 개인화가 동작해도 화면이 안 바뀌는 원인이 이것이다.
--
-- goods_tag를 재파생하지 않고 두 단계로 깎는다. 재파생하면 V71의 수동 보정(goods 1·5)과
-- 카테고리 기본 태그(source_ingredient_id IS NULL, 클렌징=cleanse·선케어=uv 등 130행)가
-- 사라진다 — 그 이력은 성분에서 다시 만들어낼 수 없다.

-- =====================================================================
-- (a) 부형제 6종이 "보조 성분"으로 만든 태그를 지운다.
--     글리세린(25)·판테놀(26)·알란토인(27)·토코페롤(28)·아데노신(29)·알파비사보롤(30)은
--     제형 상수라, 들어 있다는 사실이 "이 상품은 그것을 위한 것"을 뜻하지 않는다.
--     is_key=1로 들어간 것은 남긴다 — 그때는 제조사가 소구점으로 내세운 것이다.
--     반대로 BHA·레티노이드·비타민C 같은 소구 성분은 미량이어도 소구점이므로 건드리지 않는다.
--     (성분의 *성격*으로 가르는 것이 is_key를 일괄 요구하는 것보다 정확하다 — 후자는
--      규칙 19개 중 10개를 후보 4개 미만으로 떨어뜨린다.)
-- =====================================================================
DELETE gt FROM goods_tag gt
JOIN goods_ingredient gi
  ON gi.goods_id = gt.goods_id AND gi.ingredient_id = gt.source_ingredient_id
WHERE gt.source_ingredient_id IN (25, 26, 27, 28, 29, 30)
  AND gi.is_key = 0;

-- =====================================================================
-- (b) 상품당 태그를 3개로 제한한다. 상한이 희석의 실질 지렛대다((a)만으로는 moisture가
--     51%까지밖에 안 내려간다). 3개면 카드가 표시하는 수(2개)보다 하나 많아 표시 손실이 없다.
--
--     순위: is_key 우선 → **희소한 태그 우선** → 함량순 → tag_id.
--
--     왜 희소 우선인가: 성분 하나가 태그 여럿을 만든다(BHA·살리실산 → 각질·피지·모공·트러블 4개).
--     여기서 전역 고정 순서(tag.sort_order)로 자르면 순서가 뒤인 태그가 **카탈로그 전체에서
--     일괄로** 잘려나간다 — 실제로 trouble이 190개 상품 중 0개가 됐고 concern 규칙 2행이
--     죽은 행이 됐다. 반면 보유 상품이 적은 태그를 남기면 그 사고가 없을 뿐 아니라,
--     변별력이 큰 태그가 살아남아 이 마이그레이션의 목적에 직접 부합한다
--     (moisture 67%→23%, trouble 생존, 죽은 규칙 0).
--
--     - is_key가 여전히 1순위다 — 제조사가 소구점으로 내세운 성분은 희소도와 무관하게 남는다.
--     - 카테고리 기본 태그(source_ingredient_id IS NULL)는 COALESCE로 is_key 취급해 맨 앞에
--       둔다 — "클렌징폼이 세정"은 성분과 무관하게 참이라 상한에 밀려나면 안 된다.
--     - 화장품 전성분은 함량 순 표기라 goods_ingredient.sort_order가 관련도 신호다.
--     - 마지막 tag_id는 완전 결정성을 위한 것이다. 없으면 동점에서 삭제 대상이 옵티마이저
--       재량이 되어 clean 로드마다 살아남는 태그가 달라진다.
--     - freq는 (a)가 끝난 뒤의 goods_tag를 세므로, 부형제 정리가 반영된 희소도다.
-- =====================================================================
DELETE gt FROM goods_tag gt
JOIN (
  SELECT x.goods_id, x.tag_id,
         ROW_NUMBER() OVER (
           PARTITION BY x.goods_id
           ORDER BY COALESCE(x.is_key, 1) DESC,
                    f.freq ASC,
                    COALESCE(x.ing_sort, 0) ASC,
                    x.tag_id ASC
         ) AS rn
  FROM (
    SELECT gt2.goods_id, gt2.tag_id, gi2.is_key, gi2.sort_order AS ing_sort
    FROM goods_tag gt2
    LEFT JOIN goods_ingredient gi2
      ON gi2.goods_id = gt2.goods_id AND gi2.ingredient_id = gt2.source_ingredient_id
  ) x
  JOIN (SELECT tag_id, COUNT(*) AS freq FROM goods_tag GROUP BY tag_id) f
    ON f.tag_id = x.tag_id
) r ON r.goods_id = gt.goods_id AND r.tag_id = gt.tag_id
WHERE r.rn > 3;
