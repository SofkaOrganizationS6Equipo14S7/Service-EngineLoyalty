package com.loyalty.service_engine.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for customer classification request (INTERNAL)NOT EXPOSED AS PUBLIC ENDPOINT.
 * Evaluates customer metrics to determine loyalty tier.
 *
 * Fields used in classification via JSONB logic_conditions:
 * - totalSpent: customer lifetime purchase value
 * - orderCount: number of orders placed
 * - membershipDays: days since account creation
 * - lastPurchaseDate: optional, for recency-based rules
 */
public record ClassifyRequestV1(
    @NotNull(message = "Total spent is required")
    @DecimalMin(value = "0", inclusive = true, message = "Total spent must be non-negative")
    BigDecimal totalSpent,

    @NotNull(message = "Order count is required")
    @Min(value = 0, message = "Order count must be non-negative")
    Integer orderCount,

    @NotNull(message = "Membership days is required")
    @Min(value = 0, message = "Membership days must be non-negative")
    Integer membershipDays,

    Instant lastPurchaseDate // Optional
) {
}
