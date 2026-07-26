-- V74__routine_flow_rule.sql — 루틴 전이 규칙. reason이 화면 문구의 유일한 출처.
-- tag slug·category code에 물리 FK를 걸지 않는다(패키지 경계 너머 물리 FK 금지 관례).
CREATE TABLE routine_flow_rule (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  from_category_code VARCHAR(12)  NOT NULL,  -- 중분류(C001001). goods.category_code(leaf)는 접두사 매칭
  from_tag_slug      VARCHAR(40)  NULL,      -- NULL = 태그 무관
  to_category_code   VARCHAR(12)  NOT NULL,  -- 중분류
  to_tag_slug        VARCHAR(40)  NULL,      -- 추천 대상이 가져야 할 태그. NULL 허용
  edge_kind          VARCHAR(20)  NOT NULL,  -- NEXT_STEP | PAIRED_REMOVAL | BUFFER
  reason             VARCHAR(200) NOT NULL,  -- 화면에 그대로 나가는 이유 문장
  priority           INT          NOT NULL DEFAULT 0  -- 낮을수록 우선. BUFFER는 NEXT_STEP보다 낮게 시드
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
