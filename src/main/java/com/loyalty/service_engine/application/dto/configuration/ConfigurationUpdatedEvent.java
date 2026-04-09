package com.loyalty.service_engine.application.dto.configuration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConfigurationUpdatedEvent(
        String eventType,
        UUID configId,
        UUID ecommerceId,
        long version,
        String currency,
        RoundingRule roundingRule,
        CapType capType,
        BigDecimal capValue,
        CapAppliesTo capAppliesTo,
        List<PriorityItem> priority,
        Instant updatedAt
) {
    public record PriorityItem(
            UUID priorityId,
            String type,
            int order
    ) {
    }

    public enum RoundingRule {
        HALF_UP,
        DOWN,
        UP
    }

    public enum CapType {
        PERCENTAGE
    }

    public enum CapAppliesTo {
        SUBTOTAL,
        TOTAL,
        BEFORE_TAX,
        AFTER_TAX
    }
}
