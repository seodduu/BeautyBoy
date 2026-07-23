-- 상품×날짜 일별 통계 원장. 조회수는 상세 조회 때 실시간 증가하고(T1-6),
-- 판매·찜은 매시 배치가 타 도메인 Provider에서 받아 채운다(T1-7).
-- PK를 (goods_id, stat_date) 복합으로 잡아 upsert(ON DUPLICATE KEY UPDATE)가 성립하게 한다.
CREATE TABLE goods_daily_stat (
  goods_id BIGINT NOT NULL,
  stat_date DATE NOT NULL,
  view_count INT NOT NULL DEFAULT 0,
  sales_count INT NOT NULL DEFAULT 0,
  wish_count INT NOT NULL DEFAULT 0,
  PRIMARY KEY (goods_id, stat_date),
  -- 배치가 "최근 3일 전체"를 훑으므로 날짜 선행 인덱스가 필요하다.
  INDEX idx_goods_daily_stat_date (stat_date)
);

-- 매시 배치가 통째로 교체하는 랭킹 결과. 조회는 이 테이블만 읽는다(설계 5장).
-- category_code는 대분류(C001 등)이고, 전체 랭킹은 'ALL'이라는 예약값을 쓴다 —
-- NULL로 두면 "전체"와 "미분류"가 구분되지 않고 인덱스에서도 다루기 번거롭다.
CREATE TABLE ranking_snapshot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_code VARCHAR(12) NOT NULL,
  goods_id BIGINT NOT NULL,
  rank_no INT NOT NULL,
  score DOUBLE NOT NULL,
  generated_at DATETIME NOT NULL,
  UNIQUE KEY uk_ranking_category_rank (category_code, rank_no)
);
