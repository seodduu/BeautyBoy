package com.beautyboy.review;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewHelpfulRepository extends JpaRepository<ReviewHelpful, Long> {

    boolean existsByReviewIdAndMemberId(Long reviewId, Long memberId);

    // 리뷰 삭제 시 FK(fk_review_helpful_review) 때문에 자식을 먼저 지워야 한다(설계 §2.5).
    void deleteByReviewId(Long reviewId);
}
