-- 상품 리뷰. 구매인증은 order_item FK가 아니라 order.OrderQueryService로 코드에서 확인한다
-- (패키지 경계: review는 order 테이블에 직접 접근하지 않는다). 그래서 여기에는 FK를 걸지 않는다.
--
-- UNIQUE(member_id, goods_id): 한 회원은 한 상품에 리뷰 1개. 설계의 "이미 리뷰 작성한 주문"을
-- MVP에서는 상품 단위로 단순화한다 — order_item 단위 중복 방지는 orderItemId를 프론트가 들고
-- 다녀야 해 흐름이 무거워지고, 상품당 1리뷰가 커머스 리뷰의 일반적 기대에 더 맞는다.
--
-- skin_type_snapshot: 작성 시점의 회원 피부타입을 복사한다(설계 5장). 프로필이 바뀌어도
-- "이 리뷰를 쓸 때 이 사람의 피부는 무엇이었나"는 그 시점의 사실이라 변하면 안 된다. NULL 허용
-- (피부타입 미입력 회원). member 도메인의 값이지만 스냅샷이라 FK로 참조하지 않는다.
CREATE TABLE review (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL,
  goods_id BIGINT NOT NULL,
  rating TINYINT NOT NULL,               -- 1~5. 범위 검증은 애플리케이션이 한다.
  content VARCHAR(2000) NOT NULL,
  skin_type_snapshot VARCHAR(20) NULL,
  helpful_count INT NOT NULL DEFAULT 0,  -- review_helpful 집계 비정규화(정렬용). 눌림/취소 시 갱신.
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_review_member_goods (member_id, goods_id),
  -- 상품 상세의 리뷰 목록은 "이 상품의 리뷰 최신순/도움순"이라 goods_id 선행 인덱스가 필요하다.
  INDEX idx_review_goods_created (goods_id, created_at),
  CONSTRAINT fk_review_member FOREIGN KEY (member_id) REFERENCES member(id)
);

-- 도움됐어요. member×review 유니크(설계 5장) — 한 사람이 같은 리뷰에 여러 번 못 누른다.
CREATE TABLE review_helpful (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  review_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_review_helpful (review_id, member_id),
  CONSTRAINT fk_review_helpful_review FOREIGN KEY (review_id) REFERENCES review(id)
);

-- 상품별 평점 평균·개수 비정규화(설계 5장). 리뷰 작성·삭제 때 재집계해 upsert한다.
-- 상품 목록/상세가 이 테이블만 읽어 매번 AVG를 돌리지 않게 하는 것이 목적이다.
-- goods_id를 PK로 두어 상품당 1행을 upsert로 유지한다.
CREATE TABLE goods_review_stat (
  goods_id BIGINT PRIMARY KEY,
  review_count INT NOT NULL DEFAULT 0,
  rating_sum INT NOT NULL DEFAULT 0,      -- 평균을 저장하지 않고 합/개수를 저장한다 — 부동소수 누적오차가 없고 재집계가 정확하다.
  updated_at DATETIME NOT NULL
);
