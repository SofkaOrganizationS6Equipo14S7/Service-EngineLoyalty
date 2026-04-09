package com.loyalty.service_engine.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for classification rule used in Caffeine cache.
 * Lightweight representation of a rule with JSONB logic_conditions.
 * Synchronized from Admin Service via RabbitMQ.
 */
public record ClassificationRuleDTO(
    UUID id,
    UUID ecommerceId,
    String name,
    String discountTypeCode, // "CLASSIFICATION"
    String discountType, // PERCENTAGE | FIXED_AMOUNT
    BigDecimal discountValue,
    String appliedWith, // INDIVIDUAL | CUMULATIVE | EXCLUSIVE
    Map<String, Object> logicConditions, // JSONB criteria
    Integer priorityLevel,
    boolean isActive,
    Instant syncedAt
) {
}
