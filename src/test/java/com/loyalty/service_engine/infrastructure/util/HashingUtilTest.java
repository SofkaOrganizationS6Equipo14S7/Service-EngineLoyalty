package com.loyalty.service_engine.infrastructure.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HashingUtilTest {

    @Test
    @DisplayName("Should produce consistent SHA-256 hash")
    void consistentHash() {
        String input = "test-api-key-123";
        String hash1 = HashingUtil.sha256(input);
        String hash2 = HashingUtil.sha256(input);
        assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("Hash should be 64 hex characters")
    void hashLength() {
        String hash = HashingUtil.sha256("anything");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"));
    }

    @Test
    @DisplayName("Different inputs produce different hashes")
    void differentInputsDifferentHashes() {
        String hash1 = HashingUtil.sha256("key1");
        String hash2 = HashingUtil.sha256("key2");
        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("Empty string produces valid hash")
    void emptyStringHash() {
        String hash = HashingUtil.sha256("");
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    @DisplayName("Known SHA-256 vector for 'hello'")
    void knownVector() {
        String hash = HashingUtil.sha256("hello");
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hash);
    }
}
