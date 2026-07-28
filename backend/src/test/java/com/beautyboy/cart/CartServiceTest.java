package com.beautyboy.cart;

import com.beautyboy.cart.dto.CartAddRequest;
import com.beautyboy.cart.dto.CartItemResponse;
import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsOption;
import com.beautyboy.catalog.GoodsQueryService;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 장바구니 서비스 재고 상한·응답 확장 테스트.
 *
 * <p>재고 검증은 "결과 수량"이 기준이다 — 줄 단위 검증이면 나눠 담아 재고를 넘는 경우를 못 잡는다.
 * 검증만 하고 차감하지 않는다(차감은 결제 승인 시점).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CartServiceTest {

    private static final Long 회원 = 1L;

    @Autowired
    CartService cartService;
    @Autowired
    CartItemRepository cartItemRepository;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    /**
     * 이 테스트가 이 계획의 존재 이유다 — 줄 수가 늘어도 상품 조회는 한 번이어야 한다.
     * 응답 내용만 단언하면 N+1이 되살아나도 녹색이라 잡히지 않는다.
     */
    @MockitoSpyBean
    GoodsQueryService goodsQueryService;

    @Test
    @DisplayName("담기: 기존 수량과 합쳐 재고를 넘으면 409 ORDER_OUT_OF_STOCK")
    void 담기_재고_초과() {
        // 재고 3인 옵션에 2개가 이미 담긴 상태에서 2개를 더 담는다 — 결과 4 > 3
        Long goodsNo = 상품_저장("토너", 16000);
        Long optionNo = 옵션_저장(goodsNo, "200ml", 0, 3);

        cartService.add(회원, new CartAddRequest(goodsNo, optionNo, 2));

        assertThatThrownBy(() -> cartService.add(회원, new CartAddRequest(goodsNo, optionNo, 2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_OUT_OF_STOCK);
    }

    @Test
    @DisplayName("수량 변경: 재고를 넘는 값이면 409, 재고 이내면 성공")
    void 수량변경_재고_초과() {
        Long goodsNo = 상품_저장("토너", 16000);
        Long optionNo = 옵션_저장(goodsNo, "200ml", 0, 3);
        cartService.add(회원, new CartAddRequest(goodsNo, optionNo, 1));
        Long cartItemId = 단일_항목_id(회원);

        assertThatThrownBy(() -> cartService.changeQuantity(회원, cartItemId, 4)) // 재고 3
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_OUT_OF_STOCK);

        cartService.changeQuantity(회원, cartItemId, 3); // 경계값 — 재고와 같으면 성공
        assertThat(cartService.itemsOf(회원).get(0).quantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("옵션 없는 상품(stock=MAX_VALUE)은 상한 검증을 자연 통과한다")
    void 옵션없는_상품_통과() {
        Long optionlessGoodsNo = 상품_저장("옵션 없는 토너", 16000);

        cartService.add(회원, new CartAddRequest(optionlessGoodsNo, null, 50));
        // 예외 없이 통과하면 성공 — 별도 단언 불필요
    }

    @Test
    @DisplayName("응답에 thumbnailUrl과 stock이 실려 온다")
    void 응답_확장_필드() {
        Long goodsNo = 상품_저장("토너", 16000);
        Long optionNo = 옵션_저장(goodsNo, "200ml", 0, 3);
        cartService.add(회원, new CartAddRequest(goodsNo, optionNo, 1));

        CartItemResponse item = cartService.itemsOf(회원).get(0);

        assertThat(item.thumbnailUrl()).isEqualTo("https://img/x.jpg");
        assertThat(item.stock()).isEqualTo(3);
    }

    @Test
    @DisplayName("장바구니 조회: 줄이 3개여도 상품 조회는 배치 1회 — 단건 조회는 부르지 않는다")
    void 조회는_배치_한_번() {
        // 서로 다른 상품 3개를 담는다(담기 과정의 호출을 세지 않도록 clearInvocations 후 조회)
        for (int i = 0; i < 3; i++) {
            Long goodsNo = 상품_저장("토너" + i, 16000);
            Long optionNo = 옵션_저장(goodsNo, "200ml", 0, 3);
            cartService.add(회원, new CartAddRequest(goodsNo, optionNo, 1));
        }

        clearInvocations(goodsQueryService);
        cartService.itemsOf(회원);

        verify(goodsQueryService, times(1)).findOrderSnapshots(any());
        verify(goodsQueryService, never()).findOrderSnapshot(any(), any());
    }

    @Test
    @DisplayName("숨김 상품 줄은 목록에서 조용히 빠지고 나머지 줄은 그대로 남는다")
    void 숨김_줄만_빠진다() {
        Long goodsNo1 = 상품_저장("토너", 16000);
        Long optionNo1 = 옵션_저장(goodsNo1, "200ml", 0, 3);
        cartService.add(회원, new CartAddRequest(goodsNo1, optionNo1, 1));

        Long goodsNo2 = 상품_저장("로션", 18000);
        Long optionNo2 = 옵션_저장(goodsNo2, "150ml", 0, 3);
        cartService.add(회원, new CartAddRequest(goodsNo2, optionNo2, 1));

        Goods hidden = goodsRepository.findById(goodsNo1).orElseThrow();
        hidden.hide();
        goodsRepository.saveAndFlush(hidden);

        assertThat(cartService.itemsOf(회원)).hasSize(1);
        assertThat(cartItemRepository.findByMemberIdOrderByIdAsc(회원)).hasSize(2); // 지우지는 않는다
    }

    @Test
    @DisplayName("optionNo=null로 담긴 레거시 행도 대표 옵션 이름·번호로 채워진다(배치 경로에서도)")
    void 레거시_행도_대표_옵션으로() {
        Long goodsNo = 상품_저장("토너", 16000);
        Long 대표옵션_id = 옵션_저장(goodsNo, "200ml", 0, 3);
        cartItemRepository.save(new CartItem(회원, goodsNo, null, 1));

        CartItemResponse response = cartService.itemsOf(회원).get(0);

        assertThat(response.optionNo()).isEqualTo(대표옵션_id);
        assertThat(response.optionName()).isEqualTo("200ml");
    }

    private Long 단일_항목_id(Long memberId) {
        return cartItemRepository.findByMemberIdOrderByIdAsc(memberId).get(0).getId();
    }

    private Long 상품_저장(String name, int salePrice) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        return goodsRepository.save(
                new Goods(brand, "C001001001", name, null, "https://img/x.jpg", salePrice, salePrice)).getId();
    }

    private Long 옵션_저장(Long goodsId, String name, int addPrice, int stock) {
        Goods goods = goodsRepository.findById(goodsId).orElseThrow();
        goods.getOptions().add(new GoodsOption(goods, name, addPrice, stock, 0));
        // goods가 이미 영속 상태라 save()는 merge를 타고, 새 옵션은 복사본으로 persist된다 —
        // 넘긴 인스턴스가 아니라 저장 결과의 컬렉션에서 id를 읽어야 한다.
        Goods saved = goodsRepository.saveAndFlush(goods);
        return saved.getOptions().stream()
                .filter(o -> o.getName().equals(name))
                .map(GoodsOption::getId)
                .findFirst()
                .orElseThrow();
    }
}
