package com.beautyboy.catalog;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class StockService implements StockCommandService {

    private final GoodsOptionRepository goodsOptionRepository;

    public StockService(GoodsOptionRepository goodsOptionRepository) {
        this.goodsOptionRepository = goodsOptionRepository;
    }

    /**
     * MANDATORY인 이유: 트랜잭션 없이 부르면 차감이 그 자리에서 커밋되어 "롤백이 복원"이라는
     * 계약(계획서 §2 결정 2)이 조용히 깨진다. 그 오용을 예외로 바꾼다.
     *
     * <p>TreeMap인 이유 둘: (1) 같은 옵션 여러 줄을 합산해 한 번에 깎는다 — 줄 단위로 깎으면
     * 합계는 부족한데 각 줄은 통과할 수 있다. (2) optionId 오름차순이 락 획득 순서를 전역으로
     * 일치시켜 교차 주문 데드락을 없앤다(§2 결정 3).
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deductAll(List<DeductionLine> lines) {
        Map<Long, Integer> merged = new TreeMap<>();
        for (DeductionLine line : lines) {
            merged.merge(line.optionId(), line.quantity(), Integer::sum);
        }
        for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
            if (goodsOptionRepository.deduct(entry.getKey(), entry.getValue()) == 0) {
                // 어느 옵션이 모자랐는지는 응답에 싣지 않는다 — 메시지는 공용 문장으로 충분하고,
                // 실패 시 전체 롤백이라 부분 상태를 설명할 필요가 없다.
                throw new BusinessException(ErrorCode.ORDER_OUT_OF_STOCK);
            }
        }
    }
}
