package com.beautyboy.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GoodsOrderSnapshotTest {

    @Autowired
    GoodsQueryService goodsQueryService;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    @Test
    void 옵션이_없으면_상품_판매가가_단가다() {
        Goods goods = 상품_저장("그린티 토너", 20000, 16000);

        Optional<GoodsQueryService.OrderGoodsSnapshot> snapshot =
                goodsQueryService.findOrderSnapshot(goods.getId(), null);

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().goodsName()).isEqualTo("그린티 토너");
        assertThat(snapshot.get().optionName()).isNull();
        // 정가(20000)가 아니라 판매가(16000)다. 여기가 틀리면 손님이 정가로 결제한다.
        assertThat(snapshot.get().unitPrice()).isEqualTo(16000);
    }

    @Test
    void 옵션이_하나도_없는_상품만_재고가_무제한이다() {
        // 회귀 방어(Task 4-18): MAX_VALUE는 "옵션이 진짜 없는 상품"에만 남아야 한다.
        Goods goods = 상품_저장("옵션 없는 상품", 20000, 16000);

        GoodsQueryService.OrderGoodsSnapshot snapshot =
                goodsQueryService.findOrderSnapshot(goods.getId(), null).orElseThrow();

        assertThat(snapshot.stock()).isEqualTo(Integer.MAX_VALUE);
        assertThat(snapshot.optionId()).isNull();
        assertThat(snapshot.optionName()).isNull();
        assertThat(snapshot.unitPrice()).isEqualTo(16000);
    }

    @Test
    void 옵션을_지정하지_않으면_sortOrder가_가장_낮은_옵션이_대표가_된다() {
        // 상세 화면이 첫 번째로 보여주는 옵션(sortOrder 최소)과 서버의 대표값이 일치해야 한다.
        Goods goods = 상품_저장("토너", 20000, 16000);
        Long 옵션_300ml = 옵션_저장(goods, "300ml", 3000, 40, 2);
        Long 옵션_200ml = 옵션_저장(goods, "200ml", 0, 150, 1);

        GoodsQueryService.OrderGoodsSnapshot snapshot =
                goodsQueryService.findOrderSnapshot(goods.getId(), null).orElseThrow();

        assertThat(snapshot.optionId()).isEqualTo(옵션_200ml);
        assertThat(snapshot.optionName()).isEqualTo("200ml");
        assertThat(snapshot.unitPrice()).isEqualTo(16000);
        assertThat(snapshot.stock()).isEqualTo(150);
        // 추가금이 붙은 300ml는 나중 순서라 대표가 아니다.
        assertThat(snapshot.optionId()).isNotEqualTo(옵션_300ml);
        // 옵션이 있는 상품이 재고 무제한으로 답하는 순간 재고 게이트가 무력화된다.
        assertThat(snapshot.stock()).isNotEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void 대표_옵션에_추가금이_있으면_단가에_반영된다() {
        // sortOrder 최소 옵션 자체가 추가금을 가진 경우. 대표 선택과 가격 계산이 함께 맞아야 한다.
        Goods goods = 상품_저장("기획세트", 40000, 30000);
        Long 옵션_대용량 = 옵션_저장(goods, "대용량", 5000, 12, 1);
        옵션_저장(goods, "리필", -3000, 12, 2);

        GoodsQueryService.OrderGoodsSnapshot snapshot =
                goodsQueryService.findOrderSnapshot(goods.getId(), null).orElseThrow();

        assertThat(snapshot.optionId()).isEqualTo(옵션_대용량);
        assertThat(snapshot.unitPrice()).isEqualTo(35000);
    }

    @Test
    void 대표_옵션이_품절이면_재고_0이_실린다() {
        // 시드 goods 3(유일 옵션 재고 0)과 같은 형태. "재고 있는 옵션 우선"으로 숨기지 않고
        // 정직하게 0을 실어 호출자(주문)가 ORDER_OUT_OF_STOCK으로 막게 한다.
        Goods goods = 상품_저장("품절 세럼", 25000, 20000);
        Long 유일_옵션 = 옵션_저장(goods, "180ml", 0, 0, 1);

        GoodsQueryService.OrderGoodsSnapshot snapshot =
                goodsQueryService.findOrderSnapshot(goods.getId(), null).orElseThrow();

        assertThat(snapshot.optionId()).isEqualTo(유일_옵션);
        assertThat(snapshot.stock()).isZero();
    }

    @Test
    void sortOrder가_동률이면_id가_낮은_옵션이_대표다() {
        // 결정적이어야 한다 — 같은 입력에 같은 답이 나와야 테스트가 흔들리지 않는다.
        Goods goods = 상품_저장("립밤", 9000, 9000);
        Long 먼저_저장 = 옵션_저장(goods, "먼저", 0, 5, 0);
        Long 나중_저장 = 옵션_저장(goods, "나중", 1000, 5, 0);

        GoodsQueryService.OrderGoodsSnapshot snapshot =
                goodsQueryService.findOrderSnapshot(goods.getId(), null).orElseThrow();

        assertThat(먼저_저장).isLessThan(나중_저장);
        assertThat(snapshot.optionId()).isEqualTo(먼저_저장);
        assertThat(snapshot.optionName()).isEqualTo("먼저");
    }

    @Test
    void 옵션이_있으면_판매가에_추가금을_더한_값이_단가다() {
        Goods goods = 상품_저장("선크림", 30000, 24000);
        Long optionId = 옵션_저장(goods, "50ml 대용량", 3000, 7);

        GoodsQueryService.OrderGoodsSnapshot snapshot =
                goodsQueryService.findOrderSnapshot(goods.getId(), optionId).orElseThrow();

        assertThat(snapshot.optionName()).isEqualTo("50ml 대용량");
        assertThat(snapshot.unitPrice()).isEqualTo(27000);
        assertThat(snapshot.stock()).isEqualTo(7);
    }

    @Test
    void 없는_상품이면_비어_있다() {
        assertThat(goodsQueryService.findOrderSnapshot(999999L, null)).isEmpty();
    }

    @Test
    void 숨김_상품은_주문할_수_없도록_비어_있다() {
        // 목록·상세에서 숨긴 상품을 주문 경로로 우회해 살 수 있으면 숨김이 의미가 없다.
        Goods goods = 상품_저장("단종 상품", 10000, 10000);
        goods.hide();
        goodsRepository.save(goods);

        assertThat(goodsQueryService.findOrderSnapshot(goods.getId(), null)).isEmpty();
    }

    @Test
    void 다른_상품의_옵션을_붙이면_비어_있다() {
        // 옵션 id만 바꿔치기하면 싼 상품 가격에 비싼 옵션을 붙이는 식의 조작이 가능해진다.
        Goods 상품A = 상품_저장("상품A", 10000, 10000);
        Goods 상품B = 상품_저장("상품B", 90000, 90000);
        Long 상품B의_옵션 = 옵션_저장(상품B, "옵션", 0, 5);

        assertThat(goodsQueryService.findOrderSnapshot(상품A.getId(), 상품B의_옵션)).isEmpty();
    }

    private Goods 상품_저장(String name, int listPrice, int salePrice) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        return goodsRepository.save(
                new Goods(brand, "C001001001", name, null, "https://img/x.jpg", listPrice, salePrice));
    }

    private Long 옵션_저장(Goods goods, String name, int addPrice, int stock) {
        return 옵션_저장(goods, name, addPrice, stock, 0);
    }

    private Long 옵션_저장(Goods goods, String name, int addPrice, int stock, int sortOrder) {
        // GoodsOption 실제 생성자는 (goods, name, addPrice, stock, sortOrder) 5개 인자다.
        GoodsOption option = new GoodsOption(goods, name, addPrice, stock, sortOrder);
        goods.getOptions().add(option);
        // goods가 이미 저장된(managed) 엔티티라 save()는 merge를 타고, merge 캐스케이드가 새 옵션을
        // 복사본으로 치환한다 — 원래 넘긴 option 인스턴스가 아니라 저장된 goods의 컬렉션에서 id를 읽는다.
        Goods saved = goodsRepository.save(goods);
        goodsRepository.flush();
        return saved.getOptions().stream()
                .filter(o -> o.getName().equals(name))
                .map(GoodsOption::getId)
                .findFirst()
                .orElseThrow();
    }
}
