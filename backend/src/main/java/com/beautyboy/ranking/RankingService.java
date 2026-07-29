package com.beautyboy.ranking;

import com.beautyboy.catalog.GoodsRatingProvider;
import com.beautyboy.catalog.WishedGoodsProvider;
import com.beautyboy.ranking.dto.RankingItem;
import jakarta.persistence.EntityManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 랭킹 조회. 스냅샷만 읽고 점수를 다시 계산하지 않는다(설계 5장).
 *
 * <p>상품 정보를 붙이는 방식: 스냅샷에서 goods_id 목록을 뽑아 <b>1쿼리로 일괄 조회</b>한 뒤
 * 메모리에서 합친다. 순위마다 상품을 조회하면 50번 왕복하는 N+1이 된다.
 * catalog 리포지토리를 import하지 않기 위해 필요한 컬럼만 네이티브로 읽는다.
 *
 * <p>별점·찜은 catalog가 정의한 {@link GoodsRatingProvider}/{@link WishedGoodsProvider}를 통해서만
 * 채운다 — ranking은 review/wishlist 테이블을 직접 알 수 없다(패키지 = 서비스 경계). 이 둘도 goods_id
 * 목록을 모아 각각 한 번씩만 부른다.
 */
@Service
public class RankingService {

    private final RankingSnapshotRepository rankingSnapshotRepository;
    private final EntityManager em;
    private final GoodsRatingProvider goodsRatingProvider;
    private final WishedGoodsProvider wishedGoodsProvider;

    public RankingService(RankingSnapshotRepository rankingSnapshotRepository, EntityManager em,
                           GoodsRatingProvider goodsRatingProvider, WishedGoodsProvider wishedGoodsProvider) {
        this.rankingSnapshotRepository = rankingSnapshotRepository;
        this.em = em;
        this.goodsRatingProvider = goodsRatingProvider;
        this.wishedGoodsProvider = wishedGoodsProvider;
    }

    /**
     * B2 — 캐시 키는 카테고리와 viewerId를 함께 반영한다({@code categoryCode + ':' + viewerId}).
     * 설계 §6은 키를 카테고리만으로 잡았지만(기간 파라미터는 이 메서드엔 없다), 실제 시그니처는
     * viewerId로 찜 여부({@link RankingItem#wished()})가 갈리는 개인화 응답을 반환한다. 카테고리만
     * 키로 쓰면 먼저 조회한 사용자의 찜 상태가 다른 사용자에게도 그대로 캐시돼 나가는 오답이 된다
     * (서로 다른 조회가 같은 키로 뭉개짐). {@link RankingCacheRefresher}의 워밍은 viewerId=null
     * (비로그인) 슬롯만 미리 채운다 — 로그인 사용자의 캐시는 첫 조회 때 자연히 채워진다.
     */
    @Cacheable(cacheNames = "ranking",
            key = "(#categoryCode == null || #categoryCode.isBlank() "
                    + "? T(com.beautyboy.ranking.RankingSnapshot).CATEGORY_ALL : #categoryCode) "
                    + "+ ':' + #viewerId")
    @Transactional(readOnly = true)
    public List<RankingItem> rankings(String categoryCode, Long viewerId) {
        String category = (categoryCode == null || categoryCode.isBlank())
                ? RankingSnapshot.CATEGORY_ALL
                : categoryCode;

        List<RankingSnapshot> snapshots =
                rankingSnapshotRepository.findByCategoryCodeOrderByRankNoAsc(category);
        if (snapshots.isEmpty()) {
            return List.of();
        }

        List<Long> goodsIds = snapshots.stream().map(RankingSnapshot::getGoodsId).toList();
        Map<Long, Object[]> goodsById = 상품_일괄_조회(goodsIds);
        Map<Long, GoodsRatingProvider.RatingStat> ratingsByGoodsId = goodsRatingProvider.ratingsByGoods(goodsIds);
        Set<Long> wishedGoodsIds = wishedGoodsProvider.wishedGoodsIds(viewerId, goodsIds);

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
            GoodsRatingProvider.RatingStat ratingStat = ratingsByGoodsId.get(snapshot.getGoodsId());
            items.add(new RankingItem(
                    snapshot.getRankNo(),
                    snapshot.getGoodsId(),
                    (String) goods[0],
                    (String) goods[1],
                    (String) goods[2],
                    listPrice,
                    salePrice,
                    discountRate(listPrice, salePrice),
                    ratingStat == null ? 0.0 : ratingStat.rating(),
                    ratingStat == null ? 0 : ratingStat.reviewCount(),
                    wishedGoodsIds.contains(snapshot.getGoodsId()),
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
