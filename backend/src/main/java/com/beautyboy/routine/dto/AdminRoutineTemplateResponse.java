package com.beautyboy.routine.dto;

import java.util.List;

public record AdminRoutineTemplateResponse(
        Long templateId,
        String name,
        String skinType,
        String timeSlot,
        String description,
        List<AdminRoutineStepResponse> steps) {
}
