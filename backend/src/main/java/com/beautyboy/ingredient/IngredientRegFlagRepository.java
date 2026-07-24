package com.beautyboy.ingredient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IngredientRegFlagRepository extends JpaRepository<IngredientRegFlag, Long> {

    /**
     * 제품의 성분과 규제 플래그를 한 번에 모은다. goods_ingredient/ingredient에 JPA 연관이 없어
     * 네이티브로 묶는다(기존 GoodsIngredientRepository 패턴). reg_flag는 inci_name LEFT JOIN —
     * 무플래그 성분도 남긴다("확인 성분 없음"도 사실 진술이므로 판정 서비스가 개수를 세려면 필요).
     * 반환 행: [ingredient_id, name, inci_name, flag_type(NULL 가능), source_ref(NULL 가능), sort_order].
     */
    @Query(value = "SELECT gi.ingredient_id, i.name, i.inci_name, f.flag_type, f.source_ref, gi.sort_order "
            + "FROM goods_ingredient gi JOIN ingredient i ON gi.ingredient_id = i.id "
            + "LEFT JOIN ingredient_reg_flag f ON f.inci_name = i.inci_name "
            + "WHERE gi.goods_id = :goodsId ORDER BY gi.sort_order", nativeQuery = true)
    List<Object[]> findFlagRowsByGoodsId(@Param("goodsId") Long goodsId);
}
