package com.beautyboy.ranking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RankingSnapshotRepository extends JpaRepository<RankingSnapshot, Long> {

    List<RankingSnapshot> findByCategoryCodeOrderByRankNoAsc(String categoryCode);
}
