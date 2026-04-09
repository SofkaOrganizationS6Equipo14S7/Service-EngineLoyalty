package com.loyalty.service_engine.infrastructure.rabbitmq;

import com.loyalty.service_engine.application.dto.RuleStatusChangedEvent;
import com.loyalty.service_engine.application.service.ClassificationMatrixCaffeineCacheService;
import com.loyalty.service_engine.domain.entity.ClassificationRuleReplicaEntity;
import com.loyalty.service_engine.domain.repository.ClassificationRuleReplicaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Consumes rule status change events from Admin Service.
 * Synchronizes is_active flag to engine_rules table and invalidates cache.
 *
 * SPEC-008 (Admin): Publishes RuleStatusChangedEvent when rule status changes (PATCH endpoint)
 * SPEC-009 (Engine): Consumes event and updates engine_rules.is_active + invalidates cache
 *
 * Event Flow:
 * Admin Service (PATCH /api/v1/rules/{ruleId}/status) → RabbitMQ
 *   → Engine Service (RuleStatusEventConsumer) → DB update + Cache invalidation
 *
 * Queue: engine-service-rule-status-changes (routing key: rule.status.changed)
 * DLQ: engine-service-rule-status-changes.dlq (on permanent failure)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleStatusEventConsumer {

    private final ClassificationRuleReplicaRepository ruleRepository;
    private final ClassificationMatrixCaffeineCacheService cacheService;

    @RabbitListener(queues = "${rabbitmq.queue.rule-status-changes:engine-service-rule-status-changes}")
    public void handleRuleStatusChanged(RuleStatusChangedEvent event) {
        
        // 1. Validate payload
        if (event == null || event.ruleId() == null || event.ecommerceId() == null || event.newStatus() == null) {
            log.error("Received invalid RuleStatusChangedEvent: ruleId={}, ecommerceId={}, newStatus={}",
                    event != null ? event.ruleId() : "null",
                    event != null ? event.ecommerceId() : "null",
                    event != null ? event.newStatus() : "null");
            return;
        }
        
        try {
            // 2. Find rule in DB replica (engine_rules table)
            // Tenant isolation: findByIdAndEcommerceId ensures only rules for this ecommerce
            ClassificationRuleReplicaEntity rule = ruleRepository
                .findByIdAndEcommerceId(event.ruleId(), event.ecommerceId())
                .orElseGet(() -> {
                    log.warn("Rule {} not found in engine_rules for ecommerce {}. " +
                            "Skipping status update. Will be replicated on next CREATE/UPDATE event.",
                            event.ruleId(), event.ecommerceId());
                    return null;
                });
            
            if (rule == null) return;
            
            // 3. Update status and timestamp
            Boolean previousStatus = rule.getIsActive();
            rule.setIsActive(event.newStatus());
            rule.setUpdatedAt(Instant.now());
            ruleRepository.save(rule);
            
            log.info("Rule status updated in engine_rules: ruleId={}, ecommerceId={}, previousStatus={}, newStatus={}",
                    event.ruleId(), event.ecommerceId(), previousStatus, event.newStatus());
            
            // 4. Invalidate cache for the ecommerce
            // Forces lazy reload on next classification/discount calculation
            cacheService.invalidateEcommerce(event.ecommerceId());
            log.debug("Cache invalidated for ecommerce: {} after rule status change", event.ecommerceId());
            
        } catch (Exception ex) {
            log.error("Error processing RuleStatusChangedEvent: ruleId={}, ecommerceId={}, newStatus={}",
                    event.ruleId(), event.ecommerceId(), event.newStatus(), ex);
            throw ex; // Let RabbitMQ handle retry and DLQ
        }
    }
}
