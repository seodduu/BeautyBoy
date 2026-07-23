package com.beautyboy.cart;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByMemberIdOrderByIdAsc(Long memberId);

    /**
     * 같은 상품+옵션이 이미 담겨 있는지. optionId가 null인 경우를 Spring Data가
     * {@code option_id is null}로 풀어주므로 별도 분기가 필요 없다.
     */
    Optional<CartItem> findByMemberIdAndGoodsIdAndOptionId(Long memberId, Long goodsId, Long optionId);

    void deleteByMemberId(Long memberId);
}
