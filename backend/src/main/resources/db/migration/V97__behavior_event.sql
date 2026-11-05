-- 설계 §5: 서버에 남는 것은 회원·상품·종류·시각 네 칸뿐이다. IP·UA·세션·검색어는 컬럼 자체를 두지 않는다.
CREATE TABLE behavior_event (
  id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
  member_id  BIGINT      NOT NULL,
  goods_id   BIGINT      NOT NULL,
  kind       VARCHAR(8)  NOT NULL,          -- VIEW | WISH | CART (BehaviorKind)
  created_at DATETIME    NOT NULL,
  CONSTRAINT fk_behavior_event_member FOREIGN KEY (member_id) REFERENCES member(id),
  CONSTRAINT fk_behavior_event_goods  FOREIGN KEY (goods_id)  REFERENCES goods(id),
  INDEX idx_behavior_event_member_time (member_id, created_at),
  INDEX idx_behavior_event_time (created_at)   -- 90일 삭제 배치(Wave B-T2 B2-3)가 범위 스캔한다
);
