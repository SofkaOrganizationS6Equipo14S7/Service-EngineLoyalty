package com.loyalty.service_engine.application.service;

import com.loyalty.service_engine.application.dto.ClassificationRuleDTO;
import com.loyalty.service_engine.application.dto.CustomerTierDTO;
import com.loyalty.service_engine.infrastructure.exception.CacheUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClassificationMatrixCaffeineCacheServiceTest {

    private ClassificationMatrixCaffeineCacheService cacheService;
    private UUID ecommerceId;

    @BeforeEach
    void setUp() {
        cacheService = new ClassificationMatrixCaffeineCacheService();
        ecommerceId = UUID.randomUUID();
    }

    @Test
    @DisplayName("putTiers and getTiers round-trip")
    void putAndGetTiers() {
        var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                new BigDecimal("10"), 3, true, Instant.now());
        cacheService.putTiers(ecommerceId, List.of(tier));

        Optional<List<CustomerTierDTO>> result = cacheService.getTiers(ecommerceId);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().size());
        assertEquals("Gold", result.get().get(0).name());
    }

    @Test
    @DisplayName("getTiers returns empty for unknown ecommerce")
    void getTiersEmpty() {
        Optional<List<CustomerTierDTO>> result = cacheService.getTiers(UUID.randomUUID());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getTiers returns empty for null ecommerceId")
    void getTiersNullId() {
        Optional<List<CustomerTierDTO>> result = cacheService.getTiers(null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("putTiers throws for null ecommerceId")
    void putTiersNullId() {
        assertThrows(CacheUnavailableException.class, () -> cacheService.putTiers(null, List.of()));
    }

    @Test
    @DisplayName("putRules and getRules round-trip")
    void putAndGetRules() {
        var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule1",
                "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                Map.of(), 1, true, Instant.now());
        cacheService.putRules(ecommerceId, List.of(rule));

        Optional<List<ClassificationRuleDTO>> result = cacheService.getRules(ecommerceId);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().size());
    }

    @Test
    @DisplayName("getRules returns empty for null ecommerceId")
    void getRulesNullId() {
        assertTrue(cacheService.getRules(null).isEmpty());
    }

    @Test
    @DisplayName("putRules throws for null ecommerceId")
    void putRulesNullId() {
        assertThrows(CacheUnavailableException.class, () -> cacheService.putRules(null, List.of()));
    }

    @Test
    @DisplayName("invalidateEcommerce removes tiers and rules")
    void invalidateEcommerce() {
        cacheService.putTiers(ecommerceId, List.of());
        cacheService.putRules(ecommerceId, List.of());

        cacheService.invalidateEcommerce(ecommerceId);

        assertTrue(cacheService.getTiers(ecommerceId).isEmpty());
        assertTrue(cacheService.getRules(ecommerceId).isEmpty());
    }

    @Test
    @DisplayName("invalidateEcommerce with null does nothing")
    void invalidateNullEcommerce() {
        assertDoesNotThrow(() -> cacheService.invalidateEcommerce(null));
    }

    @Test
    @DisplayName("invalidateAll clears everything")
    void invalidateAll() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        cacheService.putTiers(id1, List.of());
        cacheService.putTiers(id2, List.of());

        cacheService.invalidateAll();

        assertTrue(cacheService.getTiers(id1).isEmpty());
        assertTrue(cacheService.getTiers(id2).isEmpty());
    }

    @Test
    @DisplayName("isPopulated returns true when both present")
    void isPopulated() {
        cacheService.putTiers(ecommerceId, List.of());
        cacheService.putRules(ecommerceId, List.of());

        assertTrue(cacheService.isPopulated(ecommerceId));
    }

    @Test
    @DisplayName("isPopulated returns false when only tiers present")
    void isNotPopulatedOnlyTiers() {
        cacheService.putTiers(ecommerceId, List.of());
        assertFalse(cacheService.isPopulated(ecommerceId));
    }

    @Test
    @DisplayName("getStats returns non-null")
    void getStats() {
        assertNotNull(cacheService.getStats());
    }
}
