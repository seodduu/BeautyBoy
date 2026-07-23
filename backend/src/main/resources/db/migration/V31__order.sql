-- 주문. 배송지는 member.address를 참조하지 않고 이 행에 복사해 둔다(스냅샷).
-- 참조로 두면 회원이 배송지를 수정하는 순간 과거 주문서의 배송지가 조용히 바뀐다 —
-- "어디로 보냈는가"는 그 시점의 사실이라 나중에 달라지면 안 된다.
CREATE TABLE orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  -- 외부에 노출되는 주문번호. PK(연번)를 노출하면 총 주문 수가 새어나가고 남의 주문을 추측할 수 있다.
  order_no VARCHAR(30) NOT NULL UNIQUE,
  member_id BIGINT NOT NULL,
  -- PENDING(결제대기) → PAID(결제완료) → PREPARING → SHIPPING → DONE / CANCELED
  status VARCHAR(20) NOT NULL,
  total_amount INT NOT NULL,           -- 상품 합계(정가 아님, 판매가 기준)
  discount_amount INT NOT NULL DEFAULT 0,  -- 쿠폰·포인트. 1차에서는 항상 0.
  payable_amount INT NOT NULL,         -- 실제 결제할 금액 = total - discount. 결제 검증의 기준값.
  receiver_name VARCHAR(50) NOT NULL,
  receiver_phone VARCHAR(20) NOT NULL,
  zipcode VARCHAR(10) NOT NULL,
  address1 VARCHAR(200) NOT NULL,
  address2 VARCHAR(200) NULL,
  delivery_type VARCHAR(20) NOT NULL,  -- NORMAL|TODAY_DREAM|PICKUP
  ordered_at DATETIME NOT NULL,
  paid_at DATETIME NULL,
  -- 주문 목록은 "내 주문 최신순"이라 이 순서가 그대로 인덱스가 된다.
  INDEX idx_orders_member_ordered_at (member_id, ordered_at),
  -- 랭킹 배치가 "그 날 결제된 주문"을 훑는다(T2-8).
  INDEX idx_orders_status_paid_at (status, paid_at),
  CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES member(id)
);

-- 주문 상품. 상품명·옵션명·단가를 전부 복사한다.
-- goods_id는 "무엇을 샀는지" 추적용으로만 남기고, 표시는 스냅샷 컬럼으로 한다.
CREATE TABLE order_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  goods_id BIGINT NOT NULL,
  option_id BIGINT NULL,
  goods_name VARCHAR(200) NOT NULL,    -- 스냅샷
  option_name VARCHAR(100) NULL,       -- 스냅샷
  unit_price INT NOT NULL,             -- 스냅샷(옵션 추가금 포함한 1개 가격)
  quantity INT NOT NULL,
  line_amount INT NOT NULL,            -- unit_price * quantity. 저장해 두면 합계 검산이 쉽다.
  INDEX idx_order_item_goods (goods_id),
  CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders(id)
);
