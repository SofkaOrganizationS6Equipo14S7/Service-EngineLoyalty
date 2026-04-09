package com.loyalty.service_engine.infrastructure.exception;

/**
 * Excepción para errores en el cálculo de descuentos
 */
public class DiscountCalculationException extends RuntimeException {
    
    public DiscountCalculationException(String message) {
        super(message);
    }

    public DiscountCalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}
