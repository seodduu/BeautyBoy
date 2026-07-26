package com.beautyboy.catalog;

import java.util.Collection;
import java.util.Set;

/** 카탈로그 카드의 wished 플래그 공급자. wishlist 도메인이 구현한다(의존성 역전). */
public interface WishedGoodsProvider {

    /**
     * @param viewerId 보는 사람. <b>비로그인이면 null</b>이며 이때는 항상 빈 집합을 반환한다
     *                 (공개 엔드포인트에서도 카드가 그려져야 하므로 널을 예외로 만들지 않는다).
     * @return 이 회원이 찜한 goods.id 집합. 없으면 빈 집합(널 반환 금지).
     */
    Set<Long> wishedGoodsIds(Long viewerId, Collection<Long> goodsIds);
}
