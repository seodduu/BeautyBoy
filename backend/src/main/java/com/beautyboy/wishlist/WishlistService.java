package com.beautyboy.wishlist;

import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.wishlist.dto.WishlistItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final GoodsQueryService goodsQueryService;

    public WishlistService(WishlistRepository wishlistRepository, GoodsQueryService goodsQueryService) {
        this.wishlistRepository = wishlistRepository;
        this.goodsQueryService = goodsQueryService;
    }

    @Transactional
    public void add(Long memberId, Long goodsNo) {
        if (!goodsQueryService.exists(goodsNo)) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        if (wishlistRepository.existsByMemberIdAndGoodsId(memberId, goodsNo)) {
            throw new BusinessException(ErrorCode.WISHLIST_ALREADY_ADDED);
        }
        wishlistRepository.save(new Wishlist(memberId, goodsNo));
    }

    @Transactional
    public void remove(Long memberId, Long goodsNo) {
        wishlistRepository.deleteByMemberIdAndGoodsId(memberId, goodsNo);
    }

    @Transactional(readOnly = true)
    public List<WishlistItemResponse> itemsOf(Long memberId) {
        return wishlistRepository.findByMemberIdOrderByIdDesc(memberId).stream()
                .map(wishlist -> new WishlistItemResponse(wishlist.getGoodsId()))
                .toList();
    }
}
