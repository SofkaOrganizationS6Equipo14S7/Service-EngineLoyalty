package com.loyalty.service_engine.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO para response de cálculo de descuentos
 * Contiene breakdown completo de subtotal, descuentos, caps y reglas aplicadas
 * 
 * @param subtotalAmount Monto subtotal del carrito (suma de quantity * unitPrice)
 * @param discountCalculated Descuento calculado antes de aplicar cap
 * @param discountApplied Descuento final después de aplicar cap
 * @param finalAmount Monto final (subtotal - discountApplied)
 * @param customerTier Tier de clasificación del cliente (ej. "Gold")
 * @param wasCapped true si se limitó descuento por max_discount_cap
 * @param capReason Razón del cap ("max_discount_cap" o null)
 * @param appliedRules Desglose de reglas aplicadas
 * @param transactionId ID del registro en transaction_logs para auditoría
 * @param calculatedAt Timestamp del cálculo
 */
public record DiscountCalculateResponseV2(
    BigDecimal subtotalAmount,
    BigDecimal discountCalculated,
    BigDecimal discountApplied,
    BigDecimal finalAmount,
    String customerTier,
    Boolean wasCapped,
    String capReason,
    List<AppliedRuleDetail> appliedRules,
    UUID transactionId,
    Instant calculatedAt
) {
}
