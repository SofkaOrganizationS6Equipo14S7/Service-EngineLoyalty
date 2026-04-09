package com.loyalty.service_engine.application.dto;

/**
 * Payload de evento recibido desde Admin Service.
 * 
 * SEGURIDAD: hashedKey contiene el SHA-256 hash de la API Key.
 * Admin Service NUNCA transmite el plaintext, solo el hash.
 */
public record ApiKeyEventPayload(
    String eventType,           // API_KEY_CREATED o API_KEY_DELETED
    String keyId,               // UUID de la key
    String hashedKey,           // SHA-256 hash de la API Key (nunca plaintext)
    String ecommerceId,         // UUID del ecommerce propietario
    String timestamp            // ISO8601 timestamp del evento
) {
}
