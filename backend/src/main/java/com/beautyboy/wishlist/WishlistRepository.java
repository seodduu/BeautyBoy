package com.beautyboy.wishlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    Optional<Wishlist> findByMemberIdAndGoodsId(Long memberId, Long goodsId);

    List<Wishlist> findByMemberIdOrderByIdDesc(Long memberId);

    boolean existsByMemberIdAndGoodsId(Long memberId, Long goodsId);

    void deleteByMemberIdAndGoodsId(Long memberId, Long goodsId);

    /**
     * catalog 카드의 wished 판정용 배치 조회. 이 회원이 주어진 goodsId들 중 찜한 것만 뽑는다.
     * (파생 쿼리 이름의 "GoodsIds"는 엔티티 프로퍼티 "goodsId"와 정확히 매칭되지 않아 명시적 JPQL을 쓴다.)
     */
    @Query("select w.goodsId from Wishlist w where w.memberId = :memberId and w.goodsId in :goodsIds")
    List<Long> findGoodsIdsByMemberIdAndGoodsIdIn(@Param("memberId") Long memberId,
                                                   @Param("goodsIds") Collection<Long> goodsIds);

    /**
     * 테스트 전용: {@code created_at}을 100일 전으로 당긴다.
     *
     * <p>{@code @CreationTimestamp}라 엔티티 setter로는 값을 바꿀 수 없어, "오늘 아닌 찜은
     * 집계에서 빠진다"를 검증하려는 {@code WishlistWishStatProviderTest}를 위해 존재한다.
     * 기준 시각은 애플리케이션에서 계산해 넘긴다(DB 방언별 날짜 함수 차이를 피한다).
     */
    default void 백일_전으로_당긴다(Long id) {
        createdAt을_강제한다(id, LocalDateTime.now().minusDays(100));
    }

    @Modifying
    @Query("update Wishlist w set w.createdAt = :ts where w.id = :id")
    void createdAt을_강제한다(@Param("id") Long id, @Param("ts") LocalDateTime ts);
}
