package com.loyalty.service_engine.infrastructure.exception;

/**
 * Exception thrown when classification cache is unavailable.
 * Occurs when Caffeine cache fails to load tiers or rules.
 */
public class CacheUnavailableException extends RuntimeException {
    public CacheUnavailableException(String message) {
        super(message);
    }

    public CacheUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
