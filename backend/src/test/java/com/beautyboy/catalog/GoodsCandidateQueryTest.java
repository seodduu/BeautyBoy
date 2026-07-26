package com.beautyboy.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 4(routine)가 소비할 "다음 단계 후보" 조회 인터페이스(GoodsQueryService.findCandidateGoodsNos·
 * tagSlugs)의 계약을 검증한다. 픽스처는 자가주입 — 태그 마스터·goods_tag까지 이 테스트가 직접 만든다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GoodsCandidateQueryTest {

    @Autowired GoodsQueryService goodsQueryService;
    @Autowired BrandRepository brandRepository;
    @Autowired GoodsRepository goodsRepository;
    @Autowired TagRepository tagRepository;
    @Autowired GoodsTagRepository goodsTagRepository;

    @Test
    void 접두사와_태그로_후보를_인기순으로_뽑는다() {
        Goods soothe세럼 = 상품_저장("C001002001", 300);
        상품_저장("C001002001", 200);
        상품_저장("C001002001", 100);
        Goods 다른상품 = 상품_저장("C001002001", 50);
        Tag soothe = tagRepository.save(new Tag("진정", "EFFECT", "soothe", 0));
        goodsTagRepository.save(new GoodsTag(soothe세럼.getId(), soothe.getId(), null, 0));

        List<Long> got = goodsQueryService.findCandidateGoodsNos(
                "C001002", "soothe", 다른상품.getId(), 4);

        assertThat(got).containsExactly(soothe세럼.getId());
    }

    @Test
    void 태그가_null이면_태그_무관_인기순() {
        Goods 뷰300 = 상품_저장("C001002001", 300);
        Goods 뷰200 = 상품_저장("C001002001", 200);
        Goods 뷰100 = 상품_저장("C001002001", 100);
        Goods hidden세럼 = 상품_저장("C001002002", 900);
        hidden세럼.hide();
        goodsRepository.save(hidden세럼);
        Goods 다른상품 = 상품_저장("C001002001", 50);

        List<Long> got = goodsQueryService.findCandidateGoodsNos(
                "C001002", null, 다른상품.getId(), 4);

        assertThat(got).containsExactly(뷰300.getId(), 뷰200.getId(), 뷰100.getId());
    }

    @Test
    void 자기_자신과_HIDDEN은_제외된다() {
        Goods 뷰300 = 상품_저장("C001002001", 300);
        상품_저장("C001002001", 200);
        Goods hidden세럼 = 상품_저장("C001002002", 900);
        hidden세럼.hide();
        goodsRepository.save(hidden세럼);

        List<Long> got = goodsQueryService.findCandidateGoodsNos(
                "C001002", null, 뷰300.getId(), 4);

        assertThat(got).doesNotContain(뷰300.getId(), hidden세럼.getId());
    }

    @Test
    void tagSlugs는_태그_슬러그_집합_없으면_빈집합() {
        Goods soothe세럼 = 상품_저장("C001002001", 300);
        Goods 태그없는세럼 = 상품_저장("C001002001", 200);
        Tag soothe = tagRepository.save(new Tag("진정", "EFFECT", "soothe", 0));
        goodsTagRepository.save(new GoodsTag(soothe세럼.getId(), soothe.getId(), null, 0));

        Set<String> soothe세럼의_태그 = goodsQueryService.tagSlugs(soothe세럼.getId());
        Set<String> 태그없는세럼의_태그 = goodsQueryService.tagSlugs(태그없는세럼.getId());

        assertThat(soothe세럼의_태그).contains("soothe");
        assertThat(태그없는세럼의_태그).isEmpty();
    }

    private int seq = 0;

    private Goods 상품_저장(String categoryCode, int viewCount) {
        seq++;
        Brand brand = brandRepository.save(new Brand("브랜드" + seq + "_" + System.nanoTime(), null));
        Goods goods = new Goods(brand, categoryCode, "세럼" + seq, "요약", "https://img.example/x.jpg", 10000, 10000);
        goods.increaseViewCount(viewCount);
        return goodsRepository.save(goods);
    }
}
