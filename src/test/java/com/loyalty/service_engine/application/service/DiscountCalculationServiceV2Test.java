package com.loyalty.service_engine.application.service;

import com.loyalty.service_engine.application.dto.*;
import com.loyalty.service_engine.domain.entity.EngineDiscountSettingsEntity;
import com.loyalty.service_engine.domain.repository.EngineDiscountSettingsRepository;
import com.loyalty.service_engine.infrastructure.exception.InvalidCartException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscountCalculationServiceV2Test {

    @Mock
    private FidelityClassificationService fidelityClassificationService;

    @Mock
    private ClassificationMatrixCaffeineCacheService cacheService;

    @Mock
    private DiscountCappingEngine discountCappingEngine;

    @Mock
    private TransactionLogWriter transactionLogWriter;

    @Mock
    private EngineDiscountSettingsRepository engineDiscountSettingsRepository;

    @InjectMocks
    private DiscountCalculationServiceV2 service;

    private UUID ecommerceId;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
        ecommerceId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
    }

    private DiscountCalculateRequestV2 buildRequest(List<CartItemRequest> items) {
        return new DiscountCalculateRequestV2(
                ecommerceId, "ORDER-001", "CUST-001",
                new BigDecimal("5000"), 10, 365, items
        );
    }

    private CartItemRequest cartItem(String productId, int qty, String price) {
        return new CartItemRequest(productId, qty, new BigDecimal(price), "electronics");
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should throw InvalidCartException when items is null")
        void shouldThrowWhenItemsNull() {
            DiscountCalculateRequestV2 request = new DiscountCalculateRequestV2(
                    ecommerceId, "ORD-1", "C1", BigDecimal.TEN, 1, 1, null);
            assertThrows(InvalidCartException.class, () -> service.calculate(request));
        }

        @Test
        @DisplayName("Should throw InvalidCartException when items is empty")
        void shouldThrowWhenItemsEmpty() {
            DiscountCalculateRequestV2 request = new DiscountCalculateRequestV2(
                    ecommerceId, "ORD-1", "C1", BigDecimal.TEN, 1, 1, List.of());
            assertThrows(InvalidCartException.class, () -> service.calculate(request));
        }

        @Test
        @DisplayName("Should throw InvalidCartException when quantity is zero")
        void shouldThrowWhenQuantityZero() {
            var items = List.of(new CartItemRequest("P1", 0, BigDecimal.TEN, null));
            var request = buildRequest(items);
            assertThrows(InvalidCartException.class, () -> service.calculate(request));
        }

        @Test
        @DisplayName("Should throw InvalidCartException when quantity is negative")
        void shouldThrowWhenQuantityNegative() {
            var items = List.of(new CartItemRequest("P1", -1, BigDecimal.TEN, null));
            var request = buildRequest(items);
            assertThrows(InvalidCartException.class, () -> service.calculate(request));
        }

        @Test
        @DisplayName("Should throw InvalidCartException when quantity is null")
        void shouldThrowWhenQuantityNull() {
            var items = List.of(new CartItemRequest("P1", null, BigDecimal.TEN, null));
            var request = buildRequest(items);
            assertThrows(InvalidCartException.class, () -> service.calculate(request));
        }

        @Test
        @DisplayName("Should throw InvalidCartException when unitPrice is null")
        void shouldThrowWhenUnitPriceNull() {
            var items = List.of(new CartItemRequest("P1", 1, null, null));
            var request = buildRequest(items);
            assertThrows(InvalidCartException.class, () -> service.calculate(request));
        }

        @Test
        @DisplayName("Should throw InvalidCartException when unitPrice is negative")
        void shouldThrowWhenUnitPriceNegative() {
            var items = List.of(new CartItemRequest("P1", 1, new BigDecimal("-5"), null));
            var request = buildRequest(items);
            assertThrows(InvalidCartException.class, () -> service.calculate(request));
        }
    }

    @Nested
    @DisplayName("Calculation Tests")
    class CalculationTests {

        @Test
        @DisplayName("Should calculate with no rules applied")
        void shouldCalculateWithNoRules() {
            var items = List.of(cartItem("P1", 2, "50.00"));
            var request = buildRequest(items);

            when(fidelityClassificationService.classify(eq(ecommerceId), any()))
                    .thenReturn(ClassificationResult.NONE);
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of()));
            when(engineDiscountSettingsRepository.findByEcommerceIdAndIsActiveTrue(ecommerceId))
                    .thenReturn(Optional.empty());
            when(discountCappingEngine.applyCap(any(), any()))
                    .thenReturn(new DiscountCappingEngine.CapResult(BigDecimal.ZERO, false, null));
            when(discountCappingEngine.applyRounding(any(), any()))
                    .thenAnswer(inv -> {
                        BigDecimal v = inv.getArgument(0);
                        return v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP);
                    });
            when(transactionLogWriter.writeLog(any(), any())).thenReturn(transactionId);

            DiscountCalculateResponseV2 result = service.calculate(request);

            assertNotNull(result);
            assertEquals(0, result.subtotalAmount().compareTo(new BigDecimal("100.00")));
            assertEquals(0, result.discountCalculated().compareTo(BigDecimal.ZERO.setScale(2)));
            assertEquals("UNCLASSIFIED", result.customerTier());
            assertEquals(transactionId, result.transactionId());
        }

        @Test
        @DisplayName("Should calculate with percentage rule")
        void shouldCalculateWithPercentageRule() {
            var items = List.of(cartItem("P1", 1, "200.00"));
            var request = buildRequest(items);

            ClassificationResult tier = ClassificationResult.of(
                    UUID.randomUUID(), "Gold", 3, new BigDecimal("10"), List.of("min_spent"));
            when(fidelityClassificationService.classify(eq(ecommerceId), any())).thenReturn(tier);

            ClassificationRuleDTO rule = new ClassificationRuleDTO(
                    UUID.randomUUID(), ecommerceId, "Gold Discount", "CLASSIFICATION",
                    "PERCENTAGE", new BigDecimal("10"), "INDIVIDUAL", Map.of(), 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));
            when(engineDiscountSettingsRepository.findByEcommerceIdAndIsActiveTrue(ecommerceId))
                    .thenReturn(Optional.empty());

            BigDecimal expectedDiscount = new BigDecimal("20.0000");
            when(discountCappingEngine.applyCap(any(), any()))
                    .thenReturn(new DiscountCappingEngine.CapResult(expectedDiscount, false, null));
            when(discountCappingEngine.applyRounding(any(), any()))
                    .thenAnswer(inv -> {
                        BigDecimal v = inv.getArgument(0);
                        return v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP);
                    });
            when(transactionLogWriter.writeLog(any(), any())).thenReturn(transactionId);

            DiscountCalculateResponseV2 result = service.calculate(request);

            assertNotNull(result);
            assertEquals("Gold", result.customerTier());
            assertEquals(0, result.discountCalculated().compareTo(new BigDecimal("20.00")));
        }

        @Test
        @DisplayName("Should calculate with fixed amount rule")
        void shouldCalculateWithFixedAmountRule() {
            var items = List.of(cartItem("P1", 1, "200.00"));
            var request = buildRequest(items);

            when(fidelityClassificationService.classify(eq(ecommerceId), any()))
                    .thenReturn(ClassificationResult.NONE);

            ClassificationRuleDTO rule = new ClassificationRuleDTO(
                    UUID.randomUUID(), ecommerceId, "Fixed Discount", "FIDELITY",
                    "FIXED_AMOUNT", new BigDecimal("50"), "INDIVIDUAL", Map.of(), 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));
            when(engineDiscountSettingsRepository.findByEcommerceIdAndIsActiveTrue(ecommerceId))
                    .thenReturn(Optional.empty());
            when(discountCappingEngine.applyCap(any(), any()))
                    .thenReturn(new DiscountCappingEngine.CapResult(new BigDecimal("50"), false, null));
            when(discountCappingEngine.applyRounding(any(), any()))
                    .thenAnswer(inv -> {
                        BigDecimal v = inv.getArgument(0);
                        return v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP);
                    });
            when(transactionLogWriter.writeLog(any(), any())).thenReturn(transactionId);

            DiscountCalculateResponseV2 result = service.calculate(request);

            assertNotNull(result);
            assertEquals(0, result.discountApplied().compareTo(new BigDecimal("50.00")));
        }

        @Test
        @DisplayName("Should apply cap when discount exceeds max")
        void shouldApplyCapWhenExceedsMax() {
            var items = List.of(cartItem("P1", 1, "100.00"));
            var request = buildRequest(items);

            when(fidelityClassificationService.classify(eq(ecommerceId), any()))
                    .thenReturn(ClassificationResult.NONE);

            ClassificationRuleDTO rule = new ClassificationRuleDTO(
                    UUID.randomUUID(), ecommerceId, "Big Discount", "SEASONAL",
                    "FIXED_AMOUNT", new BigDecimal("80"), "INDIVIDUAL", Map.of(), 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));

            EngineDiscountSettingsEntity settings = new EngineDiscountSettingsEntity();
            settings.setMaxDiscountCap(new BigDecimal("50"));
            settings.setIsActive(true);
            when(engineDiscountSettingsRepository.findByEcommerceIdAndIsActiveTrue(ecommerceId))
                    .thenReturn(Optional.of(settings));
            when(discountCappingEngine.applyCap(any(), eq(new BigDecimal("50"))))
                    .thenReturn(new DiscountCappingEngine.CapResult(new BigDecimal("50"), true, "max_discount_cap"));
            when(discountCappingEngine.applyRounding(any(), any()))
                    .thenAnswer(inv -> {
                        BigDecimal v = inv.getArgument(0);
                        return v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP);
                    });
            when(transactionLogWriter.writeLog(any(), any())).thenReturn(transactionId);

            DiscountCalculateResponseV2 result = service.calculate(request);

            assertTrue(result.wasCapped());
            assertEquals("max_discount_cap", result.capReason());
        }

        @Test
        @DisplayName("Should cap to subtotal when discount exceeds subtotal")
        void shouldCapToSubtotalWhenDiscountExceedsSubtotal() {
            var items = List.of(cartItem("P1", 1, "30.00"));
            var request = buildRequest(items);

            when(fidelityClassificationService.classify(eq(ecommerceId), any()))
                    .thenReturn(ClassificationResult.NONE);

            ClassificationRuleDTO rule = new ClassificationRuleDTO(
                    UUID.randomUUID(), ecommerceId, "Over Discount", "SEASONAL",
                    "FIXED_AMOUNT", new BigDecimal("50"), "INDIVIDUAL", Map.of(), 1, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule)));
            when(engineDiscountSettingsRepository.findByEcommerceIdAndIsActiveTrue(ecommerceId))
                    .thenReturn(Optional.empty());
            // Cap result returns 50 (no cap applied), but then subtotal limit kicks in
            when(discountCappingEngine.applyCap(any(), any()))
                    .thenReturn(new DiscountCappingEngine.CapResult(new BigDecimal("50"), false, null));
            when(discountCappingEngine.applyRounding(any(), any()))
                    .thenAnswer(inv -> {
                        BigDecimal v = inv.getArgument(0);
                        return v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP);
                    });
            when(transactionLogWriter.writeLog(any(), any())).thenReturn(transactionId);

            DiscountCalculateResponseV2 result = service.calculate(request);

            assertTrue(result.wasCapped());
            assertEquals("subtotal_limit", result.capReason());
        }

        @Test
        @DisplayName("Should select exclusive rule with highest discount")
        void shouldSelectExclusiveRule() {
            var items = List.of(cartItem("P1", 1, "100.00"));
            var request = buildRequest(items);

            when(fidelityClassificationService.classify(eq(ecommerceId), any()))
                    .thenReturn(ClassificationResult.NONE);

            ClassificationRuleDTO rule1 = new ClassificationRuleDTO(
                    UUID.randomUUID(), ecommerceId, "Discount A", "FIDELITY",
                    "FIXED_AMOUNT", new BigDecimal("10"), "EXCLUSIVE", Map.of(), 1, true, Instant.now());
            ClassificationRuleDTO rule2 = new ClassificationRuleDTO(
                    UUID.randomUUID(), ecommerceId, "Discount B", "SEASONAL",
                    "FIXED_AMOUNT", new BigDecimal("20"), "EXCLUSIVE", Map.of(), 2, true, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(rule1, rule2)));
            when(engineDiscountSettingsRepository.findByEcommerceIdAndIsActiveTrue(ecommerceId))
                    .thenReturn(Optional.empty());
            when(discountCappingEngine.applyCap(any(), any()))
                    .thenAnswer(inv -> new DiscountCappingEngine.CapResult(inv.getArgument(0), false, null));
            when(discountCappingEngine.applyRounding(any(), any()))
                    .thenAnswer(inv -> {
                        BigDecimal v = inv.getArgument(0);
                        return v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP);
                    });
            when(transactionLogWriter.writeLog(any(), any())).thenReturn(transactionId);

            DiscountCalculateResponseV2 result = service.calculate(request);

            // Should select the exclusive rule with highest discount (20)
            assertEquals(1, result.appliedRules().size());
            assertEquals(0, result.discountApplied().compareTo(new BigDecimal("20.00")));
        }

        @Test
        @DisplayName("Should handle classification exception with UNCLASSIFIED fallback")
        void shouldFallbackToUnclassifiedOnError() {
            var items = List.of(cartItem("P1", 1, "100.00"));
            var request = buildRequest(items);

            when(fidelityClassificationService.classify(eq(ecommerceId), any()))
                    .thenThrow(new RuntimeException("Cache error"));
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of()));
            when(engineDiscountSettingsRepository.findByEcommerceIdAndIsActiveTrue(ecommerceId))
                    .thenReturn(Optional.empty());
            when(discountCappingEngine.applyCap(any(), any()))
                    .thenReturn(new DiscountCappingEngine.CapResult(BigDecimal.ZERO, false, null));
            when(discountCappingEngine.applyRounding(any(), any()))
                    .thenAnswer(inv -> {
                        BigDecimal v = inv.getArgument(0);
                        return v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP);
                    });
            when(transactionLogWriter.writeLog(any(), any())).thenReturn(transactionId);

            DiscountCalculateResponseV2 result = service.calculate(request);

            assertEquals("UNCLASSIFIED", result.customerTier());
        }

        @Test
        @DisplayName("Should filter inactive rules")
        void shouldFilterInactiveRules() {
            var items = List.of(cartItem("P1", 1, "100.00"));
            var request = buildRequest(items);

            when(fidelityClassificationService.classify(eq(ecommerceId), any()))
                    .thenReturn(ClassificationResult.NONE);

            ClassificationRuleDTO active = new ClassificationRuleDTO(
                    UUID.randomUUID(), ecommerceId, "Active", "FIDELITY",
                    "FIXED_AMOUNT", new BigDecimal("10"), "INDIVIDUAL", Map.of(), 1, true, Instant.now());
            ClassificationRuleDTO inactive = new ClassificationRuleDTO(
                    UUID.randomUUID(), ecommerceId, "Inactive", "FIDELITY",
                    "FIXED_AMOUNT", new BigDecimal("50"), "INDIVIDUAL", Map.of(), 2, false, Instant.now());
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of(active, inactive)));
            when(engineDiscountSettingsRepository.findByEcommerceIdAndIsActiveTrue(ecommerceId))
                    .thenReturn(Optional.empty());
            when(discountCappingEngine.applyCap(any(), any()))
                    .thenAnswer(inv -> new DiscountCappingEngine.CapResult(inv.getArgument(0), false, null));
            when(discountCappingEngine.applyRounding(any(), any()))
                    .thenAnswer(inv -> {
                        BigDecimal v = inv.getArgument(0);
                        return v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP);
                    });
            when(transactionLogWriter.writeLog(any(), any())).thenReturn(transactionId);

            DiscountCalculateResponseV2 result = service.calculate(request);

            assertEquals(1, result.appliedRules().size());
        }

        @Test
        @DisplayName("Should handle no cached rules gracefully")
        void shouldHandleNoCachedRules() {
            var items = List.of(cartItem("P1", 1, "100.00"));
            var request = buildRequest(items);

            when(fidelityClassificationService.classify(eq(ecommerceId), any()))
                    .thenReturn(ClassificationResult.NONE);
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.empty());
            when(engineDiscountSettingsRepository.findByEcommerceIdAndIsActiveTrue(ecommerceId))
                    .thenReturn(Optional.empty());
            when(discountCappingEngine.applyCap(any(), any()))
                    .thenReturn(new DiscountCappingEngine.CapResult(BigDecimal.ZERO, false, null));
            when(discountCappingEngine.applyRounding(any(), any()))
                    .thenAnswer(inv -> {
                        BigDecimal v = inv.getArgument(0);
                        return v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP);
                    });
            when(transactionLogWriter.writeLog(any(), any())).thenReturn(transactionId);

            DiscountCalculateResponseV2 result = service.calculate(request);

            assertNotNull(result);
            assertTrue(result.appliedRules().isEmpty());
        }

        @Test
        @DisplayName("Should calculate subtotal correctly with multiple items")
        void shouldCalculateSubtotal() {
            var items = List.of(
                    cartItem("P1", 2, "50.00"),
                    cartItem("P2", 3, "25.00")
            );
            var request = buildRequest(items);

            when(fidelityClassificationService.classify(eq(ecommerceId), any()))
                    .thenReturn(ClassificationResult.NONE);
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of()));
            when(engineDiscountSettingsRepository.findByEcommerceIdAndIsActiveTrue(ecommerceId))
                    .thenReturn(Optional.empty());
            when(discountCappingEngine.applyCap(any(), any()))
                    .thenReturn(new DiscountCappingEngine.CapResult(BigDecimal.ZERO, false, null));
            when(discountCappingEngine.applyRounding(any(), any()))
                    .thenAnswer(inv -> {
                        BigDecimal v = inv.getArgument(0);
                        return v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP);
                    });
            when(transactionLogWriter.writeLog(any(), any())).thenReturn(transactionId);

            DiscountCalculateResponseV2 result = service.calculate(request);

            // 2*50 + 3*25 = 175
            assertEquals(0, result.subtotalAmount().compareTo(new BigDecimal("175.00")));
        }

        @Test
        @DisplayName("Should allow zero unit price items")
        void shouldAllowZeroUnitPrice() {
            var items = List.of(new CartItemRequest("FREEBIE", 1, BigDecimal.ZERO, null));
            var request = buildRequest(items);

            when(fidelityClassificationService.classify(eq(ecommerceId), any()))
                    .thenReturn(ClassificationResult.NONE);
            when(cacheService.getRules(ecommerceId)).thenReturn(Optional.of(List.of()));
            when(engineDiscountSettingsRepository.findByEcommerceIdAndIsActiveTrue(ecommerceId))
                    .thenReturn(Optional.empty());
            when(discountCappingEngine.applyCap(any(), any()))
                    .thenReturn(new DiscountCappingEngine.CapResult(BigDecimal.ZERO, false, null));
            when(discountCappingEngine.applyRounding(any(), any()))
                    .thenAnswer(inv -> {
                        BigDecimal v = inv.getArgument(0);
                        return v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP);
                    });
            when(transactionLogWriter.writeLog(any(), any())).thenReturn(transactionId);

            DiscountCalculateResponseV2 result = service.calculate(request);

            assertEquals(0, result.subtotalAmount().compareTo(BigDecimal.ZERO.setScale(2)));
        }
    }
}
