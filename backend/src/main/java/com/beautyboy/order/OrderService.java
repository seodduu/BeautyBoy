package com.beautyboy.order;

import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.common.PageRequests;
import com.beautyboy.order.dto.OrderCreateRequest;
import com.beautyboy.order.dto.OrderCreateResponse;
import com.beautyboy.common.PageResponse;
import com.beautyboy.order.dto.OrderDetailResponse;
import com.beautyboy.order.dto.OrderSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 주문 생성.
 *
 * <p>이 클래스의 존재 이유 한 줄: <b>클라이언트가 보낸 금액을 쓰지 않는다.</b>
 * goodsNo와 수량만 받아 가격을 catalog에서 다시 읽고, 합계를 서버가 계산한다(설계 7장 결제 2단계 1항).
 */
@Service
public class OrderService implements OrderQueryService {

    private static final DateTimeFormatter ORDER_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ORDER_NO_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ORDER_NO_RANDOM_LENGTH = 8;
    private static final int ORDER_NO_MAX_ATTEMPTS = 5;
    /** 목록 기본 크기. 마이페이지 주문내역 한 화면 분량이며 admin 문의 목록과 같은 값이다. */
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final OrderRepository orderRepository;
    private final GoodsQueryService goodsQueryService;
    private final SecureRandom random = new SecureRandom();

    public OrderService(OrderRepository orderRepository,
                        GoodsQueryService goodsQueryService) {
        this.orderRepository = orderRepository;
        this.goodsQueryService = goodsQueryService;
    }

    @Transactional
    public OrderCreateResponse create(Long memberId, OrderCreateRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        Order order = new Order(
                주문번호_생성(),
                memberId,
                request.receiverName(),
                request.receiverPhone(),
                request.zipcode(),
                request.address1(),
                request.address2(),
                request.deliveryType(),
                LocalDateTime.now());

        for (OrderCreateRequest.OrderItemRequest item : request.items()) {
            if (item.quantity() <= 0) {
                throw new BusinessException(ErrorCode.CART_QUANTITY_INVALID);
            }

            // 가격을 여기서 다시 읽는다. 요청에 담긴 어떤 금액도 보지 않는다.
            GoodsQueryService.OrderGoodsSnapshot snapshot =
                    goodsQueryService.findOrderSnapshot(item.goodsNo(), item.optionNo())
                            .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));

            // 재고는 여기서 검증만 한다(UX 게이트). 차감은 결제 승인 트랜잭션에서 한다
            // — PaymentService.confirm (3).
            if (snapshot.stock() < item.quantity()) {
                throw new BusinessException(ErrorCode.ORDER_OUT_OF_STOCK);
            }

            order.addItem(new OrderItem(
                    snapshot.goodsId(),
                    snapshot.optionId(),
                    snapshot.goodsName(),
                    snapshot.optionName(),
                    snapshot.unitPrice(),
                    item.quantity()));
        }

        Order saved = orderRepository.save(order);

        // 장바구니는 여기서 비우지 않는다(설계 §2-2, 의도된 행동 변경).
        // 예전에는 주문이 성립하면 곧장 비웠는데, 그러면 결제창을 닫거나 결제가 실패해도
        // 담아둔 것이 전부 사라졌다. 이제는 결제가 확정된 뒤 그 주문의 상품만 빠진다
        // — PostOrderTasks.onOrderConfirmed(1).
        return new OrderCreateResponse(saved.getOrderNo(), saved.getPayableAmount());
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> ordersOf(Long memberId, int page, int size) {
        // 손으로 친 파라미터를 그대로 PageRequest에 넣으면 음수·0에서 IllegalArgumentException(500)이 난다.
        int safePage = PageRequests.clampPage(page);
        int safeSize = PageRequests.clampSize(size);

        Page<Order> found = orderRepository.findByMemberIdOrderByOrderedAtDescIdDesc(
                memberId, PageRequest.of(safePage, safeSize));

        return PageResponse.of(
                found.getContent().stream().map(this::toSummary).toList(),
                safePage, safeSize, found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse orderDetail(Long memberId, String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .filter(o -> o.ownedBy(memberId))   // 남의 주문은 존재를 숨긴다.
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return toDetail(order);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPurchased(Long memberId, Long goodsNo) {
        // 이 회원의 결제완료 주문 중 그 상품을 포함한 것이 하나라도 있으면 true.
        // exists 쿼리라 건수를 세지 않고 첫 매칭에서 멈춘다.
        return orderRepository.existsPaidItem(memberId, goodsNo, Order.STATUS_PAID);
    }

    private OrderSummaryResponse toSummary(Order order) {
        List<OrderItem> items = order.getItems();
        String representative = items.isEmpty() ? "" : items.get(0).getGoodsName();
        return new OrderSummaryResponse(
                order.getOrderNo(),
                order.getStatus(),
                representative,
                items.size(),
                order.getPayableAmount(),
                order.getOrderedAt());
    }

    private OrderDetailResponse toDetail(Order order) {
        List<OrderDetailResponse.OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderDetailResponse.OrderItemResponse(
                        item.getGoodsName(),
                        item.getOptionName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getLineAmount()))
                .toList();

        return new OrderDetailResponse(
                order.getOrderNo(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getPayableAmount(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getZipcode(),
                order.getAddress1(),
                order.getAddress2(),
                order.getDeliveryType(),
                order.getOrderedAt(),
                order.getPaidAt(),
                items);
    }

    /**
     * 주문번호 생성: {@code yyyyMMdd + 랜덤 8자}.
     *
     * <p>PK 연번을 노출하지 않는 이유: 총 주문 수가 새어나가고, 앞뒤 번호로 남의 주문을 찔러볼 수 있다.
     * 알파벳에서 {@code I O 0 1}을 뺀 것은 고객센터에서 번호를 불러줄 때 헷갈리기 때문이다.
     *
     * <p>중복 시 재시도하는 이유: 랜덤이라 충돌 확률이 극히 낮지만 0은 아니다.
     * DB 유니크 제약이 최종 방어선이고, 여기서 미리 피해 500을 줄인다.
     */
    private String 주문번호_생성() {
        for (int attempt = 0; attempt < ORDER_NO_MAX_ATTEMPTS; attempt++) {
            StringBuilder builder = new StringBuilder(LocalDateTime.now().format(ORDER_NO_DATE));
            for (int i = 0; i < ORDER_NO_RANDOM_LENGTH; i++) {
                builder.append(ORDER_NO_ALPHABET.charAt(random.nextInt(ORDER_NO_ALPHABET.length())));
            }
            String candidate = builder.toString();
            if (!orderRepository.existsByOrderNo(candidate)) {
                return candidate;
            }
        }
        // 5번 연속 충돌은 랜덤이 고장났다는 뜻이다. 조용히 넘어가면 원인을 못 찾는다.
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
