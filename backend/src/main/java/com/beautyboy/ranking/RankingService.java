package com.beautyboy.ranking;

import com.beautyboy.ranking.dto.RankingItem;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 랭킹 조회. 스냅샷만 읽고 점수를 다시 계산하지 않는다(설계 5장).
 *
 * <p>상품 정보를 붙이는 방식: 스냅샷에서 goods_id 목록을 뽑아 <b>1쿼리로 일괄 조회</b>한 뒤
 * 메모리에서 합친다. 순위마다 상품을 조회하면 50번 왕복하는 N+1이 된다.
 * catalog 리포지토리를 import하지 않기 위해 필요한 컬럼만 네이티브로 읽는다.
 */
@Service
public class RankingService {

    private final RankingSnapshotRepository rankingSnapshotRepository;
    private final EntityManager em;

    public RankingService(RankingSnapshotRepository rankingSnapshotRepository, EntityManager em) {
        this.rankingSnapshotRepository = rankingSnapshotRepository;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public List<RankingItem> rankings(String categoryCode) {
        String category = (categoryCode == null || categoryCode.isBlank())
                ? RankingSnapshot.CATEGORY_ALL
                : categoryCode;

        List<RankingSnapshot> snapshots =
                rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc(category);
        if (snapshots.isEmpty()) {
            return List.of();
        }

        Map<Long, Object[]> goodsById = 상품_일괄_조회(
                snapshots.stream().map(RankingSnapshot::getGoodsId).toList());

        List<RankingItem> items = new ArrayList<>();
        for (RankingSnapshot snapshot : snapshots) {
            Object[] goods = goodsById.get(snapshot.getGoodsId());
            // 배치 이후 숨겨진 상품은 스냅샷에 남아 있을 수 있다. 순위를 비우지 않고 그 행만 건너뛴다
            // (재계산은 다음 배치의 몫이다 — 조회 요청이 랭킹을 고치기 시작하면 읽기 경로가 무거워진다).
            if (goods == null) {
                continue;
            }
            int listPrice = ((Number) goods[3]).intValue();
            int salePrice = ((Number) goods[4]).intValue();
            items.add(new RankingItem(
                    snapshot.getRankNo(),
                    snapshot.getGoodsId(),
                    (String) goods[0],
                    (String) goods[1],
                    (String) goods[2],
                    listPrice,
                    salePrice,
                    discountRate(listPrice, salePrice),
                    snapshot.getScore()));
        }
        return items;
    }

    private Map<Long, Object[]> 상품_일괄_조회(List<Long> goodsIds) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "select g.id, b.name, g.name, g.thumbnail_url, g.list_price, g.sale_price "
                                + "from goods g join brand b on g.brand_id = b.id "
                                + "where g.id in (:goodsIds) and g.status <> 'HIDDEN'")
                .setParameter("goodsIds", goodsIds)
                .getResultList();

        Map<Long, Object[]> goodsById = new HashMap<>();
        for (Object[] row : rows) {
            goodsById.put(((Number) row[0]).longValue(),
                    new Object[]{row[1], row[2], row[3], row[4], row[5]});
        }
        return goodsById;
    }

    private int discountRate(int listPrice, int salePrice) {
        if (listPrice == 0) {
            return 0;
        }
        return (listPrice - salePrice) * 100 / listPrice;
    }
}
