package com.loyalty.service_engine.application.dto.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Generic unified event for all rule types (FIDELITY, SEASONAL, PRODUCT, CLASSIFICATION).
 * Consumed by Engine Service RuleEventConsumer to sync engine_rules table.
 *
 * SPEC-010: Unifies CLASSIFICATION, PRODUCT, SEASONAL, FIDELITY rule events.
 * Published by Admin Service RuleService on CREATE, UPDATE, DELETE operations.
 */
public record RuleEvent(
    String eventType,                      // "RULE_CREATED" | "RULE_UPDATED" | "RULE_DELETED"
    UUID ruleId,                           // Rule ID (uuid from Admin)
    UUID ecommerceId,                      // Tenant ID (multi-tenant isolation)
    String name,                           // Rule display name
    String description,                    // Rule description (nullable)
    String discountTypeCode,               // "FIDELITY" | "SEASONAL" | "PRODUCT" | "CLASSIFICATION"
    BigDecimal discountValue,              // Discount amount or percentage
    Integer priorityLevel,                 // Evaluation order (1, 2, 3, ...)
    Map<String, Object> logicConditions,   // Flexible JSONB: {start_date, end_date, product_type, min_value, ...}
    Boolean isActive,                      // true = active, false = inactive
    String appliedWith,                    // "INDIVIDUAL" | "STACKED" (optional, defaults to "INDIVIDUAL")
    Instant timestamp                      // UTC timestamp of event
) {
}
