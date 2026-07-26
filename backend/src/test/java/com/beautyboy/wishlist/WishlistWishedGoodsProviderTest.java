package com.beautyboy.wishlist;

import com.beautyboy.catalog.WishedGoodsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WishlistWishedGoodsProviderTest {

    @Autowired
    WishedGoodsProvider provider;
    @Autowired
    WishlistRepository wishlistRepository;

    @Test
    void 폴백이_아니라_wishlist_도메인_구현이_주입된다() {
        // 이 구현이 있으면 catalog의 빈 집합 폴백이 물러나야 한다.
        // 여기가 깨지면 카드 wished가 영원히 false로 남는다.
        assertThat(provider).isInstanceOf(WishlistWishedGoodsProvider.class);
    }

    @Test
    void viewerId가_null이면_리포지토리를_부르지_않고_빈_집합이다() {
        WishlistRepository mockRepository = mock(WishlistRepository.class);
        WishlistWishedGoodsProvider isolatedProvider = new WishlistWishedGoodsProvider(mockRepository);
        Long 상품A = 1L;

        assertThat(isolatedProvider.wishedGoodsIds(null, List.of(상품A))).isEmpty();
        verifyNoInteractions(mockRepository);
    }

    @Test
    void 찜한_상품만_집합에_담긴다() {
        Long 회원1 = 1L;
        Long 상품A = 100L;
        Long 상품B = 200L;
        wishlistRepository.save(new Wishlist(회원1, 상품A));

        assertThat(provider.wishedGoodsIds(회원1, List.of(상품A, 상품B))).containsExactly(상품A);
    }
}
