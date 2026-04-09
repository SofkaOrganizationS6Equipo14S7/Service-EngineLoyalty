package com.loyalty.service_engine.infrastructure.exception;

/**
 * Excepción para carrito inválido (vacío, items faltantes, etc.)
 */
public class InvalidCartException extends RuntimeException {
    
    public InvalidCartException(String message) {
        super(message);
    }

    public InvalidCartException(String message, Throwable cause) {
        super(message, cause);
    }
}
