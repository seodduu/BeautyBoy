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
     * T3(wishlist)까지 머지된 지금, sales·wish 두 도메인 모두 실 구현이 주입된다 —
     * 더 이상 폴백으로 뜨는 도메인이 없다. 이 클래스는 그 통합 상태(둘 다 명명된 실 구현)를
     * 검증한다: {@code SalesStatProvider}는 order의 {@code OrderSalesStatProvider},
     * {@code WishStatProvider}는 wishlist의 {@code WishlistWishStatProvider}가 주입되어야
     * 랭킹 점수 `판매×3 + 찜×2 + 조회×1`의 세 항이 모두 실 데이터로 채워진다.
     *
     * <p>폴백(람다/익명 클래스) 자체가 여전히 동작함은 아래 {@code 실_구현이_있을_때}에서
     * @Primary 가짜로 격리 검증한다. {@code RankingStatFallbackAutoConfiguration}은 유지한다 —
     * 다음에 어느 도메인이 실 구현 없이 빠지면 그 도메인만 폴백으로 앱을 띄우기 위함이다.
     */
    @SpringBootTest
    @ActiveProfiles("test")
    static class 두_공급자가_모두_실_구현으로_주입된다 {

        @Autowired
        SalesStatProvider salesStatProvider;
        @Autowired
        WishStatProvider wishStatProvider;

        @Test
        void 두_공급자가_모두_주입되어_앱이_뜬다() {
            assertThat(salesStatProvider).isNotNull();
            assertThat(wishStatProvider).isNotNull();
        }

        @Test
        void sales는_폴백_람다가_아니라_order의_실_구현이다() {
            // 클래스 단순명으로 확인 — 패키지 경계상 order를 test에서 import하지 않는다.
            // CGLIB 프록시("...$$SpringCGLIB$$0")로 감싸질 수 있어 startsWith로 비교한다.
            // 폴백이라면 익명 클래스/람다라서 이 이름으로 시작하지 않는다.
            assertThat(salesStatProvider.getClass().getSimpleName())
                    .startsWith("OrderSalesStatProvider");
        }

        @Test
        void wish는_폴백_람다가_아니라_wishlist의_실_구현이다() {
            // 찜은 더 이상 폴백이 아니다(T3-3 WishlistWishStatProvider 머지 완료).
            assertThat(wishStatProvider.getClass().getSimpleName())
                    .startsWith("WishlistWishStatProvider");
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
