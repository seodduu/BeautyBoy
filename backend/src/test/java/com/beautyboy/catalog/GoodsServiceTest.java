package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.GoodsListItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GoodsServiceTest {

    @Autowired
    GoodsService goodsService;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @MockBean
    GoodsRatingProvider goodsRatingProvider;
    @MockBean
    WishedGoodsProvider wishedGoodsProvider;

    @Test
    void findListItems_는_HIDDEN을_빼고_카드로_반환한다() {
        Brand brand = brandRepository.save(new Brand("브랜드1", null));
        categoryRepository.save(new Category("C001001001", null, "카테고리", 3, 0));

        Goods 노출A = 상품_저장(brand, "노출A", 10000, 9000);
        Goods 노출B = 상품_저장(brand, "노출B", 20000, 20000);
        Goods 숨김C = 상품_저장(brand, "숨김C", 30000, 30000);
        숨김C.hide();
        goodsRepository.save(숨김C);

        var items = goodsService.findListItems(List.of(노출A.getId(), 노출B.getId(), 숨김C.getId()), null);

        assertThat(items).extracting(GoodsListItem::goodsNo)
                .containsExactlyInAnyOrder(노출A.getId(), 노출B.getId());
    }

    @Test
    void findListItems_는_빈_입력에_빈_목록을_반환한다() {
        assertThat(goodsService.findListItems(List.of(), null)).isEmpty();
    }

    @Test
    void 목록_카드에_별점과_리뷰수가_공급자_값으로_채워진다() {
        Brand brand = brandRepository.save(new Brand("브랜드1", null));
        categoryRepository.save(new Category("C001001001", null, "카테고리", 3, 0));
        Goods 상품A = 상품_저장(brand, "상품A", 10000, 9000);
        Goods 상품B = 상품_저장(brand, "상품B", 10000, 9000);

        given(goodsRatingProvider.ratingsByGoods(any()))
                .willReturn(Map.of(상품A.getId(), new GoodsRatingProvider.RatingStat(4.5, 12)));

        List<GoodsListItem> items = goodsService.findListItems(List.of(상품A.getId(), 상품B.getId()), null);

        assertThat(items).filteredOn(i -> i.goodsNo().equals(상품A.getId())).singleElement()
                .satisfies(i -> {
                    assertThat(i.rating()).isEqualTo(4.5);
                    assertThat(i.reviewCount()).isEqualTo(12);
                });
        // 리뷰가 없는 상품은 0.0/0 — 공급자가 키를 안 주면 기본값이다
        assertThat(items).filteredOn(i -> i.goodsNo().equals(상품B.getId())).singleElement()
                .satisfies(i -> {
                    assertThat(i.rating()).isEqualTo(0.0);
                    assertThat(i.reviewCount()).isEqualTo(0);
                });
    }

    @Test
    void 로그인한_회원이_찜한_상품만_wished가_true다() {
        Brand brand = brandRepository.save(new Brand("브랜드1", null));
        categoryRepository.save(new Category("C001001001", null, "카테고리", 3, 0));
        Goods 상품A = 상품_저장(brand, "상품A", 10000, 9000);
        Goods 상품B = 상품_저장(brand, "상품B", 10000, 9000);
        Long 회원1 = 1L;

        given(wishedGoodsProvider.wishedGoodsIds(회원1, List.of(상품A.getId(), 상품B.getId())))
                .willReturn(Set.of(상품A.getId()));

        List<GoodsListItem> items = goodsService.findListItems(List.of(상품A.getId(), 상품B.getId()), 회원1);

        assertThat(items).filteredOn(GoodsListItem::wished)
                .extracting(GoodsListItem::goodsNo).containsExactly(상품A.getId());
    }

    @Test
    void 비로그인이면_wished는_전부_false이고_공급자에_null이_전달된다() {
        Brand brand = brandRepository.save(new Brand("브랜드1", null));
        categoryRepository.save(new Category("C001001001", null, "카테고리", 3, 0));
        Goods 상품A = 상품_저장(brand, "상품A", 10000, 9000);

        given(wishedGoodsProvider.wishedGoodsIds(isNull(), any())).willReturn(Set.of());

        List<GoodsListItem> items = goodsService.findListItems(List.of(상품A.getId()), null);

        assertThat(items).allMatch(i -> !i.wished());
    }

    @Test
    void 주문_스냅샷에_썸네일_URL이_실려_온다() {
        // 장바구니 표시용 — 스냅샷을 이미 부르고 있는 CartService가 별도 조회 없이 그대로 옮긴다.
        Brand brand = brandRepository.save(new Brand("브랜드1", null));
        Goods goods = 상품_저장(brand, "토너", 10000, 9000);

        GoodsQueryService.OrderGoodsSnapshot snapshot =
                goodsService.findOrderSnapshot(goods.getId(), null).orElseThrow();

        assertThat(snapshot.thumbnailUrl()).isEqualTo("https://img.example/토너.jpg");
    }

    private Goods 상품_저장(Brand brand, String name, int listPrice, int salePrice) {
        Goods goods = new Goods(brand, "C001001001", name, "요약", "https://img.example/" + name + ".jpg",
                listPrice, salePrice);
        return goodsRepository.save(goods);
    }
}
