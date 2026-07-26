-- V72__expand_product_tag.sql
-- 태그 마스터 확장(13→18종) + 파생 기준 완화(is_key 조건 제거) + 신규 태그 파생 재작성
-- 확정 세트·컬러 근거: docs/plans/2026-07-27-tag-expansion.md "확정 태그 세트" 표
-- V71(id 1~13)은 수정하지 않는다. 여기서 다시 실행하는 INSERT…SELECT는
-- ON DUPLICATE KEY UPDATE sort_order = goods_tag.sort_order(자기참조 no-op)로
-- V71이 이미 심어둔 행(수동 보정 포함)을 그대로 보존한다.

-- =====================================================================
-- Step (a): 태그 마스터 — 명칭 변경 + 신규 5종
-- =====================================================================
UPDATE tag SET name = '안티에이징' WHERE slug = 'anti-aging';

INSERT INTO tag (id, name, kind, slug, sort_order) VALUES
(14, '모공 케어', 'EFFECT', 'pore', 11),
(15, '트러블 케어', 'EFFECT', 'trouble', 12),
(16, '장벽 케어', 'EFFECT', 'barrier', 13),
(17, '항산화', 'EFFECT', 'antioxidant', 14),
(18, '저자극', 'PROPERTY', 'gentle', 30);

-- =====================================================================
-- Step (b-1): 성분 category → 태그. is_key 조건 제거(보조성분도 파생).
-- 카테고리 하나가 여러 태그에 대응하는 경우(예: BHA→각질케어·피지관리·모공·트러블)가 있어
-- CASE 대신 (category, slug) 매핑 파생테이블과 JOIN해 카테고리당 여러 행을 만든다.
-- sort_order = 10 + t.sort_order + IF(gi.is_key=1, 0, 20) — 보조성분은 뒤로 민다.
-- =====================================================================
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order)
SELECT gi.goods_id, t.id, gi.ingredient_id, 10 + t.sort_order + IF(gi.is_key = 1, 0, 20)
FROM goods_ingredient gi
JOIN ingredient i ON gi.ingredient_id = i.id
JOIN (
  SELECT 'AHA' AS category, 'exfoliate' AS slug
  UNION ALL SELECT 'BHA', 'exfoliate'
  UNION ALL SELECT 'SALICYLIC', 'exfoliate'
  UNION ALL SELECT 'BHA', 'sebum'
  UNION ALL SELECT 'SALICYLIC', 'sebum'
  UNION ALL SELECT 'NIACINAMIDE', 'sebum'
  UNION ALL SELECT 'CENTELLA', 'soothe'
  UNION ALL SELECT 'HYALURONIC', 'moisture'
  UNION ALL SELECT 'CERAMIDE', 'moisture'
  UNION ALL SELECT 'SPF_FILTER', 'uv'
  UNION ALL SELECT 'VITAMIN_C', 'bright'
  UNION ALL SELECT 'PEPTIDE', 'firm'
  UNION ALL SELECT 'RETINOID', 'anti-aging'
  UNION ALL SELECT 'BHA', 'pore'
  UNION ALL SELECT 'SALICYLIC', 'pore'
  UNION ALL SELECT 'NIACINAMIDE', 'pore'
  UNION ALL SELECT 'BHA', 'trouble'
  UNION ALL SELECT 'SALICYLIC', 'trouble'
  UNION ALL SELECT 'CERAMIDE', 'barrier'
  UNION ALL SELECT 'VITAMIN_C', 'antioxidant'
) m ON m.category = i.category
JOIN tag t ON t.slug = m.slug
ON DUPLICATE KEY UPDATE sort_order = goods_tag.sort_order;

-- =====================================================================
-- Step (b-2): OTHER 카테고리 성분(id로만 구분 가능)의 개별 매핑
-- 글리세린(25)→보습, 판테놀(26)·알란토인(27)→진정, 토코페롤(28)→항산화,
-- 아데노신(29)→안티에이징, 알파비사보롤(30)→브라이트닝
-- =====================================================================
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order)
SELECT gi.goods_id, t.id, gi.ingredient_id, 10 + t.sort_order + IF(gi.is_key = 1, 0, 20)
FROM goods_ingredient gi
JOIN tag t ON t.slug = CASE gi.ingredient_id
  WHEN 25 THEN 'moisture'
  WHEN 26 THEN 'soothe'
  WHEN 27 THEN 'soothe'
  WHEN 28 THEN 'antioxidant'
  WHEN 29 THEN 'anti-aging'
  WHEN 30 THEN 'bright'
  ELSE NULL END
WHERE gi.ingredient_id IN (25, 26, 27, 28, 29, 30)
ON DUPLICATE KEY UPDATE sort_order = goods_tag.sort_order;

-- =====================================================================
-- Step (c): 카테고리 기본값 추가분 — 애프터쉐이브(C005003)는 진정
-- (cleanse/uv/scalp의 C002/C004/C003001 매핑은 V71이 이미 심어둠, 재실행 불필요)
-- =====================================================================
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order)
SELECT g.id, (SELECT id FROM tag WHERE slug = 'soothe'), NULL, 1
FROM goods g WHERE g.category_code LIKE 'C005003%'
ON DUPLICATE KEY UPDATE sort_order = goods_tag.sort_order;

-- =====================================================================
-- Step (d): 저자극(gentle, PROPERTY) — 매핑된 성분이 1개 이상이고,
-- 매핑 전 성분(is_key 불문) 중 irritation_level > 2가 하나도 없는 상품
-- =====================================================================
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order)
SELECT g.id, (SELECT id FROM tag WHERE slug='gentle'), NULL, 40
FROM goods g
WHERE EXISTS (SELECT 1 FROM goods_ingredient gi WHERE gi.goods_id=g.id)
  AND NOT EXISTS (SELECT 1 FROM goods_ingredient gi JOIN ingredient i ON i.id=gi.ingredient_id
                  WHERE gi.goods_id=g.id AND i.irritation_level > 2)
ON DUPLICATE KEY UPDATE sort_order = goods_tag.sort_order;

-- =====================================================================
-- Step (e): V71 수동 보정 재적용
-- Step (b-1)의 is_key 조건 제거로 goods 1(각질케어·안티에이징)·goods 5(안티에이징)가
-- V71이 상품명과의 모순 때문에 일부러 지운 태그를 되살린다(둘 다 보조성분이 아니라
-- is_key 성분 자체가 근거라 재파생됨). V71의 DELETE를 그대로 재적용해 보정을 유지한다.
-- =====================================================================
DELETE FROM goods_tag WHERE goods_id=1 AND tag_id IN ((SELECT id FROM tag WHERE slug='exfoliate'),(SELECT id FROM tag WHERE slug='anti-aging'));
DELETE FROM goods_tag WHERE goods_id=5 AND tag_id=(SELECT id FROM tag WHERE slug='anti-aging');
