package com.loyalty.service_engine.application.service;

import com.loyalty.service_engine.application.dto.ClassifyRequestV1;
import com.loyalty.service_engine.application.dto.ClassifyResponseV1;
import com.loyalty.service_engine.domain.entity.ClassificationRuleReplicaEntity;
import com.loyalty.service_engine.domain.entity.CustomerTierReplicaEntity;
import com.loyalty.service_engine.domain.repository.ClassificationRuleReplicaRepository;
import com.loyalty.service_engine.domain.repository.CustomerTierReplicaRepository;
import com.loyalty.service_engine.infrastructure.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Classification Engine — DETERMINISTIC classification logic.
 * 
 * Adapter service that integrates FidelityClassificationService with legacy DiscountCalculationServiceV2.
 * This service ensures backward compatibility while delegating to the new JSONB-based classification.
 * 
 * Algorithm (3 paths):
 * 1. Evaluate ALL active rules against payload metrics using JSONB logic_conditions
 * 2. Find ALL matching rules
 * 3. If multiple tiers match → select HIGHEST tier level (Platino > Oro > Plata > Bronce)
 * 4. Return single tier OR null (no match)
 * 
 * Heavy lifting: in-memory Caffeine cache. Fallback: read from replica tables.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClassificationEngine {

    private final FidelityClassificationService fidelityClassificationService;
    private final ClassificationMatrixCaffeineCacheService cacheService;
    private final CustomerTierReplicaRepository tierReplicaRepo;
    private final ClassificationRuleReplicaRepository ruleReplicaRepo;

    /**
     * Classify a customer based on their metrics.
     * 
     * IMPORTANT: This method assumes single-ecommerce context (uses "default" ecommerce UUID).
     * For multi-ecommerce systems, use FidelityClassificationService.classify(UUID, ClassifyRequestV1) instead.
     * 
     * @param request contains totalSpent, orderCount, membershipDays
     * @return ClassifyResponseV1 with matched tier or null
     * @throws ServiceUnavailableException if matrix unavailable
     */
    public ClassifyResponseV1 classify(ClassifyRequestV1 request) {
        // Use default ecommerce ID for backward compatibility
        // In production, this should be passed from the caller (DiscountCalculationServiceV2)
        UUID defaultEcommerceId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        
        log.info("Classifying customer (legacy): totalSpent={}, orderCount={}, membershipDays={}",
            request.totalSpent(), request.orderCount(), request.membershipDays());

        try {
            // Try cache first
            var tiersOptional = cacheService.getTiers(defaultEcommerceId);
            var rulesOptional = cacheService.getRules(defaultEcommerceId);

            // Fallback to database if cache empty
            if (tiersOptional.isEmpty() || rulesOptional.isEmpty()) {
                loadMatrixFromDatabase();
                tiersOptional = cacheService.getTiers(defaultEcommerceId);
                rulesOptional = cacheService.getRules(defaultEcommerceId);
            }

            if (tiersOptional.isEmpty() || rulesOptional.isEmpty()) {
                throw new ServiceUnavailableException("Classification matrix not available");
            }

            // Delegate to new JSONB-based service
            var classificationResult = fidelityClassificationService.classify(defaultEcommerceId, request);
            
            if (classificationResult.getTierUid().isPresent()) {
                return new ClassifyResponseV1(
                    classificationResult.getTierUid().get(),
                    classificationResult.getTierName().get(),
                    classificationResult.getHierarchyLevel().get(),
                    classificationResult.getDiscountPercentage().get(),
                    classificationResult.getCriteriaMet(),
                    Instant.now()
                );
            } else {
                return new ClassifyResponseV1(null, null, null, null, List.of(), Instant.now());
            }
        } catch (Exception e) {
            log.error("Classification engine error: {}", e.getMessage(), e);
            throw new ServiceUnavailableException("Classification failed: " + e.getMessage());
        }
    }

    /**
     * Load matrix from replica tables (for Cold Start).
     */
    private void loadMatrixFromDatabase() {
        UUID defaultEcommerceId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        
        log.info("Loading classification matrix from replica database...");
        List<CustomerTierReplicaEntity> tiers = tierReplicaRepo.findByEcommerceIdAndIsActiveTrueOrderByHierarchyLevelAsc(defaultEcommerceId);
        List<ClassificationRuleReplicaEntity> rules = ruleReplicaRepo.findByEcommerceIdAndDiscountTypeCodeAndIsActiveTrueOrderByPriorityLevelAsc(
            defaultEcommerceId, "CLASSIFICATION"
        );

        log.info("Classification matrix loaded: {} tiers, {} rules", tiers.size(), rules.size());
    }
}
