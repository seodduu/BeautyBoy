CREATE TABLE ingredient (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL UNIQUE,
  category VARCHAR(40) NOT NULL,       -- RETINOID|AHA|BHA|VITAMIN_C|NIACINAMIDE|
                                       -- HYALURONIC|CERAMIDE|PEPTIDE|CENTELLA|
                                       -- SALICYLIC|FRAGRANCE|ALCOHOL|SPF_FILTER|OTHER
  irritation_level TINYINT NOT NULL,   -- 1(순함) ~ 5(자극 강함)
  comedogenic TINYINT NOT NULL,        -- 0 ~ 5 (모공 막힘 지수)
  summary VARCHAR(200) NOT NULL        -- 한줄설명(비전문가용)
);
CREATE INDEX idx_ingredient_category ON ingredient(category);

CREATE TABLE goods_ingredient (
  goods_id BIGINT NOT NULL,
  ingredient_id BIGINT NOT NULL,
  is_key TINYINT(1) NOT NULL DEFAULT 0,  -- 핵심 성분(상세 배지 노출 대상)
  sort_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (goods_id, ingredient_id),
  CONSTRAINT fk_gi_goods FOREIGN KEY (goods_id) REFERENCES goods(id),
  CONSTRAINT fk_gi_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredient(id)
);

-- 분류 쌍 규칙. (A,B)와 (B,A)를 둘 다 넣지 않기 위해 저장 시 사전순 정렬을 규약으로 한다
-- (category_a < category_b). 조회 측도 같은 규약으로 정규화해서 찾는다.
CREATE TABLE ingredient_rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_a VARCHAR(40) NOT NULL,
  category_b VARCHAR(40) NOT NULL,
  verdict VARCHAR(20) NOT NULL,        -- CONFLICT|CAUTION|SYNERGY
  reason VARCHAR(300) NOT NULL,
  CONSTRAINT uk_rule_pair UNIQUE (category_a, category_b)
);
