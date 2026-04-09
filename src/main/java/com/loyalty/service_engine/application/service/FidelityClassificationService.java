package com.loyalty.service_engine.application.service;

import com.loyalty.service_engine.application.dto.ClassificationResult;
import com.loyalty.service_engine.application.dto.ClassifyRequestV1;
import com.loyalty.service_engine.application.dto.ClassificationRuleDTO;
import com.loyalty.service_engine.application.dto.CustomerTierDTO;
import com.loyalty.service_engine.infrastructure.exception.ClassificationValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal service for customer loyalty tier classification.
 * NOT EXPOSED AS PUBLIC ENDPOINT.
 *
 * Classification Logic (3-path):
 * 1. EXACT MATCH: Customer metrics match one tier's criteria
 * 2. FALLTHROUGH: Customer metrics exceed a tier's base requirements
 * 3. NONE: Customer does not meet minimum criteria
 *
 * Determinism: Same payload always produces same classification.
 * Evaluation via JSONB logic_conditions with fields:
 * - min_spent, max_spent
 * - min_order_count
 * - min_membership_days
 * - evaluation_logic (optional dynamic expression)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FidelityClassificationService {

    private final ClassificationMatrixCaffeineCacheService cacheService;

    /**
     * Classify a customer based on their metrics.
     * Evaluates JSONB logic_conditions to determine tier.
     *
     * @param ecommerceId Tenant identifier
     * @param request Customer metrics (totalSpent, orderCount, membershipDays)
     * @return ClassificationResult with tier info or NONE
     * @throws ClassificationValidationException if validation fails
     */
    public ClassificationResult classify(UUID ecommerceId, ClassifyRequestV1 request) {
        if (ecommerceId == null) {
            log.warn("Classification request: ecommerceId is null");
            throw new ClassificationValidationException("ecommerceId cannot be null");
        }

        if (request == null) {
            log.warn("Classification request: request is null");
            throw new ClassificationValidationException("classification request cannot be null");
        }

        if (request.totalSpent() == null || request.totalSpent().signum() < 0) {
            log.warn("Classification request: invalid totalSpent={}", request.totalSpent());
            throw new ClassificationValidationException("totalSpent must be non-negative");
        }

        if (request.orderCount() == null || request.orderCount() < 0) {
            log.warn("Classification request: invalid orderCount={}", request.orderCount());
            throw new ClassificationValidationException("orderCount must be non-negative");
        }

        if (request.membershipDays() == null || request.membershipDays() < 0) {
            log.warn("Classification request: invalid membershipDays={}", request.membershipDays());
            throw new ClassificationValidationException("membershipDays must be non-negative");
        }

        // Fetch tiers from cache
        Optional<List<CustomerTierDTO>> tiersOpt = cacheService.getTiers(ecommerceId);
        if (tiersOpt.isEmpty() || tiersOpt.get().isEmpty()) {
            log.debug("No tiers configured for ecommerce: {}", ecommerceId);
            return ClassificationResult.NONE;
        }

        // Fetch rules from cache
        Optional<List<ClassificationRuleDTO>> rulesOpt = cacheService.getRules(ecommerceId);
        if (rulesOpt.isEmpty() || rulesOpt.get().isEmpty()) {
            log.debug("No classification rules configured for ecommerce: {}", ecommerceId);
            return ClassificationResult.NONE;
        }

        List<CustomerTierDTO> tiers = tiersOpt.get();
        List<ClassificationRuleDTO> rules = rulesOpt.get();

        // Find matching tier by evaluating rules
        for (ClassificationRuleDTO rule : rules) {
            if (rule.isActive() && rule.logicConditions() != null) {
                if (evaluateConditions(rule.logicConditions(), request)) {
                    // Find the tier associated with this rule
                    Optional<CustomerTierDTO> tierOpt = tiers.stream()
                        .filter(t -> t.isActive())
                        .max(Comparator.comparingInt(CustomerTierDTO::hierarchyLevel));

                    if (tierOpt.isPresent()) {
                        CustomerTierDTO tier = tierOpt.get();
                        List<String> criteriaMet = extractCriteriaNames(rule.logicConditions());
                        
                        log.debug("Classification matched: ecommerce={}, tier={}, level={}, criteria={}",
                            ecommerceId, tier.name(), tier.hierarchyLevel(), criteriaMet);

                        return ClassificationResult.of(
                            tier.uid(),
                            tier.name(),
                            tier.hierarchyLevel(),
                            tier.discountPercentage(),
                            criteriaMet
                        );
                    }
                }
            }
        }

        // PATH 3: No exact match - assign minimum tier (lowest hierarchy level)
        Optional<CustomerTierDTO> minimumTier = tiers.stream()
            .filter(CustomerTierDTO::isActive)
            .min(Comparator.comparingInt(CustomerTierDTO::hierarchyLevel));

        if (minimumTier.isPresent()) {
            CustomerTierDTO tier = minimumTier.get();
            log.debug("Classification fallthrough: ecommerce={}, assigned minimum tier={}, level={}",
                ecommerceId, tier.name(), tier.hierarchyLevel());

            return ClassificationResult.of(
                tier.uid(),
                tier.name(),
                tier.hierarchyLevel(),
                tier.discountPercentage(),
                List.of("fallback_minimum_tier")
            );
        }

        log.warn("Classification failed: no valid tiers available for ecommerce={}", ecommerceId);
        return ClassificationResult.NONE;
    }

    /**
     * Evaluate JSONB logic_conditions against customer metrics.
     * Supports fields: min_spent, max_spent, min_order_count, min_membership_days
     */
    private boolean evaluateConditions(Map<String, Object> conditions, ClassifyRequestV1 request) {
        // Check min_spent
        if (conditions.containsKey("min_spent")) {
            BigDecimal minSpent = extractBigDecimal(conditions.get("min_spent"));
            if (minSpent != null && request.totalSpent().compareTo(minSpent) < 0) {
                return false;
            }
        }

        // Check max_spent
        if (conditions.containsKey("max_spent")) {
            BigDecimal maxSpent = extractBigDecimal(conditions.get("max_spent"));
            if (maxSpent != null && request.totalSpent().compareTo(maxSpent) > 0) {
                return false;
            }
        }

        // Check min_order_count
        if (conditions.containsKey("min_order_count")) {
            Integer minOrders = extractInteger(conditions.get("min_order_count"));
            if (minOrders != null && request.orderCount() < minOrders) {
                return false;
            }
        }

        // Check min_membership_days
        if (conditions.containsKey("min_membership_days")) {
            Integer minDays = extractInteger(conditions.get("min_membership_days"));
            if (minDays != null && request.membershipDays() < minDays) {
                return false;
            }
        }

        return true;
    }

    /**
     * Extract BigDecimal from JSONB field (handles nested structure).
     */
    @SuppressWarnings("unchecked")
    private BigDecimal extractBigDecimal(Object field) {
        if (field == null) return null;

        if (field instanceof BigDecimal) {
            return (BigDecimal) field;
        }

        if (field instanceof Number) {
            return new BigDecimal(field.toString());
        }

        if (field instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) field;
            Object value = map.get("value");
            if (value != null) {
                return new BigDecimal(value.toString());
            }
        }

        if (field instanceof String) {
            try {
                return new BigDecimal((String) field);
            } catch (NumberFormatException e) {
                log.warn("Could not parse BigDecimal from: {}", field);
            }
        }

        return null;
    }

    /**
     * Extract Integer from JSONB field (handles nested structure).
     */
    @SuppressWarnings("unchecked")
    private Integer extractInteger(Object field) {
        if (field == null) return null;

        if (field instanceof Integer) {
            return (Integer) field;
        }

        if (field instanceof Number) {
            return ((Number) field).intValue();
        }

        if (field instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) field;
            Object value = map.get("value");
            if (value != null) {
                if (value instanceof Integer) {
                    return (Integer) value;
                } else if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
            }
        }

        if (field instanceof String) {
            try {
                return Integer.parseInt((String) field);
            } catch (NumberFormatException e) {
                log.warn("Could not parse Integer from: {}", field);
            }
        }

        return null;
    }

    /**
     * Extract criteria names from logic_conditions for audit logging.
     */
    private List<String> extractCriteriaNames(Map<String, Object> conditions) {
        List<String> criteria = new ArrayList<>();
        if (conditions.containsKey("min_spent")) criteria.add("min_spent");
        if (conditions.containsKey("max_spent")) criteria.add("max_spent");
        if (conditions.containsKey("min_order_count")) criteria.add("min_order_count");
        if (conditions.containsKey("min_membership_days")) criteria.add("min_membership_days");
        return criteria;
    }
}
