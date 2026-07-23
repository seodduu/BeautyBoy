package com.beautyboy.common;

public record ErrorResponse(String code, String message, Object detail) {
}
