package com.loyalty.service_engine.infrastructure.exception;

/**
 * Excepción lanzada cuando la matriz de clasificación no está disponible.
 * Esto ocurre cuando el caché Caffeine está vacío, la DB de réplicas no es accesible,
 * y el Engine no puede cargar la matriz de reglas de clasificación.
 * Corresponde al código HTTP 503 Service Unavailable.
 */
public class ClassificationMatrixUnavailableException extends ServiceUnavailableException {
    
    public ClassificationMatrixUnavailableException(String message) {
        super(message);
    }
    
    public ClassificationMatrixUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
