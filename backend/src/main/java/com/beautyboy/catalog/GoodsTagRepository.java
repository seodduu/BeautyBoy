package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.TagView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface GoodsTagRepository extends JpaRepository<GoodsTag, GoodsTag.Pk> {

    /**
     * goods_tag에는 tag로의 JPA 연관관계가 없으므로(스칼라 goodsId/tagId만 매핑), 태그 이름·종류·
     * slug를 얻으려면 명시적 조인이 필요하다. 상품 여러 개를 한 번에 배치 조회하기 위한 세타 조인이다
     * (기존 GoodsIngredientRepository.findCategoriesByGoodsIds / GoodsQueryRepository.findValidBadges
     * 패턴과 동일).
     */
    @Query("select gt.goodsId, t.name, t.kind, t.slug from GoodsTag gt, Tag t "
            + "where gt.tagId = t.id and gt.goodsId in :ids order by gt.goodsId, gt.sortOrder")
    List<Object[]> findTagRows(@Param("ids") Collection<Long> ids);

    default Map<Long, List<TagView>> findTagsByGoodsIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<TagView>> tagsByGoodsId = new LinkedHashMap<>();
        for (Object[] row : findTagRows(ids)) {
            Long goodsId = (Long) row[0];
            TagView tagView = new TagView((String) row[1], (String) row[2], (String) row[3]);
            tagsByGoodsId.computeIfAbsent(goodsId, key -> new ArrayList<>()).add(tagView);
        }
        return tagsByGoodsId;
    }
}
