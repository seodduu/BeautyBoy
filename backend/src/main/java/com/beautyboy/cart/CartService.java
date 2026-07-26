package com.beautyboy.cart;

import com.beautyboy.cart.dto.CartAddRequest;
import com.beautyboy.cart.dto.CartItemResponse;
import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final GoodsQueryService goodsQueryService;

    public CartService(CartItemRepository cartItemRepository, GoodsQueryService goodsQueryService) {
        this.cartItemRepository = cartItemRepository;
        this.goodsQueryService = goodsQueryService;
    }

    @Transactional
    public void add(Long memberId, CartAddRequest request) {
        if (request.quantity() <= 0) {
            throw new BusinessException(ErrorCode.CART_QUANTITY_INVALID);
        }
        // 담는 시점에 존재·판매 가능 여부를 확인한다. 없는 상품이 장바구니에 남으면
        // 주문 단계에서야 실패해 손님이 결제 직전에 막힌다.
        GoodsQueryService.OrderGoodsSnapshot snapshot =
                goodsQueryService.findOrderSnapshot(request.goodsNo(), request.optionNo())
                        .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));

        // 요청의 optionNo가 아니라 catalog가 해석해 준 optionId를 저장한다.
        // 루틴 전체담기처럼 optionNo=null로 오는 경로는 catalog가 대표 옵션을 골라주는데,
        // 그것을 null로 저장해 두면 읽을 때마다 다시 해석하게 되고 옵션의 sortOrder가 바뀌거나
        // 옵션이 삭제되는 순간 장바구니 내용이 조용히 달라진다. 담긴 순간에 확정하는 것이
        // 스냅샷 원칙과도 맞는다. (옵션이 하나도 없는 상품이면 그대로 null이다.)
        Long optionId = snapshot.optionId();

        cartItemRepository
                .findByMemberIdAndGoodsIdAndOptionId(memberId, request.goodsNo(), optionId)
                .ifPresentOrElse(
                        // 이미 있으면 더한다. 유니크 제약에 부딪히기 전에 애플리케이션이 먼저 처리한다.
                        // 해석된 optionId로 찾으므로, 같은 상품을 루틴(optionNo=null)과 상세(optionNo 지정)에서
                        // 각각 담아도 같은 옵션이면 한 행으로 합쳐진다 — 예전에는 (goodsId, null)과
                        // (goodsId, optionId) 두 행으로 갈라져 같은 옵션이 두 줄로 보였다.
                        existing -> existing.addQuantity(request.quantity()),
                        () -> cartItemRepository.save(new CartItem(
                                memberId, request.goodsNo(), optionId, request.quantity())));
    }

    @Transactional
    public void addAll(Long memberId, List<CartAddRequest> requests) {
        // 루틴 담기는 "전부 담기거나 전부 안 담기거나"여야 한다 —
        // 한 건이 품절이라 절반만 담기면 손님은 무엇이 빠졌는지 모른 채 결제로 간다.
        // @Transactional이 한 건 실패 시 전체를 되돌린다.
        for (CartAddRequest request : requests) {
            add(memberId, request);
        }
    }

    @Transactional(readOnly = true)
    public List<CartItemResponse> itemsOf(Long memberId) {
        List<CartItemResponse> responses = new ArrayList<>();
        for (CartItem item : cartItemRepository.findByMemberIdOrderByIdAsc(memberId)) {
            // 담은 뒤 숨겨진 상품은 목록에서 제외한다. 지우지는 않는다 —
            // 다시 판매되면 그대로 살아나는 편이 손님에게 자연스럽다.
            goodsQueryService.findOrderSnapshot(item.getGoodsId(), item.getOptionId())
                    .ifPresent(snapshot -> responses.add(new CartItemResponse(
                            item.getId(),
                            item.getGoodsId(),
                            item.getOptionId(),
                            snapshot.goodsName(),
                            snapshot.optionName(),
                            snapshot.unitPrice(),
                            item.getQuantity(),
                            snapshot.unitPrice() * item.getQuantity())));
        }
        return responses;
    }

    @Transactional
    public void changeQuantity(Long memberId, Long cartItemId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.CART_QUANTITY_INVALID);
        }
        내_항목(memberId, cartItemId).changeQuantity(quantity);
    }

    @Transactional
    public void remove(Long memberId, Long cartItemId) {
        cartItemRepository.delete(내_항목(memberId, cartItemId));
    }

    /** 주문이 성립하면 장바구니를 비운다(T2-4가 호출). */
    @Transactional
    public void clear(Long memberId) {
        cartItemRepository.deleteByMemberId(memberId);
    }

    /**
     * 소유 검사.
     *
     * <p>남의 항목에 403이 아니라 404를 주는 이유: 403은 "그 id는 존재한다"는 정보를 흘린다.
     * 존재 여부 자체를 숨기는 편이 안전하다.
     */
    private CartItem 내_항목(Long memberId, Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
                .filter(item -> item.ownedBy(memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
    }
}
