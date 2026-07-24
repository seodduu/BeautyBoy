package com.beautyboy.routine.dto;

import java.util.List;

/** 관리자 화면용 단계 응답. 일반 조회(RoutineStepResponse)와 달리 카드가 아니라 goodsNo만 싣는다. */
public record AdminRoutineStepResponse(int stepOrder, String stepName, String beginnerTip, List<Long> goodsNos) {
}
