package com.beautyboy.ranking;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * 랭킹 통계 공급자의 임시 기본 구현.
 *
 * <p>왜 있는가: Wave 2가 병렬이라 {@code order}(T2)·{@code wishlist}(T3)의 실 구현이
 * ranking보다 늦게 들어온다. 폴백이 없으면 T1 터미널에서 앱 컨텍스트가 아예 뜨지 않아
 * ranking을 혼자 개발할 수 없다.
 *
 * <p><b>왜 일반 {@code @Configuration}이 아니라 {@link AutoConfiguration}인가:</b>
 * {@link ConditionalOnMissingBean}은 조건을 평가하는 시점에 이미 등록된 빈만 볼 수 있다.
 * 일반 사용자 설정끼리는 처리 순서가 보장되지 않아, 폴백이 먼저 평가되면 실 구현이 있는데도
 * 폴백이 등록되고 이후 {@code Provider} 주입에서 충돌하거나 폴백이 이겨버린다.
 * 자동 설정은 사용자 빈이 전부 등록된 <b>뒤에</b> 처리되므로 이 조건이 의도대로 동작한다.
 * (등록은 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports})
 *
 * <p>따라서 T2·T3가 {@code @Component}로 실 구현을 올리면 <b>이 폴백은 자동으로 물러난다</b> —
 * 나중에 이 파일을 지우는 것을 기억할 필요가 없다. 폴백이 살아 있는 동안 랭킹 점수는
 * 조회수만 반영한다(판매·찜은 0). 결함이 아니라 아직 데이터 소유자가 없다는 뜻이다.
 *
 * <p><b>Wave 2 통합 시 확인할 것:</b> T2·T3 머지 후 실제로 폴백이 아니라 실 구현이 주입되는지
 * 한 번 확인한다. 조용히 폴백이 계속 쓰이면 랭킹이 영원히 조회수 랭킹으로 남는다.
 */
@AutoConfiguration
public class RankingStatFallbackAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SalesStatProvider.class)
    public SalesStatProvider emptySalesStatProvider() {
        return date -> Map.of();
    }

    @Bean
    @ConditionalOnMissingBean(WishStatProvider.class)
    public WishStatProvider emptyWishStatProvider() {
        return date -> Map.of();
    }
}
