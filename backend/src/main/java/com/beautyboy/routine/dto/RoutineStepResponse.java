package com.beautyboy.routine.dto;

import com.beautyboy.catalog.dto.GoodsListItem;

import java.util.List;

public record RoutineStepResponse(int stepOrder, String stepName, String beginnerTip,
                                  List<GoodsListItem> recommendations) {
}
