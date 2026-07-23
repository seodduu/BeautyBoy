-- 결제. 주문 1건당 최대 1건(재결제는 1차 범위 밖)이라 order_id에 유니크를 건다 —
-- 이것이 이중 승인(같은 주문을 두 번 결제)에 대한 DB 차원의 마지막 방어선이다.
CREATE TABLE payment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  payment_key VARCHAR(200) NOT NULL,   -- 토스가 발급한 식별자
  amount INT NOT NULL,                 -- 토스가 승인했다고 응답한 금액
  status VARCHAR(20) NOT NULL,         -- APPROVED|CANCELED
  raw_response TEXT NOT NULL,          -- 승인 응답 원문. 분쟁 시 우리 해석이 아니라 원문이 근거다.
  approved_at DATETIME NOT NULL,
  UNIQUE KEY uk_payment_order (order_id),
  UNIQUE KEY uk_payment_key (payment_key),
  CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders(id)
);
