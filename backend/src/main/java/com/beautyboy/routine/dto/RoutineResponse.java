package com.beautyboy.routine.dto;

import java.util.List;

public record RoutineResponse(Long templateId, String name, String skinType, String time,
                              String description, List<RoutineStepResponse> steps) {
}
