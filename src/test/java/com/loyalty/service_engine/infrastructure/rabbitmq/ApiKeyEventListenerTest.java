package com.loyalty.service_engine.infrastructure.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.service_engine.infrastructure.cache.ApiKeyCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyEventListenerTest {

    @Mock
    private ApiKeyCache apiKeyCache;

    private ObjectMapper objectMapper;
    private ApiKeyEventListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new ApiKeyEventListener(apiKeyCache, objectMapper);
    }

    @Test
    @DisplayName("Should handle API_KEY_CREATED event")
    void handleApiKeyCreated() {
        String payload = """
            {
                "eventType": "API_KEY_CREATED",
                "keyId": "key-001",
                "hashedKey": "abc123def456",
                "ecommerceId": "550e8400-e29b-41d4-a716-446655440000",
                "timestamp": "2026-01-01T00:00:00Z"
            }
            """;

        listener.onApiKeyEvent(payload);

        verify(apiKeyCache).addKey("abc123def456", "550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    @DisplayName("Should handle API_KEY_DELETED event")
    void handleApiKeyDeleted() {
        String payload = """
            {
                "eventType": "API_KEY_DELETED",
                "keyId": "key-002",
                "hashedKey": "xyz789",
                "ecommerceId": "550e8400-e29b-41d4-a716-446655440000",
                "timestamp": "2026-01-01T00:00:00Z"
            }
            """;

        listener.onApiKeyEvent(payload);

        verify(apiKeyCache).removeKey("xyz789");
    }

    @Test
    @DisplayName("Should deduplicate events with same eventId")
    void deduplicateEvents() {
        String payload = """
            {
                "eventType": "API_KEY_CREATED",
                "keyId": "key-dup",
                "hashedKey": "hashdup",
                "ecommerceId": "550e8400-e29b-41d4-a716-446655440000",
                "timestamp": "2026-01-01T00:00:00Z"
            }
            """;

        listener.onApiKeyEvent(payload);
        listener.onApiKeyEvent(payload);

        // Should only process once due to deduplication
        verify(apiKeyCache, times(1)).addKey("hashdup", "550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    @DisplayName("Should throw on invalid payload")
    void invalidPayload() {
        assertThrows(IllegalStateException.class, () -> listener.onApiKeyEvent("invalid json"));
    }

    @Test
    @DisplayName("Should throw on null event type")
    void nullEventType() {
        String payload = """
            {
                "eventType": null,
                "keyId": "key-001",
                "hashedKey": "abc",
                "ecommerceId": "550e8400-e29b-41d4-a716-446655440000",
                "timestamp": "2026-01-01T00:00:00Z"
            }
            """;

        assertThrows(IllegalStateException.class, () -> listener.onApiKeyEvent(payload));
    }

    @Test
    @DisplayName("Should throw on unknown event type")
    void unknownEventType() {
        String payload = """
            {
                "eventType": "API_KEY_UNKNOWN",
                "keyId": "key-001",
                "hashedKey": "abc",
                "ecommerceId": "550e8400-e29b-41d4-a716-446655440000",
                "timestamp": "2026-01-01T00:00:00Z"
            }
            """;

        assertThrows(IllegalStateException.class, () -> listener.onApiKeyEvent(payload));
    }

    @Test
    @DisplayName("Should throw on missing ecommerceId")
    void missingEcommerceId() {
        String payload = """
            {
                "eventType": "API_KEY_CREATED",
                "keyId": "key-001",
                "hashedKey": "abc",
                "timestamp": "2026-01-01T00:00:00Z"
            }
            """;

        assertThrows(IllegalStateException.class, () -> listener.onApiKeyEvent(payload));
    }

    @Test
    @DisplayName("Should throw on missing keyId")
    void missingKeyId() {
        String payload = """
            {
                "eventType": "API_KEY_CREATED",
                "hashedKey": "abc",
                "ecommerceId": "550e8400-e29b-41d4-a716-446655440000",
                "timestamp": "2026-01-01T00:00:00Z"
            }
            """;

        assertThrows(IllegalStateException.class, () -> listener.onApiKeyEvent(payload));
    }

    @Test
    @DisplayName("Should throw on missing hashedKey")
    void missingHashedKey() {
        String payload = """
            {
                "eventType": "API_KEY_CREATED",
                "keyId": "key-001",
                "ecommerceId": "550e8400-e29b-41d4-a716-446655440000",
                "timestamp": "2026-01-01T00:00:00Z"
            }
            """;

        assertThrows(IllegalStateException.class, () -> listener.onApiKeyEvent(payload));
    }

    @Test
    @DisplayName("Should throw on empty ecommerceId")
    void emptyEcommerceId() {
        String payload = """
            {
                "eventType": "API_KEY_CREATED",
                "keyId": "key-001",
                "hashedKey": "abc",
                "ecommerceId": "",
                "timestamp": "2026-01-01T00:00:00Z"
            }
            """;

        assertThrows(IllegalStateException.class, () -> listener.onApiKeyEvent(payload));
    }

    @Test
    @DisplayName("Should remove from processedEvents on addKey failure")
    void removesFromDedupOnFailure() {
        doThrow(new RuntimeException("cache error")).when(apiKeyCache).addKey(anyString(), anyString());

        String payload = """
            {
                "eventType": "API_KEY_CREATED",
                "keyId": "key-fail",
                "hashedKey": "hashfail",
                "ecommerceId": "550e8400-e29b-41d4-a716-446655440000",
                "timestamp": "2026-01-01T00:00:00Z"
            }
            """;

        assertThrows(IllegalStateException.class, () -> listener.onApiKeyEvent(payload));

        // Event should be retryable (removed from dedup map), so second call should also attempt processing
        assertThrows(IllegalStateException.class, () -> listener.onApiKeyEvent(payload));
        verify(apiKeyCache, times(2)).addKey("hashfail", "550e8400-e29b-41d4-a716-446655440000");
    }
}
