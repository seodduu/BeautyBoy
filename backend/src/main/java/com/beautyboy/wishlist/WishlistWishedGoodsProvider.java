package com.beautyboy.wishlist;

import com.beautyboy.catalog.WishedGoodsProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 카탈로그가 요구하는 wished 플래그를 wishlist가 공급한다(의존성 역전).
 *
 * <p>catalog는 wishlist 테이블을 직접 읽을 수 없다(패키지 = 서비스 경계). 그래서 catalog가
 * 인터페이스를 정의하고 데이터를 가진 wishlist가 구현한다. 이 {@code @Component}가 존재하면
 * catalog의 빈 집합 폴백({@code CatalogStatFallbackAutoConfiguration})이 자동으로 물러난다.
 *
 * <p>비로그인(viewerId == null)이면 리포지토리를 부르지 않고 빈 집합을 낸다 — 공개 엔드포인트에서도
 * 카드가 그려져야 하므로 널을 예외로 만들지 않는다(계약 문서 참고).
 */
@Component
public class WishlistWishedGoodsProvider implements WishedGoodsProvider {

    private final WishlistRepository wishlistRepository;

    public WishlistWishedGoodsProvider(WishlistRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> wishedGoodsIds(Long viewerId, Collection<Long> goodsIds) {
        if (viewerId == null || goodsIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(wishlistRepository.findGoodsIdsByMemberIdAndGoodsIdIn(viewerId, goodsIds));
    }
}
