-- 리뷰 많은 순 정렬용 비정규화 컬럼 (view_count·sales_count와 같은 계열 — V10 "결정 2" 참조).
-- 진실은 review 도메인의 goods_review_stat이고, 이 컬럼은 재집계 시점마다 같은 값으로 동기화된다
-- (GoodsReviewCountCommand). 정렬 키는 Goods 엔티티 안에 있어야 한다 — catalog JPQL은
-- 타 도메인 테이블을 조인할 수 없다(패키지 = 서비스 경계).
ALTER TABLE goods ADD COLUMN review_count INT NOT NULL DEFAULT 0;

-- 백필: 기존 리뷰 통계를 그대로 옮긴다. 리뷰 없는 상품은 DEFAULT 0.
UPDATE goods g
JOIN goods_review_stat s ON s.goods_id = g.id
SET g.review_count = s.review_count;
