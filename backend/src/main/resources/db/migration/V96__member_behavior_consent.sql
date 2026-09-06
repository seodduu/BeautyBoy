-- 설계 §5: 행동 로그 수집 동의. 기본 0(거부). 거부 회원은 v2 방식(기기 사본만)으로 동작한다.
ALTER TABLE member_profile
  ADD COLUMN behavior_consent    TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN behavior_consent_at DATETIME   NULL;
