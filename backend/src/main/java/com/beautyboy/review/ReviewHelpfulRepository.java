package com.beautyboy.review;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewHelpfulRepository extends JpaRepository<ReviewHelpful, Long> {

    boolean existsByReviewIdAndMemberId(Long reviewId, Long memberId);
}
