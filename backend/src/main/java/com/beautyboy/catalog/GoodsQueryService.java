package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.GoodsListItem;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 타 도메인이 catalog를 경유할 때 쓰는 진입점. 도메인 패키지는 서로의 엔티티/리포지토리를
 * 직접 import하지 않으므로(패키지 = 서비스 경계), 상품 존재 여부가 필요한 타 도메인(예:
 * Wave 1 ingredient의 `/goods/{goodsNo}/ingredients`)은 이 인터페이스만 본다.
 */
public interface GoodsQueryService {

    /**
     * 상품이 존재하고 노출 상태(HIDDEN이 아님)인지 여부.
     * 상세/설명/추천과 동일한 기준을 쓴다 — 목록에서 숨긴 상품을 다른 경로로도 보면 안 되므로,
     * HIDDEN 상품은 "행이 존재해도" false를 반환한다.
     */
    boolean exists(Long goodsNo);

    /**
     * 상품의 leaf 카테고리 코드(예: "C002001"). 판정 엔진이 rinse-off(씻어내는 제품) 여부를
     * 카테고리 접두사로 파생할 때 쓴다. 존재하지 않는 상품이면 null.
     */
    String categoryCode(Long goodsNo);

    /**
     * 주문·장바구니가 쓰는 상품 스냅샷 조회.
     *
     * <p>왜 catalog가 내주는가: 주문 금액은 서버가 다시 계산해야 하는데(클라이언트 금액 불신),
     * order 패키지는 goods 테이블에 직접 접근할 수 없다. 필요한 값만 이 인터페이스로 넘긴다.
     *
     * <p>숨김(HIDDEN) 상품과 상품-옵션 불일치는 <b>빈 값</b>으로 답한다. 예외를 던지지 않는 이유는
     * 호출자(주문)가 "여러 건 중 어느 것이 문제인지"를 모아서 판단해야 하기 때문이다.
     *
     * @param goodsNo  상품 번호
     * @param optionNo 옵션 번호. 옵션 없는 상품이면 null.
     * @return 주문 가능한 상품이면 스냅샷, 아니면 빈 값
     */
    Optional<OrderGoodsSnapshot> findOrderSnapshot(Long goodsNo, Long optionNo);

    /** goods_no 목록 → 카드 아이템. HIDDEN 제외. 입력 순서를 보존하지 않는다.
     *  viewerId는 wished 판정에만 쓰이며 비로그인이면 null이다. */
    List<GoodsListItem> findListItems(Collection<Long> goodsNos, Long viewerId);

    /**
     * 주문 시점에 복사해 둘 상품 정보.
     *
     * @param unitPrice 1개 가격. 옵션 추가금이 이미 더해진 값이다 — 호출자가 다시 더하면 중복 계산이 된다.
     * @param stock     옵션 재고. 옵션이 없으면 {@link Integer#MAX_VALUE}(재고 관리 대상 아님).
     *                  이 웨이브는 재고를 <b>검증만</b> 하고 차감하지 않는다(차감은 Wave 3).
     */
    record OrderGoodsSnapshot(
            Long goodsId,
            Long optionId,
            String goodsName,
            String optionName,
            int unitPrice,
            int stock) {
    }
}
