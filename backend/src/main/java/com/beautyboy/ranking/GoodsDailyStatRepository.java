package com.beautyboy.ranking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface GoodsDailyStatRepository extends JpaRepository<GoodsDailyStat, GoodsDailyStat.Key> {

    /**
     * 조회수 upsert. 행이 없으면 만들고 있으면 더한다.
     *
     * <p>"조회 후 없으면 insert"로 짜면 동시 요청 2개가 동시에 '없음'을 보고 둘 다 insert해
     * PK 중복으로 하나가 500이 된다. DB에 원자적으로 맡긴다.
     *
     * <p>H2도 MySQL 모드에서 ON DUPLICATE KEY UPDATE를 지원하므로 테스트에서 같은 경로가 돈다.
     */
    @Modifying
    @Query(value = "insert into goods_daily_stat (goods_id, stat_date, view_count, sales_count, wish_count) "
            + "values (:goodsId, :statDate, :delta, 0, 0) "
            + "on duplicate key update view_count = view_count + :delta", nativeQuery = true)
    void upsertViewCount(@Param("goodsId") Long goodsId,
                         @Param("statDate") LocalDate statDate,
                         @Param("delta") int delta);

    /**
     * 판매·찜 수치 덮어쓰기. 배치가 Provider에서 받은 값을 그대로 반영한다(T1-7).
     *
     * <p>조회수와 달리 더하지 않고 **대입**한다 — Provider가 주는 값은 이미 그 날의 합계라
     * 배치가 두 번 돌면 더하기 방식은 값이 두 배가 된다(배치는 매시 도는데 날짜는 하루짜리다).
     */
    @Modifying
    @Query(value = "insert into goods_daily_stat (goods_id, stat_date, view_count, sales_count, wish_count) "
            + "values (:goodsId, :statDate, 0, :salesCount, :wishCount) "
            + "on duplicate key update sales_count = :salesCount, wish_count = :wishCount", nativeQuery = true)
    void upsertSalesAndWish(@Param("goodsId") Long goodsId,
                            @Param("statDate") LocalDate statDate,
                            @Param("salesCount") int salesCount,
                            @Param("wishCount") int wishCount);

    /** 기준일 이후 통계 전체. 배치가 최근 3일치를 한 번에 읽는다. */
    List<GoodsDailyStat> findByStatDateGreaterThanEqual(LocalDate from);
}
