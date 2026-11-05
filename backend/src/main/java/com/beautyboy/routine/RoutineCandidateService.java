package com.beautyboy.routine;

import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 루틴 화면 후보 30개 조립(계획서 §5 B2-5, 로드맵 §3.3). 인기순 상위 {@code size - 2}개에
 * 같은 카테고리 접두사 안 무작위 2개를 이어 붙인다.
 *
 * <p>무작위 2개의 시드는 {@code categoryCode + "|" + 오늘 날짜}다. 같은 날 같은 카테고리면
 * 항상 같은 두 개가 나온다. 새로고침마다 픽이 바뀌면 사용자가 추천을 믿지 못하기 때문이다.
 *
 * <p>이 저장소에는 아직 전역 {@code Clock} 빈이 없다({@code BehaviorEventRateLimiter}와 같은
 * 사정). 그래서 새 설정 클래스를 만드는 대신, 스프링이 쓰는 생성자는
 * {@link Clock#systemDefaultZone()}을 기본값으로 받는다. 테스트는 패키지 전용 생성자로
 * {@code Clock.fixed(...)}를 직접 넣어 날짜를 고정한다.
 */
@Service
public class RoutineCandidateService {

    static final int MIN_SIZE = 1;
    static final int MAX_SIZE = 30;
    private static final int RANDOM_PICK_COUNT = 2;
    private static final int RANDOM_POOL_MARGIN = 200;

    private final GoodsQueryService goodsQueryService;
    private final Clock clock;

    @Autowired
    public RoutineCandidateService(GoodsQueryService goodsQueryService) {
        this(goodsQueryService, Clock.systemDefaultZone());
    }

    /** 테스트가 {@code Clock.fixed(...)}를 갈아 끼우기 위한 생성자. */
    RoutineCandidateService(GoodsQueryService goodsQueryService, Clock clock) {
        this.goodsQueryService = goodsQueryService;
        this.clock = clock;
    }

    public List<GoodsListItem> candidates(String categoryCode, int size, Long viewerId) {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        List<Long> orderedGoodsNos = pickGoodsNos(categoryCode, size);
        if (orderedGoodsNos.isEmpty()) {
            return List.of();
        }
        Map<Long, GoodsListItem> byGoodsNo = goodsQueryService.findListItems(orderedGoodsNos, viewerId).stream()
                .collect(Collectors.toMap(GoodsListItem::goodsNo, item -> item));
        // findListItems는 입력 순서를 보존하지 않으므로 인기순 뒤 무작위 순서로 직접 정렬한다.
        return orderedGoodsNos.stream().map(byGoodsNo::get).filter(Objects::nonNull).toList();
    }

    /** 인기순 {@code size - 2}개 뒤에 같은 카테고리 안 무작위 2개를 이어 붙인 goods_no 순서. */
    List<Long> pickGoodsNos(String categoryCode, int size) {
        int popularCount = Math.max(0, size - RANDOM_PICK_COUNT);
        int randomWanted = size - popularCount;

        List<Long> popular = goodsQueryService.findCandidateGoodsNos(categoryCode, null, null, popularCount);
        Set<Long> popularSet = new LinkedHashSet<>(popular);

        // 넉넉히 더 뽑은 뒤 이미 뽑힌 인기순 항목을 제외한 꼬리에서 무작위 2개를 고른다.
        List<Long> extended = goodsQueryService.findCandidateGoodsNos(
                categoryCode, null, null, popularCount + RANDOM_POOL_MARGIN);
        List<Long> tailPool = extended.stream().filter(no -> !popularSet.contains(no)).toList();

        List<Long> randomPicks = pickRandom(tailPool, randomWanted, categoryCode);

        List<Long> ordered = new ArrayList<>(popular);
        ordered.addAll(randomPicks);
        return ordered;
    }

    private List<Long> pickRandom(List<Long> pool, int wanted, String categoryCode) {
        if (wanted <= 0 || pool.isEmpty()) {
            return List.of();
        }
        long seed = (categoryCode + "|" + LocalDate.now(clock)).hashCode();
        Random random = new Random(seed);
        List<Long> mutablePool = new ArrayList<>(pool);
        List<Long> picked = new ArrayList<>();
        int count = Math.min(wanted, mutablePool.size());
        for (int i = 0; i < count; i++) {
            int index = random.nextInt(mutablePool.size());
            picked.add(mutablePool.remove(index));
        }
        return picked;
    }
}
