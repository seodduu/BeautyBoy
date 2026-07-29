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
     * 판매 수량 증분. 주문이 확정될 때마다 그 줄의 수량만큼 더한다(A4b 후처리 → A5 컨슈머).
     *
     * <p><b>판매 수치를 쓰는 경로는 이것 하나뿐이다.</b> 예전에는 랭킹 배치가 주문 테이블을 매시
     * 재집계해 {@code sales_count}에 <b>대입</b>했는데(구 {@code upsertSalesAndWish}), 증분 경로와
     * 공존하면 배치가 도는 순간 그때까지 쌓인 증분이 배치가 넘긴 값으로 덮여 사라진다.
     * 설계 §2-3의 "한 시점에 한 경로만"이 가리키는 지점이 정확히 여기라서, 대입 메서드를
     * 남겨두지 않고 아래 {@link #upsertWishCount}(찜 전용)로 좁혔다 — 판매를 덮어쓸 수 있는
     * 메서드가 아예 없으면 실수로 되살릴 수도 없다.
     */
    @Modifying
    @Query(value = "insert into goods_daily_stat (goods_id, stat_date, view_count, sales_count, wish_count) "
            + "values (:goodsId, :statDate, 0, :quantity, 0) "
            + "on duplicate key update sales_count = sales_count + :quantity", nativeQuery = true)
    void upsertSalesIncrement(@Param("goodsId") Long goodsId,
                              @Param("statDate") LocalDate statDate,
                              @Param("quantity") int quantity);

    /**
     * 찜 수치 덮어쓰기. 배치가 {@link WishStatProvider}에서 받은 값을 그대로 반영한다.
     *
     * <p>조회·판매와 달리 더하지 않고 <b>대입</b>한다 — Provider가 주는 값은 이미 그 날의 합계라
     * 더하기로 하면 매시 도는 배치가 값을 계속 부풀린다.
     *
     * <p><b>{@code sales_count}를 건드리지 않는 것이 이 메서드의 핵심 계약이다.</b>
     * update 절에 판매가 없으므로 배치가 몇 번을 돌아도 증분된 판매량은 그대로 남는다.
     * (INSERT 쪽 {@code 0}은 행이 없을 때만 쓰이므로 기존 값을 지우지 않는다.)
     */
    @Modifying
    @Query(value = "insert into goods_daily_stat (goods_id, stat_date, view_count, sales_count, wish_count) "
            + "values (:goodsId, :statDate, 0, 0, :wishCount) "
            + "on duplicate key update wish_count = :wishCount", nativeQuery = true)
    void upsertWishCount(@Param("goodsId") Long goodsId,
                         @Param("statDate") LocalDate statDate,
                         @Param("wishCount") int wishCount);

    /** 기준일 이후 통계 전체. 배치가 최근 3일치를 한 번에 읽는다. */
    List<GoodsDailyStat> findByStatDateGreaterThanEqual(LocalDate from);
}
