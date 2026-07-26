package com.beautyboy.qna;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QnaApiTest {

    private static final Long 작성자 = 1L;
    private static final Long 남 = 2L;
    private static final Long 상품 = 700L;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    QnaService qnaService;
    @Autowired
    QnaRepository qnaRepository;

    @Test
    void 질문을_등록하고_목록에서_본다() throws Exception {
        질문등록(작성자, 상품, "재고 있나요?", false).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/qna").param("goodsNo", String.valueOf(상품)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].question").value("재고 있나요?"));
    }

    @Test
    void 비밀글은_남에게_본문이_가려진다() throws Exception {
        질문등록(작성자, 상품, "비밀 질문입니다", true);

        // 남이 조회 — 본문 마스킹
        mockMvc.perform(get("/api/v1/qna").param("goodsNo", String.valueOf(상품)).with(로그인(남)))
                .andExpect(jsonPath("$.data.content[0].question").value("비밀글입니다."));
    }

    @Test
    void 비밀글도_작성자_본인에게는_보인다() throws Exception {
        질문등록(작성자, 상품, "비밀 질문입니다", true);

        mockMvc.perform(get("/api/v1/qna").param("goodsNo", String.valueOf(상품)).with(로그인(작성자)))
                .andExpect(jsonPath("$.data.content[0].question").value("비밀 질문입니다"));
    }

    @Test
    void 비밀글은_비로그인에게_가려진다() throws Exception {
        질문등록(작성자, 상품, "비밀 질문입니다", true);

        // 조회는 공개 엔드포인트라 비로그인도 200이지만 본문은 마스킹.
        mockMvc.perform(get("/api/v1/qna").param("goodsNo", String.valueOf(상품)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].question").value("비밀글입니다."));
    }

    @Test
    void 질문_등록은_인증이_필요하다() throws Exception {
        mockMvc.perform(post("/api/v1/qna")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions 질문등록(
            Long memberId, Long goodsNo, String question, boolean secret) throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("goodsNo", goodsNo, "question", question, "isSecret", secret));
        return mockMvc.perform(post("/api/v1/qna")
                .with(로그인(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void 답변을_등록하면_상태가_ANSWERED로_바뀐다() throws Exception {
        Qna qna = qnaRepository.save(new Qna(작성자, 상품, "재고 있나요?", false));

        qnaService.answer(qna.getId(), "네, 재고 있습니다.");

        com.beautyboy.support.TestPersistence.DB_왕복_강제(entityManager);
        Qna 다시_읽음 = qnaRepository.findById(qna.getId()).orElseThrow();
        assertThat(다시_읽음.getAnswer()).isEqualTo("네, 재고 있습니다.");
        assertThat(다시_읽음.getStatus()).isEqualTo("ANSWERED");
        assertThat(다시_읽음.getAnsweredAt()).isNotNull();
    }

    @Test
    void 이미_답변된_문의에_다시_답변하면_QNA_ALREADY_ANSWERED다() throws Exception {
        Qna qna = qnaRepository.save(new Qna(작성자, 상품, "재고 있나요?", false));
        qnaService.answer(qna.getId(), "첫 답변");

        assertThatThrownBy(() -> qnaService.answer(qna.getId(), "두번째 답변"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.QNA_ALREADY_ANSWERED);
    }

    @jakarta.persistence.PersistenceContext
    jakarta.persistence.EntityManager entityManager;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor 로그인(Long memberId) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                memberId, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }
}
