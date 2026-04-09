package com.loyalty.service_engine.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.service_engine.application.dto.AppliedRuleDetail;
import com.loyalty.service_engine.application.dto.DiscountCalculateRequestV2;
import com.loyalty.service_engine.application.dto.DiscountCalculateResponseV2;
import com.loyalty.service_engine.domain.entity.TransactionLogEntity;
import com.loyalty.service_engine.domain.repository.TransactionLogRepository;
import com.loyalty.service_engine.infrastructure.exception.DiscountCalculationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio para escribir auditoría de cálculos de descuentos en transaction_logs.
 * 
 * Registra:
 * - Subtotal, descuentos (calculado vs aplicado), monto final
 * - Flag de capping y razón
 * - Reglas aplicadas en JSONB
 * - Métricas del cliente sin PII
 * - Tier de clasificación
 * - Timestamps de cálculo y expiración (7 días)
 * 
 * Garantiza:
 * - external_order_id único por ecommerce (evita duplicados)
 * - Integridad financiera: final_amount = subtotal - discount_applied
 * - Sin datos personales (PII) en logs
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionLogWriter {

    private final TransactionLogRepository transactionLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Escribe registro de cálculo de descuentos
     * 
     * @param request datos de entrada del cálculo
     * @param response respuesta con resultados
     * @return UUID del registro creado
     * @throws DiscountCalculationException si hay error al escribir
     */
    @Transactional
    public UUID writeLog(
        DiscountCalculateRequestV2 request,
        DiscountCalculateResponseV2 response
    ) {
        try {
            // Validar que external_order_id no existe (evitar duplicados)
            if (transactionLogRepository.existsByEcommerceIdAndExternalOrderId(
                request.ecommerceId(), request.externalOrderId())) {
                throw new DiscountCalculationException(
                    "Duplicate external_order_id: " + request.externalOrderId()
                );
            }

            // Validar integridad financiera
            validateFinancialIntegrity(response);

            // Serializar reglas aplicadas
            Object appliedRulesJson = serializeAppliedRules(response.appliedRules());

            // Serializar métricas del cliente (sin PII)
            Object clientMetricsJson = serializeClientMetrics(request);

            // Construir entidad
            TransactionLogEntity logEntity = TransactionLogEntity.builder()
                .ecommerceId(request.ecommerceId())
                .externalOrderId(request.externalOrderId())
                .subtotalAmount(response.subtotalAmount())
                .discountCalculated(response.discountCalculated())
                .discountApplied(response.discountApplied())
                .finalAmount(response.finalAmount())
                .wasCapped(response.wasCapped())
                .capReason(response.capReason())
                .appliedRulesJson(objectMapper.convertValue(appliedRulesJson, com.fasterxml.jackson.databind.JsonNode.class))
                .customerTier(response.customerTier())
                .clientMetricsJson(clientMetricsJson != null ? objectMapper.convertValue(clientMetricsJson, com.fasterxml.jackson.databind.JsonNode.class) : null)
                .status("SUCCESS")
                .calculatedAt(response.calculatedAt() != null ? response.calculatedAt() : Instant.now())
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(7 * 86400))  // 7 días
                .build();

            // Guardar
            TransactionLogEntity saved = transactionLogRepository.save(logEntity);
            
            log.info("Transaction log written: ecommerce={}, external_order_id={}, transaction_id={}", 
                request.ecommerceId(), request.externalOrderId(), saved.getId());

            return saved.getId();

        } catch (Exception e) {
            log.error("Error writing transaction log: ecommerce={}, external_order_id={}, error={}",
                request.ecommerceId(), request.externalOrderId(), e.getMessage(), e);
            throw new DiscountCalculationException("Failed to write transaction log: " + e.getMessage(), e);
        }
    }

    /**
     * Valida integridad de ecuación financiera
     */
    private void validateFinancialIntegrity(DiscountCalculateResponseV2 response) {
        // final_amount debe ser = subtotal - discount_applied
        // Con tolerancia de redondeo (0.05)
        var expected = response.subtotalAmount().subtract(response.discountApplied());
        var tolerance = java.math.BigDecimal.valueOf(0.05);
        
        if (response.finalAmount().subtract(expected).abs().compareTo(tolerance) > 0) {
            throw new DiscountCalculationException(
                String.format(
                    "Financial integrity error: final_amount=%s, expected=%s",
                    response.finalAmount(), expected
                )
            );
        }
    }

    /**
     * Serializa lista de reglas aplicadas a estructura JSONB
     */
    private Object serializeAppliedRules(List<AppliedRuleDetail> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }

        return rules.stream()
            .map(rule -> {
                Map<String, Object> map = new HashMap<>();
                map.put("rule_id", rule.ruleId().toString());
                map.put("rule_name", rule.ruleName());
                map.put("discount_type_code", rule.discountTypeCode());
                map.put("discount_type", rule.discountType());
                map.put("applied_with", rule.appliedWith());
                map.put("discount_percentage", rule.discountPercentage() != null ? rule.discountPercentage().toPlainString() : "0");
                map.put("discount_amount", rule.discountAmount().toPlainString());
                map.put("priority_level", rule.priorityLevel());
                return map;
            })
            .toList();
    }

    /**
     * Serializa métricas del cliente sin datos personales (PII)
     */
    private Object serializeClientMetrics(DiscountCalculateRequestV2 request) {
        return Map.of(
            "total_spent", request.totalSpent(),
            "order_count", request.orderCount(),
            "membership_days", request.membershipDays()
            // Nota: customer_id, external_order_id no se incluyen por privacidad
        );
    }
}
