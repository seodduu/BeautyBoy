package com.beautyboy.catalog;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.Map;
import java.util.Set;

/**
 * 카탈로그 카드 통계 공급자의 임시 기본 구현.
 *
 * <p>왜 있는가: catalog는 review·wishlist를 컴파일 의존하지 않는다(패키지 = 서비스 경계).
 * 두 도메인의 실 구현이 없는 컨텍스트(단위/슬라이스 테스트, 컴포넌트 미등록 상태)에서도
 * {@link GoodsService}가 뜨려면 폴백이 있어야 한다.
 *
 * <p><b>왜 일반 {@code @Configuration}이 아니라 {@link AutoConfiguration}인가:</b>
 * {@link ConditionalOnMissingBean}은 조건을 평가하는 시점에 이미 등록된 빈만 볼 수 있다.
 * 일반 사용자 설정끼리는 처리 순서가 보장되지 않아, 폴백이 먼저 평가되면 실 구현이 있는데도
 * 폴백이 등록되고 이후 Provider 주입에서 충돌하거나 폴백이 이겨버린다.
 * 자동 설정은 사용자 빈이 전부 등록된 <b>뒤에</b> 처리되므로 이 조건이 의도대로 동작한다.
 * (등록은 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports})
 *
 * <p>따라서 review·wishlist가 {@code @Component}로 실 구현을 올리면 <b>이 폴백은 자동으로
 * 물러난다</b>. 폴백이 살아 있는 동안 카드는 별점 0·찜 해제로 보인다 — 결함이 아니라
 * 아직 데이터 소유자가 없다는 뜻이다. (참고: ranking.RankingStatFallbackAutoConfiguration과 같은 패턴)
 */
@AutoConfiguration
public class CatalogStatFallbackAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GoodsRatingProvider.class)
    public GoodsRatingProvider emptyGoodsRatingProvider() {
        return goodsIds -> Map.of();
    }

    @Bean
    @ConditionalOnMissingBean(WishedGoodsProvider.class)
    public WishedGoodsProvider emptyWishedGoodsProvider() {
        return (viewerId, goodsIds) -> Set.of();
    }
}
