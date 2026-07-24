-- V50__routine.sql — 루틴 큐레이션 템플릿(피부타입×시간대). 추천 상품은 goods_no 논리참조(물리 FK 없음).
CREATE TABLE routine_template (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(80)  NOT NULL,
  skin_type   VARCHAR(20)  NOT NULL,           -- DRY|OILY|COMBINATION|SENSITIVE
  time_slot   VARCHAR(20)  NOT NULL,           -- 1차: BASIC 하나(아침/저녁 미구분)
  description  VARCHAR(300) NOT NULL,
  CONSTRAINT uq_routine_template UNIQUE (skin_type, time_slot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE routine_step (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  template_id  BIGINT      NOT NULL,
  step_order   TINYINT     NOT NULL,           -- 1..5
  step_name    VARCHAR(40) NOT NULL,           -- 클렌징 / 토너 / 세럼 / 크림 / 선크림
  beginner_tip VARCHAR(200) NOT NULL,
  CONSTRAINT fk_step_template FOREIGN KEY (template_id) REFERENCES routine_template(id),
  CONSTRAINT uq_step_order UNIQUE (template_id, step_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE routine_step_goods (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  step_id   BIGINT  NOT NULL,
  goods_no  BIGINT  NOT NULL,                  -- goods(goods_no) 논리참조. 물리 FK 없음(패키지 경계).
  sort_order TINYINT NOT NULL,
  CONSTRAINT fk_step_goods_step FOREIGN KEY (step_id) REFERENCES routine_step(id),
  CONSTRAINT uq_step_goods UNIQUE (step_id, goods_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
