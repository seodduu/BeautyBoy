package com.beautyboy.wishlist;

import com.beautyboy.ranking.WishStatProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WishlistWishStatProviderTest {

    @Autowired
    WishStatProvider wishStatProvider;
    @Autowired
    WishlistRepository wishlistRepository;

    @Test
    void 폴백이_아니라_wishlist_구현이_주입된다() {
        // 이 구현이 있으면 ranking의 빈 맵 폴백이 물러나야 한다.
        // 여기가 깨지면 랭킹의 찜 항이 영원히 0으로 남는다.
        assertThat(wishStatProvider).isInstanceOf(WishlistWishStatProvider.class);
    }

    @Test
    void 그_날_추가된_찜을_상품별로_센다() {
        wishlistRepository.saveAndFlush(new Wishlist(1L, 100L));
        wishlistRepository.saveAndFlush(new Wishlist(2L, 100L));
        wishlistRepository.saveAndFlush(new Wishlist(1L, 200L));

        Map<Long, Integer> result = wishStatProvider.wishCountByGoods(LocalDate.now());

        assertThat(result).containsEntry(100L, 2).containsEntry(200L, 1);
    }

    @Test
    void 다른_날_추가분은_세지_않는다() {
        // created_at을 어제로 조작해 저장. 오늘 집계에 들어오면 "어제 인기"가 오늘 순위를 오염시킨다.
        Wishlist old = new Wishlist(1L, 100L);
        wishlistRepository.saveAndFlush(old);
        wishlistRepository.flush();
        // created_at을 어제로 강제(네이티브) — @CreationTimestamp라 엔티티로는 못 바꾼다.
        wishlistRepository.백일_전으로_당긴다(old.getId());

        assertThat(wishStatProvider.wishCountByGoods(LocalDate.now())).isEmpty();
    }
}
