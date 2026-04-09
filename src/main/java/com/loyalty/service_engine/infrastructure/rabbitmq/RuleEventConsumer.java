package com.loyalty.service_engine.infrastructure.rabbitmq;

import com.loyalty.service_engine.application.dto.events.RuleEvent;
import com.loyalty.service_engine.application.service.EngineRuleService;
import com.loyalty.service_engine.application.service.ClassificationMatrixCaffeineCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Unified consumer for all rule types: FIDELITY, SEASONAL, PRODUCT, CLASSIFICATION.
 *
 * SPEC-010: Handles CREATE, UPDATE, DELETE events from Admin Service.
 * Synchronizes engine_rules table and invalidates Caffeine cache per ecommerce.
 *
 * Event Flow:
 * Admin RuleService → publishes RuleEvent → loyalty.events exchange
 *   → loyalty.rules.queue → RuleEventConsumer
 *   → EngineRuleService → engine_rules table update
 *   → ClassificationMatrixCaffeineCacheService → cache invalidation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RuleEventConsumer {

    private final EngineRuleService engineRuleService;
    private final ClassificationMatrixCaffeineCacheService cacheService;

    /**
     * Consume generic RuleEvent for all rule types.
     * 
     * Listens on queue: loyalty.rules.queue
     * Exchange: loyalty.events
     * Routing key: rule.* (matches rule.created, rule.updated, rule.deleted)
     *
     * @param event RuleEvent from Admin Service
     */
    @RabbitListener(queues = "${rabbitmq.queue.rules:loyalty.rules.queue}")
    public void consume(RuleEvent event) {
        try {
            log.info("Received RuleEvent: eventType={}, ruleId={}, discountTypeCode={}, ecommerceId={}",
                    event.eventType(), event.ruleId(), event.discountTypeCode(), event.ecommerceId());

            // Validate event payload
            validateRuleEvent(event);

            // Route to appropriate handler based on event type
            switch (event.eventType()) {
                case "RULE_CREATED" -> handleRuleCreated(event);
                case "RULE_UPDATED" -> handleRuleUpdated(event);
                case "RULE_DELETED" -> handleRuleDeleted(event);
                default -> {
                    log.warn("Unknown event type: {}. Ignoring.", event.eventType());
                    throw new IllegalArgumentException("Unknown eventType: " + event.eventType());
                }
            }

            // Invalidate cache for this ecommerce
            cacheService.invalidateEcommerce(event.ecommerceId());
            log.debug("Cache invalidated for ecommerce: {} after rule event", event.ecommerceId());

            log.info("Successfully processed RuleEvent: ruleId={}, eventType={}", event.ruleId(), event.eventType());

        } catch (Exception ex) {
            log.error("Error processing RuleEvent: eventType={}, ruleId={}, ecommerceId={}, error={}",
                    event.eventType(), event.ruleId(), event.ecommerceId(), ex.getMessage(), ex);
            // Throw exception to trigger RabbitMQ retry/DLQ mechanism
            throw new RuntimeException("RuleEvent processing failed", ex);
        }
    }

    /**
     * Handler for RULE_CREATED events.
     * Inserts new rule into engine_rules.
     */
    private void handleRuleCreated(RuleEvent event) {
        engineRuleService.createOrUpdateEngineRule(
                event.ruleId(),
                event.ecommerceId(),
                event.name(),
                event.description(),
                event.discountTypeCode(),
                event.discountValue(),
                event.priorityLevel(),
                event.logicConditions(),
                event.isActive(),
                event.appliedWith()
        );
    }

    /**
     * Handler for RULE_UPDATED events.
     * Updates existing rule in engine_rules (idempotent with INSERT).
     */
    private void handleRuleUpdated(RuleEvent event) {
        // UPDATE is treated same as CREATE for idempotent behavior
        engineRuleService.createOrUpdateEngineRule(
                event.ruleId(),
                event.ecommerceId(),
                event.name(),
                event.description(),
                event.discountTypeCode(),
                event.discountValue(),
                event.priorityLevel(),
                event.logicConditions(),
                event.isActive(),
                event.appliedWith()
        );
    }

    /**
     * Handler for RULE_DELETED events.
     * Marks rule as inactive (soft delete).
     */
    private void handleRuleDeleted(RuleEvent event) {
        engineRuleService.markRuleAsDeleted(event.ruleId(), event.ecommerceId());
    }

    /**
     * Validate RuleEvent payload.
     * Checks that required fields are present and non-null.
     *
     * @param event RuleEvent to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateRuleEvent(RuleEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("RuleEvent cannot be null");
        }

        if (event.ruleId() == null) {
            throw new IllegalArgumentException("RuleEvent.ruleId cannot be null");
        }

        if (event.ecommerceId() == null) {
            throw new IllegalArgumentException("RuleEvent.ecommerceId cannot be null");
        }

        if (event.eventType() == null || event.eventType().isBlank()) {
            throw new IllegalArgumentException("RuleEvent.eventType cannot be null or empty");
        }

        if (event.discountTypeCode() == null || event.discountTypeCode().isBlank()) {
            throw new IllegalArgumentException("RuleEvent.discountTypeCode cannot be null or empty");
        }

        if (event.isActive() == null) {
            throw new IllegalArgumentException("RuleEvent.isActive cannot be null");
        }

        log.debug("RuleEvent validation passed: ruleId={}, ecommerceId={}", 
                event.ruleId(), event.ecommerceId());
    }
}
