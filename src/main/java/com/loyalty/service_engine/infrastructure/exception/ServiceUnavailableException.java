package com.loyalty.service_engine.infrastructure.exception;

/**
 * Exception when service is temporarily unavailable (e.g., cache empty, matrix not synced).
 * Maps to HTTP 503 Service Unavailable.
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
