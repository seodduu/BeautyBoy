package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.GoodsListItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void findListItems_는_HIDDEN을_빼고_카드로_반환한다() {
        Brand brand = brandRepository.save(new Brand("브랜드1", null));
        categoryRepository.save(new Category("C001001001", null, "카테고리", 3, 0));

        Goods 노출A = 상품_저장(brand, "노출A", 10000, 9000);
        Goods 노출B = 상품_저장(brand, "노출B", 20000, 20000);
        Goods 숨김C = 상품_저장(brand, "숨김C", 30000, 30000);
        숨김C.hide();
        goodsRepository.save(숨김C);

        var items = goodsService.findListItems(List.of(노출A.getId(), 노출B.getId(), 숨김C.getId()));

        assertThat(items).extracting(GoodsListItem::goodsNo)
                .containsExactlyInAnyOrder(노출A.getId(), 노출B.getId());
    }

    @Test
    void findListItems_는_빈_입력에_빈_목록을_반환한다() {
        assertThat(goodsService.findListItems(List.of())).isEmpty();
    }

    private Goods 상품_저장(Brand brand, String name, int listPrice, int salePrice) {
        Goods goods = new Goods(brand, "C001001001", name, "요약", "https://img.example/" + name + ".jpg",
                listPrice, salePrice);
        return goodsRepository.save(goods);
    }
}
