package com.loyalty.service_engine.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request para calcular descuentos aplicando prioridad y límite máximo.
 * Incluye información de cliente para clasificación de fidelidad.
 * 
 * @param transactionId ID único de la transacción
 * @param clientFidelityPoints Puntos de fidelidad acumulados del cliente (para clasificación)
 * @param ecommerceId Tenant identifier (para context)
 * @param discounts Lista de descuentos identificados para la transacción
 */
public record DiscountCalculateRequest(
    String transactionId,
    Integer clientFidelityPoints,
    UUID ecommerceId,
    List<DiscountItem> discounts
) {
    /**
     * Representa un descuento individual a aplicar.
     * @param discountType Tipo de descuento
     * @param amount Monto del descuento
     */
    public record DiscountItem(
        String discountType,
        BigDecimal amount
    ) {}
}
