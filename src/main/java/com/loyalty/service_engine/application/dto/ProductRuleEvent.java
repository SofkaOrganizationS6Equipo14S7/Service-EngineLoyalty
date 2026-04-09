package com.loyalty.service_engine.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event received from RabbitMQ when product rules are created, updated, or deleted
 * in the Admin Service
 */
public record ProductRuleEvent(
    String eventType,               // "PRODUCT_RULE_CREATED", "PRODUCT_RULE_UPDATED", "PRODUCT_RULE_DELETED"
    UUID uid,                       // Rule identifier
    UUID ecommerceId,               // Ecommerce identifier
    String name,
    String productType,
    BigDecimal discountPercentage,
    String benefit,
    Boolean isActive,
    Instant timestamp
) {}
