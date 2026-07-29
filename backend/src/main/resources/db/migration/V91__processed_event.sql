CREATE TABLE processed_event (
    event_id     BIGINT       NOT NULL,              -- outbox_event.id
    consumer     VARCHAR(50)  NOT NULL,              -- 'sales-aggregation' 등
    processed_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (event_id, consumer)
);
