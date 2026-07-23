-- 회원 장바구니. 비회원은 프론트가 localStorage로 들고 있다가 로그인 시 병합하므로 서버는 회원 것만 안다.
--
-- option_id가 NULL 허용인 이유: 옵션이 없는 상품이 있다(단일 규격).
-- 그런데 MySQL의 UNIQUE는 NULL을 서로 다른 값으로 취급해서, 옵션 없는 같은 상품을 여러 번 담으면
-- 유니크 제약이 막지 못한다. 그래서 애플리케이션이 "같은 상품+옵션이면 수량을 더한다"로 처리하고
-- 유니크 제약은 최후의 방어선으로만 둔다(T2-3에서 이 동작을 테스트로 고정한다).
CREATE TABLE cart_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL,
  goods_id BIGINT NOT NULL,
  option_id BIGINT NULL,
  quantity INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cart_member_goods_option (member_id, goods_id, option_id),
  -- 장바구니 조회는 항상 "내 것 전부"라 member_id 단독 인덱스면 충분하다(위 유니크의 선행 컬럼이라 별도 인덱스 불필요).
  CONSTRAINT fk_cart_item_member FOREIGN KEY (member_id) REFERENCES member(id)
);
