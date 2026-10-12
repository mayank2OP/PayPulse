package com.service.paypulse.dto.response;

import java.time.Instant;

public record ErrorResponseDto(
        String error,
        String message,
        Instant timestamp
) {

    public ErrorResponseDto(String error, String message) {
        this(error, message, Instant.now());
    }
}
