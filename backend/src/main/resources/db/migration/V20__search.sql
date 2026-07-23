-- 검색어 원장. 인기검색어는 이 로그를 24시간 창으로 집계해 만든다(설계 8장).
-- 비로그인도 검색하므로 member_id는 NULL 허용이고 FK를 걸지 않는다 —
-- 로그는 회원이 탈퇴해도 통계로 남아야 하고, FK가 있으면 탈퇴가 로그 삭제를 강요한다.
CREATE TABLE search_keyword_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  keyword VARCHAR(100) NOT NULL,
  member_id BIGINT NULL,
  searched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  -- 집계는 항상 "최근 N시간 × 키워드별 건수"라 이 순서가 곧 커버링 인덱스가 된다.
  INDEX idx_search_log_searched_at_keyword (searched_at, keyword)
);
