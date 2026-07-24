-- 찜. member×goods 유니크 — 같은 상품을 두 번 찜할 수 없다.
-- created_at을 날짜로 집계해 WishStatProvider가 "그 날 새로 추가된 찜 수"를 랭킹에 공급한다(T3-3).
CREATE TABLE wishlist (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL,
  goods_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wishlist_member_goods (member_id, goods_id),
  -- 랭킹 배치가 "그 날 추가분"을 날짜로 집계하므로 created_at 인덱스가 필요하다.
  INDEX idx_wishlist_created (created_at),
  CONSTRAINT fk_wishlist_member FOREIGN KEY (member_id) REFERENCES member(id)
);
