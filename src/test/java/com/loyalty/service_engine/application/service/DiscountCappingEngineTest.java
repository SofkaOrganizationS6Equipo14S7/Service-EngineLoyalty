package com.loyalty.service_engine.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class DiscountCappingEngineTest {

    private final DiscountCappingEngine engine = new DiscountCappingEngine();

    @Nested
    @DisplayName("applyCap()")
    class ApplyCapTests {

        @Test
        @DisplayName("No cap when maxCap is null")
        void noCap_whenMaxCapNull() {
            var result = engine.applyCap(new BigDecimal("100"), null);

            assertEquals(0, result.appliedDiscount().compareTo(new BigDecimal("100")));
            assertFalse(result.wasCapped());
            assertNull(result.capReason());
        }

        @Test
        @DisplayName("No cap when maxCap is zero")
        void noCap_whenMaxCapZero() {
            var result = engine.applyCap(new BigDecimal("100"), BigDecimal.ZERO);

            assertEquals(0, result.appliedDiscount().compareTo(new BigDecimal("100")));
            assertFalse(result.wasCapped());
        }

        @Test
        @DisplayName("No cap when maxCap is negative")
        void noCap_whenMaxCapNegative() {
            var result = engine.applyCap(new BigDecimal("100"), new BigDecimal("-10"));

            assertEquals(0, result.appliedDiscount().compareTo(new BigDecimal("100")));
            assertFalse(result.wasCapped());
        }

        @Test
        @DisplayName("No cap when discount within limit")
        void noCap_whenDiscountWithinLimit() {
            var result = engine.applyCap(new BigDecimal("30"), new BigDecimal("50"));

            assertEquals(0, result.appliedDiscount().compareTo(new BigDecimal("30")));
            assertFalse(result.wasCapped());
            assertNull(result.capReason());
        }

        @Test
        @DisplayName("No cap when discount equals limit")
        void noCap_whenDiscountEqualsLimit() {
            var result = engine.applyCap(new BigDecimal("50"), new BigDecimal("50"));

            assertEquals(0, result.appliedDiscount().compareTo(new BigDecimal("50")));
            assertFalse(result.wasCapped());
        }

        @Test
        @DisplayName("Cap applied when discount exceeds limit")
        void cap_whenDiscountExceedsLimit() {
            var result = engine.applyCap(new BigDecimal("100"), new BigDecimal("50"));

            assertEquals(0, result.appliedDiscount().compareTo(new BigDecimal("50")));
            assertTrue(result.wasCapped());
            assertEquals("max_discount_cap", result.capReason());
        }
    }

    @Nested
    @DisplayName("applyRounding()")
    class ApplyRoundingTests {

        @Test
        @DisplayName("Null returns null")
        void nullReturnsNull() {
            assertNull(engine.applyRounding(null, DiscountCappingEngine.RoundingRule.ROUND_HALF_UP));
        }

        @Test
        @DisplayName("ROUND_HALF_UP rounds correctly")
        void roundHalfUp() {
            BigDecimal result = engine.applyRounding(new BigDecimal("10.335"), DiscountCappingEngine.RoundingRule.ROUND_HALF_UP);
            assertEquals(0, result.compareTo(new BigDecimal("10.34")));
        }

        @Test
        @DisplayName("FLOOR rounds down")
        void floor() {
            BigDecimal result = engine.applyRounding(new BigDecimal("10.339"), DiscountCappingEngine.RoundingRule.FLOOR);
            assertEquals(0, result.compareTo(new BigDecimal("10.33")));
        }

        @Test
        @DisplayName("CEIL rounds up")
        void ceil() {
            BigDecimal result = engine.applyRounding(new BigDecimal("10.331"), DiscountCappingEngine.RoundingRule.CEIL);
            assertEquals(0, result.compareTo(new BigDecimal("10.34")));
        }

        @Test
        @DisplayName("Null rounding rule defaults to HALF_UP")
        void nullRuleDefaultsHalfUp() {
            BigDecimal result = engine.applyRounding(new BigDecimal("10.335"), null);
            assertEquals(0, result.compareTo(new BigDecimal("10.34")));
        }

        @Test
        @DisplayName("Already 2 decimals unchanged")
        void alreadyTwoDecimals() {
            BigDecimal result = engine.applyRounding(new BigDecimal("10.50"), DiscountCappingEngine.RoundingRule.ROUND_HALF_UP);
            assertEquals(0, result.compareTo(new BigDecimal("10.50")));
        }

        @Test
        @DisplayName("Integer value gets 2 decimals")
        void integerGetsTwoDecimals() {
            BigDecimal result = engine.applyRounding(new BigDecimal("10"), DiscountCappingEngine.RoundingRule.ROUND_HALF_UP);
            assertEquals(2, result.scale());
            assertEquals(0, result.compareTo(new BigDecimal("10.00")));
        }
    }
}
