package com.beautyboy.member.dto;

import jakarta.validation.constraints.NotNull;

public record BehaviorConsentRequest(@NotNull Boolean consent) {
}
