package com.beautyboy.search;

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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PopularKeywordTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    SearchKeywordLogRepository searchKeywordLogRepository;
    @Autowired
    PopularKeywordHolder popularKeywordHolder;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    void 검색하면_검색어가_로그에_남는다() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "토너")).andExpect(status().isOk());

        TestPersistence.DB_왕복_강제(entityManager);

        assertThat(searchKeywordLogRepository.findAll())
                .extracting(SearchKeywordLog::getKeyword)
                .containsExactly("토너");
    }

    @Test
    void 집계는_최근_24시간만_세고_많이_검색된_순으로_정렬한다() {
        LocalDateTime now = LocalDateTime.now();
        검색로그_저장("토너", now.minusHours(1));
        검색로그_저장("토너", now.minusHours(2));
        검색로그_저장("크림", now.minusHours(3));
        // 25시간 전 — 창 밖이라 세면 안 된다. 이게 새면 "어제 유행"이 오늘 1위로 남는다.
        검색로그_저장("선크림", now.minusHours(25));
        검색로그_저장("선크림", now.minusHours(26));
        검색로그_저장("선크림", now.minusHours(27));

        TestPersistence.DB_왕복_강제(entityManager);

        popularKeywordHolder.refresh();

        assertThat(popularKeywordHolder.current()).containsExactly("토너", "크림");
    }

    @Test
    void 집계_전에는_빈_목록을_준다() throws Exception {
        // 배치가 한 번도 안 돈 부팅 직후에도 500이 아니라 빈 목록이어야 한다.
        popularKeywordHolder.reset();

        mockMvc.perform(get("/api/v1/search/popular-keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private void 검색로그_저장(String keyword, LocalDateTime searchedAt) {
        searchKeywordLogRepository.save(new SearchKeywordLog(keyword, null, searchedAt));
    }
}
