package com.beautyboy.ranking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 랭킹 통계 공급자 계약 테스트.
 *
 * <p>이 계약이 깨지면 Wave 2의 T1(ranking)·T2(order)·T3(wishlist) 세 터미널이 동시에 막힌다.
 * 그래서 실 구현이 하나도 없는 지금 미리 못 박아둔다.
 */
class RankingStatFallbackTest {

    @SpringBootTest
    @ActiveProfiles("test")
    static class 실_구현이_없을_때 {

        @Autowired
        SalesStatProvider salesStatProvider;
        @Autowired
        WishStatProvider wishStatProvider;

        @Test
        void 폴백이_주입되어_앱이_뜬다() {
            // order·wishlist가 아직 없어도 ranking을 혼자 개발할 수 있어야 한다.
            assertThat(salesStatProvider).isNotNull();
            assertThat(wishStatProvider).isNotNull();
        }

        @Test
        void 폴백은_널이_아니라_빈_맵을_준다() {
            // 집계 쪽에서 널 체크를 하지 않도록 계약으로 고정한다.
            assertThat(salesStatProvider.salesQuantityByGoods(LocalDate.now())).isEmpty();
            assertThat(wishStatProvider.wishCountByGoods(LocalDate.now())).isEmpty();
        }
    }

    @SpringBootTest
    @ActiveProfiles("test")
    static class 실_구현이_있을_때 {

        @TestConfiguration
        static class 가짜_주문도메인 {
            @Bean
            SalesStatProvider 실_구현_흉내() {
                return date -> Map.of(42L, 7);
            }
        }

        @Autowired
        SalesStatProvider salesStatProvider;

        @Test
        void 폴백이_아니라_실_구현이_이긴다() {
            // T2가 @Component로 구현을 올리는 순간 폴백이 물러나야 한다.
            // 여기가 깨지면 랭킹이 조용히 조회수 랭킹으로 남는다 — 자동 설정 등록이 풀렸다는 뜻이다.
            assertThat(salesStatProvider.salesQuantityByGoods(LocalDate.now()))
                    .containsExactly(Map.entry(42L, 7));
        }
    }
}
