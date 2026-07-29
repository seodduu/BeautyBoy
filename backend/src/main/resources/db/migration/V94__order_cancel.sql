-- 잔여 수량 = quantity - canceled_quantity. 환불액은 항상 스냅샷 unit_price × 취소 수량.
ALTER TABLE order_item ADD COLUMN canceled_quantity INT NOT NULL DEFAULT 0;

CREATE TABLE order_cancel (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id      BIGINT       NOT NULL,
  -- payment_key를 두지 않는다: 결제 지식은 payment 도메인의 것이고(경계), 주문 1건당 결제
  -- 1건(uk_payment_order)이라 order_id만으로 역추적된다. 취소↔결제 연결은
  -- payment_compensation(orderNo·paymentKey·amount)과 감사 로그가 보존한다.
  refund_amount INT          NOT NULL,   -- 이 회차 환불액. 서버 계산값만 저장한다.
  reason        VARCHAR(200) NOT NULL,
  canceled_at   DATETIME     NOT NULL,
  KEY idx_order_cancel_order (order_id)
);

CREATE TABLE order_cancel_item (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  cancel_id     BIGINT NOT NULL,
  order_item_id BIGINT NOT NULL,
  quantity      INT    NOT NULL,
  KEY idx_order_cancel_item_cancel (cancel_id)
);
