package com.loyalty.service_engine.application.dto.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published by Admin Service when a fidelity range is created.
 * Consumed by Engine Service to synchronize cache.
 */
public record FidelityRangeCreatedEvent(
    UUID uid,
    UUID ecommerceId,
    String name,
    Integer minPoints,
    Integer maxPoints,
    BigDecimal discountPercentage,
    Instant timestamp
) { }
