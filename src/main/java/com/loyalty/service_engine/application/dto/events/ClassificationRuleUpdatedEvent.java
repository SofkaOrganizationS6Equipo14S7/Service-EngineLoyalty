package com.loyalty.service_engine.application.dto.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event consumed from RabbitMQ when Admin updates a classification rule.
 * Used to replicate in service-engine's database and invalidate cache.
 */
public record ClassificationRuleUpdatedEvent(
    String eventType,           // "CLASSIFICATION_RULE_UPDATED"
    UUID ruleUid,
    UUID tierUid,
    String metricType,
    BigDecimal minValue,
    BigDecimal maxValue,
    Integer priority,
    Boolean isActive,
    Instant updatedAt
) {
}
