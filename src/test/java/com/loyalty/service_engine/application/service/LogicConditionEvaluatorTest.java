package com.loyalty.service_engine.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loyalty.service_engine.application.dto.CartItemRequest;
import com.loyalty.service_engine.application.dto.DiscountCalculateRequestV2;
import com.loyalty.service_engine.infrastructure.exception.RuleEvaluationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LogicConditionEvaluatorTest {

    private LogicConditionEvaluator evaluator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        evaluator = new LogicConditionEvaluator(objectMapper);
    }

    private DiscountCalculateRequestV2 request(BigDecimal totalSpent, int orderCount, int membershipDays, List<CartItemRequest> items) {
        return new DiscountCalculateRequestV2(
                UUID.randomUUID(), "ORD-1", "C1", totalSpent, orderCount, membershipDays, items
        );
    }

    private CartItemRequest item(String category) {
        return new CartItemRequest("P1", 1, BigDecimal.TEN, category);
    }

    @Nested
    @DisplayName("Null/Empty conditions")
    class NullEmptyTests {

        @Test
        void nullConditionsReturnsTrue() {
            var req = request(BigDecimal.TEN, 1, 1, List.of(item("elec")));
            assertTrue(evaluator.evaluateCondition(null, req, BigDecimal.TEN, "Gold"));
        }

        @Test
        void emptyMapReturnsTrue() {
            var req = request(BigDecimal.TEN, 1, 1, List.of(item("elec")));
            assertTrue(evaluator.evaluateCondition(Map.of(), req, BigDecimal.TEN, "Gold"));
        }

        @Test
        void unknownTypeReturnsTrue() {
            var req = request(BigDecimal.TEN, 1, 1, List.of(item("elec")));
            assertTrue(evaluator.evaluateCondition("some-string", req, BigDecimal.TEN, "Gold"));
        }
    }

    @Nested
    @DisplayName("Map-based conditions")
    class MapConditions {

        @Test
        void minSpent_pass() {
            Map<String, Object> cond = Map.of("min_spent", "500");
            var req = request(new BigDecimal("1000"), 1, 1, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void minSpent_fail() {
            Map<String, Object> cond = Map.of("min_spent", "5000");
            var req = request(new BigDecimal("1000"), 1, 1, List.of(item(null)));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void maxSpent_pass() {
            Map<String, Object> cond = Map.of("max_spent", "5000");
            var req = request(new BigDecimal("1000"), 1, 1, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void maxSpent_fail() {
            Map<String, Object> cond = Map.of("max_spent", "500");
            var req = request(new BigDecimal("1000"), 1, 1, List.of(item(null)));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void minSubtotal_pass() {
            Map<String, Object> cond = Map.of("min_subtotal", "5");
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void minSubtotal_fail() {
            Map<String, Object> cond = Map.of("min_subtotal", "50");
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void maxSubtotal_pass() {
            Map<String, Object> cond = Map.of("max_subtotal", "50");
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void maxSubtotal_fail() {
            Map<String, Object> cond = Map.of("max_subtotal", "5");
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void minOrderCount_pass() {
            Map<String, Object> cond = Map.of("min_order_count", 5);
            var req = request(BigDecimal.TEN, 10, 1, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void minOrderCount_fail() {
            Map<String, Object> cond = Map.of("min_order_count", 50);
            var req = request(BigDecimal.TEN, 10, 1, List.of(item(null)));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void minMembershipDays_pass() {
            Map<String, Object> cond = Map.of("min_membership_days", 30);
            var req = request(BigDecimal.TEN, 1, 100, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void minMembershipDays_fail() {
            Map<String, Object> cond = Map.of("min_membership_days", 365);
            var req = request(BigDecimal.TEN, 1, 100, List.of(item(null)));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void customerTier_pass() {
            Map<String, Object> cond = Map.of("customer_tier", "Gold");
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, "Gold"));
        }

        @Test
        void customerTier_fail() {
            Map<String, Object> cond = Map.of("customer_tier", "Platino");
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, "Gold"));
        }

        @Test
        void customerTier_caseInsensitive() {
            Map<String, Object> cond = Map.of("customer_tier", "GOLD");
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, "gold"));
        }

        @Test
        void customerTier_nullTier() {
            Map<String, Object> cond = Map.of("customer_tier", "Gold");
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }
    }

    @Nested
    @DisplayName("Categories condition")
    class CategoriesTests {

        @Test
        void categories_matchFound() {
            Map<String, Object> cond = Map.of("categories", List.of("electronics", "books"));
            var req = request(BigDecimal.TEN, 1, 1, List.of(item("electronics")));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void categories_noMatch() {
            Map<String, Object> cond = Map.of("categories", List.of("electronics", "books"));
            var req = request(BigDecimal.TEN, 1, 1, List.of(item("clothing")));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void categories_emptyArray() {
            Map<String, Object> cond = Map.of("categories", List.of());
            var req = request(BigDecimal.TEN, 1, 1, List.of(item("clothing")));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void categories_nullCategoryOnItem() {
            Map<String, Object> cond = Map.of("categories", List.of("electronics"));
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void categories_emptyCategoryOnItem() {
            Map<String, Object> cond = Map.of("categories", List.of("electronics"));
            var req = request(BigDecimal.TEN, 1, 1, List.of(item("")));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }
    }

    @Nested
    @DisplayName("Combined conditions with AND logic")
    class CombinedTests {

        @Test
        void allConditionsMet() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("min_spent", "100");
            cond.put("min_order_count", 3);
            cond.put("min_membership_days", 30);
            var req = request(new BigDecimal("500"), 10, 100, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void oneConditionFails() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("min_spent", "100");
            cond.put("min_order_count", 50); // fails
            var req = request(new BigDecimal("500"), 10, 100, List.of(item(null)));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void minSpentFails_shortCircuit() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("min_spent", "999999");
            cond.put("max_spent", "1000000");
            cond.put("min_order_count", 1);
            var req = request(new BigDecimal("500"), 10, 100, List.of(item(null)));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void maxSpentFails_shortCircuit() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("max_spent", "1");
            var req = request(new BigDecimal("500"), 10, 100, List.of(item(null)));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void minSubtotalFails_shortCircuit() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("min_subtotal", "999999");
            var req = request(new BigDecimal("500"), 10, 100, List.of(item(null)));
            assertFalse(evaluator.evaluateCondition(cond, req, new BigDecimal("10"), null));
        }

        @Test
        void maxSubtotalFails_shortCircuit() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("max_subtotal", "1");
            var req = request(new BigDecimal("500"), 10, 100, List.of(item(null)));
            assertFalse(evaluator.evaluateCondition(cond, req, new BigDecimal("100"), null));
        }

        @Test
        void minMembershipDaysFails_shortCircuit() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("min_membership_days", 999999);
            var req = request(new BigDecimal("500"), 10, 1, List.of(item(null)));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void categoriesFails_shortCircuit() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("categories", List.of("nonexistent"));
            var req = request(new BigDecimal("500"), 10, 100, List.of(item("electronics")));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void tierFails_shortCircuit() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("customer_tier", "Platinum");
            var req = request(new BigDecimal("500"), 10, 100, List.of(item(null)));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, "Gold"));
        }

        @Test
        void allEightConditionsPass() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("min_spent", "100");
            cond.put("max_spent", "10000");
            cond.put("min_subtotal", "5");
            cond.put("max_subtotal", "500");
            cond.put("min_order_count", 3);
            cond.put("min_membership_days", 30);
            cond.put("categories", List.of("electronics"));
            cond.put("customer_tier", "Gold");
            var req = request(new BigDecimal("500"), 10, 100, List.of(item("electronics")));
            assertTrue(evaluator.evaluateCondition(cond, req, new BigDecimal("100"), "Gold"));
        }

        @Test
        void allEightConditionsFail() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("min_spent", "999999");
            cond.put("max_spent", "1");
            cond.put("min_subtotal", "999999");
            cond.put("max_subtotal", "1");
            cond.put("min_order_count", 999999);
            cond.put("min_membership_days", 999999);
            cond.put("categories", List.of("nonexistent"));
            cond.put("customer_tier", "Platinum");
            var req = request(new BigDecimal("500"), 10, 100, List.of(item("electronics")));
            assertFalse(evaluator.evaluateCondition(cond, req, new BigDecimal("100"), "Gold"));
        }
    }

    @Nested
    @DisplayName("Custom evaluation_logic")
    class EvaluationLogicTests {

        @Test
        void andLogic() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("min_spent", "100");
            cond.put("min_order_count", 3);
            cond.put("evaluation_logic", "AND");
            var req = request(new BigDecimal("500"), 10, 100, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void andLogicWithFailure() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("min_spent", "999999");
            cond.put("evaluation_logic", "AND");
            var req = request(new BigDecimal("10"), 1, 1, List.of(item(null)));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void orLogic() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("min_spent", "100000"); // fails
            cond.put("min_order_count", 3); // passes
            cond.put("evaluation_logic", "OR");
            var req = request(new BigDecimal("500"), 10, 100, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void orLogicAllFalse() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("min_spent", "999999");
            cond.put("max_spent", "1");
            cond.put("min_subtotal", "999999");
            cond.put("max_subtotal", "1");
            cond.put("min_order_count", 999999);
            cond.put("min_membership_days", 999999);
            cond.put("categories", List.of("nonexistent"));
            cond.put("customer_tier", "NONEXISTENT");
            cond.put("evaluation_logic", "OR");
            var req = request(new BigDecimal("500"), 10, 100, List.of(item("electronics")));
            assertFalse(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, "Gold"));
        }

        @Test
        void unknownLogicDefaultsToAnd() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("min_spent", "100");
            cond.put("evaluation_logic", "XOR");
            var req = request(new BigDecimal("500"), 10, 100, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }
    }

    @Nested
    @DisplayName("JsonNode-based conditions")
    class JsonNodeTests {

        @Test
        void jsonNodeConditions() {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("min_spent", "100");
            node.put("min_order_count", 3);
            var req = request(new BigDecimal("500"), 10, 100, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(node, req, BigDecimal.TEN, null));
        }

        @Test
        void jsonNodeNullNode() {
            JsonNode nullNode = objectMapper.nullNode();
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(nullNode, req, BigDecimal.TEN, null));
        }
    }

    @Nested
    @DisplayName("Null handling in conditions")
    class NullFieldTests {

        @Test
        void nullMinSpentFieldIgnored() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("min_spent", null);
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void nullMaxSpentFieldIgnored() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("max_spent", null);
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void nullCategoriesFieldIgnored() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("categories", null);
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void nullCustomerTierFieldIgnored() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("customer_tier", null);
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void nullMinSubtotalFieldIgnored() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("min_subtotal", null);
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void nullMaxSubtotalFieldIgnored() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("max_subtotal", null);
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void nullMinOrderCountFieldIgnored() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("min_order_count", null);
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }

        @Test
        void nullMinMembershipDaysFieldIgnored() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("min_membership_days", null);
            var req = request(BigDecimal.TEN, 1, 1, List.of(item(null)));
            assertTrue(evaluator.evaluateCondition(cond, req, BigDecimal.TEN, null));
        }
    }
}
