package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.GoodsDescriptionResponse;
import com.beautyboy.catalog.dto.GoodsDetailResponse;
import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.catalog.dto.GoodsOptionResponse;
import com.beautyboy.catalog.dto.GoodsSearchCondition;
import com.beautyboy.catalog.dto.TagView;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.CacheKeys;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.common.PageResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class GoodsService implements GoodsQueryService {

    private static final int RECOMMENDED_LIMIT = 8;

    private final GoodsRepository goodsRepository;
    private final GoodsQueryRepository goodsQueryRepository;
    private final CategoryRepository categoryRepository;
    private final GoodsRatingProvider goodsRatingProvider;
    private final WishedGoodsProvider wishedGoodsProvider;
    private final GoodsTagRepository goodsTagRepository;

    public GoodsService(GoodsRepository goodsRepository,
                         GoodsQueryRepository goodsQueryRepository,
                         CategoryRepository categoryRepository,
                         GoodsRatingProvider goodsRatingProvider,
                         WishedGoodsProvider wishedGoodsProvider,
                         GoodsTagRepository goodsTagRepository) {
        this.goodsRepository = goodsRepository;
        this.goodsQueryRepository = goodsQueryRepository;
        this.categoryRepository = categoryRepository;
        this.goodsRatingProvider = goodsRatingProvider;
        this.wishedGoodsProvider = wishedGoodsProvider;
        this.goodsTagRepository = goodsTagRepository;
    }

    /**
     * B3 — 목록 조회 캐시. {@link GoodsListItem#wished()}가 {@code viewerId}에 따라 갈리는
     * 개인화 응답이라({@link RankingService}의 B2와 같은 함정), 키에 카테고리/정렬/페이지/필터
     * 뿐 아니라 {@code viewerId}까지 반영한다 — 그렇지 않으면 먼저 조회한 사용자의 찜 상태가
     * 다른 사용자에게 그대로 캐시돼 나간다. {@link #filtersOf}가 필터를 {@code Map<String,String>}으로
     * 눌러 {@link CacheKeys#goodsList}에 넘긴다.
     */
    @Cacheable(cacheNames = "goodsList",
            key = "T(com.beautyboy.common.CacheKeys).goodsList("
                    + "(#condition.categoryCode() == null || #condition.categoryCode().isBlank()) ? 'ALL' : #condition.categoryCode(), "
                    + "#condition.sort().name(), #condition.page(), "
                    + "T(com.beautyboy.catalog.GoodsService).filtersOf(#condition)) + ':' + #viewerId")
    @Transactional(readOnly = true)
    public PageResponse<GoodsListItem> list(GoodsSearchCondition condition, Long viewerId) {
        List<GoodsQueryRepository.GoodsRow> rows = goodsQueryRepository.findList(condition);
        long totalElements = goodsQueryRepository.count(condition);

        List<GoodsListItem> items = toItems(rows, viewerId);

        return PageResponse.of(items, condition.page(), condition.size(), totalElements);
    }

    /** {@link #list}의 캐시 키에 쓸 필터 맵. brandId는 정렬해 연접해야 순서에 무관한 키가 된다. */
    public static Map<String, String> filtersOf(GoodsSearchCondition condition) {
        Map<String, String> filters = new LinkedHashMap<>();
        if (condition.brandId() != null && !condition.brandId().isEmpty()) {
            filters.put("brandId", condition.brandId().stream()
                    .sorted()
                    .map(String::valueOf)
                    .reduce((a, b) -> a + "," + b)
                    .orElse(""));
        }
        if (condition.minPrice() != null) {
            filters.put("minPrice", String.valueOf(condition.minPrice()));
        }
        if (condition.maxPrice() != null) {
            filters.put("maxPrice", String.valueOf(condition.maxPrice()));
        }
        if (condition.tagSlug() != null && !condition.tagSlug().isBlank()) {
            filters.put("tag", condition.tagSlug());
        }
        return filters;
    }

    /**
     * PDP 지연 로딩 3분할 중 빠른 기본 정보. description은 여기서 조회하지 않는다
     * (Goods 엔티티가 join fetch되지만, 응답 DTO 조립 시 description 필드를 아예 담지 않는다 —
     * 엔티티 로딩 자체를 막을 수는 없어도 응답 페이로드에는 절대 실리지 않는다).
     */
    @Transactional(readOnly = true)
    public GoodsDetailResponse detail(Long goodsNo, Long viewerId) {
        Goods goods = goodsRepository.findDetailById(goodsNo, Goods.STATUS_HIDDEN)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));

        List<Object[]> optionRows = goodsRepository.findOptionRowsByGoodsId(goodsNo);
        List<String> badges = goodsQueryRepository
                .findValidBadges(List.of(goodsNo), LocalDateTime.now())
                .getOrDefault(goodsNo, List.of());
        List<String> categoryPath = categoryPath(goods.getCategoryCode());
        List<TagView> tags = goodsTagRepository.findTagsByGoodsIds(List.of(goodsNo))
                .getOrDefault(goodsNo, List.of());

        GoodsRatingProvider.RatingStat ratingStat = goodsRatingProvider.ratingsByGoods(List.of(goodsNo))
                .get(goodsNo);
        boolean wished = wishedGoodsProvider.wishedGoodsIds(viewerId, List.of(goodsNo)).contains(goodsNo);

        return new GoodsDetailResponse(
                goods.getId(),
                goods.getBrand().getName(),
                goods.getBrand().getId(),
                goods.getName(),
                goods.getSummary(),
                goods.getCategoryCode(),
                categoryPath,
                goods.getThumbnailUrl(),
                goods.getListPrice(),
                goods.getSalePrice(),
                discountRate(goods.getListPrice(), goods.getSalePrice()),
                badges,
                goods.getStatus(),
                optionRows.stream().map(this::toOptionResponse).toList(),
                ratingStat == null ? 0.0 : ratingStat.rating(),
                ratingStat == null ? 0 : ratingStat.reviewCount(),
                wished,
                false,
                tags);
    }

    /** PDP 지연 로딩 3분할 중 무거운 본문. 목록/기본 상세와 같은 존재·노출 기준을 쓴다. */
    @Transactional(readOnly = true)
    public GoodsDescriptionResponse description(Long goodsNo) {
        Goods goods = goodsRepository.findDetailById(goodsNo, Goods.STATUS_HIDDEN)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
        return new GoodsDescriptionResponse(goods.getId(), goods.getDescription());
    }

    /** 같은 leaf 카테고리 내 view_count DESC 상위 8건, 자기 자신 제외. 목록 매핑을 그대로 재사용한다. */
    @Transactional(readOnly = true)
    public List<GoodsListItem> recommended(Long goodsNo, Long viewerId) {
        Goods goods = goodsRepository.findDetailById(goodsNo, Goods.STATUS_HIDDEN)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));

        List<Object[]> rows = goodsRepository.findRecommendedRows(
                goods.getCategoryCode(), Goods.STATUS_HIDDEN, goodsNo, PageRequest.of(0, RECOMMENDED_LIMIT));

        List<GoodsQueryRepository.GoodsRow> goodsRows = rows.stream()
                .map(row -> new GoodsQueryRepository.GoodsRow(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        (Integer) row[4],
                        (Integer) row[5]))
                .toList();

        return toItems(goodsRows, viewerId);
    }

    /**
     * 타 도메인 진입점. 상세와 동일한 기준(HIDDEN 제외)으로 상품 존재를 판단한다 —
     * 목록/상세에서 숨긴 상품을 다른 경로(예: ingredient의 상품 성분 조회)로도 보면 안 되기 때문이다.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean exists(Long goodsNo) {
        return goodsRepository.existsByIdAndStatusNot(goodsNo, Goods.STATUS_HIDDEN);
    }

    @Override
    @Transactional(readOnly = true)
    public String categoryCode(Long goodsNo) {
        return goodsRepository.findById(goodsNo).map(Goods::getCategoryCode).orElse(null);
    }

    /**
     * 대표 옵션 선택 순서: {@code sortOrder} 오름차순, 동률이면 {@code id} 오름차순.
     *
     * <p>왜 sortOrder인가: 상세 화면의 옵션 목록도 sortOrder 순으로 내려가므로, 손님이 화면에서
     * 처음 보는 옵션과 서버가 고르는 대표 옵션이 같아진다. 그래서 담긴 옵션·가격이 화면과 어긋나지 않는다.
     *
     * <p>왜 "재고 있는 옵션 우선"이 아닌가: 그러면 손님이 본 것과 다른 옵션·다른 가격이 조용히 담긴다.
     * 대표 옵션이 품절이면 숨기지 않고 {@code ORDER_OUT_OF_STOCK}으로 정직하게 막는 것이 옳다.
     *
     * <p>왜 id를 2차 키로 두는가: 결정적이어야 한다. sortOrder가 동률인데 순서가 컬렉션 로딩 순서에
     * 좌우되면 같은 입력에 다른 답이 나오고 테스트가 흔들린다.
     *
     * <p><b>미저장 옵션</b>: {@code id}가 null인 옵션(같은 트랜잭션에서 아직 flush되지 않은
     * {@code new GoodsOption(...)})이 컬렉션에 섞여도 NPE 없이 <b>맨 뒤로</b> 밀린다
     * ({@code nullsLast}). 저장된 옵션들끼리의 순서가 먼저 결정되므로 대표 옵션이 미저장분에
     * 가로채이지 않는다. 그래도 옵션을 추가한 뒤에는 flush/재조회로 id를 확정한 컬렉션을 넘겨라 —
     * 정렬이 안전해졌을 뿐, 미저장 엔티티를 흘려보내는 것이 옳은 코드는 아니다.
     */
    private static final java.util.Comparator<GoodsOption> 대표_옵션_순서 =
            java.util.Comparator.comparingInt(GoodsOption::getSortOrder)
                    .thenComparing(GoodsOption::getId,
                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));

    /**
     * 주문·장바구니용 상품 스냅샷. 숨김 상품과 상품-옵션 불일치는 빈 값으로 답한다
     * (예외를 던지지 않는 이유는 인터페이스 문서 참고).
     *
     * <p><b>장바구니에 담긴 옵션이 나중에 삭제되면</b>: {@code CartItem}은 해석된 {@code option_id}를
     * 확정해 저장하므로(Task 4-18), 그 옵션이 삭제되면 여기서 "상품-옵션 불일치"로 빈 값을 반환하고
     * {@code CartService#itemsOf}가 그 행을 목록에서 조용히 제외한다. 예외를 던지거나 행을 강제로
     * 지우지 않는 이유는 숨김 상품과 같은 정책이다 — 손님이 장바구니를 열 때마다 "옵션이
     * 사라졌습니다" 에러로 막기보다, 있던 자리에서 자연스럽게 빠지는 편을 택했다. 다시 판매되는
     * 상품이 살아나는 것과 대칭이지만, 옵션은 삭제되면 되살아나지 않으므로 이 행은 사실상 영구히
     * 사라진다 — 의도된 트레이드오프다(옵션 삭제 자체가 드문 admin 조작이라 손님에게 노출되는 폭이 작다).
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<OrderGoodsSnapshot> findOrderSnapshot(Long goodsNo, Long optionNo) {
        return goodsRepository.findById(goodsNo)
                .filter(goods -> !Goods.STATUS_HIDDEN.equals(goods.getStatus()))
                .flatMap(goods -> 해석(goods, optionNo));
    }

    /**
     * 여러 키를 한 번에 해석한다. 상품+옵션을 fetch join 한 방으로 읽고, 해석은 단건과 <b>같은</b>
     * {@link #해석} 하나를 쓴다 — 해석이 두 벌이 되면 대표 옵션 규칙이 한쪽에서만 바뀌는 날이 오고,
     * 그때 장바구니와 주문이 서로 다른 옵션을 고른다.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<OrderSnapshotKey, OrderGoodsSnapshot> findOrderSnapshots(Collection<OrderSnapshotKey> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        Set<Long> goodsIds = keys.stream()
                .map(OrderSnapshotKey::goodsNo)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (goodsIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Goods> 노출_상품 = goodsRepository.findAllWithOptionsByIdIn(goodsIds).stream()
                .filter(goods -> !Goods.STATUS_HIDDEN.equals(goods.getStatus()))
                .collect(java.util.stream.Collectors.toMap(Goods::getId, goods -> goods));

        Map<OrderSnapshotKey, OrderGoodsSnapshot> result = new java.util.LinkedHashMap<>();
        for (OrderSnapshotKey key : keys) {
            Goods goods = 노출_상품.get(key.goodsNo());
            if (goods == null) {
                continue;   // 미존재·숨김은 키를 넣지 않는다(단건의 Optional.empty와 같은 계약)
            }
            해석(goods, key.optionNo()).ifPresent(snapshot -> result.put(key, snapshot));
        }
        return result;
    }

    /**
     * 상품 하나에 대해 optionNo를 스냅샷으로 해석한다. 노출 여부(HIDDEN) 판정은 호출자가 이미 끝냈다.
     *
     * <p>optionNo가 null인 것은 "옵션을 특정하지 않았다"는 뜻일 뿐 "상품에 옵션이 없다"는 뜻이 아니다.
     * 예전에는 이 둘을 같게 보고 무조건 재고 MAX_VALUE로 답했고, 그래서 루틴 전체담기
     * (항상 optionNo=null을 보낸다)로 담은 품절 상품이 재고 게이트를 통째로 통과했다.
     */
    private Optional<OrderGoodsSnapshot> 해석(Goods goods, Long optionNo) {
        if (optionNo == null) {
            return goods.getOptions().stream()
                    .min(대표_옵션_순서)
                    .map(option -> 스냅샷(goods, option))
                    // 옵션이 진짜 하나도 없는 상품만 여기로 온다. 재고 관리 단위가 옵션이므로
                    // 이 경우에만 상품 단위 재고를 무제한으로 본다.
                    .or(() -> Optional.of(new OrderGoodsSnapshot(
                            goods.getId(), null, goods.getName(), null,
                            goods.getSalePrice(), Integer.MAX_VALUE, goods.getThumbnailUrl())));
        }
        // 옵션은 반드시 그 상품의 것이어야 한다. 남의 옵션을 붙이는 조작을 여기서 끊는다.
        return goods.getOptions().stream()
                .filter(option -> option.getId().equals(optionNo))
                .findFirst()
                .map(option -> 스냅샷(goods, option));
    }

    private OrderGoodsSnapshot 스냅샷(Goods goods, GoodsOption option) {
        return new OrderGoodsSnapshot(
                goods.getId(),
                option.getId(),
                goods.getName(),
                option.getName(),
                goods.getSalePrice() + option.getAddPrice(),
                option.getStock(),
                goods.getThumbnailUrl());
    }

    /**
     * 타 도메인(routine 등)이 goods_no 목록을 카드로 바꿔갈 때 쓰는 배치 조회. HIDDEN은 제외한다
     * (목록/상세와 같은 노출 기준). 목록 매핑(toItem)과 배지 조회를 그대로 재사용한다.
     * 입력 순서를 보존하지 않으므로 호출자가 필요하면 자기 순서로 재정렬한다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<GoodsListItem> findListItems(java.util.Collection<Long> goodsNos, Long viewerId) {
        if (goodsNos.isEmpty()) {
            return List.of();
        }
        List<GoodsQueryRepository.GoodsRow> rows = goodsQueryRepository.findByIds(goodsNos);

        return toItems(rows, viewerId);
    }

    /**
     * routine의 "다음 단계" 후보 조회. tagSlug 유무로 태그 무관/태그 조건부 쿼리 중 하나를 고른다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Long> findCandidateGoodsNos(String categoryCodePrefix, String tagSlug, Long excludeGoodsNo, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        if (tagSlug == null) {
            return goodsRepository.findCandidateIds(categoryCodePrefix, Goods.STATUS_HIDDEN, excludeGoodsNo, pageRequest);
        }
        return goodsRepository.findCandidateIdsByTag(
                categoryCodePrefix, tagSlug, Goods.STATUS_HIDDEN, excludeGoodsNo, pageRequest);
    }

    /** 상품의 태그 슬러그 집합. kind 불문 전 슬러그를 모은다. 태그가 없으면 빈 집합. */
    @Override
    @Transactional(readOnly = true)
    public java.util.Set<String> tagSlugs(Long goodsNo) {
        return goodsTagRepository.findTagsByGoodsIds(List.of(goodsNo))
                .getOrDefault(goodsNo, List.of())
                .stream()
                .map(TagView::slug)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 코드 접두사(C001, C001001, C001001001)를 한 번에 IN 조회해 depth 1→3 순서 이름 배열로 만든다. */
    private List<String> categoryPath(String leafCode) {
        List<String> prefixes = List.of(
                leafCode.substring(0, 4),
                leafCode.substring(0, 7),
                leafCode.substring(0, 10));
        Map<String, Category> categoriesByCode = categoryRepository.findAllById(prefixes).stream()
                .collect(java.util.stream.Collectors.toMap(Category::getCode, c -> c));
        return prefixes.stream()
                .map(code -> categoriesByCode.get(code).getName())
                .toList();
    }

    private GoodsOptionResponse toOptionResponse(Object[] row) {
        Long optionNo = (Long) row[0];
        String name = (String) row[1];
        int addPrice = (Integer) row[2];
        int stock = (Integer) row[3];
        return new GoodsOptionResponse(optionNo, name, addPrice, stock, stock == 0);
    }

    /**
     * 행 목록 → 카드 목록. 배지·별점·찜을 각각 <b>한 번씩만</b> 배치 조회한 뒤 메모리에서 합친다
     * (N+1 금지 — 상품별로 반복 조회하지 않는다).
     *
     * <p>패키지 접근(default)인 이유: 같은 catalog 패키지 소속인 {@code AdminGoodsService}가
     * 관리자 목록(HIDDEN 포함)을 만들 때 이 매핑을 그대로 재사용한다 — 배지/별점/찜 조립 로직을
     * 두 곳에 중복시키지 않는다.
     */
    List<GoodsListItem> toItems(List<GoodsQueryRepository.GoodsRow> rows, Long viewerId) {
        List<Long> goodsIds = rows.stream().map(GoodsQueryRepository.GoodsRow::goodsId).toList();

        Map<Long, List<String>> badgesByGoodsId = goodsQueryRepository.findValidBadges(goodsIds, LocalDateTime.now());
        Map<Long, List<TagView>> tagsByGoodsId = goodsTagRepository.findTagsByGoodsIds(goodsIds);
        Map<Long, GoodsRatingProvider.RatingStat> ratingsByGoodsId = goodsRatingProvider.ratingsByGoods(goodsIds);
        java.util.Set<Long> wishedGoodsIds = wishedGoodsProvider.wishedGoodsIds(viewerId, goodsIds);

        return rows.stream()
                .map(row -> toItem(
                        row,
                        badgesByGoodsId.getOrDefault(row.goodsId(), List.of()),
                        tagsByGoodsId.getOrDefault(row.goodsId(), List.of()),
                        ratingsByGoodsId.get(row.goodsId()),
                        wishedGoodsIds.contains(row.goodsId())))
                .toList();
    }

    private GoodsListItem toItem(GoodsQueryRepository.GoodsRow row, List<String> badges, List<TagView> tags,
                                  GoodsRatingProvider.RatingStat ratingStat, boolean wished) {
        return new GoodsListItem(
                row.goodsId(),
                row.brandName(),
                row.name(),
                row.thumbnailUrl(),
                row.listPrice(),
                row.salePrice(),
                discountRate(row.listPrice(), row.salePrice()),
                badges,
                ratingStat == null ? 0.0 : ratingStat.rating(),
                ratingStat == null ? 0 : ratingStat.reviewCount(),
                wished,
                false,
                tags);
    }

    private int discountRate(int listPrice, int salePrice) {
        if (listPrice == 0) {
            return 0;
        }
        return (listPrice - salePrice) * 100 / listPrice;
    }
}
