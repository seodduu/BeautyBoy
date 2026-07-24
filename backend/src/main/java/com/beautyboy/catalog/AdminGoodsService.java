package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.AdminGoodsSaveRequest;
import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.common.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** 일반 목록과 다른 유일한 지점 — HIDDEN도 포함한다. 숨긴 상품을 못 보면 되살릴 방법이 없다. */
    @Transactional(readOnly = true)
    public PageResponse<GoodsListItem> list(int page, int size, String q) {
        List<GoodsQueryRepository.GoodsRow> rows = goodsQueryRepository.findAdminList(q, page, size);
        long totalElements = goodsQueryRepository.countAdmin(q);
        List<GoodsListItem> items = goodsService.toItems(rows, null);
        return PageResponse.of(items, page, size, totalElements);
    }

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
