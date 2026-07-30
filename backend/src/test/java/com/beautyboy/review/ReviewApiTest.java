package com.beautyboy.review;

import com.beautyboy.catalog.Brand;
import com.beautyboy.catalog.BrandRepository;
import com.beautyboy.catalog.Goods;
import com.beautyboy.catalog.GoodsRepository;
import com.beautyboy.order.Order;
import com.beautyboy.order.OrderItem;
import com.beautyboy.order.OrderRepository;
import com.beautyboy.support.TestPersistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewApiTest {

    private static final Long 구매자 = 1L;
    private static final Long 비구매자 = 2L;

    // review는 catalog.GoodsQueryService.exists로 상품 존재를 확인하므로
    // 실제 goods 행이 있어야 한다(하드코딩된 상품번호로는 항상 GOODS_NOT_FOUND).
    private Long 상품;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    ReviewRepository reviewRepository;
    @Autowired
    GoodsReviewStatRepository goodsReviewStatRepository;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    ReviewHelpfulRepository reviewHelpfulRepository;
    @PersistenceContext
    EntityManager entityManager;

    @BeforeEach
    void 상품을_준비한다() {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        Goods goods = goodsRepository.save(
                new Goods(brand, "C001001001", "테스트 상품", null, "https://img/x.jpg", 10000, 10000));
        상품 = goods.getId();
    }

    @Test
    void 구매자는_리뷰를_쓸_수_있다() throws Exception {
        결제완료_주문(구매자, 상품);

        리뷰작성(구매자, 상품, 5, "좋아요")
                .andExpect(status().isCreated());

        assertThat(reviewRepository.findAll()).hasSize(1);
    }

    @Test
    void 사지_않은_사람은_403과_REVIEW_NOT_PURCHASED() throws Exception {
        // 구매인증의 핵심. 여기가 뚫리면 아무나 리뷰를 쓴다.
        리뷰작성(비구매자, 상품, 5, "안 샀는데 씀")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_PURCHASED"));
    }

    @Test
    void 같은_상품에_두_번_쓰면_409와_REVIEW_ALREADY_WRITTEN() throws Exception {
        결제완료_주문(구매자, 상품);
        리뷰작성(구매자, 상품, 5, "첫 리뷰");

        리뷰작성(구매자, 상품, 4, "또 씀")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_WRITTEN"));
    }

    @Test
    void 평점이_1_5_범위_밖이면_400() throws Exception {
        결제완료_주문(구매자, 상품);

        리뷰작성(구매자, 상품, 6, "범위 밖")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 리뷰_목록을_최신순으로_조회한다() throws Exception {
        결제완료_주문(구매자, 상품);
        리뷰작성(구매자, 상품, 5, "리뷰 본문");

        mockMvc.perform(get("/api/v1/reviews").param("goodsNo", String.valueOf(상품)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].content").value("리뷰 본문"));
    }

    @Test
    void 리뷰_조회는_비로그인도_가능하다() throws Exception {
        // 설계 7장 공개 목록. 토큰 없이 200.
        mockMvc.perform(get("/api/v1/reviews").param("goodsNo", String.valueOf(상품)))
                .andExpect(status().isOk());
    }

    @Test
    void 리뷰_작성시_평점_통계가_재집계된다() throws Exception {
        결제완료_주문(구매자, 상품);
        결제완료_주문(비구매자, 상품);
        리뷰작성(구매자, 상품, 5, "별 다섯");
        리뷰작성(비구매자, 상품, 3, "별 셋");

        TestPersistence.DB_왕복_강제(entityManager);

        GoodsReviewStat stat = goodsReviewStatRepository.findById(상품).orElseThrow();
        assertThat(stat.getReviewCount()).isEqualTo(2);
        // 평균 (5+3)/2 = 4.0
        assertThat(stat.average()).isEqualTo(4.0);
    }

    @Test
    void 통계_조회가_평균과_개수를_준다() throws Exception {
        결제완료_주문(구매자, 상품);
        리뷰작성(구매자, 상품, 4, "리뷰");

        mockMvc.perform(get("/api/v1/reviews/stats").param("goodsNo", String.valueOf(상품)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCount").value(1))
                .andExpect(jsonPath("$.data.averageRating").value(4.0));
    }

    // ── 설계 §2.7 수정·삭제 ──────────────────────────────────────────────

    @Test
    void 작성자는_별점과_본문을_수정할_수_있다() throws Exception {
        결제완료_주문(구매자, 상품);
        리뷰작성(구매자, 상품, 5, "원래 본문");
        Long reviewId = reviewRepository.findAll().get(0).getId();

        리뷰수정(구매자, reviewId, 3, "고친 본문")
                .andExpect(status().isOk());

        Review review = reviewRepository.findById(reviewId).orElseThrow();
        assertThat(review.getRating()).isEqualTo(3);
        assertThat(review.getContent()).isEqualTo("고친 본문");
    }

    @Test
    void 수정하면_평점_통계가_다시_집계된다() throws Exception {
        결제완료_주문(구매자, 상품);
        리뷰작성(구매자, 상품, 5, "원래 본문");
        Long reviewId = reviewRepository.findAll().get(0).getId();

        리뷰수정(구매자, reviewId, 2, "고친 본문")
                .andExpect(status().isOk());
        TestPersistence.DB_왕복_강제(entityManager);

        GoodsReviewStat stat = goodsReviewStatRepository.findById(상품).orElseThrow();
        assertThat(stat.getReviewCount()).isEqualTo(1);
        assertThat(stat.average()).isEqualTo(2.0);
    }

    @Test
    void 수정해도_피부타입_스냅샷은_그대로다() throws Exception {
        결제완료_주문(구매자, 상품);
        리뷰작성(구매자, 상품, 5, "원래 본문");
        Review review = reviewRepository.findAll().get(0);
        entityManager.createQuery("update Review r set r.skinTypeSnapshot = :s where r.id = :id")
                .setParameter("s", "지성")
                .setParameter("id", review.getId())
                .executeUpdate();
        entityManager.clear();

        리뷰수정(구매자, review.getId(), 3, "고친 본문")
                .andExpect(status().isOk());

        Review updated = reviewRepository.findById(review.getId()).orElseThrow();
        assertThat(updated.getSkinTypeSnapshot()).isEqualTo("지성");
    }

    @Test
    void 남의_리뷰는_수정할_수_없고_404다() throws Exception {
        결제완료_주문(구매자, 상품);
        리뷰작성(구매자, 상품, 5, "원래 본문");
        Long reviewId = reviewRepository.findAll().get(0).getId();

        리뷰수정(비구매자, reviewId, 3, "남이 고침")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
    }

    @Test
    void 없는_리뷰를_수정하면_404다() throws Exception {
        리뷰수정(구매자, 999999L, 3, "없는 리뷰")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
    }

    @Test
    void 별점_범위를_벗어나면_400이다() throws Exception {
        결제완료_주문(구매자, 상품);
        리뷰작성(구매자, 상품, 5, "원래 본문");
        Long reviewId = reviewRepository.findAll().get(0).getId();

        리뷰수정(구매자, reviewId, 6, "범위 밖")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 작성자는_리뷰를_삭제할_수_있다() throws Exception {
        결제완료_주문(구매자, 상품);
        리뷰작성(구매자, 상품, 5, "지울 리뷰");
        Long reviewId = reviewRepository.findAll().get(0).getId();

        리뷰삭제(구매자, reviewId)
                .andExpect(status().isOk());

        assertThat(reviewRepository.findAll()).isEmpty();
    }

    @Test
    void 삭제하면_평점_통계가_0건으로_재집계된다() throws Exception {
        결제완료_주문(구매자, 상품);
        리뷰작성(구매자, 상품, 5, "지울 리뷰");
        Long reviewId = reviewRepository.findAll().get(0).getId();

        리뷰삭제(구매자, reviewId)
                .andExpect(status().isOk());
        TestPersistence.DB_왕복_강제(entityManager);

        GoodsReviewStat stat = goodsReviewStatRepository.findById(상품).orElseThrow();
        assertThat(stat.getReviewCount()).isEqualTo(0);
        assertThat(stat.average()).isEqualTo(0.0);
    }

    @Test
    void 도움돼요가_눌린_리뷰도_삭제된다() throws Exception {
        결제완료_주문(구매자, 상품);
        결제완료_주문(비구매자, 상품);
        리뷰작성(구매자, 상품, 5, "지울 리뷰");
        Long reviewId = reviewRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/v1/reviews/{id}/helpful", reviewId).with(로그인(비구매자)))
                .andExpect(status().isOk());

        리뷰삭제(구매자, reviewId)
                .andExpect(status().isOk());

        assertThat(reviewRepository.findAll()).isEmpty();
        assertThat(reviewHelpfulRepository.findAll()).isEmpty();
    }

    @Test
    void 남의_리뷰는_삭제할_수_없고_404다() throws Exception {
        결제완료_주문(구매자, 상품);
        리뷰작성(구매자, 상품, 5, "지킬 리뷰");
        Long reviewId = reviewRepository.findAll().get(0).getId();

        리뷰삭제(비구매자, reviewId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));

        assertThat(reviewRepository.findAll()).hasSize(1);
    }

    @Test
    void 삭제한_뒤에는_같은_상품에_다시_쓸_수_있다() throws Exception {
        결제완료_주문(구매자, 상품);
        리뷰작성(구매자, 상품, 5, "첫 리뷰");
        Long reviewId = reviewRepository.findAll().get(0).getId();
        리뷰삭제(구매자, reviewId);

        리뷰작성(구매자, 상품, 4, "다시 쓴 리뷰")
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions 리뷰수정(
            Long memberId, Long reviewId, int rating, String content) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("rating", rating, "content", content));
        return mockMvc.perform(put("/api/v1/reviews/{id}", reviewId)
                .with(로그인(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions 리뷰삭제(
            Long memberId, Long reviewId) throws Exception {
        return mockMvc.perform(delete("/api/v1/reviews/{id}", reviewId)
                .with(로그인(memberId)));
    }

    private org.springframework.test.web.servlet.ResultActions 리뷰작성(
            Long memberId, Long goodsNo, int rating, String content) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("goodsNo", goodsNo, "rating", rating, "content", content));
        return mockMvc.perform(post("/api/v1/reviews")
                .with(로그인(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor 로그인(Long memberId) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                memberId, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }

    private void 결제완료_주문(Long memberId, Long goodsId) {
        Order order = new Order("ORD-" + System.nanoTime(), memberId, "홍길동", "010-0000-0000",
                "06234", "서울시", "101호", "NORMAL", LocalDateTime.now());
        order.addItem(new OrderItem(goodsId, null, "상품", null, 10000, 1));
        order.markPaid(LocalDateTime.now());
        orderRepository.saveAndFlush(order);
    }
}
