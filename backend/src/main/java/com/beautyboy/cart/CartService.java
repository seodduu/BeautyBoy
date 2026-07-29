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
import java.util.Map;

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

        java.util.Optional<CartItem> found =
                cartItemRepository.findByMemberIdAndGoodsIdAndOptionId(memberId, request.goodsNo(), optionId);

        // 기존 항목 누적을 포함한 "결과 수량"으로 재고를 검증한다.
        // 줄 단위 검증이면 2개 담고 또 2개 담아 재고 3을 넘는 경우를 못 잡는다.
        // 옵션 없는 상품은 stock이 Integer.MAX_VALUE라 자연 통과한다.
        // 검증만 한다 — 차감은 결제 승인 시점에 StockCommandService가 한다.
        int resultingQuantity = found
                .map(existing -> existing.getQuantity() + request.quantity())
                .orElse(request.quantity());
        if (resultingQuantity > snapshot.stock()) {
            throw new BusinessException(ErrorCode.ORDER_OUT_OF_STOCK);
        }

        found.ifPresentOrElse(
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
        List<CartItem> items = cartItemRepository.findByMemberIdOrderByIdAsc(memberId);
        if (items.isEmpty()) {
            return List.of();
        }

        // 줄마다 findOrderSnapshot을 부르면 줄 수만큼 쿼리가 나간다(N+1).
        // 키를 모아 한 번만 묻는다 — search/ranking/review가 이미 쓰는 규칙이다.
        List<GoodsQueryService.OrderSnapshotKey> keys = items.stream()
                .map(item -> new GoodsQueryService.OrderSnapshotKey(item.getGoodsId(), item.getOptionId()))
                .toList();
        Map<GoodsQueryService.OrderSnapshotKey, GoodsQueryService.OrderGoodsSnapshot> snapshots =
                goodsQueryService.findOrderSnapshots(keys);

        List<CartItemResponse> responses = new ArrayList<>();
        for (CartItem item : items) {
            // 담은 뒤 숨겨진 상품(과 삭제된 옵션)은 목록에서 제외한다. 지우지는 않는다 —
            // 다시 판매되면 그대로 살아나는 편이 손님에게 자연스럽다.
            // 맵에 키가 없는 것이 곧 그 경우다(배치 계약).
            GoodsQueryService.OrderGoodsSnapshot snapshot = snapshots.get(
                    new GoodsQueryService.OrderSnapshotKey(item.getGoodsId(), item.getOptionId()));
            if (snapshot == null) {
                continue;
            }
            responses.add(new CartItemResponse(
                    item.getId(),
                    item.getGoodsId(),
                    // item.getOptionId()가 아니라 snapshot.optionId()를 내려준다. 4-18 이전에
                    // 담긴 레거시 행(옵션 있는 상품인데 option_id=NULL로 저장된 행)은
                    // item.getOptionId()가 여전히 null이라, 그걸 그대로 내리면 optionNo=null인데
                    // optionName은 대표 옵션 이름이 나오는 자기모순 응답이 된다.
                    snapshot.optionId(),
                    snapshot.goodsName(),
                    snapshot.optionName(),
                    snapshot.unitPrice(),
                    item.getQuantity(),
                    snapshot.unitPrice() * item.getQuantity(),
                    snapshot.thumbnailUrl(),
                    snapshot.stock()));
        }
        return responses;
    }

    @Transactional
    public void changeQuantity(Long memberId, Long cartItemId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.CART_QUANTITY_INVALID);
        }
        CartItem item = 내_항목(memberId, cartItemId);
        // 스냅샷을 새로 조회해 재고 상한을 검증한다. 상품이 사라졌으면 담기와 같은 관례로 GOODS_NOT_FOUND.
        GoodsQueryService.OrderGoodsSnapshot snapshot =
                goodsQueryService.findOrderSnapshot(item.getGoodsId(), item.getOptionId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
        if (quantity > snapshot.stock()) {
            throw new BusinessException(ErrorCode.ORDER_OUT_OF_STOCK);
        }
        item.changeQuantity(quantity);
    }

    @Transactional
    public void remove(Long memberId, Long cartItemId) {
        cartItemRepository.delete(내_항목(memberId, cartItemId));
    }

    /** 회원의 장바구니를 통째로 비운다. */
    @Transactional
    public void clear(Long memberId) {
        cartItemRepository.deleteByMemberId(memberId);
    }

    /**
     * 결제가 끝난 상품만 장바구니에서 뺀다(확정 후처리가 호출).
     *
     * <p>왜 {@link #clear(Long)}가 아닌가: 예전에는 주문 <b>생성</b> 시점에 장바구니를 통째로
     * 비웠고, 그래서 결제를 포기해도 담아둔 것이 전부 사라졌다. 이제는 확정된 주문에 담긴
     * 상품만 지운다 — 같은 시각에 다른 탭에서 담은 상품은 그대로 남는다.
     *
     * <p>옵션이 아니라 <b>상품 단위</b>로 지운다. 주문한 옵션만 지우면 같은 상품의 다른 옵션이
     * 남아 "방금 산 걸 또 담아둔" 목록이 된다. 옵션 선택은 결국 같은 상품을 사려던 것이므로
     * 상품 단위가 손님의 의도에 가깝다.
     *
     * <p>없는 항목을 지우는 것은 no-op이다 — 같은 이벤트를 두 번 소비해도 안전해야 하기 때문이다
     * (A5의 cart-clear 컨슈머는 이 자연 멱등성에 기대어 처리 기록 테이블을 쓰지 않는다).
     *
     * <p>파생 삭제 쿼리({@code deleteByMemberIdAndGoodsIdIn}) 대신 이미 있는 조회로 거르는 이유는
     * 순전히 파일 소유권이다 — 이 태스크의 Files 목록에 {@code CartItemRepository}가 없다.
     * 장바구니는 회원당 수십 줄 규모라 실측 차이가 없고, 필요하면 A5에서 파생 쿼리로 바꾸면 된다.
     */
    @Transactional
    public void removeByGoods(Long memberId, List<Long> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return;
        }
        List<CartItem> 지울_항목 = cartItemRepository.findByMemberIdOrderByIdAsc(memberId).stream()
                .filter(item -> goodsIds.contains(item.getGoodsId()))
                .toList();
        if (지울_항목.isEmpty()) {
            return;
        }
        cartItemRepository.deleteAll(지울_항목);
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
