package com.beautyboy.review;

import com.beautyboy.support.TestPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewHelpfulTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ReviewRepository reviewRepository;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    void 도움됐어요를_누르면_카운트가_오른다() throws Exception {
        Long reviewId = 리뷰_저장(1L, 500L);

        mockMvc.perform(post("/api/v1/reviews/" + reviewId + "/helpful").with(로그인(9L)))
                .andExpect(status().isOk());

        TestPersistence.DB_왕복_강제(entityManager);
        assertThat(reviewRepository.findById(reviewId).orElseThrow().getHelpfulCount()).isEqualTo(1);
    }

    @Test
    void 같은_사람이_두_번_누르면_409() throws Exception {
        Long reviewId = 리뷰_저장(1L, 500L);
        mockMvc.perform(post("/api/v1/reviews/" + reviewId + "/helpful").with(로그인(9L)));

        mockMvc.perform(post("/api/v1/reviews/" + reviewId + "/helpful").with(로그인(9L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_HELPFUL_DUPLICATED"));
    }

    @Test
    void 없는_리뷰면_404() throws Exception {
        mockMvc.perform(post("/api/v1/reviews/999999/helpful").with(로그인(9L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor 로그인(Long memberId) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                memberId, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Long 리뷰_저장(Long memberId, Long goodsId) {
        return reviewRepository.saveAndFlush(new Review(memberId, goodsId, 5, "리뷰", null)).getId();
    }
}
