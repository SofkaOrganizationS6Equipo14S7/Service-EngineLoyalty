package com.loyalty.service_engine.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response del cálculo de descuentos respetando prioridad y límite máximo.
 * Incluye clasificación de fidelidad del cliente.
 * 
 * @param transactionId ID único de la transacción
 * @param originalDiscounts Descuentos originales identificados
 * @param appliedDiscounts Descuentos finales después de aplicar límite
 * @param totalOriginal Suma total de descuentos antes de límite
 * @param totalApplied Suma total de descuentos después de límite
 * @param maxDiscountLimit Límite máximo configurado
 * @param limitExceeded Indica si el total original superaba el límite
 * @param fidelityClassification Clasificación de fidelidad del cliente (puede ser null si NONE)
 * @param calculatedAt Timestamp del cálculo (UTC)
 */
public record DiscountCalculateResponse(
    String transactionId,
    List<DiscountItem> originalDiscounts,
    List<DiscountItem> appliedDiscounts,
    BigDecimal totalOriginal,
    BigDecimal totalApplied,
    BigDecimal maxDiscountLimit,
    Boolean limitExceeded,
    FidelityClassification fidelityClassification,
    Instant calculatedAt
) {
    /**
     * Representa un descuento individual.
     * @param discountType Tipo de descuento
     * @param amount Monto del descuento
     */
    public record DiscountItem(
        String discountType,
        BigDecimal amount
    ) {}

    /**
     * Clasificación de fidelidad asignada al cliente
     * @param clientPoints Puntos de fidelidad acumulados
     * @param fidelityLevelUid UID del nivel de fidelidad (null si NONE)
     * @param fidelityLevelName Nombre del nivel de fidelidad (e.g., "Bronce", "Plata")
     * @param isClassified true si cliente está clasificado a un nivel, false si NONE
     * @param discountPercentage Descuento aplicable por fidelidad (null si NONE)
     */
    public record FidelityClassification(
        Integer clientPoints,
        UUID fidelityLevelUid,
        String fidelityLevelName,
        Boolean isClassified,
        BigDecimal discountPercentage
    ) {}
}

