package com.loyalty.service_engine.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for customer tier used in Caffeine cache.
 * Lightweight representation of a tier with its attributes.
 * Synchronized from Admin Service via RabbitMQ.
 */
public record CustomerTierDTO(
    UUID uid,
    UUID ecommerceId,
    String name,
    BigDecimal discountPercentage,
    Integer hierarchyLevel,
    boolean isActive,
    Instant syncedAt
) {
}
