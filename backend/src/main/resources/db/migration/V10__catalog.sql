CREATE TABLE brand (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(60) NOT NULL UNIQUE,
  logo_url VARCHAR(300) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 계층 인코딩: C001(대) → C001001(중) → C001001001(소). 부모코드가 접두사이므로
-- 하위 전체 조회는 code LIKE 'C001%' 하나로 끝난다(설계 2장 올리브영 실측 반영).
CREATE TABLE category (
  code VARCHAR(12) PRIMARY KEY,
  parent_code VARCHAR(12) NULL,
  name VARCHAR(60) NOT NULL,
  depth TINYINT NOT NULL,             -- 1|2|3
  sort_order INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_category_parent FOREIGN KEY (parent_code) REFERENCES category(code)
);

CREATE TABLE goods (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  brand_id BIGINT NOT NULL,
  category_code VARCHAR(12) NOT NULL,   -- 항상 leaf(depth=3)
  name VARCHAR(200) NOT NULL,
  summary VARCHAR(300) NULL,
  description TEXT NULL,                -- 지연 로딩 대상(상세 본문)
  thumbnail_url VARCHAR(300) NOT NULL,
  list_price INT NOT NULL,              -- 정가
  sale_price INT NOT NULL,              -- 판매가
  status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',   -- ON_SALE|SOLD_OUT|HIDDEN
  view_count INT NOT NULL DEFAULT 0,    -- sort=popular (결정 2)
  sales_count INT NOT NULL DEFAULT 0,   -- sort=sales   (결정 2)
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_goods_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
  CONSTRAINT fk_goods_category FOREIGN KEY (category_code) REFERENCES category(code)
);
-- 목록 조회는 항상 "카테고리 접두사 + 노출상태"로 좁힌 뒤 정렬한다
CREATE INDEX idx_goods_category_status ON goods(category_code, status);
CREATE INDEX idx_goods_brand ON goods(brand_id);

CREATE TABLE goods_option (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  goods_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,           -- "100ml", "민감성용"
  add_price INT NOT NULL DEFAULT 0,
  stock INT NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_option_goods FOREIGN KEY (goods_id) REFERENCES goods(id)
);
CREATE INDEX idx_option_goods ON goods_option(goods_id);

-- 배지(SALE|COUPON|GIFT|ONE_PLUS_ONE)는 저장하지 않고 이 조인에서 파생한다(설계 2장)
CREATE TABLE promotion (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  badge_type VARCHAR(20) NOT NULL,      -- SALE|COUPON|GIFT|ONE_PLUS_ONE
  starts_at DATETIME NOT NULL,
  ends_at DATETIME NOT NULL
);
CREATE TABLE promotion_goods (
  promotion_id BIGINT NOT NULL,
  goods_id BIGINT NOT NULL,
  PRIMARY KEY (promotion_id, goods_id),
  CONSTRAINT fk_pg_promotion FOREIGN KEY (promotion_id) REFERENCES promotion(id),
  CONSTRAINT fk_pg_goods FOREIGN KEY (goods_id) REFERENCES goods(id)
);
