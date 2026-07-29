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
     * 현재 상태(A4b 이후): 찜만 실 구현이고 <b>판매는 폴백으로 뜬다.</b>
     *
     * <p>판매량이 주문 확정 이벤트의 증분으로 옮겨가면서 order의 {@code OrderSalesStatProvider}가
     * 삭제됐다. 랭킹 배치도 더는 {@code SalesStatProvider}를 부르지 않는다 — 판매 항은
     * {@code goods_daily_stat.sales_count}에 이미 쌓여 있고, 배치는 그것을 읽어 점수에만 쓴다.
     * 그래서 이 인터페이스와 폴백 빈은 <b>지금 아무도 소비하지 않는다</b>. 폴백이 남아 있어야
     * 앱이 그대로 뜨므로(주입 대상이 없어도 빈 등록 자체는 무해하다) 이 클래스는 그 사실을
     * 회귀로 못 박는다: 판매는 폴백, 찜은 실 구현.
     *
     * <p>{@code SalesStatProvider}/{@code RankingStatFallbackAutoConfiguration} 자체는 이 태스크의
     * Files 목록 밖이라 손대지 않았다. 정리 여부는 A5 이후에 판단한다.
     */
    @SpringBootTest
    @ActiveProfiles("test")
    static class 공급자_주입_상태 {

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
        void sales는_실_구현이_없어_빈_맵_폴백이_주입된다() {
            // 판매 수집 경로가 사라졌으므로 폴백(람다)이 남는다. 여기가 깨졌다면 어딘가에
            // 판매 Provider 구현이 새로 생겼다는 뜻이고, 그러면 증분과 대입 두 경로가
            // 다시 공존할 위험이 있으니 설계 §2-3부터 다시 읽어야 한다.
            assertThat(salesStatProvider.salesQuantityByGoods(LocalDate.now())).isEmpty();
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
            // @Primary는 이제 필수는 아니지만(실 구현이 없어 충돌하지 않는다) 그대로 둔다 —
            // 나중에 어느 도메인이 실 구현을 다시 올려도 이 테스트의 의도(폴백보다 실 구현이 이긴다)가
            // 흔들리지 않는다.
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
