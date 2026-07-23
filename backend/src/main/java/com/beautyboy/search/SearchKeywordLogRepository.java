package com.beautyboy.search;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SearchKeywordLogRepository extends JpaRepository<SearchKeywordLog, Long> {

    /**
     * 기준 시각 이후 검색어를 건수 내림차순으로. 상위 N건만 필요하므로 Pageable로 자른다.
     *
     * <p>동점일 때 키워드 오름차순을 2차 키로 붙인다 — 없으면 집계할 때마다 순서가 흔들려
     * "인기검색어가 새로고침마다 바뀐다"는 버그로 보인다.
     */
    @Query("select l.keyword from SearchKeywordLog l "
            + "where l.searchedAt >= :from group by l.keyword order by count(l) desc, l.keyword asc")
    List<String> findTopKeywordsSince(@Param("from") LocalDateTime from, Pageable pageable);
}
