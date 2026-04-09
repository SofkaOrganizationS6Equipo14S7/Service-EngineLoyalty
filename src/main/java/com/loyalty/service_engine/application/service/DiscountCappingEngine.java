package com.loyalty.service_engine.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Motor de aplicación de topes (caps) y redondeo de moneda.
 * 
 * Responsabilidades:
 * 1. Aplicar tope máximo de descuento (max_discount_cap)
 * 2. Redondear a 2 decimales según regla configurada (ROUND_HALF_UP, FLOOR, CEIL)
 * 3. Retornar información sobre si fue capeado y razón
 */
@Service
@Slf4j
public class DiscountCappingEngine {

    /**
     * Aplica tope máximo a descuento calculado
     * 
     * @param discountCalculated descuento antes de aplicar cap
     * @param maxCap máximo permitido (null = sin límite)
     * @return mapa con { applied, was_capped, cap_reason }
     */
    public CapResult applyCap(BigDecimal discountCalculated, BigDecimal maxCap) {
        if (maxCap == null || maxCap.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("No cap limit configured, applying full discount: {}", discountCalculated);
            return new CapResult(discountCalculated, false, null);
        }

        if (discountCalculated.compareTo(maxCap) <= 0) {
            log.debug("Discount within cap limit: discount={}, cap={}", discountCalculated, maxCap);
            return new CapResult(discountCalculated, false, null);
        }

        log.info("Discount exceeds cap: calculated={}, cap={}, applied cap", discountCalculated, maxCap);
        return new CapResult(maxCap, true, "max_discount_cap");
    }

    /**
     * Redondea monto monetario según regla configurada
     * Se asume 2 decimales (moneda estándar: USD, COP, etc.)
     * 
     * @param amount monto a redondear
     * @param roundingRule estrategia: ROUND_HALF_UP (default), FLOOR, CEIL
     * @return monto redondeado
     */
    public BigDecimal applyRounding(BigDecimal amount, RoundingRule roundingRule) {
        if (amount == null) {
            return null;
        }

        RoundingMode mode = getRoundingMode(roundingRule);
        BigDecimal rounded = amount.setScale(2, mode);
        
        log.debug("Applied rounding: original={}, rule={}, rounded={}", amount, roundingRule, rounded);
        return rounded;
    }

    /**
     * Mapea RoundingRule enum a RoundingMode de Java
     */
    private RoundingMode getRoundingMode(RoundingRule rule) {
        if (rule == null) {
            rule = RoundingRule.ROUND_HALF_UP;  // Default
        }

        return switch (rule) {
            case ROUND_HALF_UP -> RoundingMode.HALF_UP;
            case FLOOR -> RoundingMode.FLOOR;
            case CEIL -> RoundingMode.CEILING;
            default -> RoundingMode.HALF_UP;
        };
    }

    /**
     * Resultado de aplicación de cap
     */
    public record CapResult(
        BigDecimal appliedDiscount,
        boolean wasCapped,
        String capReason  // null si no fue capeado
    ) {}

    /**
     * Enum de estrategias de redondeo soportadas
     */
    public enum RoundingRule {
        ROUND_HALF_UP,  // 0.335 → 0.34 (default)
        FLOOR,          // 0.339 → 0.33
        CEIL            // 0.331 → 0.34
    }
}
