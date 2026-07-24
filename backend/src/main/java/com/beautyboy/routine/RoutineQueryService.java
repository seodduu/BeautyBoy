package com.beautyboy.routine;

import com.beautyboy.routine.dto.RoutineResponse;

public interface RoutineQueryService {

    /** viewerId는 단계별 추천 카드의 wished 판정에만 쓰이며 비로그인이면 null이다. */
    RoutineResponse find(String skinType, String time, Long viewerId);
}
