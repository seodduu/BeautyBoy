CREATE TABLE payment_compensation (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no     VARCHAR(30)  NOT NULL,
  payment_key  VARCHAR(200) NOT NULL,
  action       VARCHAR(20)  NOT NULL,  -- CANCEL_FULL | CANCEL_PARTIAL
  amount       INT          NOT NULL,
  reason       VARCHAR(200) NOT NULL,
  status       VARCHAR(20)  NOT NULL,  -- 의미는 설계 §5-3 상태표가 진실
  retry_count  INT          NOT NULL DEFAULT 0,
  last_error   VARCHAR(500) NULL,
  created_at   DATETIME     NOT NULL,
  resolved_at  DATETIME     NULL,
  KEY idx_compensation_status (status, created_at)
);
