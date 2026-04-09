package com.loyalty.service_engine.infrastructure.startup;

import com.loyalty.service_engine.application.service.ClassificationMatrixCaffeineCacheService;
import com.loyalty.service_engine.domain.entity.ClassificationRuleReplicaEntity;
import com.loyalty.service_engine.domain.entity.CustomerTierReplicaEntity;
import com.loyalty.service_engine.domain.repository.ClassificationRuleReplicaRepository;
import com.loyalty.service_engine.domain.repository.CustomerTierReplicaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassificationMatrixStartupLoaderTest {

    @Mock
    private CustomerTierReplicaRepository tierReplicaRepository;

    @Mock
    private ClassificationRuleReplicaRepository ruleReplicaRepository;

    @Mock
    private ClassificationMatrixCaffeineCacheService cacheService;

    @InjectMocks
    private ClassificationMatrixStartupLoader loader;

    private final UUID ecommerceId = UUID.randomUUID();

    @Test
    @DisplayName("Should load tiers and rules into cache on startup")
    void loadTiersAndRules() {
        CustomerTierReplicaEntity tier = new CustomerTierReplicaEntity(
                UUID.randomUUID(), ecommerceId, "Gold", BigDecimal.TEN, 2, true,
                Instant.now(), Instant.now(), Instant.now());

        ClassificationRuleReplicaEntity rule = new ClassificationRuleReplicaEntity(
                UUID.randomUUID(), ecommerceId, "Gold Rule", "desc",
                "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                Map.of(), 1, true, Instant.now(), Instant.now(), Instant.now());

        when(tierReplicaRepository.findAll()).thenReturn(List.of(tier));
        when(ruleReplicaRepository.findAll()).thenReturn(List.of(rule));

        loader.loadClassificationMatrixOnStartup();

        verify(cacheService).putTiers(eq(ecommerceId), anyList());
        verify(cacheService).putRules(eq(ecommerceId), anyList());
    }

    @Test
    @DisplayName("Should handle empty database")
    void emptyDatabase() {
        when(tierReplicaRepository.findAll()).thenReturn(List.of());
        when(ruleReplicaRepository.findAll()).thenReturn(List.of());

        loader.loadClassificationMatrixOnStartup();

        verify(cacheService, never()).putTiers(any(), anyList());
        verify(cacheService, never()).putRules(any(), anyList());
    }

    @Test
    @DisplayName("Should filter out inactive tiers and rules")
    void filterInactive() {
        CustomerTierReplicaEntity activeTier = new CustomerTierReplicaEntity(
                UUID.randomUUID(), ecommerceId, "Gold", BigDecimal.TEN, 2, true,
                Instant.now(), Instant.now(), Instant.now());
        CustomerTierReplicaEntity inactiveTier = new CustomerTierReplicaEntity(
                UUID.randomUUID(), ecommerceId, "Silver", BigDecimal.valueOf(5), 1, false,
                Instant.now(), Instant.now(), Instant.now());

        ClassificationRuleReplicaEntity inactiveRule = new ClassificationRuleReplicaEntity(
                UUID.randomUUID(), ecommerceId, "Inactive Rule", "desc",
                "CLASSIFICATION", "PERCENTAGE", BigDecimal.ONE, "INDIVIDUAL",
                Map.of(), 1, false, Instant.now(), Instant.now(), Instant.now());

        when(tierReplicaRepository.findAll()).thenReturn(List.of(activeTier, inactiveTier));
        when(ruleReplicaRepository.findAll()).thenReturn(List.of(inactiveRule));

        loader.loadClassificationMatrixOnStartup();

        // Only active tier should be cached; no active rules, so no putRules call
        verify(cacheService).putTiers(eq(ecommerceId), argThat(list -> list.size() == 1));
        verify(cacheService, never()).putRules(any(), anyList());
    }

    @Test
    @DisplayName("Should handle database error gracefully")
    void databaseError() {
        when(tierReplicaRepository.findAll()).thenThrow(new RuntimeException("DB down"));

        // Should not throw — catches internally
        loader.loadClassificationMatrixOnStartup();
    }

    @Test
    @DisplayName("Should handle multiple ecommerces")
    void multipleEcommerces() {
        UUID ecommerce1 = UUID.randomUUID();
        UUID ecommerce2 = UUID.randomUUID();

        CustomerTierReplicaEntity tier1 = new CustomerTierReplicaEntity(
                UUID.randomUUID(), ecommerce1, "Gold", BigDecimal.TEN, 2, true,
                Instant.now(), Instant.now(), Instant.now());
        CustomerTierReplicaEntity tier2 = new CustomerTierReplicaEntity(
                UUID.randomUUID(), ecommerce2, "Platinum", BigDecimal.valueOf(15), 3, true,
                Instant.now(), Instant.now(), Instant.now());

        when(tierReplicaRepository.findAll()).thenReturn(List.of(tier1, tier2));
        when(ruleReplicaRepository.findAll()).thenReturn(List.of());

        loader.loadClassificationMatrixOnStartup();

        verify(cacheService).putTiers(eq(ecommerce1), anyList());
        verify(cacheService).putTiers(eq(ecommerce2), anyList());
    }

    @Test
    @DisplayName("Should filter tiers with null isActive")
    void nullIsActiveTier() {
        // Tier with isActive = null should be filtered out (treated as inactive)
        CustomerTierReplicaEntity nullActiveTier = new CustomerTierReplicaEntity(
                UUID.randomUUID(), ecommerceId, "NullTier", BigDecimal.TEN, 2, null,
                Instant.now(), Instant.now(), Instant.now());

        ClassificationRuleReplicaEntity activeRule = new ClassificationRuleReplicaEntity(
                UUID.randomUUID(), ecommerceId, "Active Rule", "desc",
                "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                Map.of(), 1, true, Instant.now(), Instant.now(), Instant.now());

        when(tierReplicaRepository.findAll()).thenReturn(List.of(nullActiveTier));
        when(ruleReplicaRepository.findAll()).thenReturn(List.of(activeRule));

        loader.loadClassificationMatrixOnStartup();

        // Null-active tier should be filtered out, so no putTiers call
        verify(cacheService, never()).putTiers(any(), anyList());
        verify(cacheService).putRules(eq(ecommerceId), anyList());
    }

    @Test
    @DisplayName("Should filter rules with null isActive")
    void nullIsActiveRule() {
        CustomerTierReplicaEntity activeTier = new CustomerTierReplicaEntity(
                UUID.randomUUID(), ecommerceId, "Gold", BigDecimal.TEN, 2, true,
                Instant.now(), Instant.now(), Instant.now());

        ClassificationRuleReplicaEntity nullActiveRule = new ClassificationRuleReplicaEntity(
                UUID.randomUUID(), ecommerceId, "Null Rule", "desc",
                "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                Map.of(), 1, null, Instant.now(), Instant.now(), Instant.now());

        when(tierReplicaRepository.findAll()).thenReturn(List.of(activeTier));
        when(ruleReplicaRepository.findAll()).thenReturn(List.of(nullActiveRule));

        loader.loadClassificationMatrixOnStartup();

        verify(cacheService).putTiers(eq(ecommerceId), anyList());
        // Null-active rule should be filtered out
        verify(cacheService, never()).putRules(any(), anyList());
    }

    @Test
    @DisplayName("Should handle per-ecommerce error without affecting others")
    void perEcommerceError() {
        UUID ecommerce1 = UUID.randomUUID();
        UUID ecommerce2 = UUID.randomUUID();

        CustomerTierReplicaEntity tier1 = new CustomerTierReplicaEntity(
                UUID.randomUUID(), ecommerce1, "Gold", BigDecimal.TEN, 2, true,
                Instant.now(), Instant.now(), Instant.now());
        CustomerTierReplicaEntity tier2 = new CustomerTierReplicaEntity(
                UUID.randomUUID(), ecommerce2, "Silver", BigDecimal.ONE, 1, true,
                Instant.now(), Instant.now(), Instant.now());

        when(tierReplicaRepository.findAll()).thenReturn(List.of(tier1, tier2));
        when(ruleReplicaRepository.findAll()).thenReturn(List.of());

        // First ecommerce fails when putting tiers
        doThrow(new RuntimeException("cache failure")).when(cacheService).putTiers(eq(ecommerce1), anyList());

        loader.loadClassificationMatrixOnStartup();

        // Second ecommerce should still be loaded
        verify(cacheService).putTiers(eq(ecommerce2), anyList());
    }

    @Test
    @DisplayName("Should load ecommerce with only rules and no tiers")
    void onlyRulesNoTiers() {
        ClassificationRuleReplicaEntity rule = new ClassificationRuleReplicaEntity(
                UUID.randomUUID(), ecommerceId, "Lone Rule", "desc",
                "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                Map.of(), 1, true, Instant.now(), Instant.now(), Instant.now());

        when(tierReplicaRepository.findAll()).thenReturn(List.of());
        when(ruleReplicaRepository.findAll()).thenReturn(List.of(rule));

        loader.loadClassificationMatrixOnStartup();

        verify(cacheService, never()).putTiers(any(), anyList());
        verify(cacheService).putRules(eq(ecommerceId), anyList());
    }
}
