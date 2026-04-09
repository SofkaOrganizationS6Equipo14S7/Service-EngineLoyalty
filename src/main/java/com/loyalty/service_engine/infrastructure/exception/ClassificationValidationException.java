package com.loyalty.service_engine.infrastructure.exception;

/**
 * Exception thrown when customer classification validation fails.
 * Used when required fields are missing or invalid.
 */
public class ClassificationValidationException extends RuntimeException {
    public ClassificationValidationException(String message) {
        super(message);
    }

    public ClassificationValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
