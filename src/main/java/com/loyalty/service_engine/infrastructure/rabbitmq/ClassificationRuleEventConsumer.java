package com.loyalty.service_engine.infrastructure.rabbitmq;

import com.loyalty.service_engine.application.service.ClassificationMatrixCaffeineCacheService;
import com.loyalty.service_engine.domain.entity.ClassificationRuleReplicaEntity;
import com.loyalty.service_engine.domain.repository.ClassificationRuleReplicaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumes classification rule events published by Admin Service.
 * Updates the in-memory Caffeine cache and replica database to keep Engine in sync.
 *
 * Event flow:
 * Admin Service (source of truth) → RabbitMQ → Engine Service (listener) → DB replica + Caffeine Cache
 *
 * Three paths:
 * 1. RuleCreated: Add new rule to DB replica and invalidate cache for ecommerce
 * 2. RuleUpdated: Update rule in DB replica and invalidate cache for ecommerce
 * 3. RuleDeleted: Soft-delete rule in DB replica and invalidate cache for ecommerce
 *
 * Event payload structure:
 * {
 *   "ruleId": "uuid",
 *   "ecommerceId": "uuid",
 *   "name": "Gold Tier Rule",
 *   "discountTypeCode": "CLASSIFICATION",
 *   "discountType": "PERCENTAGE",
 *   "discountValue": 10.0,
 *   "appliedWith": "INDIVIDUAL",
 *   "logicConditions": { "min_spent": {...}, ... },
 *   "priorityLevel": 1,
 *   "isActive": true,
 *   "eventType": "CREATED|UPDATED|DELETED"
 * }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClassificationRuleEventConsumer {

    private final ClassificationRuleReplicaRepository ruleReplicaRepo;
    private final ClassificationMatrixCaffeineCacheService cacheService;

    @RabbitListener(queues = "${rabbitmq.queue.classification-rules:classification-rules.queue}")
    public void handleRuleEvent(ClassificationRuleEvent event) {
        if (event == null) {
            log.warn("Received null rule event");
            return;
        }

        try {
            switch (event.eventType().toUpperCase()) {
                case "CREATED":
                    handleRuleCreated(event);
                    break;
                case "UPDATED":
                    handleRuleUpdated(event);
                    break;
                case "DELETED":
                    handleRuleDeleted(event);
                    break;
                default:
                    log.warn("Unknown rule event type: {}", event.eventType());
                    return;
            }

            // Invalidate cache for this ecommerce
            if (event.ecommerceId() != null) {
                cacheService.invalidateEcommerce(event.ecommerceId());
                log.debug("Cache invalidated for ecommerce: {} after {} event", event.ecommerceId(), event.eventType());
            }

        } catch (Exception e) {
            log.error("Error processing rule event: {}", event, e);
        }
    }

    private void handleRuleCreated(ClassificationRuleEvent event) {
        log.info("Creating classification rule: id={}, ecommerce={}, name={}", 
            event.ruleId(), event.ecommerceId(), event.name());

        ClassificationRuleReplicaEntity replica = new ClassificationRuleReplicaEntity(
            event.ruleId(),
            event.ecommerceId(),
            event.name(),
            event.description(),
            event.discountTypeCode(),
            event.discountType(),
            event.discountValue(),
            event.appliedWith(),
            event.logicConditions(),
            event.priorityLevel(),
            true,
            Instant.now(),
            Instant.now(),
            Instant.now()
        );

        ruleReplicaRepo.save(replica);
        log.info("Rule created in replica: id={}", event.ruleId());
    }

    private void handleRuleUpdated(ClassificationRuleEvent event) {
        log.info("Updating classification rule: id={}, ecommerce={}", event.ruleId(), event.ecommerceId());

        Optional<ClassificationRuleReplicaEntity> existing = ruleReplicaRepo.findById(event.ruleId());

        if (existing.isPresent()) {
            ClassificationRuleReplicaEntity entity = existing.get();
            entity.setName(event.name());
            entity.setDescription(event.description());
            entity.setDiscountTypeCode(event.discountTypeCode());
            entity.setDiscountType(event.discountType());
            entity.setDiscountValue(event.discountValue());
            entity.setAppliedWith(event.appliedWith());
            entity.setLogicConditions(event.logicConditions());
            entity.setPriorityLevel(event.priorityLevel());
            entity.setIsActive(event.isActive());
            entity.setSyncedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());

            ruleReplicaRepo.save(entity);
            log.info("Rule updated in replica: id={}", event.ruleId());
        } else {
            log.warn("Rule not found for update: id={}", event.ruleId());
            // Treat as create if not exists
            handleRuleCreated(event);
        }
    }

    private void handleRuleDeleted(ClassificationRuleEvent event) {
        log.info("Soft-deleting classification rule: id={}", event.ruleId());

        Optional<ClassificationRuleReplicaEntity> existing = ruleReplicaRepo.findById(event.ruleId());

        if (existing.isPresent()) {
            ClassificationRuleReplicaEntity entity = existing.get();
            entity.setIsActive(false);
            entity.setSyncedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());

            ruleReplicaRepo.save(entity);
            log.info("Rule soft-deleted in replica: id={}", event.ruleId());
        } else {
            log.warn("Rule not found for deletion: id={}", event.ruleId());
        }
    }

    /**
     * Event payload for classification rule synchronization from Admin Service.
     */
    public record ClassificationRuleEvent(
        String eventType,
        UUID ruleId,
        UUID ecommerceId,
        String name,
        String description,
        String discountTypeCode,
        String discountType,
        BigDecimal discountValue,
        String appliedWith,
        Map<String, Object> logicConditions,
        Integer priorityLevel,
        boolean isActive
    ) {}
}
