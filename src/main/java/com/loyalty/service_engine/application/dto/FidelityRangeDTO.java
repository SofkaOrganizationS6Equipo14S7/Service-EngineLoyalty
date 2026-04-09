package com.loyalty.service_engine.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only DTO representing a fidelity range cached in memory.
 * Synchronized from Admin Service via RabbitMQ events.
 */
public record FidelityRangeDTO(
    UUID uid,
    UUID ecommerceId,
    String name,
    Integer minPoints,
    Integer maxPoints,
    BigDecimal discountPercentage,
    boolean isActive,
    Instant createdAt,
    Instant updatedAt
) { }
