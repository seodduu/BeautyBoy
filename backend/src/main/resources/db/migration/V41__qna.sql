-- 상품 Q&A. 답변은 관리자만 다는데 admin 기능은 Wave 4라, 이 웨이브는 질문 등록·조회까지다.
-- answer/answered_at 컬럼은 미리 두되(스키마가 진실), 답변 등록 API는 만들지 않는다.
--
-- is_secret: 비밀글이면 작성자와 관리자만 본문을 볼 수 있다. 목록에는 "비밀글입니다"로 표시되고
-- 본문은 내려가지 않는다 — 접근 판정은 애플리케이션이 한다(T3-6).
CREATE TABLE qna (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL,
  goods_id BIGINT NOT NULL,
  question VARCHAR(1000) NOT NULL,
  answer VARCHAR(2000) NULL,
  is_secret BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(20) NOT NULL DEFAULT 'WAITING',  -- WAITING|ANSWERED
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  answered_at DATETIME NULL,
  INDEX idx_qna_goods_created (goods_id, created_at),
  CONSTRAINT fk_qna_member FOREIGN KEY (member_id) REFERENCES member(id)
);
