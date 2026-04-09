package com.loyalty.service_engine.application.service;

import com.loyalty.service_engine.application.dto.AppliedRuleDetail;
import com.loyalty.service_engine.application.dto.ClassifyRequestV1;
import com.loyalty.service_engine.application.dto.ClassificationResult;
import com.loyalty.service_engine.application.dto.ClassificationRuleDTO;
import com.loyalty.service_engine.application.dto.DiscountCalculateRequestV2;
import com.loyalty.service_engine.application.dto.DiscountCalculateResponseV2;
import com.loyalty.service_engine.domain.entity.EngineDiscountSettingsEntity;
import com.loyalty.service_engine.domain.repository.EngineDiscountSettingsRepository;
import com.loyalty.service_engine.infrastructure.exception.InvalidCartException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio de cálculo de descuentos con clasificación automática de fidelidad.
 * Integra el ClassificationEngine para asignar un tier de fidelidad durante el cálculo.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DiscountCalculationServiceV2 {

    private final FidelityClassificationService fidelityClassificationService;
    private final ClassificationMatrixCaffeineCacheService cacheService;
    private final DiscountCappingEngine discountCappingEngine;
    private final TransactionLogWriter transactionLogWriter;
    private final EngineDiscountSettingsRepository engineDiscountSettingsRepository;

    @Transactional
    public DiscountCalculateResponseV2 calculate(DiscountCalculateRequestV2 request) {
        log.info("Starting cart calculation: ecommerceId={}, externalOrderId={}",
            request.ecommerceId(), request.externalOrderId());

        validateRequest(request);
        BigDecimal subtotal = calculateSubtotal(request);

        String customerTier = classifyCustomer(request);
        
        // Obtener reglas de clasificación activas desde cache
        List<AppliedRuleDetail> evaluatedRules = cacheService.getRules(request.ecommerceId())
            .orElse(List.of())
            .stream()
            .filter(ClassificationRuleDTO::isActive)
            .map(rule -> new AppliedRuleDetail(
                rule.id(),
                rule.name(),
                rule.discountTypeCode(),
                rule.discountType(),
                rule.appliedWith(),
                null,
                calculateRuleDiscountFromDTO(rule, subtotal),
                rule.priorityLevel()
            ))
            .collect(Collectors.toList());

        List<AppliedRuleDetail> selectedRules = applyStackingPolicy(request.ecommerceId(), evaluatedRules);
        BigDecimal discountCalculated = selectedRules.stream()
            .map(AppliedRuleDetail::discountAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal maxCap = resolveMaxDiscountCap(request.ecommerceId());
        DiscountCappingEngine.CapResult capResult = discountCappingEngine.applyCap(discountCalculated, maxCap);

        BigDecimal discountApplied = capResult.appliedDiscount().min(subtotal);
        boolean wasCapped = capResult.wasCapped() || capResult.appliedDiscount().compareTo(subtotal) > 0;
        String capReason = capResult.appliedDiscount().compareTo(subtotal) > 0
            ? "subtotal_limit"
            : capResult.capReason();

        BigDecimal discountCalculatedRounded = applyRounding(discountCalculated);
        BigDecimal discountAppliedRounded = applyRounding(discountApplied);
        BigDecimal finalAmount = applyRounding(subtotal.subtract(discountAppliedRounded));

        DiscountCalculateResponseV2 draft = new DiscountCalculateResponseV2(
            subtotal,
            discountCalculatedRounded,
            discountAppliedRounded,
            finalAmount,
            customerTier,
            wasCapped,
            capReason,
            selectedRules,
            null,
            Instant.now()
        );

        UUID transactionId = transactionLogWriter.writeLog(request, draft);
        return new DiscountCalculateResponseV2(
            draft.subtotalAmount(),
            draft.discountCalculated(),
            draft.discountApplied(),
            draft.finalAmount(),
            draft.customerTier(),
            draft.wasCapped(),
            draft.capReason(),
            draft.appliedRules(),
            transactionId,
            draft.calculatedAt()
        );
    }

    /**
     * Clasifica cliente usando el servicio determinístico por ecommerce.
     */
    private String classifyCustomer(DiscountCalculateRequestV2 request) {
        try {
            ClassifyRequestV1 classificationRequest = new ClassifyRequestV1(
                request.totalSpent(),
                request.orderCount(),
                request.membershipDays(),
                null
            );
            ClassificationResult classification = fidelityClassificationService.classify(
                request.ecommerceId(),
                classificationRequest
            );
            return classification.getTierName().orElse("UNCLASSIFIED");
        } catch (Exception e) {
            log.warn("Customer classification unavailable for ecommerceId={}. Fallback tier used.",
                request.ecommerceId(), e);
            return "UNCLASSIFIED";
        }
    }

    private void validateRequest(DiscountCalculateRequestV2 request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new InvalidCartException("Carrito sin items");
        }
        for (var item : request.items()) {
            if (item.quantity() == null || item.quantity() <= 0) {
                throw new InvalidCartException("Carrito con quantity invalida");
            }
            if (item.unitPrice() == null || item.unitPrice().signum() < 0) {
                throw new InvalidCartException("Carrito con unit_price invalido");
            }
        }
    }

    private BigDecimal calculateSubtotal(DiscountCalculateRequestV2 request) {
        BigDecimal subtotal = request.items().stream()
            .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return subtotal.setScale(4, RoundingMode.HALF_UP);
    }

    private List<AppliedRuleDetail> applyStackingPolicy(UUID ecommerceId, List<AppliedRuleDetail> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }

        List<AppliedRuleDetail> sortedRules = rules.stream()
            .sorted(Comparator.comparing(AppliedRuleDetail::priorityLevel, Comparator.nullsLast(Integer::compareTo)))
            .collect(Collectors.toCollection(ArrayList::new));

        List<AppliedRuleDetail> exclusiveRules = sortedRules.stream()
            .filter(rule -> "EXCLUSIVE".equals(normalize(rule.appliedWith())))
            .toList();

        if (!exclusiveRules.isEmpty()) {
            AppliedRuleDetail highestExclusive = exclusiveRules.stream()
                .max(Comparator.comparing(AppliedRuleDetail::discountAmount))
                .orElse(exclusiveRules.get(0));
            return List.of(highestExclusive);
        }

        boolean allowStacking = resolveAllowStacking(ecommerceId);
        if (!allowStacking) {
            AppliedRuleDetail highestRule = sortedRules.stream()
                .max(Comparator.comparing(AppliedRuleDetail::discountAmount))
                .orElse(sortedRules.get(0));
            return List.of(highestRule);
        }

        return sortedRules;
    }

    private BigDecimal calculateRuleDiscountFromDTO(ClassificationRuleDTO rule, BigDecimal subtotal) {
        if (rule == null || rule.discountValue() == null) {
            return BigDecimal.ZERO;
        }
        if ("PERCENTAGE".equals(rule.discountType())) {
            return subtotal.multiply(rule.discountValue()).divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
        }
        return rule.discountValue().min(subtotal);
    }

    private BigDecimal resolveMaxDiscountCap(UUID ecommerceId) {
        return engineDiscountSettingsRepository.findByEcommerceIdAndIsActiveTrue(ecommerceId)
            .map(EngineDiscountSettingsEntity::getMaxDiscountLimit)
            .filter(limit -> limit.signum() > 0)
            .orElse(null);
    }

    private boolean resolveAllowStacking(UUID ecommerceId) {
        // Mientras allow_stacking no esté replicado en entidad, se usa default TRUE.
        return true;
    }

    private BigDecimal applyRounding(BigDecimal value) {
        return discountCappingEngine.applyRounding(value, DiscountCappingEngine.RoundingRule.ROUND_HALF_UP);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
