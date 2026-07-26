-- V70__product_tag.sql — 상품 효과·사용감 태그. 주의(CAUTION)는 성분 판정 카드가 담당하므로 kind에 없다.
CREATE TABLE tag (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  name       VARCHAR(40)  NOT NULL,          -- "피지 관리"
  kind       VARCHAR(20)  NOT NULL,          -- EFFECT|TEXTURE
  slug       VARCHAR(40)  NOT NULL UNIQUE,   -- 필터 URL·안정 참조 "sebum"
  sort_order INT          NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE goods_tag (
  goods_id             BIGINT NOT NULL,
  tag_id               BIGINT NOT NULL,
  source_ingredient_id BIGINT NULL,          -- 효과 태그 근거 성분(B). 사용감은 NULL.
  sort_order           INT NOT NULL DEFAULT 0,
  PRIMARY KEY (goods_id, tag_id),
  CONSTRAINT fk_gt_goods FOREIGN KEY (goods_id) REFERENCES goods(id),
  CONSTRAINT fk_gt_tag FOREIGN KEY (tag_id) REFERENCES tag(id),
  CONSTRAINT fk_gt_ingredient FOREIGN KEY (source_ingredient_id) REFERENCES ingredient(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_goods_tag_tag ON goods_tag(tag_id);
