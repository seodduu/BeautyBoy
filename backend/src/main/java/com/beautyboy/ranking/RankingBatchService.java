package com.beautyboy.ranking;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 랭킹 스냅샷 재생성 배치.
 *
 * <p>순서: (1) 찜 Provider에서 오늘의 찜을 받아 일별 통계에 반영 →
 * (2) 최근 3일 통계를 읽어 가중 점수 계산 → (3) 스냅샷을 트랜잭션 안에서 통째 교체.
 *
 * <p><b>판매 수집은 여기서 하지 않는다(A4b).</b> 판매량은 주문 확정 시점에 증분으로 쌓인다
 * ({@code GoodsDailyStatRepository.upsertSalesIncrement}). 배치가 주문 테이블을 다시 집계해
 * 대입하면 그 증분이 통째로 덮여 사라지므로, 판매는 <b>증분 경로 하나만</b> 남긴다(설계 §2-3).
 * 배치는 자기가 쓰지 않는 {@code sales_count}를 읽어 점수에만 반영한다.
 *
 * <p>(3)이 한 트랜잭션인 것이 핵심이다. 지우고 커밋한 뒤 새로 넣으면 그 사이에 들어온 조회 요청이
 * "랭킹 없음"을 본다. 매시 몇 초씩 랭킹이 사라지는 것은 장애로 보인다.
 */
@Service
public class RankingBatchService {

    /** 설계 5장: 판매×3 + 찜×2 + 조회×1. */
    private static final int SALES_WEIGHT = 3;
    private static final int WISH_WEIGHT = 2;
    private static final int VIEW_WEIGHT = 1;

    /**
     * 최근 3일 가중치(오늘 → 그저께). 오늘 일어난 일을 가장 크게 본다.
     *
     * <p>이 값이 랭킹의 성격을 정한다 — 평평하게 두면 3일 내내 같은 순위가 굳고,
     * 너무 가파르면 하루 반짝한 상품이 계속 1위를 갈아치운다. 1.0 / 0.6 / 0.3은
     * "어제 것이 오늘 것의 절반보다 조금 더" 정도의 감쇠다.
     */
    private static final double[] DAY_WEIGHTS = {1.0, 0.6, 0.3};

    /** 카테고리당 보관하는 최대 순위. 랭킹 화면이 그 이상 보여주지 않는다. */
    private static final int MAX_RANK = 50;

    private final GoodsDailyStatRepository goodsDailyStatRepository;
    private final RankingSnapshotRepository rankingSnapshotRepository;
    private final WishStatProvider wishStatProvider;
    private final EntityManager em;

    public RankingBatchService(GoodsDailyStatRepository goodsDailyStatRepository,
                               RankingSnapshotRepository rankingSnapshotRepository,
                               WishStatProvider wishStatProvider,
                               EntityManager em) {
        this.goodsDailyStatRepository = goodsDailyStatRepository;
        this.rankingSnapshotRepository = rankingSnapshotRepository;
        this.wishStatProvider = wishStatProvider;
        this.em = em;
    }

    @Transactional
    public void rebuild() {
        LocalDate today = LocalDate.now();

        수집_찜(today);
        Map<Long, Double> scoreByGoodsId = 점수_계산(today);
        스냅샷_통째_교체(scoreByGoodsId, LocalDateTime.now());
    }

    /**
     * Provider가 준 오늘의 찜 수치를 일별 통계에 대입한다.
     * 조회수는 인터셉터가, 판매량은 주문 확정 후처리가 이미 실시간으로 채워 놨다 —
     * {@code upsertWishCount}는 그 둘을 건드리지 않는다.
     */
    private void 수집_찜(LocalDate today) {
        Map<Long, Integer> wishes = wishStatProvider.wishCountByGoods(today);

        for (Map.Entry<Long, Integer> wish : wishes.entrySet()) {
            goodsDailyStatRepository.upsertWishCount(wish.getKey(), today, wish.getValue());
        }
        // upsert는 네이티브 쿼리라 영속성 컨텍스트를 우회한다.
        // 바로 아래에서 같은 행을 읽으므로 캐시를 비워 DB 값을 보게 한다.
        em.flush();
        em.clear();
    }

