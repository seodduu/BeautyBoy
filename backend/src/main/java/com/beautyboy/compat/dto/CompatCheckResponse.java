package com.beautyboy.compat.dto;

import java.util.List;

public record CompatCheckResponse(
        String overall,               // CONFLICT|CAUTION|SYNERGY|OK — findings의 최악 등급(없으면 OK)
        List<CompatFinding> findings  // SYNERGY도 포함(설득 재료)
) {
}
