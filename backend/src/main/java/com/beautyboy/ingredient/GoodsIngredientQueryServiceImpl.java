package com.beautyboy.ingredient;

import com.beautyboy.ingredient.dto.IngredientBadge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class GoodsIngredientQueryServiceImpl implements GoodsIngredientQueryService {

    private final GoodsIngredientRepository goodsIngredientRepository;

    public GoodsIngredientQueryServiceImpl(GoodsIngredientRepository goodsIngredientRepository) {
        this.goodsIngredientRepository = goodsIngredientRepository;
    }

    @Override
    public Map<Long, Set<String>> findCategoriesByGoodsIds(Collection<Long> goodsIds) {
        Map<Long, Set<String>> result = new HashMap<>();
        if (goodsIds == null || goodsIds.isEmpty()) {
            return result;
        }
        List<Object[]> rows = goodsIngredientRepository.findCategoriesByGoodsIds(goodsIds);
        for (Object[] row : rows) {
            Long goodsId = (Long) row[0];
            String category = (String) row[1];
            result.computeIfAbsent(goodsId, k -> new HashSet<>()).add(category);
        }
        return result;
    }

    @Override
    public List<IngredientBadge> findBadges(Long goodsId) {
        List<Object[]> rows = goodsIngredientRepository.findBadgeRowsByGoodsId(goodsId);
        return rows.stream()
                .map(row -> new IngredientBadge(
                        (Long) row[0],
                        (String) row[2],
                        (String) row[3],
                        (Integer) row[4],
                        (Integer) row[5],
                        (String) row[6],
                        (Boolean) row[1]))
                .toList();
    }
}
