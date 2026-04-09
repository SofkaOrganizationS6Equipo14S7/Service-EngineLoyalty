package com.loyalty.service_engine.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.service_engine.application.dto.*;
import com.loyalty.service_engine.domain.entity.TransactionLogEntity;
import com.loyalty.service_engine.domain.repository.TransactionLogRepository;
import com.loyalty.service_engine.infrastructure.exception.DiscountCalculationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionLogWriterTest {

    @Mock
    private TransactionLogRepository transactionLogRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TransactionLogWriter writer;

    private UUID ecommerceId;

    @BeforeEach
    void setUp() {
        ecommerceId = UUID.randomUUID();
    }

    private DiscountCalculateRequestV2 buildRequest() {
        return new DiscountCalculateRequestV2(
                ecommerceId, "ORD-001", "CUST-001",
                new BigDecimal("5000"), 10, 365,
                List.of(new CartItemRequest("P1", 2, new BigDecimal("50"), "elec"))
        );
    }

    private DiscountCalculateResponseV2 buildResponse(BigDecimal subtotal, BigDecimal calculated, BigDecimal applied) {
        BigDecimal finalAmt = subtotal.subtract(applied);
        return new DiscountCalculateResponseV2(
                subtotal, calculated, applied, finalAmt,
                "Gold", false, null, List.of(), null, Instant.now()
        );
    }

    @Test
    @DisplayName("Should write log successfully")
    void shouldWriteLog() {
        var request = buildRequest();
        var response = buildResponse(new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("10"));

        when(transactionLogRepository.existsByEcommerceIdAndExternalOrderId(ecommerceId, "ORD-001"))
                .thenReturn(false);

        UUID savedId = UUID.randomUUID();
        TransactionLogEntity savedEntity = TransactionLogEntity.builder().build();
        savedEntity.setId(savedId); // Simulate generated ID
        when(transactionLogRepository.save(any())).thenReturn(savedEntity);

        UUID result = writer.writeLog(request, response);

        assertEquals(savedId, result);
        verify(transactionLogRepository).save(any(TransactionLogEntity.class));
    }

    @Test
    @DisplayName("Should throw on duplicate external_order_id")
    void shouldThrowOnDuplicate() {
        var request = buildRequest();
        var response = buildResponse(new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("10"));

        when(transactionLogRepository.existsByEcommerceIdAndExternalOrderId(ecommerceId, "ORD-001"))
                .thenReturn(true);

        assertThrows(DiscountCalculationException.class, () -> writer.writeLog(request, response));
    }

    @Test
    @DisplayName("Should throw on financial integrity error")
    void shouldThrowOnFinancialIntegrityError() {
        var request = buildRequest();
        // final_amount=80, but subtotal=100, applied=10 → expected finalAmount=90 ≠ 80
        var response = new DiscountCalculateResponseV2(
                new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("80"),
                "Gold", false, null, List.of(), null, Instant.now()
        );

        when(transactionLogRepository.existsByEcommerceIdAndExternalOrderId(ecommerceId, "ORD-001"))
                .thenReturn(false);

        assertThrows(DiscountCalculationException.class, () -> writer.writeLog(request, response));
    }

    @Test
    @DisplayName("Should serialize applied rules into log entity")
    void shouldSerializeAppliedRules() {
        var request = buildRequest();
        var rule = new AppliedRuleDetail(
                UUID.randomUUID(), "Gold Discount", "FIDELITY", "PERCENTAGE",
                "INDIVIDUAL", new BigDecimal("10"), new BigDecimal("10"), 1
        );
        var response = new DiscountCalculateResponseV2(
                new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("90"),
                "Gold", false, null, List.of(rule), null, Instant.now()
        );

        when(transactionLogRepository.existsByEcommerceIdAndExternalOrderId(ecommerceId, "ORD-001"))
                .thenReturn(false);
        UUID savedId = UUID.randomUUID();
        TransactionLogEntity savedEntity = TransactionLogEntity.builder().build();
        savedEntity.setId(savedId);
        when(transactionLogRepository.save(any())).thenReturn(savedEntity);

        writer.writeLog(request, response);

        ArgumentCaptor<TransactionLogEntity> captor = ArgumentCaptor.forClass(TransactionLogEntity.class);
        verify(transactionLogRepository).save(captor.capture());
        TransactionLogEntity saved = captor.getValue();

        assertNotNull(saved.getAppliedRulesJson());
        assertEquals("SUCCESS", saved.getStatus());
        assertEquals("Gold", saved.getCustomerTier());
    }

    @Test
    @DisplayName("Should handle empty applied rules")
    void shouldHandleEmptyRules() {
        var request = buildRequest();
        var response = buildResponse(new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO);

        when(transactionLogRepository.existsByEcommerceIdAndExternalOrderId(ecommerceId, "ORD-001"))
                .thenReturn(false);
        UUID savedId = UUID.randomUUID();
        TransactionLogEntity savedEntity = TransactionLogEntity.builder().build();
        savedEntity.setId(savedId);
        when(transactionLogRepository.save(any())).thenReturn(savedEntity);

        UUID result = writer.writeLog(request, response);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should throw DiscountCalculationException on save error")
    void shouldThrowOnSaveError() {
        var request = buildRequest();
        var response = buildResponse(new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO);

        when(transactionLogRepository.existsByEcommerceIdAndExternalOrderId(ecommerceId, "ORD-001"))
                .thenReturn(false);
        when(transactionLogRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThrows(DiscountCalculationException.class, () -> writer.writeLog(request, response));
    }

    @Test
    @DisplayName("Should accept tolerance in financial integrity check")
    void shouldAcceptToleranceInIntegrity() {
        var request = buildRequest();
        // Small tolerance: expected=90, actual=90.04 → within 0.05 tolerance
        var response = new DiscountCalculateResponseV2(
                new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("90.04"),
                "Gold", false, null, List.of(), null, Instant.now()
        );

        when(transactionLogRepository.existsByEcommerceIdAndExternalOrderId(ecommerceId, "ORD-001"))
                .thenReturn(false);
        UUID savedId = UUID.randomUUID();
        TransactionLogEntity savedEntity = TransactionLogEntity.builder().build();
        savedEntity.setId(savedId);
        when(transactionLogRepository.save(any())).thenReturn(savedEntity);

        assertDoesNotThrow(() -> writer.writeLog(request, response));
    }

    @Test
    @DisplayName("Should handle null appliedRules list")
    void shouldHandleNullRules() {
        var request = buildRequest();
        var response = new DiscountCalculateResponseV2(
                new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"),
                "Gold", false, null, null, null, Instant.now()
        );

        when(transactionLogRepository.existsByEcommerceIdAndExternalOrderId(ecommerceId, "ORD-001"))
                .thenReturn(false);
        UUID savedId = UUID.randomUUID();
        TransactionLogEntity savedEntity = TransactionLogEntity.builder().build();
        savedEntity.setId(savedId);
        when(transactionLogRepository.save(any())).thenReturn(savedEntity);

        assertDoesNotThrow(() -> writer.writeLog(request, response));
    }

    @Test
    @DisplayName("Should handle rule with null discountPercentage")
    void shouldHandleNullDiscountPercentage() {
        var request = buildRequest();
        var rule = new AppliedRuleDetail(
                UUID.randomUUID(), "Free Shipping", "PRODUCT", "FIXED",
                "INDIVIDUAL", null, new BigDecimal("10"), 1
        );
        var response = new DiscountCalculateResponseV2(
                new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("90"),
                "Gold", false, null, List.of(rule), null, Instant.now()
        );

        when(transactionLogRepository.existsByEcommerceIdAndExternalOrderId(ecommerceId, "ORD-001"))
                .thenReturn(false);
        UUID savedId = UUID.randomUUID();
        TransactionLogEntity savedEntity = TransactionLogEntity.builder().build();
        savedEntity.setId(savedId);
        when(transactionLogRepository.save(any())).thenReturn(savedEntity);

        assertDoesNotThrow(() -> writer.writeLog(request, response));
    }

    @Test
    @DisplayName("Should use current time when calculatedAt is null")
    void shouldUseCurrentTimeWhenCalculatedAtNull() {
        var request = buildRequest();
        var response = new DiscountCalculateResponseV2(
                new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"),
                "Gold", false, null, List.of(), null, null
        );

        when(transactionLogRepository.existsByEcommerceIdAndExternalOrderId(ecommerceId, "ORD-001"))
                .thenReturn(false);
        UUID savedId = UUID.randomUUID();
        TransactionLogEntity savedEntity = TransactionLogEntity.builder().build();
        savedEntity.setId(savedId);
        when(transactionLogRepository.save(any())).thenReturn(savedEntity);

        assertDoesNotThrow(() -> writer.writeLog(request, response));

        ArgumentCaptor<TransactionLogEntity> captor = ArgumentCaptor.forClass(TransactionLogEntity.class);
        verify(transactionLogRepository).save(captor.capture());
        assertNotNull(captor.getValue().getCalculatedAt());
    }
}
