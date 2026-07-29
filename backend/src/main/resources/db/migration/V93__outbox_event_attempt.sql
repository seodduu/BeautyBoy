-- V93__outbox_event_attempt.sql
--
-- 릴레이가 "영구 실패 한 건"에 영원히 막히는 것을 막는다.
--
-- 배경: OutboxRelay는 발행에 실패하면 배치를 break한다(같은 주문 내 순서 보존이 목적).
-- 브로커 다운 같은 일시적 실패에는 옳지만, 직렬화 불가·RecordTooLargeException 같은
-- 영구 실패면 그 행이 created_at 최선두에 영원히 남아 뒤의 모든 주문 이벤트가 영영
-- 발행되지 않는다 — 결제는 성공하는데 장바구니가 안 비워지고 집계·알림도 멈추며,
-- 남는 신호는 초당 warn 로그 한 줄뿐이다. 컨슈머 실패에는 DLT가 있는데 발행 실패에는
-- 대응 장치가 없던 비대칭을 여기서 없앤다.
--
-- attempt_count가 임계치(beautyboy.events.relay-max-attempts, 기본 10)를 넘으면
-- status='FAILED'로 옮겨 폴링 쿼리(status='PENDING')에서 빠진다 — 뒤 건이 흐른다.
-- FAILED 행은 지워지지 않는다. 사람이 last_error를 보고 판단할 몫이고,
-- 살릴 때는 status='PENDING', attempt_count=0으로 되돌리면 그대로 재발행된다.
--
-- 기존 행(NOT NULL DEFAULT 0)은 그대로 0에서 시작한다 — 재계산할 이력이 없다.
ALTER TABLE outbox_event
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER status,
    -- 진단용. 스택트레이스 전체가 아니라 예외 클래스명 + 메시지 한 줄만 담는다(애플리케이션이 자른다).
    ADD COLUMN last_error VARCHAR(500) NULL AFTER attempt_count;
