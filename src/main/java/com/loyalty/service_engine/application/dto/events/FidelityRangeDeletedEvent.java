package com.loyalty.service_engine.application.dto.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published by Admin Service when a fidelity range is deleted (soft-delete).
 */
public record FidelityRangeDeletedEvent(
    UUID uid,
    UUID ecommerceId,
    Instant timestamp
) { }
