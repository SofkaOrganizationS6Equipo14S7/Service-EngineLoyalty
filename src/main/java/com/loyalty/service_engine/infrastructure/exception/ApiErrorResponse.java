package com.loyalty.service_engine.infrastructure.exception;

import java.time.Instant;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path
) {
}
