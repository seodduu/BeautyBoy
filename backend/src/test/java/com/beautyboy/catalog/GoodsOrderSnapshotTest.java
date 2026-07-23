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
        // GoodsOption 실제 생성자는 (goods, name, addPrice, stock, sortOrder) 5개 인자다.
        GoodsOption option = new GoodsOption(goods, name, addPrice, stock, 0);
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
