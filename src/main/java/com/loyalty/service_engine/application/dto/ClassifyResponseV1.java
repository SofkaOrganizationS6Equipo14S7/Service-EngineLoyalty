package com.loyalty.service_engine.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for customer classification response (INTERNAL).
 * Result of evaluating customer metrics against tiers.
 * NOT EXPOSED AS PUBLIC ENDPOINT.
 */
public record ClassifyResponseV1(
    UUID tierUid,
    String tierName,
    Integer hierarchyLevel,
    BigDecimal tierDiscountPercentage,
    List<String> criteriaMetList,
    Instant classifiedAt
) {
}
