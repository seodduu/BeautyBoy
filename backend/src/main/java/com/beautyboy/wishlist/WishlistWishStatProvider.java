package com.beautyboy.wishlist;

import com.beautyboy.ranking.WishStatProvider;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 랭킹이 요구하는 찜 통계를 wishlist가 공급한다(의존성 역전).
 *
 * <p>ranking은 wishlist 테이블을 직접 읽을 수 없다(패키지 = 서비스 경계). 그래서 ranking이 인터페이스를
 * 정의하고 데이터를 가진 wishlist가 구현한다. 이 {@code @Component}가 존재하면
 * ranking의 빈 맵 폴백({@code RankingStatFallbackAutoConfiguration})이 자동으로 물러난다 —
 * 이 웨이브가 머지되면 랭킹 점수의 찜 항(찜×2)이 비로소 실제 값으로 채워진다.
 *
 * <p>"그 날 새로 추가된 찜"만 센다(누적이 아니다). 누적을 쓰면 한 번 오른 상품이 영원히 상위에 남아
 * "최근 3일 가중"이라는 랭킹 설계가 무의미해진다(WishStatProvider 계약).
 */
@Component
public class WishlistWishStatProvider implements WishStatProvider {

    private final EntityManager em;

    public WishlistWishStatProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> wishCountByGoods(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        List<Object[]> rows = em.createQuery(
                        "select w.goodsId, count(w) from Wishlist w "
                                + "where w.createdAt >= :from and w.createdAt < :to group by w.goodsId",
                        Object[].class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return result;
    }
}
