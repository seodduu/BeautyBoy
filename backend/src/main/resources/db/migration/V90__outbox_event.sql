CREATE TABLE outbox_event (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(30)  NOT NULL,            -- 'ORDER'
    aggregate_id   BIGINT       NOT NULL,            -- orderId
    event_type     VARCHAR(50)  NOT NULL,            -- 'ORDER_CONFIRMED'
    payload        JSON         NOT NULL,            -- 설계 §4.3 스키마
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | PUBLISHED
    created_at     DATETIME(6)  NOT NULL,
    published_at   DATETIME(6)  NULL,
    INDEX idx_outbox_pending (status, created_at)    -- 릴레이의 폴링 쿼리 전용
);
