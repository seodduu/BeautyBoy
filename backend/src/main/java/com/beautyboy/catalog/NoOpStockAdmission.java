package com.beautyboy.catalog;

import java.util.List;

/**
 * 선점 필터가 꺼져 있을 때의 기본 구현. 항상 통과시킨다.
 *
 * <p>토글 기본값이 false이므로 <b>이것이 평소의 동작</b>이다 — 선점 없이도 초과 판매는
 * DB 조건부 UPDATE가 막고, 폭주 패자는 승인 후 취소로 처리된다(설계 §2의 강등 동작).
 * 빈 등록은 {@link StockAdmissionConfig}가 한다.
 */
public class NoOpStockAdmission implements StockAdmission {

    @Override
    public boolean tryAcquire(List<Line> lines) {
        return true;
    }

    @Override
    public void release(List<Line> lines) {
        // 선점한 적이 없으니 반환할 것도 없다.
    }
}