    private Map<Long, Double> 점수_계산(LocalDate today) {
        LocalDate from = today.minusDays(DAY_WEIGHTS.length - 1L);
        List<GoodsDailyStat> stats = goodsDailyStatRepository.findByStatDateGreaterThanEqual(from);

        Map<Long, Double> scoreByGoodsId = new HashMap<>();
        for (GoodsDailyStat stat : stats) {
            int daysAgo = (int) (today.toEpochDay() - stat.getStatDate().toEpochDay());
            // 미래 날짜(시계 오차)나 창 밖은 건너뛴다.
            if (daysAgo < 0 || daysAgo >= DAY_WEIGHTS.length) {
                continue;
            }
            double weighted = DAY_WEIGHTS[daysAgo] * (
                    stat.getSalesCount() * SALES_WEIGHT
                            + stat.getWishCount() * WISH_WEIGHT
                            + stat.getViewCount() * VIEW_WEIGHT);
            scoreByGoodsId.merge(stat.getGoodsId(), weighted, Double::sum);
        }
        return scoreByGoodsId;
    }

    /**
     * 스냅샷 교체.
     *
     * <p>상품의 카테고리는 catalog 소유라 엔티티로 읽을 수 없다 — 필요한 것은 goods_id → category_code
     * 매핑 하나뿐이므로 네이티브 쿼리로 최소한만 읽는다. 이것이 타 도메인 리포지토리를 import하는 것보다
     * 결합이 얕다(테이블 이름 하나만 안다).
     */
    private void 스냅샷_통째_교체(Map<Long, Double> scoreByGoodsId, LocalDateTime generatedAt) {
        rankingSnapshotRepository.deleteAllInBatch();
        em.flush();

        Map<Long, String> categoryByGoodsId = 노출중인_상품의_대분류();

        if (categoryByGoodsId.isEmpty()) {
            return;
        }

        // 카테고리별 버킷. 'ALL'은 전 상품이 들어간다.
        // 기준 집합은 "노출중인 상품 전체"다 — 최근 3일 활동이 없는 상품도 점수 0으로 랭킹에 든다.
        // 그래야 신규 카테고리처럼 활동이 아직 없는 곳도 목록이 빈 화면이 아니라 상품 나열로 보인다.
        Map<String, List<Map.Entry<Long, Double>>> bucketByCategory = new LinkedHashMap<>();
        for (Map.Entry<Long, String> visible : categoryByGoodsId.entrySet()) {
            Map.Entry<Long, Double> entry = Map.entry(
                    visible.getKey(), scoreByGoodsId.getOrDefault(visible.getKey(), 0.0));
            bucketByCategory.computeIfAbsent(RankingSnapshot.CATEGORY_ALL, k -> new ArrayList<>()).add(entry);
            bucketByCategory.computeIfAbsent(visible.getValue(), k -> new ArrayList<>()).add(entry);
        }

        List<RankingSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, List<Map.Entry<Long, Double>>> bucket : bucketByCategory.entrySet()) {
            List<Map.Entry<Long, Double>> sorted = new ArrayList<>(bucket.getValue());
            // 점수 내림차순, 동점이면 goodsId 오름차순 — 2차 키가 없으면 배치마다 순위가 흔들린다.
            sorted.sort(Comparator.<Map.Entry<Long, Double>>comparingDouble(Map.Entry::getValue).reversed()
                    .thenComparing(Map.Entry::getKey));

            int rank = 1;
            for (Map.Entry<Long, Double> entry : sorted) {
                if (rank > MAX_RANK) {
                    break;
                }
                snapshots.add(new RankingSnapshot(
                        bucket.getKey(), entry.getKey(), rank++, entry.getValue(), generatedAt));
            }
        }

        rankingSnapshotRepository.saveAll(snapshots);
    }

    private Map<Long, String> 노출중인_상품의_대분류() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "select id, substring(category_code, 1, 4) from goods where status <> 'HIDDEN'")
                .getResultList();

        Map<Long, String> categoryByGoodsId = new HashMap<>();
        for (Object[] row : rows) {
            categoryByGoodsId.put(((Number) row[0]).longValue(), (String) row[1]);
        }
        return categoryByGoodsId;
    }
}
