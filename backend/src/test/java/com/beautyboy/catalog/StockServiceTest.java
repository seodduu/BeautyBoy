package com.beautyboy.catalog;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.beautyboy.support.TestPersistence.DB_왕복_강제;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * 재고 차감 커맨드(catalog 소유)의 계약 검증.
 *
 * <p>클래스 {@code @Transactional}이 곧 호출자의 트랜잭션 역할을 한다 — 구현이 요구하는
 * {@code Propagation.MANDATORY}를 충족시킨다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StockServiceTest {

    @Autowired
    StockCommandService stockCommandService;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    GoodsOptionRepository goodsOptionRepository;
    @Autowired
    EntityManager entityManager;

    @Test
    void 재고_5에서_3을_깎으면_2가_남는다() {
        Long optionId = 옵션_저장("토너", 5);

        stockCommandService.deductAll(List.of(new StockCommandService.DeductionLine(optionId, 3)));

        assertThat(재조회_재고(optionId)).isEqualTo(2);
    }

    @Test
    void 재고_2에서_3을_깎으면_ORDER_OUT_OF_STOCK이고_그_옵션_재고는_줄지_않는다() {
        Long optionId = 옵션_저장("세럼", 2);

        assertThatThrownBy(() -> stockCommandService.deductAll(
                List.of(new StockCommandService.DeductionLine(optionId, 3))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_OUT_OF_STOCK);

        // 조건부 UPDATE라 "부족하면 아예 안 깎인다" — 음수 재고가 남지 않는다.
        assertThat(재조회_재고(optionId)).isEqualTo(2);
    }

    @Test
    void 같은_옵션_두_줄_2더하기2는_합산돼_재고_5가_1이_된다() {
        Long optionId = 옵션_저장("크림", 5);

        stockCommandService.deductAll(List.of(
                new StockCommandService.DeductionLine(optionId, 2),
                new StockCommandService.DeductionLine(optionId, 2)));

        // 합산 없이 줄 단위로 깎아도 5-2-2=1이라 값은 같지만, 아래 테스트가 줄 단위 구현을 잡는다.
        assertThat(재조회_재고(optionId)).isEqualTo(1);
    }

    @Test
    void 같은_옵션_두_줄의_합_4가_재고_3을_넘으면_실패한다() {
        // 줄 단위(2·2)로 깎으면 첫 줄이 통과해 재고가 1로 줄어드는 회귀를 잡는다.
        Long optionId = 옵션_저장("클렌저", 3);

        assertThatThrownBy(() -> stockCommandService.deductAll(List.of(
                new StockCommandService.DeductionLine(optionId, 2),
                new StockCommandService.DeductionLine(optionId, 2))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_OUT_OF_STOCK);

        assertThat(재조회_재고(optionId)).isEqualTo(3);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 트랜잭션_없이_부르면_예외다() {
        // "롤백이 곧 복원"이라는 계약은 차감이 트랜잭션 안에서만 불릴 때만 성립한다.
        // MANDATORY가 그 강제 장치이며, 살아 있는지 여기서 확인한다.
        assertThatThrownBy(() -> stockCommandService.deductAll(
                List.of(new StockCommandService.DeductionLine(1L, 1))))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void 복원은_합산되고_오름차순으로_실행된다() {
        // 줄 단위로 복원해도 재고 합계는 같다 — 그래서 결과값이 아니라 호출 자체를 본다.
        // 합산이 깨지면 UPDATE 횟수가 늘고, 정렬이 깨지면 차감과 락 순서가 어긋나 교차 데드락이 산다.
        GoodsOptionRepository repository = mock(GoodsOptionRepository.class);
        given(repository.restore(anyLong(), anyInt())).willReturn(1);

        new StockService(repository).restoreAll(List.of(
                new StockCommandService.RestoreLine(2L, 1),
                new StockCommandService.RestoreLine(1L, 1),
                new StockCommandService.RestoreLine(2L, 2)));

        InOrder 순서 = inOrder(repository);
        순서.verify(repository).restore(1L, 1);
        순서.verify(repository).restore(2L, 3);
        verifyNoMoreInteractions(repository);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 복원도_트랜잭션_없이_부르면_예외다() {
        // 복원이 자기 혼자 커밋되면 "토스 실패 = 롤백 = 재고 원복"이라는 취소 계약이 깨진다.
        assertThatThrownBy(() -> stockCommandService.restoreAll(
                List.of(new StockCommandService.RestoreLine(1L, 1))))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void 존재하지_않는_옵션_복원은_IllegalStateException() {
        // 취소 검증을 통과한 옵션이 사라졌다는 뜻 — 재고 문제가 아니라 버그다. 조용히 넘기지 않는다.
        assertThatThrownBy(() -> stockCommandService.restoreAll(
                List.of(new StockCommandService.RestoreLine(-1L, 1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("존재하지 않는 옵션 복원 시도");
    }

    // 수량 0 이하 줄은 들어오지 않는 것이 계약이다 — 주문 생성이 이미 CART_QUANTITY_INVALID로
    // 막는다(OrderService). 여기서 다시 검증하면 이중 검증이 되므로 테스트를 두지 않는다.

    private int 재조회_재고(Long optionId) {
        DB_왕복_강제(entityManager);
        return goodsOptionRepository.findById(optionId).orElseThrow().getStock();
    }

    private Long 옵션_저장(String name, int stock) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        Goods goods = goodsRepository.save(
                new Goods(brand, "C001001001", name, null, "https://img/x.jpg", 20000, 16000));
        GoodsOption option = new GoodsOption(goods, "기본", 0, stock, 0);
        goods.getOptions().add(option);
        Goods saved = goodsRepository.save(goods);
        goodsRepository.flush();
        return saved.getOptions().stream()
                .filter(o -> o.getName().equals("기본"))
                .map(GoodsOption::getId)
                .findFirst()
                .orElseThrow();
    }
}
