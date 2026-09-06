package com.beautyboy.routine;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
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
 * 루틴 후보 30개 API(계획서 §5 B2-5). 공개 경로 등록은 B2-6이 전담하므로, 그 등록이 끝나기
 * 전에는 비로그인 요청이 401로 실패할 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class RoutineCandidateApiTest {

    private static final String CATEGORY = "C001001";

    @Autowired MockMvc mockMvc;
    @Autowired BrandRepository brandRepository;
    @Autowired GoodsRepository goodsRepository;

    private int seq = 0;

    @Test
    void 기본_사이즈는_삼십이다() throws Exception {
        상품_여러개_저장(35);

        mockMvc.perform(get("/api/v1/routine/candidates").param("categoryCode", CATEGORY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(30));
    }

    @Test
    void 사이즈가_범위밖이면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/routine/candidates")
                        .param("categoryCode", CATEGORY)
                        .param("size", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/routine/candidates")
                        .param("categoryCode", CATEGORY)
                        .param("size", "31"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 카테고리코드가_없으면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/routine/candidates"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 비로그인도_받을_수_있다() throws Exception {
        상품_여러개_저장(5);

        mockMvc.perform(get("/api/v1/routine/candidates").param("categoryCode", CATEGORY))
                .andExpect(status().isOk());
    }

    @Test
    void 응답에_좌표가_없다() throws Exception {
        상품_여러개_저장(5);

        mockMvc.perform(get("/api/v1/routine/candidates").param("categoryCode", CATEGORY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].embedding").doesNotExist())
                .andExpect(jsonPath("$.data[0].vector").doesNotExist());
    }

    private void 상품_여러개_저장(int count) {
        for (int i = 0; i < count; i++) {
            상품_저장(CATEGORY + "001", 100 + i);
        }
    }

    private Long 상품_저장(String categoryCode, int viewCount) {
        seq++;
        Brand brand = brandRepository.save(new Brand("브랜드" + seq + "_" + System.nanoTime(), null));
        Goods goods = new Goods(brand, categoryCode, "상품" + seq, "요약", "https://img.example/x.jpg", 10000, 10000);
        goods.increaseViewCount(viewCount);
        return goodsRepository.save(goods).getId();
    }
}
