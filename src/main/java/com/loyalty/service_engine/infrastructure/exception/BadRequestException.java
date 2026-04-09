package com.loyalty.service_engine.infrastructure.exception;

/**
 * Excepción cuando la solicitud contiene datos inválidos o incorrectos.
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
    
    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
