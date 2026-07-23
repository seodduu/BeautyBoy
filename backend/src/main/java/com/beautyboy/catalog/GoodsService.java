package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.GoodsListItem;
import com.beautyboy.catalog.dto.GoodsSearchCondition;
import com.beautyboy.common.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class GoodsService {

    private final GoodsQueryRepository goodsQueryRepository;

    public GoodsService(GoodsQueryRepository goodsQueryRepository) {
        this.goodsQueryRepository = goodsQueryRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<GoodsListItem> list(GoodsSearchCondition condition) {
        List<GoodsQueryRepository.GoodsRow> rows = goodsQueryRepository.findList(condition);
        long totalElements = goodsQueryRepository.count(condition);

        List<Long> goodsIds = rows.stream().map(GoodsQueryRepository.GoodsRow::goodsId).toList();
        Map<Long, List<String>> badgesByGoodsId = goodsQueryRepository.findValidBadges(goodsIds, LocalDateTime.now());

        List<GoodsListItem> items = rows.stream()
                .map(row -> toItem(row, badgesByGoodsId.getOrDefault(row.goodsId(), List.of())))
                .toList();

        return PageResponse.of(items, condition.page(), condition.size(), totalElements);
    }

    private GoodsListItem toItem(GoodsQueryRepository.GoodsRow row, List<String> badges) {
        return new GoodsListItem(
                row.goodsId(),
                row.brandName(),
                row.name(),
                row.thumbnailUrl(),
                row.listPrice(),
                row.salePrice(),
                discountRate(row.listPrice(), row.salePrice()),
                badges,
                0.0,
                0,
                false,
                false);
    }

    private int discountRate(int listPrice, int salePrice) {
        if (listPrice == 0) {
            return 0;
        }
        return (listPrice - salePrice) * 100 / listPrice;
    }
}
