package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.GoodsDescriptionResponse;
import com.beautyboy.catalog.dto.GoodsDetailResponse;
import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.catalog.dto.GoodsOptionResponse;
import com.beautyboy.catalog.dto.GoodsSearchCondition;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GoodsService implements GoodsQueryService {

    private static final int RECOMMENDED_LIMIT = 8;

    private final GoodsRepository goodsRepository;
    private final GoodsQueryRepository goodsQueryRepository;
    private final CategoryRepository categoryRepository;
    private final GoodsRatingProvider goodsRatingProvider;
    private final WishedGoodsProvider wishedGoodsProvider;
    private final ViewCountRecorder viewCountRecorder;

    public GoodsService(GoodsRepository goodsRepository,
                         GoodsQueryRepository goodsQueryRepository,
                         CategoryRepository categoryRepository,
                         GoodsRatingProvider goodsRatingProvider,
                         WishedGoodsProvider wishedGoodsProvider,
                         ViewCountRecorder viewCountRecorder) {
        this.goodsRepository = goodsRepository;
        this.goodsQueryRepository = goodsQueryRepository;
        this.categoryRepository = categoryRepository;
        this.goodsRatingProvider = goodsRatingProvider;
        this.wishedGoodsProvider = wishedGoodsProvider;
        this.viewCountRecorder = viewCountRecorder;
    }

    @Transactional(readOnly = true)
    public PageResponse<GoodsListItem> list(GoodsSearchCondition condition, Long viewerId) {
        List<GoodsQueryRepository.GoodsRow> rows = goodsQueryRepository.findList(condition);
        long totalElements = goodsQueryRepository.count(condition);

        List<GoodsListItem> items = toItems(rows, viewerId);

        return PageResponse.of(items, condition.page(), condition.size(), totalElements);
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

        // 존재 확인(404) 뒤에 센다 — 없는/숨긴 상품 조회로 카운터를 오염시키지 않는다.
        // 구현은 설정이 고른다: 기본은 DB 즉시 증가, 토글이 켜지면 Redis 버퍼.
        // 실패해도 예외가 올라오지 않는 것이 ViewCountRecorder의 계약이라 상세는 여기서 절대 500이 되지 않는다.
        viewCountRecorder.record(goodsNo);

        List<Object[]> optionRows = goodsRepository.findOptionRowsByGoodsId(goodsNo);
        List<String> badges = goodsQueryRepository
                .findValidBadges(List.of(goodsNo), LocalDateTime.now())
                .getOrDefault(goodsNo, List.of());
        List<String> categoryPath = categoryPath(goods.getCategoryCode());

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
                false);
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
     * 주문·장바구니용 상품 스냅샷. 숨김 상품과 상품-옵션 불일치는 빈 값으로 답한다
     * (예외를 던지지 않는 이유는 인터페이스 문서 참고).
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<OrderGoodsSnapshot> findOrderSnapshot(Long goodsNo, Long optionNo) {
        Optional<Goods> found = goodsRepository.findById(goodsNo)
                .filter(goods -> !Goods.STATUS_HIDDEN.equals(goods.getStatus()));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Goods goods = found.get();

        if (optionNo == null) {
            // 옵션이 없는 상품. 재고 관리 단위가 옵션이므로 상품 단위 재고는 무제한으로 본다.
            return Optional.of(new OrderGoodsSnapshot(
                    goods.getId(), null, goods.getName(), null, goods.getSalePrice(), Integer.MAX_VALUE));
        }

        // 옵션은 반드시 그 상품의 것이어야 한다. 남의 옵션을 붙이는 조작을 여기서 끊는다.
        return goods.getOptions().stream()
                .filter(option -> option.getId().equals(optionNo))
                .findFirst()
                .map(option -> new OrderGoodsSnapshot(
                        goods.getId(),
                        option.getId(),
                        goods.getName(),
                        option.getName(),
                        goods.getSalePrice() + option.getAddPrice(),
                        option.getStock()));
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
        Map<Long, GoodsRatingProvider.RatingStat> ratingsByGoodsId = goodsRatingProvider.ratingsByGoods(goodsIds);
        java.util.Set<Long> wishedGoodsIds = wishedGoodsProvider.wishedGoodsIds(viewerId, goodsIds);

        return rows.stream()
                .map(row -> toItem(
                        row,
                        badgesByGoodsId.getOrDefault(row.goodsId(), List.of()),
                        ratingsByGoodsId.get(row.goodsId()),
                        wishedGoodsIds.contains(row.goodsId())))
                .toList();
    }

    private GoodsListItem toItem(GoodsQueryRepository.GoodsRow row, List<String> badges,
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
                false);
    }

    private int discountRate(int listPrice, int salePrice) {
        if (listPrice == 0) {
            return 0;
        }
        return (listPrice - salePrice) * 100 / listPrice;
    }
}
