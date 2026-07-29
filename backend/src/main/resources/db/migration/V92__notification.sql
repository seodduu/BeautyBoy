CREATE TABLE notification (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id  BIGINT       NOT NULL,
    event_id   BIGINT       NOT NULL,                -- outbox_event.id
    type       VARCHAR(30)  NOT NULL,                -- 'ORDER_CONFIRMED'
    message    VARCHAR(200) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_notification_dedup (member_id, event_id)  -- 중복 소비 차단
);
