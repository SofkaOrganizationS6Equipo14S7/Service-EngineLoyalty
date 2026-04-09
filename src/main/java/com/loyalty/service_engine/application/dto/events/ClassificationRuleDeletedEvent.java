package com.loyalty.service_engine.application.dto.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event consumed from RabbitMQ when Admin deletes (soft-delete) a classification rule.
 * Used to replicate in service-engine's database and invalidate cache.
 */
public record ClassificationRuleDeletedEvent(
    String eventType,           // "CLASSIFICATION_RULE_DELETED"
    UUID ruleUid,
    UUID tierUid,
    Instant deletedAt
) {
}
