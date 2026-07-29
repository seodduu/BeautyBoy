package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.AdminGoodsDetailResponse;
import com.beautyboy.catalog.dto.AdminGoodsListItem;
import com.beautyboy.catalog.dto.AdminGoodsSaveRequest;
import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.common.PageResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 상품 CRUD. 일반 GoodsService와 같은 catalog 패키지에 있으므로 목록 카드 매핑
 * (배지/별점/찜 조립)을 GoodsService.toItems로 그대로 재사용한다 — 로직을 두 벌 만들지 않는다.
 */
@Service
public class AdminGoodsService {

    private final GoodsRepository goodsRepository;
    private final GoodsQueryRepository goodsQueryRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final GoodsService goodsService;

    public AdminGoodsService(GoodsRepository goodsRepository,
                              GoodsQueryRepository goodsQueryRepository,
                              CategoryRepository categoryRepository,
                              BrandRepository brandRepository,
                              GoodsService goodsService) {
        this.goodsRepository = goodsRepository;
        this.goodsQueryRepository = goodsQueryRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.goodsService = goodsService;
    }

    /**
     * 일반 목록과 다른 지점 둘: HIDDEN도 포함한다(숨긴 상품을 못 보면 되살릴 방법이 없다),
     * 그리고 {@code status}를 함께 내린다(admin 화면의 "숨김" 배지가 실 API로 동작하려면 필요 —
     * Task 4-14a). {@link GoodsListItem}은 동결 계약이라 바꾸지 않고, admin 전용 DTO
     * {@link AdminGoodsListItem}에 status를 얹어 반환한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminGoodsListItem> list(int page, int size, String q) {
        List<GoodsQueryRepository.AdminGoodsRow> adminRows = goodsQueryRepository.findAdminList(q, page, size);
        long totalElements = goodsQueryRepository.countAdmin(q);

        // 배지/별점/찜 조립은 GoodsService.toItems를 그대로 재사용한다(로직 중복 금지) —
        // status가 없는 GoodsRow로 내렸다가 결과에 같은 순서로 status를 얹는다.
        List<GoodsQueryRepository.GoodsRow> rows = adminRows.stream()
                .map(row -> new GoodsQueryRepository.GoodsRow(
                        row.goodsId(), row.brandName(), row.name(), row.thumbnailUrl(),
                        row.listPrice(), row.salePrice()))
                .toList();
        List<GoodsListItem> items = goodsService.toItems(rows, null);

        List<AdminGoodsListItem> adminItems = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            GoodsListItem item = items.get(i);
            adminItems.add(new AdminGoodsListItem(
                    item.goodsNo(), item.brandName(), item.name(), item.thumbnailUrl(),
                    item.listPrice(), item.salePrice(), item.discountRate(), item.badges(),
                    item.rating(), item.reviewCount(), item.wished(), item.todayDreamAvailable(),
                    adminRows.get(i).status()));
        }
        return PageResponse.of(adminItems, page, size, totalElements);
    }

    /**
     * admin 전용 상세 조회 — 인라인 수정 폼에 필요한 값만 담는다. {@code GoodsService.detail()}과
     * 달리 HIDDEN도 조회 대상이다(숨김 상품을 admin이 수정할 수 있어야 한다 — Task 4-14a).
     * 일반 상세가 HIDDEN을 숨기는 동작은 올바르므로 그 메서드는 건드리지 않고 이 경로를 따로 둔다.
     */
    @Transactional(readOnly = true)
    public AdminGoodsDetailResponse detail(Long goodsNo) {
        Goods goods = goodsRepository.findById(goodsNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
        return new AdminGoodsDetailResponse(
                goods.getId(),
                goods.getBrand().getId(),
                goods.getCategoryCode(),
                goods.getName(),
                goods.getSummary(),
                goods.getThumbnailUrl(),
                goods.getListPrice(),
                goods.getSalePrice(),
                goods.getStatus());
    }

    /**
     * B3 — 등록/수정은 {@code goodsList} 캐시 전체를 비운다. 카테고리별 패턴 삭제(SCAN)는 키
     * 구조 결합이 생기는 데 비해 admin 변경 빈도가 낮아 이득이 없다는 판단(설계 §6에서 언급한
     * "카테고리 패턴 삭제"를 구현 단순화로 전체 clear로 대체 — ADR ③).
     */
    @CacheEvict(cacheNames = "goodsList", allEntries = true)
    @Transactional
    public Long create(AdminGoodsSaveRequest request) {
        validateCategory(request.categoryCode());
        validatePrice(request.listPrice(), request.salePrice());

        Brand brand = brandRepository.findById(request.brandId())
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));

        Goods goods = new Goods(brand, request.categoryCode(), request.name(), request.summary(),
                request.thumbnailUrl(), request.listPrice(), request.salePrice());
        return goodsRepository.save(goods).getId();
    }

    /**
     * B4 — {@code compat} 캐시도 함께 비운다. 현재 {@link AdminGoodsSaveRequest}에는 성분
     * (ingredient) 필드가 없어 이 저장소엔 실제 "성분 변경" 쓰기 경로가 아직 없다 — 그 경로가
     * 생기기 전까지는 상품 일반 정보 수정 때마다 compat을 선제적으로 비우는 과대무효화로 둔다.
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = "goodsList", allEntries = true),
            @CacheEvict(cacheNames = "compat", allEntries = true)
    })
    @Transactional
    public void update(Long goodsNo, AdminGoodsSaveRequest request) {
        validateCategory(request.categoryCode());
        validatePrice(request.listPrice(), request.salePrice());

        Goods goods = goodsRepository.findById(goodsNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));

        goods.updateInfo(request.categoryCode(), request.name(), request.summary(), request.thumbnailUrl(),
                request.listPrice(), request.salePrice(), request.status());
    }

    // 물리 삭제하지 않는 이유:
    // goods_no는 order_item·review·wishlist·routine_step_goods가 논리 참조(물리 FK 없음)로 붙들고 있다.
    // 행을 지우면 그 참조들이 조용히 유령이 된다 — 이미 결제된 주문의 상품명이 사라지는 식이다.
    // 상태를 HIDDEN으로 내리면 목록·상세·검색·랭킹·루틴이 전부 이미 그것을 제외하도록 돼 있다.
    @Transactional
    public void delete(Long goodsNo) {
        Goods goods = goodsRepository.findById(goodsNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
        goods.hide();
    }

    private void validateCategory(String categoryCode) {
        if (!categoryRepository.existsById(categoryCode)) {
            throw new BusinessException(ErrorCode.GOODS_CATEGORY_INVALID);
        }
    }

    private void validatePrice(int listPrice, int salePrice) {
        if (salePrice > listPrice) {
            throw new BusinessException(ErrorCode.GOODS_PRICE_INVALID);
        }
    }
}
