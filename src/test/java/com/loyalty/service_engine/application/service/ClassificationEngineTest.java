package com.loyalty.service_engine.application.service;

import com.loyalty.service_engine.application.dto.*;
import com.loyalty.service_engine.domain.entity.ClassificationRuleReplicaEntity;
import com.loyalty.service_engine.domain.entity.CustomerTierReplicaEntity;
import com.loyalty.service_engine.domain.repository.ClassificationRuleReplicaRepository;
import com.loyalty.service_engine.domain.repository.CustomerTierReplicaRepository;
import com.loyalty.service_engine.infrastructure.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassificationEngineTest {

    @Mock
    private FidelityClassificationService fidelityClassificationService;

    @Mock
    private ClassificationMatrixCaffeineCacheService cacheService;

    @Mock
    private CustomerTierReplicaRepository tierReplicaRepo;

    @Mock
    private ClassificationRuleReplicaRepository ruleReplicaRepo;

    @InjectMocks
    private ClassificationEngine engine;

    private UUID defaultEcommerceId;

    @BeforeEach
    void setUp() {
        defaultEcommerceId = UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    @Test
    @DisplayName("Should classify and return response with tier")
    void shouldClassifyWithTier() {
        UUID tierUid = UUID.randomUUID();
        var tierDto = new CustomerTierDTO(tierUid, defaultEcommerceId, "Gold",
                new BigDecimal("10"), 3, true, Instant.now());
        var ruleDto = new ClassificationRuleDTO(UUID.randomUUID(), defaultEcommerceId, "Rule",
                "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                Map.of(), 1, true, Instant.now());

        when(cacheService.getTiers(defaultEcommerceId)).thenReturn(Optional.of(List.of(tierDto)));
        when(cacheService.getRules(defaultEcommerceId)).thenReturn(Optional.of(List.of(ruleDto)));

        ClassificationResult classResult = ClassificationResult.of(
                tierUid, "Gold", 3, new BigDecimal("10"), List.of("min_spent"));
        when(fidelityClassificationService.classify(eq(defaultEcommerceId), any())).thenReturn(classResult);

        ClassifyRequestV1 req = new ClassifyRequestV1(new BigDecimal("1000"), 10, 100, null);
        ClassifyResponseV1 response = engine.classify(req);

        assertNotNull(response);
        assertEquals(tierUid, response.tierUid());
        assertEquals("Gold", response.tierName());
        assertEquals(3, response.hierarchyLevel());
    }

    @Test
    @DisplayName("Should return empty response when NONE classification")
    void shouldReturnEmptyOnNone() {
        when(cacheService.getTiers(defaultEcommerceId)).thenReturn(Optional.of(List.of()));
        when(cacheService.getRules(defaultEcommerceId)).thenReturn(Optional.of(List.of()));
        when(fidelityClassificationService.classify(eq(defaultEcommerceId), any()))
                .thenReturn(ClassificationResult.NONE);

        ClassifyRequestV1 req = new ClassifyRequestV1(new BigDecimal("100"), 1, 10, null);
        ClassifyResponseV1 response = engine.classify(req);

        assertNull(response.tierUid());
        assertNull(response.tierName());
    }

    @Test
    @DisplayName("Should fallback to DB when cache empty")
    void shouldFallbackToDbWhenCacheEmpty() {
        // First call: empty cache
        when(cacheService.getTiers(defaultEcommerceId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(List.of()));
        when(cacheService.getRules(defaultEcommerceId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(List.of()));

        // DB load returns empty lists
        when(tierReplicaRepo.findByEcommerceIdAndIsActiveTrueOrderByHierarchyLevelAsc(defaultEcommerceId))
                .thenReturn(List.of());
        when(ruleReplicaRepo.findByEcommerceIdAndDiscountTypeCodeAndIsActiveTrueOrderByPriorityLevelAsc(
                defaultEcommerceId, "CLASSIFICATION")).thenReturn(List.of());

        when(fidelityClassificationService.classify(eq(defaultEcommerceId), any()))
                .thenReturn(ClassificationResult.NONE);

        ClassifyRequestV1 req = new ClassifyRequestV1(new BigDecimal("100"), 1, 10, null);
        ClassifyResponseV1 response = engine.classify(req);

        assertNotNull(response);
        verify(tierReplicaRepo).findByEcommerceIdAndIsActiveTrueOrderByHierarchyLevelAsc(defaultEcommerceId);
    }

    @Test
    @DisplayName("Should throw ServiceUnavailableException when cache still empty after DB load")
    void shouldThrowWhenMatrixUnavailable() {
        when(cacheService.getTiers(defaultEcommerceId)).thenReturn(Optional.empty());
        when(cacheService.getRules(defaultEcommerceId)).thenReturn(Optional.empty());
        when(tierReplicaRepo.findByEcommerceIdAndIsActiveTrueOrderByHierarchyLevelAsc(defaultEcommerceId))
                .thenReturn(List.of());
        when(ruleReplicaRepo.findByEcommerceIdAndDiscountTypeCodeAndIsActiveTrueOrderByPriorityLevelAsc(
                defaultEcommerceId, "CLASSIFICATION")).thenReturn(List.of());

        ClassifyRequestV1 req = new ClassifyRequestV1(new BigDecimal("100"), 1, 10, null);
        assertThrows(ServiceUnavailableException.class, () -> engine.classify(req));
    }

    @Test
    @DisplayName("Should throw ServiceUnavailableException on internal error")
    void shouldThrowOnInternalError() {
        when(cacheService.getTiers(defaultEcommerceId)).thenThrow(new RuntimeException("error"));

        ClassifyRequestV1 req = new ClassifyRequestV1(new BigDecimal("100"), 1, 10, null);
        assertThrows(ServiceUnavailableException.class, () -> engine.classify(req));
    }
}
