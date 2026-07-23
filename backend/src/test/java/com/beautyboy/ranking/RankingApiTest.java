package com.beautyboy.ranking;

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

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RankingApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    RankingSnapshotRepository rankingSnapshotRepository;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    @Test
    void 스냅샷_순서대로_상품_정보와_함께_준다() throws Exception {
        Long 일위 = 상품_저장("1위 토너");
        Long 이위 = 상품_저장("2위 토너");
        스냅샷_저장("ALL", 일위, 1, 100.0);
        스냅샷_저장("ALL", 이위, 2, 50.0);

        mockMvc.perform(get("/api/v1/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].name").value("1위 토너"))
                .andExpect(jsonPath("$.data[1].rank").value(2));
    }

    @Test
    void categoryCode로_해당_카테고리_랭킹만_준다() throws Exception {
        Long 스킨케어 = 상품_저장("스킨케어 상품");
        스냅샷_저장("C001", 스킨케어, 1, 10.0);
        스냅샷_저장("ALL", 스킨케어, 1, 10.0);

        mockMvc.perform(get("/api/v1/rankings").param("categoryCode", "C001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("스킨케어 상품"));
    }

    @Test
    void 아직_집계된_랭킹이_없으면_빈_목록이다() throws Exception {
        // 부팅 직후 배치가 한 번도 안 돈 상태. 500이나 404가 아니라 빈 목록이어야
        // 프론트가 "아직 없음" 화면을 자연스럽게 그린다.
        mockMvc.perform(get("/api/v1/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 비로그인도_조회할_수_있다() throws Exception {
        // 설계 7장 공개 목록. 토큰 없이 200이어야 한다.
        mockMvc.perform(get("/api/v1/rankings")).andExpect(status().isOk());
    }

    private Long 상품_저장(String name) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        return goodsRepository.save(
                new Goods(brand, "C001001001", name, null, "https://img/x.jpg", 20000, 16000)).getId();
    }

    private void 스냅샷_저장(String categoryCode, Long goodsId, int rankNo, double score) {
        rankingSnapshotRepository.save(
                new RankingSnapshot(categoryCode, goodsId, rankNo, score, LocalDateTime.now()));
    }
}
