package com.loyalty.service_engine.application.service;

import com.loyalty.service_engine.domain.entity.ClassificationRuleReplicaEntity;
import com.loyalty.service_engine.domain.repository.ClassificationRuleReplicaRepository;
import com.loyalty.service_engine.infrastructure.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Service for synchronizing rules to engine_rules table.
 * Consumes RuleEvent from Admin Service via RabbitMQ.
 *
 * SPEC-010: Unified rule sync for all types (FIDELITY, SEASONAL, PRODUCT, CLASSIFICATION)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EngineRuleService {

    private final ClassificationRuleReplicaRepository ruleRepository;

    /**
     * Create or update engine rule (idempotent).
     * 
     * Used by RuleEventConsumer.handleRuleCreated() and handleRuleUpdated()
     * Implements CREATE and UPDATE operations from SPEC-010.
     *
     * @param ruleId            Rule ID from Admin
     * @param ecommerceId       Tenant ID
     * @param name              Rule name
     * @param description       Rule description
     * @param discountTypeCode  Type code: FIDELITY|SEASONAL|PRODUCT|CLASSIFICATION
     * @param discountValue     Discount amount/percentage
     * @param priorityLevel     Evaluation priority
     * @param logicConditions   JSONB attributes (flexible)
     * @param isActive          Active flag
     * @param appliedWith       Application mode (INDIVIDUAL|STACKED)
     */
    public void createOrUpdateEngineRule(
            UUID ruleId,
            UUID ecommerceId,
            String name,
            String description,
            String discountTypeCode,
            BigDecimal discountValue,
            Integer priorityLevel,
            Map<String, Object> logicConditions,
            Boolean isActive,
            String appliedWith
    ) {
        try {
            // Check if rule already exists (idempotence)
            ClassificationRuleReplicaEntity existingRule = ruleRepository
                    .findByIdAndEcommerceId(ruleId, ecommerceId)
                    .orElse(null);

            if (existingRule != null) {
                // UPDATE: Modify existing rule
                existingRule.setName(name);
                existingRule.setDescription(description);
                existingRule.setDiscountTypeCode(discountTypeCode);
                existingRule.setDiscountValue(discountValue);
                existingRule.setPriorityLevel(priorityLevel);
                existingRule.setLogicConditions(logicConditions);
                existingRule.setIsActive(isActive);
                existingRule.setAppliedWith(appliedWith);
                existingRule.setUpdatedAt(Instant.now());
                ruleRepository.save(existingRule);
                
                log.info("Engine rule updated: ruleId={}, ecommerceId={}, discountTypeCode={}",
                        ruleId, ecommerceId, discountTypeCode);
            } else {
                // INSERT: Create new rule
                ClassificationRuleReplicaEntity newRule = ClassificationRuleReplicaEntity.builder()
                        .id(ruleId)
                        .ecommerceId(ecommerceId)
                        .name(name)
                        .description(description)
                        .discountTypeCode(discountTypeCode)
                        .discountValue(discountValue)
                        .priorityLevel(priorityLevel)
                        .logicConditions(logicConditions)
                        .isActive(isActive)
                        .appliedWith(appliedWith)
                        .syncedAt(Instant.now())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                ruleRepository.save(newRule);
                
                log.info("Engine rule created: ruleId={}, ecommerceId={}, discountTypeCode={}",
                        ruleId, ecommerceId, discountTypeCode);
            }
        } catch (Exception ex) {
            log.error("Error syncing rule to engine_rules: ruleId={}, ecommerceId={}, error={}",
                    ruleId, ecommerceId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to sync rule to engine", ex);
        }
    }

    /**
     * Mark rule as deleted (soft delete).
     * 
     * Used by RuleEventConsumer.handleRuleDeleted()
     * Implements DELETE operation from SPEC-010.
     *
     * @param ruleId      Rule ID from Admin
     * @param ecommerceId Tenant ID
     */
    public void markRuleAsDeleted(UUID ruleId, UUID ecommerceId) {
        try {
            ClassificationRuleReplicaEntity rule = ruleRepository
                    .findByIdAndEcommerceId(ruleId, ecommerceId)
                    .orElseGet(() -> {
                        log.warn("Rule not found in engine_rules for deletion: ruleId={}, ecommerceId={}. " +
                                "Will be replicated on next CREATE event.", ruleId, ecommerceId);
                        return null;
                    });

            if (rule != null) {
                rule.setIsActive(false);
                rule.setUpdatedAt(Instant.now());
                ruleRepository.save(rule);
                
                log.info("Engine rule marked as deleted: ruleId={}, ecommerceId={}", ruleId, ecommerceId);
            }
        } catch (Exception ex) {
            log.error("Error marking rule as deleted in engine_rules: ruleId={}, ecommerceId={}, error={}",
                    ruleId, ecommerceId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to delete rule from engine", ex);
        }
    }
}
