package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.TagView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GoodsTagRepositoryTest {

    @Autowired BrandRepository brandRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired GoodsRepository goodsRepository;
    @Autowired TagRepository tagRepository;
    @Autowired GoodsTagRepository goodsTagRepository;

    @Test
    void 상품의_태그를_sort_order순으로_batch_조회한다() {
        Goods g = 상품_저장("C002001");
        Tag cleanse = 태그_저장("세정", "EFFECT", "cleanse", 0);
        Tag exfo = 태그_저장("각질 케어", "EFFECT", "exfoliate", 1);
        goodsTagRepository.save(new GoodsTag(g.getId(), exfo.getId(), null, 2));
        goodsTagRepository.save(new GoodsTag(g.getId(), cleanse.getId(), null, 1));

        Map<Long, List<TagView>> m = goodsTagRepository.findTagsByGoodsIds(List.of(g.getId()));

        assertThat(m.get(g.getId())).extracting(TagView::slug).containsExactly("cleanse", "exfoliate");
    }

    @Test
    void 빈_입력은_빈_맵() {
        assertThat(goodsTagRepository.findTagsByGoodsIds(List.of())).isEmpty();
    }

    private int seq = 0;

    private Goods 상품_저장(String categoryCode) {
        seq++;
        Brand brand = brandRepository.save(new Brand("브랜드" + seq, null));
        if (categoryRepository.findById(categoryCode).isEmpty()) {
            categoryRepository.save(new Category(categoryCode, null, "카테고리", 2, 0));
        }
        Goods goods = new Goods(brand, categoryCode, "상품" + seq, "요약", "https://img.example/x.jpg", 10000, 10000);
        return goodsRepository.save(goods);
    }

    private Tag 태그_저장(String name, String kind, String slug, int sortOrder) {
        return tagRepository.save(new Tag(name, kind, slug, sortOrder));
    }
}
