package com.beautyboy.qna;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QnaRepository extends JpaRepository<Qna, Long> {

    List<Qna> findByGoodsIdOrderByCreatedAtDesc(Long goodsId, Pageable pageable);

    long countByGoodsId(Long goodsId);

    /**
     * admin 목록 전용 — 상품 필터 없이 전체를 훑되 미답변(WAITING)을 먼저 보여준다.
     * 답변 대기 문의가 쌓이면 눈에 띄어야 admin이 놓치지 않는다.
     */
    @Query("select q from Qna q order by case when q.status = 'WAITING' then 0 else 1 end, q.createdAt desc")
    List<Qna> findAllOrderByWaitingFirst(Pageable pageable);
}
