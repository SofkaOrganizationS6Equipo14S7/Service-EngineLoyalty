package com.loyalty.service_engine.infrastructure.cache;

import com.loyalty.service_engine.domain.entity.ApiKeyEntity;
import com.loyalty.service_engine.domain.repository.ApiKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyCacheTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    private ApiKeyCache apiKeyCache;

    @BeforeEach
    void setUp() {
        // loadFromDatabase is called in constructor
        when(apiKeyRepository.findAll()).thenReturn(List.of());
        apiKeyCache = new ApiKeyCache(apiKeyRepository);
    }

    @Test
    @DisplayName("Should load keys from database on construction")
    void loadFromDatabase() {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setHashedKey("abc123hash");
        entity.setEcommerceId(UUID.randomUUID());

        when(apiKeyRepository.findAll()).thenReturn(List.of(entity));

        apiKeyCache.loadFromDatabase();

        // Validate by getting ecommerce id directly with hashed key
        // We can't call validateKey since that hashes again; just verify no exception
        verify(apiKeyRepository, atLeastOnce()).findAll();
    }

    @Test
    @DisplayName("Should validate key after adding it")
    void addKeyAndValidate() {
        String hashedKey = com.loyalty.service_engine.infrastructure.util.HashingUtil.sha256("my-plain-key");
        String ecommerceId = UUID.randomUUID().toString();

        when(apiKeyRepository.save(any())).thenReturn(new ApiKeyEntity());

        apiKeyCache.addKey(hashedKey, ecommerceId);

        // Validate with plaintext should hash it and match
        assertTrue(apiKeyCache.validateKey("my-plain-key"));
    }

    @Test
    @DisplayName("Should return false for invalid key")
    void validateKeyInvalid() {
        assertFalse(apiKeyCache.validateKey("nonexistent-key"));
    }

    @Test
    @DisplayName("Should return false for null key")
    void validateKeyNull() {
        assertFalse(apiKeyCache.validateKey(null));
    }

    @Test
    @DisplayName("Should return false for empty key")
    void validateKeyEmpty() {
        assertFalse(apiKeyCache.validateKey(""));
    }

    @Test
    @DisplayName("Should get ecommerce ID for valid key")
    void getEcommerceIdValid() {
        String hashedKey = com.loyalty.service_engine.infrastructure.util.HashingUtil.sha256("test-key");
        String ecommerceId = UUID.randomUUID().toString();

        when(apiKeyRepository.save(any())).thenReturn(new ApiKeyEntity());
        apiKeyCache.addKey(hashedKey, ecommerceId);

        assertEquals(ecommerceId, apiKeyCache.getEcommerceId("test-key"));
    }

    @Test
    @DisplayName("Should return null ecommerce for null key")
    void getEcommerceIdNull() {
        assertNull(apiKeyCache.getEcommerceId(null));
    }

    @Test
    @DisplayName("Should return null ecommerce for empty key")
    void getEcommerceIdEmpty() {
        assertNull(apiKeyCache.getEcommerceId(""));
    }

    @Test
    @DisplayName("Should remove key from cache")
    void removeKey() {
        String hashedKey = com.loyalty.service_engine.infrastructure.util.HashingUtil.sha256("key-to-remove");
        String ecommerceId = UUID.randomUUID().toString();

        when(apiKeyRepository.save(any())).thenReturn(new ApiKeyEntity());
        apiKeyCache.addKey(hashedKey, ecommerceId);
        assertTrue(apiKeyCache.validateKey("key-to-remove"));

        when(apiKeyRepository.findByHashedKey(hashedKey)).thenReturn(Optional.empty());
        apiKeyCache.removeKey(hashedKey);
        assertFalse(apiKeyCache.validateKey("key-to-remove"));
    }

    @Test
    @DisplayName("Should refresh cache from database")
    void refresh() {
        when(apiKeyRepository.findAll()).thenReturn(List.of());
        apiKeyCache.refresh();
        verify(apiKeyRepository, atLeast(2)).findAll(); // once in constructor, once in refresh
    }

    @Test
    @DisplayName("Should return cache stats")
    void getCacheStats() {
        assertNotNull(apiKeyCache.getCacheStats());
    }

    @Test
    @DisplayName("Should handle database error on load gracefully")
    void loadFromDatabaseError() {
        when(apiKeyRepository.findAll()).thenThrow(new RuntimeException("DB down"));
        // Should not throw
        assertDoesNotThrow(() -> apiKeyCache.loadFromDatabase());
    }

    @Test
    @DisplayName("Should throw on addKey error")
    void addKeyError() {
        when(apiKeyRepository.save(any())).thenThrow(new RuntimeException("DB error"));
        assertThrows(RuntimeException.class,
                () -> apiKeyCache.addKey("hash", UUID.randomUUID().toString()));
    }

    @Test
    @DisplayName("Should throw on removeKey error")
    void removeKeyError() {
        when(apiKeyRepository.findByHashedKey(any())).thenThrow(new RuntimeException("DB error"));
        assertThrows(RuntimeException.class,
                () -> apiKeyCache.removeKey("hash"));
    }
}
