package com.beautyboy.qna;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QnaRepository extends JpaRepository<Qna, Long> {

    List<Qna> findByGoodsIdOrderByCreatedAtDesc(Long goodsId, Pageable pageable);

    long countByGoodsId(Long goodsId);
}
