package com.loyalty.service_engine.infrastructure.exception;

/**
 * Excepción para errores en la evaluación lógica de criterios JSONB
 */
public class RuleEvaluationException extends RuntimeException {
    
    public RuleEvaluationException(String message) {
        super(message);
    }

    public RuleEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
