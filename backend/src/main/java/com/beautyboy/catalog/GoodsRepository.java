package com.beautyboy.catalog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GoodsRepository extends JpaRepository<Goods, Long> {

    /**
     * 상세 조회 전용. brand를 join fetch해 1쿼리로 끝낸다(옵션은 컬렉션이라 별도 조회 —
     * 컬렉션 fetch join을 브랜드 fetch join과 함께 쓰면 카테시안 곱이 생긴다).
     * HIDDEN 상품은 목록에서 숨긴 것을 URL로 직접 보면 안 되므로 조회 대상에서 제외한다.
     */
    @Query("select g from Goods g join fetch g.brand where g.id = :id and g.status <> :hidden")
    Optional<Goods> findDetailById(@Param("id") Long id, @Param("hidden") String hidden);

    boolean existsByIdAndStatusNot(Long id, String status);

    /**
     * 옵션은 엔티티(GoodsOption)가 아니라 스칼라 프로젝션으로 조회한다. 이 리포지토리의 관리 대상
     * 타입은 Goods인데, {@code @Query}가 다른 엔티티 전체(o)를 그대로 select하면 Spring Data JPA가
     * "DTO 프로젝션 요청"으로 오인해 {@code new GoodsOption(*)} 생성자 표현식을 시도하다 JPQL 문법
     * 오류를 낸다. 컬럼만 뽑으면 이 문제를 피한다.
     */
    @Query("select o.id, o.name, o.addPrice, o.stock, o.sortOrder "
            + "from GoodsOption o where o.goods.id = :goodsId order by o.sortOrder asc")
    List<Object[]> findOptionRowsByGoodsId(@Param("goodsId") Long goodsId);

    @Query("select g.id, b.name, g.name, g.thumbnailUrl, g.listPrice, g.salePrice "
            + "from Goods g join g.brand b "
            + "where g.categoryCode = :categoryCode and g.status <> :hidden and g.id <> :excludeId "
            + "order by g.viewCount desc, g.id desc")
    List<Object[]> findRecommendedRows(@Param("categoryCode") String categoryCode,
                                        @Param("hidden") String hidden,
                                        @Param("excludeId") Long excludeId,
                                        Pageable pageable);
}
