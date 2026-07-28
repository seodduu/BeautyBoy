package com.beautyboy.catalog;

/**
 * 상품 리뷰수 동기화 커맨드 경계. 값의 진실은 review 도메인(goods_review_stat)이고,
 * catalog는 정렬용 사본(goods.review_count)만 가진다. review의 재집계가 계산한 값을
 * 그대로 받아 쓴다 — 증감 누적이 아니라 set이므로 멱등이고 드리프트가 없다.
 *
 * <p>호출 계약: 호출자(review)의 트랜잭션 안에서만 부른다(구현이 MANDATORY로 강제) —
 * 리뷰 저장과 카운트 갱신이 원자적으로 함께 커밋/롤백된다.
 */
public interface GoodsReviewCountCommand {

    void syncReviewCount(Long goodsId, int reviewCount);
}
