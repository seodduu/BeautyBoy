-- V62__ingredient_inci.sql — 판정 엔진 조인 키. ingredient에 INCI 영문명을 추가하고 30행 백필한다.
-- category/irritation_level/comedogenic은 건드리지 않는다(궁합 엔진·기존 배지 의존). inci_name은 reg_flag 조인 키.
ALTER TABLE ingredient ADD COLUMN inci_name VARCHAR(255) NULL AFTER name;

UPDATE ingredient SET inci_name = CASE id
  WHEN 1 THEN 'retinol'                WHEN 2 THEN 'bakuchiol'
  WHEN 3 THEN 'glycolic acid'          WHEN 4 THEN 'lactic acid'
  WHEN 5 THEN 'salicylic acid'         WHEN 6 THEN 'betaine salicylate'
  WHEN 7 THEN 'ascorbic acid'          WHEN 8 THEN 'ascorbyl glucoside'
  WHEN 9 THEN 'niacinamide'            WHEN 10 THEN 'hyaluronic acid'
  WHEN 11 THEN 'sodium hyaluronate'    WHEN 12 THEN 'ceramide np'
  WHEN 13 THEN 'ceramide ap'           WHEN 14 THEN 'palmitoyl pentapeptide-4'
  WHEN 15 THEN 'acetyl hexapeptide-8'  WHEN 16 THEN 'centella asiatica extract'
  WHEN 17 THEN 'madecassoside'         WHEN 18 THEN 'fragrance'
  WHEN 19 THEN 'limonene'              WHEN 20 THEN 'alcohol'
  WHEN 21 THEN 'cetyl alcohol'         WHEN 22 THEN 'titanium dioxide'
  WHEN 23 THEN 'zinc oxide'            WHEN 24 THEN 'ethylhexyl methoxycinnamate'
  WHEN 25 THEN 'glycerin'              WHEN 26 THEN 'panthenol'
  WHEN 27 THEN 'allantoin'             WHEN 28 THEN 'tocopherol'
  WHEN 29 THEN 'adenosine'             WHEN 30 THEN 'bisabolol'
  ELSE inci_name END
WHERE id BETWEEN 1 AND 30;

CREATE INDEX idx_ingredient_inci ON ingredient(inci_name);
