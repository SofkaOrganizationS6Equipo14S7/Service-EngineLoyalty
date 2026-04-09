package com.loyalty.service_engine.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Result of customer loyalty tier classification.
 * Supports deterministic classification based on customer metrics
 * evaluated against JSONB logic_conditions.
 *
 * Classification Logic (3-path):
 * 1. EXACT MATCH: Customer metrics match one tier's criteria
 * 2. FALLTHROUGH: Customer metrics exceed a tier's criteria, assign next lower tier
 * 3. NONE: Customer does not meet minimum criteria (not auto-assigned to lowest tier)
 */
public class ClassificationResult {
    public static final ClassificationResult NONE = new ClassificationResult(null, null, null, null, null, null);

    private final UUID tierUid;
    private final String tierName;
    private final Integer hierarchyLevel;
    private final BigDecimal discountPercentage;
    private final List<String> criteriaMetList;
    private final Instant classifiedAt;

    private ClassificationResult(UUID tierUid, String tierName, Integer hierarchyLevel,
                               BigDecimal discountPercentage, List<String> criteriaMetList,
                               Instant classifiedAt) {
        this.tierUid = tierUid;
        this.tierName = tierName;
        this.hierarchyLevel = hierarchyLevel;
        this.discountPercentage = discountPercentage;
        this.criteriaMetList = criteriaMetList;
        this.classifiedAt = classifiedAt;
    }

    /**
     * Factory method to create a successful classification result.
     */
    public static ClassificationResult of(UUID tierUid, String tierName, Integer hierarchyLevel,
                                        BigDecimal discountPercentage, List<String> criteriaMetList) {
        return new ClassificationResult(tierUid, tierName, hierarchyLevel, discountPercentage,
            criteriaMetList, Instant.now());
    }

    /**
     * Check if client is classified to a tier.
     */
    public boolean isClassified() {
        return tierUid != null;
    }

    /**
     * Get tier UID if classified, or empty Optional if NONE.
     */
    public Optional<UUID> getTierUid() {
        return Optional.ofNullable(tierUid);
    }

    /**
     * Get tier name if classified.
     */
    public Optional<String> getTierName() {
        return Optional.ofNullable(tierName);
    }

    /**
     * Get discount percentage if classified.
     */
    public Optional<BigDecimal> getDiscountPercentage() {
        return Optional.ofNullable(discountPercentage);
    }

    /**
     * Get hierarchy level if classified.
     */
    public Optional<Integer> getHierarchyLevel() {
        return Optional.ofNullable(hierarchyLevel);
    }

    /**
     * Get criteria met (list of rule names that matched).
     */
    public List<String> getCriteriaMet() {
        return criteriaMetList != null ? criteriaMetList : List.of();
    }

    /**
     * Get classification timestamp.
     */
    public Instant getClassifiedAt() {
        return classifiedAt;
    }

    @Override
    public String toString() {
        return isClassified()
            ? String.format("ClassificationResult(tier=%s, level=%d, discount=%s%%)",
                tierName, hierarchyLevel, discountPercentage)
            : "ClassificationResult(NONE)";
    }
}
