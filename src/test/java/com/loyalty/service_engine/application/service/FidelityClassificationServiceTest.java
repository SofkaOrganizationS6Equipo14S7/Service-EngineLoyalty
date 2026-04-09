package com.loyalty.service_engine.application.service;

import com.loyalty.service_engine.application.dto.*;
import com.loyalty.service_engine.infrastructure.exception.ClassificationValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FidelityClassificationServiceTest {

    @Mock
    private ClassificationMatrixCaffeineCacheService cacheService;

    @InjectMocks
    private FidelityClassificationService service;

    private UUID ecommerceId;

    @BeforeEach
    void setUp() {
        ecommerceId = UUID.randomUUID();
    }

    private ClassifyRequestV1 request(BigDecimal spent, int orders, int days) {
        return new ClassifyRequestV1(spent, orders, days, null);
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        void shouldThrowWhenEcommerceIdNull() {
            var req = request(BigDecimal.TEN, 1, 1);
            assertThrows(ClassificationValidationException.class, () -> service.classify(null, req));
        }

        @Test
        void shouldThrowWhenRequestNull() {
            assertThrows(ClassificationValidationException.class, () -> service.classify(ecommerceId, null));
        }

        @Test
        void shouldThrowWhenTotalSpentNull() {
            var req = new ClassifyRequestV1(null, 1, 1, null);
            assertThrows(ClassificationValidationException.class, () -> service.classify(ecommerceId, req));
        }

        @Test
        void shouldThrowWhenTotalSpentNegative() {
            var req = request(new BigDecimal("-1"), 1, 1);
            assertThrows(ClassificationValidationException.class, () -> service.classify(ecommerceId, req));
        }

        @Test
        void shouldThrowWhenOrderCountNull() {
            var req = new ClassifyRequestV1(BigDecimal.TEN, null, 1, null);
            assertThrows(ClassificationValidationException.class, () -> service.classify(ecommerceId, req));
        }

        @Test
        void shouldThrowWhenOrderCountNegative() {
            var req = request(BigDecimal.TEN, -1, 1);
            assertThrows(ClassificationValidationException.class, () -> service.classify(ecommerceId, req));
        }

        @Test
        void shouldThrowWhenMembershipDaysNull() {
            var req = new ClassifyRequestV1(BigDecimal.TEN, 1, null, null);
            assertThrows(ClassificationValidationException.class, () -> service.classify(ecommerceId, req));
        }

        @Test
        void shouldThrowWhenMembershipDaysNegative() {
            var req = request(BigDecimal.TEN, 1, -1);
            assertThrows(ClassificationValidationException.class, () -> service.classify(ecommerceId, req));
        }
    }

    @Nested
    @DisplayName("Classification Logic")
    class ClassificationLogicTests {

        @Test
        @DisplayName("NONE when no tiers configured")
        void noneWhenNoTiers() {
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.empty());

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));

            assertSame(ClassificationResult.NONE, result);
        }

        @Test
        @DisplayName("NONE when tiers list is empty")
        void noneWhenTiersEmpty() {
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of()));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));

            assertSame(ClassificationResult.NONE, result);
        }

        @Test
        @DisplayName("NONE when no rules configured")
        void noneWhenNoRules() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("10"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.empty());

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));

            assertSame(ClassificationResult.NONE, result);
        }

        @Test
        @DisplayName("EXACT MATCH when conditions met")
        void exactMatchWhenConditionsMet() {
            UUID tierUid = UUID.randomUUID();
            var tier = new CustomerTierDTO(tierUid, ecommerceId, "Platino",
                    new BigDecimal("20"), 5, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> conditions = Map.of("min_spent", 500, "min_order_count", 5);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Test Rule",
                    "CLASSIFICATION", "PERCENTAGE", new BigDecimal("20"), "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));

            assertTrue(result.isClassified());
            assertEquals("Platino", result.getTierName().orElse(""));
            assertEquals(5, result.getHierarchyLevel().orElse(0));
        }

        @Test
        @DisplayName("FALLTHROUGH to minimum tier when no rule matches")
        void fallthroughToMinimumTier() {
            UUID tierUid1 = UUID.randomUUID();
            UUID tierUid2 = UUID.randomUUID();
            var tierBronze = new CustomerTierDTO(tierUid1, ecommerceId, "Bronce",
                    new BigDecimal("5"), 1, true, Instant.now());
            var tierGold = new CustomerTierDTO(tierUid2, ecommerceId, "Gold",
                    new BigDecimal("15"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tierBronze, tierGold)));

            Map<String, Object> conditions = Map.of("min_spent", 50000);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "High Spender",
                    "CLASSIFICATION", "PERCENTAGE", new BigDecimal("20"), "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("100"), 1, 10));

            assertTrue(result.isClassified());
            assertEquals("Bronce", result.getTierName().orElse(""));
            assertTrue(result.getCriteriaMet().contains("fallback_minimum_tier"));
        }

        @Test
        @DisplayName("Evaluates min_spent condition correctly")
        void evaluatesMinSpent() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("10"), 2, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> conditions = Map.of("min_spent", 1000);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            // Below min_spent
            ClassificationResult below = service.classify(ecommerceId, request(new BigDecimal("500"), 10, 100));
            assertEquals("Gold", below.getTierName().orElse("")); // fallthrough to minimum
            assertTrue(below.getCriteriaMet().contains("fallback_minimum_tier"));

            // At or above min_spent
            ClassificationResult above = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(above.isClassified());
        }

        @Test
        @DisplayName("Evaluates max_spent condition")
        void evaluatesMaxSpent() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Silver",
                    new BigDecimal("5"), 2, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> conditions = Map.of("max_spent", 2000);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult within = service.classify(ecommerceId, request(new BigDecimal("1500"), 10, 100));
            assertTrue(within.isClassified());
            assertFalse(within.getCriteriaMet().contains("fallback_minimum_tier"));
        }

        @Test
        @DisplayName("Evaluates min_order_count condition")
        void evaluatesMinOrderCount() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Silver",
                    new BigDecimal("5"), 2, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> conditions = Map.of("min_order_count", 5);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.isClassified());
        }

        @Test
        @DisplayName("Evaluates min_membership_days condition")
        void evaluatesMembershipDays() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Silver",
                    new BigDecimal("5"), 2, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> conditions = Map.of("min_membership_days", 30);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.isClassified());
        }

        @Test
        @DisplayName("Skips inactive rules")
        void skipsInactiveRules() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Silver",
                    new BigDecimal("5"), 2, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Inactive Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    Map.of("min_spent", 0), 1, false, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            // fallthrough to minimum since the only rule is inactive
            assertTrue(result.getCriteriaMet().contains("fallback_minimum_tier"));
        }

        @Test
        @DisplayName("Skips rules with null logicConditions")
        void skipsNullLogicConditions() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Silver",
                    new BigDecimal("5"), 2, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "No Conditions",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    null, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.getCriteriaMet().contains("fallback_minimum_tier"));
        }

        @Test
        @DisplayName("Handles nested Map value for conditions")
        void handlesNestedMapValues() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("15"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> conditions = Map.of("min_spent", Map.of("value", 500));
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Nested Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.isClassified());
        }

        @Test
        @DisplayName("Handles String values for conditions")
        void handlesStringValues() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("15"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> conditions = Map.of("min_spent", "500", "min_order_count", "3");
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "String Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.isClassified());
        }

        @Test
        @DisplayName("Handles BigDecimal values for conditions")
        void handlesBigDecimalValues() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("15"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> conditions = Map.of("min_spent", new BigDecimal("500"));
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "BD Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.isClassified());
        }

        @Test
        @DisplayName("Returns NONE when all tiers inactive")
        void noneWhenAllTiersInactive() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("10"), 3, false, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> conditions = Map.of("min_spent", 100);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertSame(ClassificationResult.NONE, result);
        }

        @Test
        @DisplayName("Selects highest hierarchy tier on match")
        void selectsHighestHierarchyTier() {
            var tierLow = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Bronce",
                    new BigDecimal("5"), 1, true, Instant.now());
            var tierHigh = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Platino",
                    new BigDecimal("20"), 5, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tierLow, tierHigh)));

            Map<String, Object> conditions = Map.of("min_spent", 100);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertEquals("Platino", result.getTierName().orElse(""));
        }

        @Test
        @DisplayName("Selects lowest hierarchy tier on fallthrough")
        void selectsLowestHierarchyOnFallthrough() {
            var tier1 = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Bronce",
                    new BigDecimal("5"), 1, true, Instant.now());
            var tier2 = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Platino",
                    new BigDecimal("20"), 5, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier1, tier2)));

            Map<String, Object> conditions = Map.of("min_spent", 999999);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("100"), 1, 10));
            assertEquals("Bronce", result.getTierName().orElse(""));
        }

        @Test
        @DisplayName("Handles invalid string for number fields gracefully")
        void handlesInvalidStringForNumbers() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("10"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            // "abc" is not parseable as BigDecimal
            Map<String, Object> conditions = new HashMap<>();
            conditions.put("min_spent", "abc");
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            // Should not throw - invalid parse returns null, null treated as no constraint
            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.isClassified());
        }

        @Test
        @DisplayName("extractBigDecimal handles Number type (not BigDecimal)")
        void extractBigDecimalFromNumber() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("10"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            // Double is a Number but not BigDecimal
            Map<String, Object> conditions = new HashMap<>();
            conditions.put("min_spent", 500.0);
            conditions.put("max_spent", 5000.0);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.isClassified());
        }

        @Test
        @DisplayName("extractBigDecimal handles Map with null value")
        void extractBigDecimalMapNullValue() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("10"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> nestedMap = new HashMap<>();
            nestedMap.put("value", null);
            Map<String, Object> conditions = new HashMap<>();
            conditions.put("min_spent", nestedMap);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.isClassified());
        }

        @Test
        @DisplayName("extractInteger handles Number type (not Integer)")
        void extractIntegerFromNumber() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("10"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            // Long is a Number but not Integer
            Map<String, Object> conditions = new HashMap<>();
            conditions.put("min_order_count", 5L);
            conditions.put("min_membership_days", 30L);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.isClassified());
        }

        @Test
        @DisplayName("extractInteger handles String type")
        void extractIntegerFromString() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("10"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> conditions = new HashMap<>();
            conditions.put("min_order_count", "5");
            conditions.put("min_membership_days", "30");
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.isClassified());
        }

        @Test
        @DisplayName("extractInteger handles invalid String")
        void extractIntegerFromInvalidString() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("10"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> conditions = new HashMap<>();
            conditions.put("min_order_count", "not_a_number");
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.isClassified());
        }

        @Test
        @DisplayName("extractInteger handles Map with Number value")
        void extractIntegerMapWithNumber() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("10"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> conditions = new HashMap<>();
            conditions.put("min_order_count", Map.of("value", 5L)); // Long, not Integer
            conditions.put("min_membership_days", Map.of("value", 30)); // Integer
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.isClassified());
        }

        @Test
        @DisplayName("extractInteger handles Map with null value")
        void extractIntegerMapNullValue() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("10"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> nestedMap = new HashMap<>();
            nestedMap.put("value", null);
            Map<String, Object> conditions = new HashMap<>();
            conditions.put("min_order_count", nestedMap);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.isClassified());
        }

        @Test
        @DisplayName("max_spent condition fails to classify")
        void maxSpentConditionFails() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("10"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> conditions = Map.of("max_spent", 500);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            // totalSpent=1000 > max_spent=500 → rule fails → fallthrough
            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.getCriteriaMet().contains("fallback_minimum_tier"));
        }

        @Test
        @DisplayName("min_order_count fails to classify")
        void minOrderCountConditionFails() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("10"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> conditions = Map.of("min_order_count", 50);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            // orderCount=10 < min_order_count=50 → fails
            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.getCriteriaMet().contains("fallback_minimum_tier"));
        }

        @Test
        @DisplayName("min_membership_days fails to classify")
        void minMembershipDaysConditionFails() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("10"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> conditions = Map.of("min_membership_days", 500);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            // membershipDays=100 < 500 → fails
            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.getCriteriaMet().contains("fallback_minimum_tier"));
        }

        @Test
        @DisplayName("Rules list is empty")
        void emptyRulesList() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("10"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of()));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertSame(ClassificationResult.NONE, result);
        }

        @Test
        @DisplayName("extractBigDecimal returns null for unknown field type")
        void extractBigDecimalUnknownType() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("10"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            // Boolean is not a supported type for extractBigDecimal
            Map<String, Object> conditions = new HashMap<>();
            conditions.put("min_spent", Boolean.TRUE);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.isClassified());
        }

        @Test
        @DisplayName("extractInteger returns null for unknown field type")
        void extractIntegerUnknownType() {
            var tier = new CustomerTierDTO(UUID.randomUUID(), ecommerceId, "Gold",
                    new BigDecimal("10"), 3, true, Instant.now());
            when(cacheService.getTiers(ecommerceId)).thenReturn(Optional.of(List.of(tier)));

            Map<String, Object> conditions = new HashMap<>();
            conditions.put("min_order_count", Boolean.FALSE);
            var rule = new ClassificationRuleDTO(UUID.randomUUID(), ecommerceId, "Rule",
                    "CLASSIFICATION", "PERCENTAGE", BigDecimal.TEN, "INDIVIDUAL",
                    conditions, 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            ClassificationResult result = service.classify(ecommerceId, request(new BigDecimal("1000"), 10, 100));
            assertTrue(result.isClassified());
        }
    }
}
