package com.beautyboy.routine;

import com.beautyboy.routine.dto.RoutineResponse;

public interface RoutineQueryService {

    RoutineResponse find(String skinType, String time);
}
