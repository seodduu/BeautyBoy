package com.beautyboy.common;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Category;
import com.beautyboy.catalog.CategoryRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.qna.Qna;
import com.beautyboy.qna.QnaRepository;
import com.beautyboy.routine.RoutineStep;
import com.beautyboy.routine.RoutineStepGoods;
import com.beautyboy.routine.RoutineTemplate;
import com.beautyboy.routine.RoutineTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 1-2: admin 경로 Bean Validation.
 *
 * <p>앞 7건은 "이제 막힌다"(구조적 결손 → 400 INVALID_INPUT), 뒤 4건은 "애노테이션이 도메인 코드를
 * 가로채지 않는다"(§2 결정 2의 경계 고정)를 못 박는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class AdminRequestValidationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    QnaRepository qnaRepository;
    @Autowired
    RoutineTemplateRepository routineTemplateRepository;

    // --- 구조적 결손: 이제 400 INVALID_INPUT으로 막힌다 ---

    @Test
    @DisplayName("상품 등록: name이 공백이면 400 INVALID_INPUT")
    void 상품등록_이름_공백() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "brandId", 1,
                "categoryCode", "skincare",
                "name", "  ",
                "thumbnailUrl", "https://img/x.jpg",
                "listPrice", 10000,
                "salePrice", 9000));

        mockMvc.perform(post("/api/v1/admin/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("상품 등록: brandId가 null이면 400 INVALID_INPUT — FK NPE(500)로 새지 않는다")
    void 상품등록_브랜드_null() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("brandId", null);
        body.put("categoryCode", "skincare");
        body.put("name", "토너");
        body.put("thumbnailUrl", "https://img/x.jpg");
        body.put("listPrice", 10000);
        body.put("salePrice", 9000);

        mockMvc.perform(post("/api/v1/admin/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("상품 등록: name이 200자를 넘으면 400 INVALID_INPUT — DB 예외(500)로 새지 않는다")
    void 상품등록_이름_길이초과() throws Exception {
        String 긴이름 = "가".repeat(201);
        String body = objectMapper.writeValueAsString(Map.of(
                "brandId", 1,
                "categoryCode", "skincare",
                "name", 긴이름,
                "thumbnailUrl", "https://img/x.jpg",
                "listPrice", 10000,
                "salePrice", 9000));

        mockMvc.perform(post("/api/v1/admin/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("상품 등록: thumbnailUrl이 300자를 넘으면 400 INVALID_INPUT")
    void 상품등록_썸네일_길이초과() throws Exception {
        String 긴URL = "https://img/" + "a".repeat(300);
        String body = objectMapper.writeValueAsString(Map.of(
                "brandId", 1,
                "categoryCode", "skincare",
                "name", "토너",
                "thumbnailUrl", 긴URL,
                "listPrice", 10000,
                "salePrice", 9000));

        mockMvc.perform(post("/api/v1/admin/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("상품 수정도 같은 DTO를 쓰므로 같이 막힌다 — PUT에도 @Valid가 걸려 있다")
    void 상품수정_이름_공백() throws Exception {
        Long goodsNo = 상품_저장("토너", 16000, 16000);
        String body = objectMapper.writeValueAsString(Map.of(
                "brandId", 1,
                "categoryCode", "C001001001",
                "name", "   ",
                "thumbnailUrl", "https://img/x.jpg",
                "listPrice", 16000,
                "salePrice", 16000));

        mockMvc.perform(put("/api/v1/admin/goods/{goodsNo}", goodsNo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("문의 답변: answer가 공백이면 400 INVALID_INPUT — 빈 답변이 저장되지 않는다")
    void 문의답변_공백() throws Exception {
        Long qnaId = 문의_저장();
        String body = objectMapper.writeValueAsString(Map.of("answer", "   "));

        mockMvc.perform(post("/api/v1/admin/qna/{qnaId}/answer", qnaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("루틴 단계 교체: goodsNos가 null이면 400 INVALID_INPUT — NPE(500)로 새지 않는다")
    void 루틴교체_null() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("goodsNos", null);

        mockMvc.perform(put("/api/v1/admin/routines/{templateId}/steps/{stepOrder}/goods", 1L, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // --- 경계 고정: 도메인 판정은 그대로 서비스가 한다 (§2 결정 2) ---

    @Test
    @DisplayName("없는 categoryCode는 여전히 GOODS_CATEGORY_INVALID — @Size가 유효성을 가로채지 않는다")
    void 없는_카테고리는_도메인_코드() throws Exception {
        Long brandId = 브랜드_저장();
        String body = objectMapper.writeValueAsString(Map.of(
                "brandId", brandId,
                "categoryCode", "nope",
                "name", "토너",
                "thumbnailUrl", "https://img/x.jpg",
                "listPrice", 10000,
                "salePrice", 9000));

        mockMvc.perform(post("/api/v1/admin/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GOODS_CATEGORY_INVALID"));
    }

    @Test
    @DisplayName("잘못된 가격은 여전히 GOODS_PRICE_INVALID — 애노테이션을 안 붙였다는 증거")
    void 잘못된_가격은_도메인_코드() throws Exception {
        Long brandId = 브랜드_저장();
        categoryRepository.save(new Category("C001001001", null, "카테고리", 3, 0));
        String body = objectMapper.writeValueAsString(Map.of(
                "brandId", brandId,
                "categoryCode", "C001001001",
                "name", "토너",
                "thumbnailUrl", "https://img/x.jpg",
                "listPrice", 10000,
                "salePrice", 20000));

        mockMvc.perform(post("/api/v1/admin/goods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GOODS_PRICE_INVALID"));
    }

    @Test
    @DisplayName("없는 goodsNo를 단계에 꽂으면 여전히 ROUTINE_STEP_GOODS_INVALID")
    void 없는_상품은_도메인_코드() throws Exception {
        RoutineTemplate template = 루틴_템플릿_저장();
        String body = objectMapper.writeValueAsString(Map.of("goodsNos", List.of(999999)));

        mockMvc.perform(put("/api/v1/admin/routines/{templateId}/steps/{stepOrder}/goods",
                        template.getId(), 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROUTINE_STEP_GOODS_INVALID"));
    }

    @Test
    @DisplayName("빈 goodsNos는 성공한다 — 그 단계의 추천을 비우는 유효한 요청이다")
    void 빈_배열은_유효() throws Exception {
        RoutineTemplate template = 루틴_템플릿_저장();
        String body = objectMapper.writeValueAsString(Map.of("goodsNos", List.of()));

        mockMvc.perform(put("/api/v1/admin/routines/{templateId}/steps/{stepOrder}/goods",
                        template.getId(), 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private Long 브랜드_저장() {
        return brandRepository.save(new Brand("브랜드" + System.nanoTime(), null)).getId();
    }

    private Long 상품_저장(String name, int listPrice, int salePrice) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        if (categoryRepository.findById("C001001001").isEmpty()) {
            categoryRepository.save(new Category("C001001001", null, "카테고리", 3, 0));
        }
        return goodsRepository.save(
                new Goods(brand, "C001001001", name, null, "https://img/x.jpg", listPrice, salePrice)).getId();
    }

    private Long 문의_저장() {
        Long goodsNo = 상품_저장("토너", 16000, 16000);
        return qnaRepository.save(new Qna(1L, goodsNo, "재고 있나요?", false)).getId();
    }

    private RoutineTemplate 루틴_템플릿_저장() {
        List<RoutineStepGoods> stepGoods = new ArrayList<>();
        RoutineStep step = new RoutineStep(null, 1, "클렌징", "팁", stepGoods);
        RoutineTemplate template = new RoutineTemplate(null, "건성 루틴", "DRY", "BASIC_TEST", "설명", List.of(step));
        return routineTemplateRepository.save(template);
    }
}
