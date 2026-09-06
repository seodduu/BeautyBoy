package com.beautyboy.routine;

import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.catalog.dto.GoodsListItem;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RoutineCandidateService}의 순수 로직만 본다. {@link GoodsQueryService}는 스텁으로
 * 넣는다(계획서 §5 B2-5).
 */
class RoutineCandidateServiceTest {

    private static final String CATEGORY = "C001001";
    private static final Clock 고정시계 = Clock.fixed(Instant.parse("2026-09-06T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void 앞_스물여덟개는_인기순_그대로다() {
        StubGoodsQueryService stub = new StubGoodsQueryService(전체상품(100));
        RoutineCandidateService service = new RoutineCandidateService(stub, 고정시계);

        List<Long> result = service.pickGoodsNos(CATEGORY, 30);

        assertThat(result.subList(0, 28)).containsExactlyElementsOf(stub.popularityOrder().subList(0, 28));
    }

    @Test
    void 뒤_두개는_인기_스물아홉위_밖에서_온다() {
        StubGoodsQueryService stub = new StubGoodsQueryService(전체상품(100));
        RoutineCandidateService service = new RoutineCandidateService(stub, 고정시계);

        List<Long> result = service.pickGoodsNos(CATEGORY, 30);
        List<Long> top28 = stub.popularityOrder().subList(0, 28);
        List<Long> rear = result.subList(28, 30);

        assertThat(rear).hasSize(2);
        assertThat(top28).doesNotContainAnyElementsOf(rear);
    }

    @Test
    void 같은_날_같은_카테고리는_같은_두개다() {
        StubGoodsQueryService stub = new StubGoodsQueryService(전체상품(100));
        RoutineCandidateService service = new RoutineCandidateService(stub, 고정시계);

        List<Long> first = service.pickGoodsNos(CATEGORY, 30).subList(28, 30);
        List<Long> second = service.pickGoodsNos(CATEGORY, 30).subList(28, 30);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void 날짜가_다르면_두개가_달라질_수_있다() {
        StubGoodsQueryService stub = new StubGoodsQueryService(전체상품(100));
        List<Long> baseline = new RoutineCandidateService(stub, 고정시계).pickGoodsNos(CATEGORY, 30).subList(28, 30);

        boolean 달라진_적_있음 = false;
        for (int day = 1; day <= 30; day++) {
            Clock 다른날 = Clock.fixed(고정시계.instant().plusSeconds(86400L * day), ZoneOffset.UTC);
            List<Long> other = new RoutineCandidateService(stub, 다른날).pickGoodsNos(CATEGORY, 30).subList(28, 30);
            if (!other.equals(baseline)) {
                달라진_적_있음 = true;
                break;
            }
        }

        assertThat(달라진_적_있음).isTrue();
    }

    @Test
    void 꼬리가_없으면_스물여덟개만_준다() {
        // 카탈로그 전체가 28개뿐이면 무작위로 고를 꼬리가 없다.
        StubGoodsQueryService stub = new StubGoodsQueryService(전체상품(28));
        RoutineCandidateService service = new RoutineCandidateService(stub, 고정시계);

        List<Long> result = service.pickGoodsNos(CATEGORY, 30);

        assertThat(result).hasSize(28);
    }

    @Test
    void 순서가_보존된다() {
        StubGoodsQueryService stub = new StubGoodsQueryService(전체상품(100));
        stub.셔플해서_돌려준다(true);
        RoutineCandidateService service = new RoutineCandidateService(stub, 고정시계);

        List<GoodsListItem> result = service.candidates(CATEGORY, 30, null);
        List<Long> expected = service.pickGoodsNos(CATEGORY, 30);

        assertThat(result).extracting(GoodsListItem::goodsNo).containsExactlyElementsOf(expected);
    }

    private List<Long> 전체상품(int count) {
        return IntStream.rangeClosed(1, count).mapToLong(i -> (long) i).boxed().toList();
    }

    /** 조회수 내림차순 인기 목록을 고정으로 흉내 내는 스텁. */
    private static final class StubGoodsQueryService implements GoodsQueryService {

        private final List<Long> popularityOrder;
        private boolean 셔플;

        private StubGoodsQueryService(List<Long> popularityOrder) {
            this.popularityOrder = popularityOrder;
        }

        List<Long> popularityOrder() {
            return popularityOrder;
        }

        void 셔플해서_돌려준다(boolean value) {
            this.셔플 = value;
        }

        @Override
        public boolean exists(Long goodsNo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String categoryCode(Long goodsNo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<OrderGoodsSnapshot> findOrderSnapshot(Long goodsNo, Long optionNo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<OrderSnapshotKey, OrderGoodsSnapshot> findOrderSnapshots(Collection<OrderSnapshotKey> keys) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<GoodsListItem> findListItems(Collection<Long> goodsNos, Long viewerId) {
            List<Long> nos = new ArrayList<>(new HashSet<>(goodsNos));
            if (셔플) {
                java.util.Collections.reverse(nos);
            }
            return nos.stream()
                    .map(no -> new GoodsListItem(no, "브랜드", "상품" + no, "https://img.example/x.jpg",
                            10000, 10000, 0, List.of(), 0.0, 0, false, false, List.of()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<Long> findCandidateGoodsNos(String categoryCodePrefix, String tagSlug, Long excludeGoodsNo,
                                                  int limit) {
            return popularityOrder.stream().limit(Math.max(0, limit)).toList();
        }

        @Override
        public Set<String> tagSlugs(Long goodsNo) {
            throw new UnsupportedOperationException();
        }
    }
}
