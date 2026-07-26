package com.beautyboy.routine;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.catalog.GoodsTag;
import com.beautyboy.catalog.GoodsTagRepository;
import com.beautyboy.catalog.Tag;
import com.beautyboy.catalog.TagRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 비로그인 GET이 200인지(기존 SecurityConfig의 GET /api/v1/goods/** permitAll에 자동 포함)와
 * 응답 형태를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NextStepApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired RoutineFlowRuleRepository ruleRepository;
    @Autowired BrandRepository brandRepository;
    @Autowired GoodsRepository goodsRepository;
    @Autowired TagRepository tagRepository;
    @Autowired GoodsTagRepository goodsTagRepository;

    @Test
    void next_step_응답_형태() throws Exception {
        Long 각질토너 = 상품_저장("C001001001", 400);
        태그_부여(각질토너, "exfoliate");
        Long soothe세럼 = 상품_저장("C001002001", 300);
        태그_부여(soothe세럼, "soothe");
        ruleRepository.save(new RoutineFlowRule(null, "C001001", "exfoliate", "C001002", "soothe",
                "BUFFER", "각질 케어 다음엔 진정으로 완충", 10));

        mockMvc.perform(get("/api/v1/goods/" + 각질토너 + "/next-step"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.blocks[0].edgeKind").value("BUFFER"))
                .andExpect(jsonPath("$.data.blocks[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.data.blocks[0].items[0].goodsNo").exists());
    }

    private int seq = 0;

    private Long 상품_저장(String categoryCode, int viewCount) {
        seq++;
        Brand brand = brandRepository.save(new Brand("브랜드" + seq + "_" + System.nanoTime(), null));
        Goods goods = new Goods(brand, categoryCode, "상품" + seq, "요약", "https://img.example/x.jpg", 10000, 10000);
        goods.increaseViewCount(viewCount);
        return goodsRepository.save(goods).getId();
    }

    private void 태그_부여(Long goodsNo, String slug) {
        Tag tag = tagRepository.save(new Tag(slug, "EFFECT", slug, 0));
        goodsTagRepository.save(new GoodsTag(goodsNo, tag.getId(), null, 0));
    }
}
