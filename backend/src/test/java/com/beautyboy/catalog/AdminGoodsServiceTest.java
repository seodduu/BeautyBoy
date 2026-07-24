package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.AdminGoodsSaveRequest;
import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.support.TestPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminGoodsServiceTest {

    @Autowired
    AdminGoodsService adminGoodsService;
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

    @PersistenceContext
    EntityManager em;

    private Brand 브랜드() {
        return brandRepository.save(new Brand("브랜드1", null));
    }

    private void 카테고리() {
        categoryRepository.save(new Category("C001001001", null, "카테고리", 3, 0));
    }

    private Goods 상품_저장(Brand brand, String name, int listPrice, int salePrice) {
        Goods goods = new Goods(brand, "C001001001", name, "요약", "https://img.example/" + name + ".jpg",
                listPrice, salePrice);
        return goodsRepository.save(goods);
    }

    @Test
    void 삭제는_행을_지우지_않고_상태를_HIDDEN으로_내린다() {
        카테고리();
        Goods 상품A = 상품_저장(브랜드(), "상품A", 10000, 9000);

        adminGoodsService.delete(상품A.getId());
        TestPersistence.DB_왕복_강제(em);

        assertThat(goodsRepository.findById(상품A.getId())).get()
                .satisfies(g -> assertThat(g.getStatus()).isEqualTo(Goods.STATUS_HIDDEN));
    }

    @Test
    void 존재하지_않는_상품을_삭제하면_GOODS_NOT_FOUND다() {
        assertThatThrownBy(() -> adminGoodsService.delete(999999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GOODS_NOT_FOUND);
    }

    @Test
    void 판매가가_정가보다_크면_GOODS_PRICE_INVALID다() {
        카테고리();
        Brand brand = 브랜드();
        AdminGoodsSaveRequest 요청 = new AdminGoodsSaveRequest(
                brand.getId(), "C001001001", "상품", "요약", "https://img.example/x.jpg", 10000, 12000, "ON_SALE");

        assertThatThrownBy(() -> adminGoodsService.create(요청))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GOODS_PRICE_INVALID);
    }

    @Test
    void 없는_카테고리코드로_등록하면_GOODS_CATEGORY_INVALID다() {
        Brand brand = 브랜드();
        AdminGoodsSaveRequest 요청 = new AdminGoodsSaveRequest(
                brand.getId(), "NOPE0000000", "상품", "요약", "https://img.example/x.jpg", 10000, 9000, "ON_SALE");

        assertThatThrownBy(() -> adminGoodsService.create(요청))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GOODS_CATEGORY_INVALID);
    }

    @Test
    void 유효한_요청으로_등록하면_goodsNo를_반환한다() {
        카테고리();
        Brand brand = 브랜드();
        AdminGoodsSaveRequest 요청 = new AdminGoodsSaveRequest(
                brand.getId(), "C001001001", "새상품", "요약", "https://img.example/new.jpg", 10000, 9000, "ON_SALE");

        Long goodsNo = adminGoodsService.create(요청);
        TestPersistence.DB_왕복_강제(em);

        assertThat(goodsNo).isNotNull();
        assertThat(goodsRepository.findById(goodsNo)).get()
                .satisfies(g -> assertThat(g.getName()).isEqualTo("새상품"));
    }

    @Test
    void 수정하면_이름과_상태가_바뀐다() {
        카테고리();
        Goods 상품A = 상품_저장(브랜드(), "상품A", 10000, 9000);
        AdminGoodsSaveRequest 요청 = new AdminGoodsSaveRequest(
                null, "C001001001", "바뀐이름", "새요약", "https://img.example/updated.jpg",
                20000, 15000, Goods.STATUS_SOLD_OUT);

        adminGoodsService.update(상품A.getId(), 요청);
        TestPersistence.DB_왕복_강제(em);

        assertThat(goodsRepository.findById(상품A.getId())).get()
                .satisfies(g -> {
                    assertThat(g.getName()).isEqualTo("바뀐이름");
                    assertThat(g.getStatus()).isEqualTo(Goods.STATUS_SOLD_OUT);
                    assertThat(g.getSalePrice()).isEqualTo(15000);
                });
    }

    @Test
    void 관리자_목록은_HIDDEN_상품도_포함한다() {
        // 일반 목록과 다른 유일한 지점. 숨긴 상품을 관리자가 못 보면 되살릴 방법이 없다.
        카테고리();
        Brand brand = 브랜드();
        Goods 노출A = 상품_저장(brand, "노출A", 10000, 9000);
        Goods 숨김B = 상품_저장(brand, "숨김B", 10000, 9000);
        숨김B.hide();
        goodsRepository.save(숨김B);
        TestPersistence.DB_왕복_강제(em);

        var content = adminGoodsService.list(0, 20, null).content();

        assertThat(content).extracting(GoodsListItem::goodsNo)
                .contains(노출A.getId(), 숨김B.getId());
    }
}
