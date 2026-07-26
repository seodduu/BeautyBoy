-- V71__seed_product_tag.sql
-- 태그 마스터(효과 10 + 사용감 3) + 규칙 기반 매핑(근거 성분 포함) + 데모 수동 보정

-- =====================================================================
-- Step 1: 태그 마스터 시드 (완전)
-- =====================================================================
INSERT INTO tag (id, name, kind, slug, sort_order) VALUES
(1,'세정','EFFECT','cleanse',1),(2,'각질 케어','EFFECT','exfoliate',2),(3,'피지 관리','EFFECT','sebum',3),
(4,'진정','EFFECT','soothe',4),(5,'보습','EFFECT','moisture',5),(6,'자외선차단','EFFECT','uv',6),
(7,'브라이트닝','EFFECT','bright',7),(8,'탄력','EFFECT','firm',8),(9,'항노화','EFFECT','anti-aging',9),
(10,'두피 케어','EFFECT','scalp',10),
(11,'산뜻함','TEXTURE','fresh',20),(12,'촉촉함','TEXTURE','dewy',21),(13,'매트','TEXTURE','matte',22);

-- =====================================================================
-- Step 2: 효과 태그 = is_key 성분 category에서 파생, 근거 성분과 함께 저장(설계 B)
-- 각 INSERT…SELECT = "규칙 한 줄". source_ingredient_id에 근거 성분(is_key)을 남긴다.
-- =====================================================================
-- 성분 근거 태그
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order)
SELECT gi.goods_id, t.id, gi.ingredient_id, 10 + t.sort_order
FROM goods_ingredient gi JOIN ingredient i ON gi.ingredient_id=i.id
JOIN tag t ON t.slug = CASE
  WHEN i.category IN ('BHA','SALICYLIC') THEN 'sebum'
  WHEN i.category = 'AHA' THEN 'exfoliate'
  WHEN i.category = 'NIACINAMIDE' THEN 'sebum'
  WHEN i.category = 'CENTELLA' THEN 'soothe'
  WHEN i.category = 'HYALURONIC' THEN 'moisture'
  WHEN i.category = 'SPF_FILTER' THEN 'uv'
  WHEN i.category = 'VITAMIN_C' THEN 'bright'
  WHEN i.category = 'PEPTIDE' THEN 'firm'
  WHEN i.category = 'RETINOID' THEN 'anti-aging'
  ELSE NULL END
WHERE gi.is_key = 1
ON DUPLICATE KEY UPDATE sort_order = goods_tag.sort_order;  -- 같은 상품·태그 중복 무시
-- BHA/SALICYLIC은 각질 케어도 추가
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order)
SELECT gi.goods_id, (SELECT id FROM tag WHERE slug='exfoliate'), gi.ingredient_id, 12
FROM goods_ingredient gi JOIN ingredient i ON gi.ingredient_id=i.id
WHERE gi.is_key=1 AND i.category IN ('BHA','SALICYLIC')
ON DUPLICATE KEY UPDATE sort_order = goods_tag.sort_order;
-- 제품 유형 기반(근거 성분 없음): 클렌징→세정, 선케어→자외선차단, 샴푸→두피
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order)
SELECT g.id, (SELECT id FROM tag WHERE slug='cleanse'), NULL, 1 FROM goods g WHERE g.category_code LIKE 'C002%'
ON DUPLICATE KEY UPDATE sort_order = goods_tag.sort_order;
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order)
SELECT g.id, (SELECT id FROM tag WHERE slug='uv'), NULL, 1 FROM goods g WHERE g.category_code LIKE 'C004%'
ON DUPLICATE KEY UPDATE sort_order = goods_tag.sort_order;
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order)
SELECT g.id, (SELECT id FROM tag WHERE slug='scalp'), NULL, 1 FROM goods g WHERE g.category_code LIKE 'C003001%'
ON DUPLICATE KEY UPDATE sort_order = goods_tag.sort_order;

-- =====================================================================
-- Step 3: 수동 보정 — 이름/성분 어긋난 데모 상품
-- V12__seed_catalog.sql 상품 40개의 name·summary·is_key 성분을 전수 대조한 결과,
-- 규칙 파생 태그가 상품명과 정반대로 어긋나는 사례는 goods 1·5뿐이었다(둘 다 궁합 규칙
-- 검증용으로 AHA/RETINOID, RETINOID/VITAMIN_C를 일부러 섞어 놓은 상품). 나머지는
-- is_key 성분이 실제로 가진 부가 효능이라 상품명과 모순되지 않아 그대로 둔다
-- (예: goods 35 "유분기 없는 매트 톤업크림"에 나이아신아마이드발 '피지 관리'는 오히려 부합).
-- =====================================================================
-- goods 1 "수분 진정 토너": is_key가 AHA(글리콜릭애씨드,3)·RETINOID(레티놀,1)라서
-- 규칙이 각질 케어·항노화를 붙이는데, 상품명은 정반대(수분·진정)를 내세운다.
-- 근거 성분은 goods 1의 goods_ingredient에 실재하는 것만 쓴다(브리프 예시의 병풀(16)은
-- goods 1에 매핑되어 있지 않아 사용하지 않음) — 히알루론산(10)=보습, 판테놀(26)=진정·보습.
DELETE FROM goods_tag WHERE goods_id=1 AND tag_id IN ((SELECT id FROM tag WHERE slug='exfoliate'),(SELECT id FROM tag WHERE slug='anti-aging'));
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order) VALUES
(1,(SELECT id FROM tag WHERE slug='soothe'),26,2),    -- 근거: 판테놀(26, goods 1에 sort_order 4로 존재)
(1,(SELECT id FROM tag WHERE slug='moisture'),10,3)   -- 근거: 히알루론산(10, goods 1에 sort_order 3으로 존재)
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order);

-- goods 5 "브라이트닝 세럼": is_key가 VITAMIN_C(아스코르빈산,7)·RETINOID(레티놀,1)라서
-- 규칙이 항노화도 붙이는데, 상품명·summary는 브라이트닝만 내세운다. 브라이트닝(bright)은
-- VITAMIN_C 근거로 정확히 남기고, 항노화만 제거한다.
DELETE FROM goods_tag WHERE goods_id=5 AND tag_id=(SELECT id FROM tag WHERE slug='anti-aging');

-- 사용감(TEXTURE) 소수 수동: summary가 명확한 상품에만.
INSERT INTO goods_tag (goods_id, tag_id, source_ingredient_id, sort_order) VALUES
(9,(SELECT id FROM tag WHERE slug='fresh'),NULL,30),    -- "산뜻함이 오래" (goods 9)
(2,(SELECT id FROM tag WHERE slug='dewy'),NULL,30)      -- "고보습 토너" (goods 2)
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order);
