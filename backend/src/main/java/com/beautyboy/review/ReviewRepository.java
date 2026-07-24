package com.beautyboy.review;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByGoodsIdOrderByCreatedAtDesc(Long goodsId, Pageable pageable);

    long countByGoodsId(Long goodsId);

    boolean existsByMemberIdAndGoodsId(Long memberId, Long goodsId);

    List<Review> findByMemberIdOrderByIdDesc(Long memberId, Pageable pageable);

    long countByMemberId(Long memberId);

    @Query("select count(r), coalesce(sum(r.rating),0) from Review r where r.goodsId = :goodsNo")
    Object[] aggregate(Long goodsNo);
}
