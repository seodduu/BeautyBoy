package com.beautyboy.ingredient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface GoodsIngredientRepository extends JpaRepository<GoodsIngredient, GoodsIngredient.Pk> {

    List<GoodsIngredient> findByGoodsId(Long goodsId);

    List<GoodsIngredient> findByGoodsIdIn(Collection<Long> goodsIds);

    /**
     * goods_ingredient에는 ingredient로의 JPA 연관관계가 없으므로(스칼라 goodsId/ingredientId만
     * 매핑), 성분 분류(category)를 얻으려면 명시적 조인이 필요하다. 여기서는 세타 조인(cross join +
     * where 조건)으로 단일 SQL 쿼리로 묶는다 — Wave 3 궁합 엔진이 상품 여러 개를 한 번에 넘길 때
     * N+1이 나면 못 쓰기 때문이다.
     */
    @Query("select gi.goodsId, i.category from GoodsIngredient gi, Ingredient i "
            + "where gi.ingredientId = i.id and gi.goodsId in :goodsIds")
    List<Object[]> findCategoriesByGoodsIds(@Param("goodsIds") Collection<Long> goodsIds);

    @Query("select gi.ingredientId, gi.key, i.name, i.category, i.irritationLevel, i.comedogenic, i.summary "
            + "from GoodsIngredient gi, Ingredient i "
            + "where gi.ingredientId = i.id and gi.goodsId = :goodsId "
            + "order by gi.key desc, gi.sortOrder asc")
    List<Object[]> findBadgeRowsByGoodsId(@Param("goodsId") Long goodsId);
}
