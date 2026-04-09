package com.loyalty.service_engine.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO para detalle de regla aplicada en el desglose de descuentos
 * 
 * @param ruleId UUID de la regla en engine_rules
 * @param ruleName Nombre descriptivo de la regla
 * @param discountTypeCode Código del tipo de descuento (FIDELITY, SEASONAL, etc.)
 * @param discountType Tipo de descuento (PERCENTAGE o FIXED_AMOUNT)
 * @param appliedWith Modo de aplicación (INDIVIDUAL, CUMULATIVE, EXCLUSIVE)
 * @param discountPercentage Porcentaje de descuento (si es PERCENTAGE)
 * @param discountAmount Monto de descuento aplicado
 * @param priorityLevel Nivel de prioridad de la regla
 */
public record AppliedRuleDetail(
    UUID ruleId,
    String ruleName,
    String discountTypeCode,
    String discountType,
    String appliedWith,
    BigDecimal discountPercentage,
    BigDecimal discountAmount,
    Integer priorityLevel
) {
}
