package com.beautyboy.ranking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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

    /**
     * 실 구현이 아직 없는 도메인(현재는 wishlist=T3)의 폴백 계약.
     *
     * <p>T2가 머지된 지금 SalesStatProvider는 order의 실 구현({@code OrderSalesStatProvider})이 주입된다 —
     * 그래서 여기서 폴백을 확인하는 대상은 <b>WishStatProvider</b>다. T3(wishlist)가 머지되면
     * 이 클래스는 검증할 폴백이 사라지므로, 그때 제거하거나 남는 미구현 도메인으로 갱신한다.
     */
    @SpringBootTest
    @ActiveProfiles("test")
    static class 미구현_도메인은_폴백으로_뜬다 {

        @Autowired
        SalesStatProvider salesStatProvider;
        @Autowired
        WishStatProvider wishStatProvider;

        @Test
        void 두_공급자가_모두_주입되어_앱이_뜬다() {
            // 실 구현(sales)이든 폴백(wish)이든, 랭킹 배치가 주입받을 빈이 둘 다 있어야 컨텍스트가 뜬다.
            assertThat(salesStatProvider).isNotNull();
            assertThat(wishStatProvider).isNotNull();
        }

        @Test
        void 찜_폴백은_널이_아니라_빈_맵을_준다() {
            // 집계 쪽에서 널 체크를 하지 않도록 계약으로 고정한다.
            // 찜은 아직 실 구현이 없으므로 항상 빈 맵이다(T3 머지 전).
            assertThat(wishStatProvider.wishCountByGoods(LocalDate.now())).isEmpty();
        }
    }

    @SpringBootTest
    @ActiveProfiles("test")
    static class 실_구현이_있을_때 {

        @TestConfiguration
        static class 가짜_주문도메인 {
            // @Primary: 실 OrderSalesStatProvider(@Component)와 공존하므로, 이 결정적 가짜를
            // 우선시켜 "실 구현이 폴백을 이긴다"는 사실을 재현한다(둘 다 실 구현이라 @Primary가 없으면 NoUniqueBean).
            @Bean
            @Primary
            SalesStatProvider 실_구현_흉내() {
                return date -> Map.of(42L, 7);
            }
        }

        @Autowired
        SalesStatProvider salesStatProvider;

        @Test
        void 폴백이_아니라_실_구현이_이긴다() {
            // 실 SalesStatProvider 구현이 있으면 빈 맵 폴백이 물러나야 한다.
            // 여기가 깨지면 랭킹이 조용히 조회수 랭킹으로 남는다 — 자동 설정의 @ConditionalOnMissingBean이 풀렸다는 뜻이다.
            assertThat(salesStatProvider.salesQuantityByGoods(LocalDate.now()))
                    .containsExactly(Map.entry(42L, 7));
        }
    }
}
