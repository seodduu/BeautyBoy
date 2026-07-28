package com.beautyboy.catalog;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoodsReviewCountService implements GoodsReviewCountCommand {

    private final EntityManager em;

    public GoodsReviewCountService(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void syncReviewCount(Long goodsId, int reviewCount) {
        // 엔티티 로드 없이 벌크 UPDATE — 이 트랜잭션의 영속성 컨텍스트에 Goods가 올라와 있지 않은
        // 경로(리뷰 작성)에서 불리므로 1차 캐시 불일치 우려가 없다.
        em.createQuery("update Goods g set g.reviewCount = :count where g.id = :goodsId")
                .setParameter("count", reviewCount)
                .setParameter("goodsId", goodsId)
                .executeUpdate();
    }
}
