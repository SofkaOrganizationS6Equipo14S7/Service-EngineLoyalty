package com.loyalty.service_engine.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.loyalty.service_engine.application.dto.DiscountCalculateRequestV2;
import com.loyalty.service_engine.infrastructure.exception.RuleEvaluationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Evaluador de criterios lógicos JSONB para reglas de descuento.
 * 
 * Soporta evaluación de:
 * - Criterios numéricos: min_spent, max_spent, subtotal
 * - Criterios enteros: min_order_count, min_membership_days
 * - Criterios de array: categories (verificar membresía)
 * - Criterios de tier: customer_tier
 * - Lógica de evaluación combinada: AND/OR (simple)
 * 
 * Ejemplo JSONB:
 * {
 *   "min_spent": { "type": "NUMERIC", "value": 1000.00 },
 *   "min_order_count": { "type": "INTEGER", "value": 5 },
 *   "categories": { "type": "ARRAY", "values": ["electronics", "books"] },
 *   "evaluation_logic": "min_spent AND min_order_count"
 * }
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LogicConditionEvaluator {

    private final ObjectMapper objectMapper;

    /**
     * Evalúa si un cliente y carrito cumplen con todas las condiciones de una regla
     * 
     * @param logicConditions estructura con criterios (puede ser Map o JsonNode)
     * @param request datos del carrito y cliente
     * @param subtotal monto subtotal
     * @param customerTier tier de clasificación
     * @return true si todas las condiciones se cumplen
     */
    public boolean evaluateCondition(
        Object logicConditions,
        DiscountCalculateRequestV2 request,
        BigDecimal subtotal,
        String customerTier
    ) {
        // Convertir Map a JsonNode si es necesario
        JsonNode conditionsNode;
        if (logicConditions instanceof Map) {
            conditionsNode = objectMapper.convertValue(logicConditions, JsonNode.class);
        } else if (logicConditions instanceof JsonNode) {
            conditionsNode = (JsonNode) logicConditions;
        } else if (logicConditions == null) {
            conditionsNode = null;
        } else {
            log.debug("Unknown logic_conditions type, applying rule to all");
            return true;
        }

        return evaluateJsonNode(conditionsNode, request, subtotal, customerTier);
    }

    /**
     * Evalúa condiciones desde JsonNode
     */
    private boolean evaluateJsonNode(
        JsonNode logicConditions,
        DiscountCalculateRequestV2 request,
        BigDecimal subtotal,
        String customerTier
    ) {
        if (logicConditions == null || logicConditions.isNull()) {
            log.debug("No logic_conditions provided, rule applies to all");
            return true;
        }

        try {
            // Evaluar cada criterio disponible
            boolean minSpentOk = evaluateMinSpent(logicConditions, request.totalSpent());
            boolean maxSpentOk = evaluateMaxSpent(logicConditions, request.totalSpent());
            boolean minSubtotalOk = evaluateMinSubtotal(logicConditions, subtotal);
            boolean maxSubtotalOk = evaluateMaxSubtotal(logicConditions, subtotal);
            boolean minOrderCountOk = evaluateMinOrderCount(logicConditions, request.orderCount());
            boolean minMembershipDaysOk = evaluateMinMembershipDays(logicConditions, request.membershipDays());
            boolean categoriesOk = evaluateCategories(logicConditions, request);
            boolean tierOk = evaluateCustomerTier(logicConditions, customerTier);

            // Checks basadas en evaluation_logic o AND por defecto
            String evaluationLogic = logicConditions.has("evaluation_logic")
                ? logicConditions.get("evaluation_logic").asText("")
                : "";

            if (!evaluationLogic.isEmpty()) {
                return evaluateCustomLogic(evaluationLogic,
                    minSpentOk, maxSpentOk, minSubtotalOk, maxSubtotalOk,
                    minOrderCountOk, minMembershipDaysOk, categoriesOk, tierOk);
            } else {
                // Por defecto: AND lógico - todos deben pasar
                return minSpentOk && maxSpentOk && minSubtotalOk && maxSubtotalOk &&
                       minOrderCountOk && minMembershipDaysOk && categoriesOk && tierOk;
            }
        } catch (Exception e) {
            log.error("Error evaluating rule conditions: {}", logicConditions, e);
            throw new RuleEvaluationException("Failed to evaluate rule conditions: " + e.getMessage(), e);
        }
    }

    /**
     * Evalúa criterio: min_spent (gasto histórico mínimo del cliente)
     */
    private boolean evaluateMinSpent(JsonNode logicConditions, BigDecimal totalSpent) {
        if (!logicConditions.has("min_spent") || logicConditions.get("min_spent").isNull()) {
            return true;
        }

        BigDecimal minValue = new BigDecimal(logicConditions.get("min_spent").asText("0"));
        boolean result = totalSpent.compareTo(minValue) >= 0;
        log.debug("min_spent check: totalSpent={} >= minValue={} -> {}", totalSpent, minValue, result);
        return result;
    }

    /**
     * Evalúa criterio: max_spent (gasto histórico máximo del cliente)
     */
    private boolean evaluateMaxSpent(JsonNode logicConditions, BigDecimal totalSpent) {
        if (!logicConditions.has("max_spent") || logicConditions.get("max_spent").isNull()) {
            return true;
        }

        BigDecimal maxValue = new BigDecimal(logicConditions.get("max_spent").asText());
        boolean result = totalSpent.compareTo(maxValue) <= 0;
        log.debug("max_spent check: totalSpent={} <= maxValue={} -> {}", totalSpent, maxValue, result);
        return result;
    }

    /**
     * Evalúa criterio: min_subtotal (monto mínimo del carrito actual)
     */
    private boolean evaluateMinSubtotal(JsonNode logicConditions, BigDecimal subtotal) {
        if (!logicConditions.has("min_subtotal") || logicConditions.get("min_subtotal").isNull()) {
            return true;
        }

        BigDecimal minValue = new BigDecimal(logicConditions.get("min_subtotal").asText("0"));
        boolean result = subtotal.compareTo(minValue) >= 0;
        log.debug("min_subtotal check: subtotal={} >= minValue={} -> {}", subtotal, minValue, result);
        return result;
    }

    /**
     * Evalúa criterio: max_subtotal (monto máximo del carrito actual)
     */
    private boolean evaluateMaxSubtotal(JsonNode logicConditions, BigDecimal subtotal) {
        if (!logicConditions.has("max_subtotal") || logicConditions.get("max_subtotal").isNull()) {
            return true;
        }

        BigDecimal maxValue = new BigDecimal(logicConditions.get("max_subtotal").asText());
        boolean result = subtotal.compareTo(maxValue) <= 0;
        log.debug("max_subtotal check: subtotal={} <= maxValue={} -> {}", subtotal, maxValue, result);
        return result;
    }

    /**
     * Evalúa criterio: min_order_count (cantidad mínima de órdenes históricas)
     */
    private boolean evaluateMinOrderCount(JsonNode logicConditions, Integer orderCount) {
        if (!logicConditions.has("min_order_count") || logicConditions.get("min_order_count").isNull()) {
            return true;
        }

        Integer minValue = logicConditions.get("min_order_count").asInt(0);
        boolean result = orderCount >= minValue;
        log.debug("min_order_count check: orderCount={} >= minValue={} -> {}", orderCount, minValue, result);
        return result;
    }

    /**
     * Evalúa criterio: min_membership_days (días mínimos de membresía)
     */
    private boolean evaluateMinMembershipDays(JsonNode logicConditions, Integer membershipDays) {
        if (!logicConditions.has("min_membership_days") || logicConditions.get("min_membership_days").isNull()) {
            return true;
        }

        Integer minValue = logicConditions.get("min_membership_days").asInt(0);
        boolean result = membershipDays >= minValue;
        log.debug("min_membership_days check: membershipDays={} >= minValue={} -> {}", membershipDays, minValue, result);
        return result;
    }

    /**
     * Evalúa criterio: categories (categorías de productos en carrito)
     * Verifica que AL MENOS UN item in el carrito esté en las categorías permitidas
     */
    private boolean evaluateCategories(JsonNode logicConditions, DiscountCalculateRequestV2 request) {
        if (!logicConditions.has("categories") || logicConditions.get("categories").isNull()) {
            return true;
        }

        ArrayNode allowedCategories = (ArrayNode) logicConditions.get("categories");
        if (allowedCategories.isEmpty()) {
            return true;
        }

        Set<String> allowed = new HashSet<>();
        allowedCategories.forEach(cat -> allowed.add(cat.asText()));

        // Verificar que al menos un item está en las categorías permitidas
        boolean result = request.items().stream()
            .filter(item -> item.category() != null && !item.category().isEmpty())
            .anyMatch(item -> allowed.contains(item.category()));

        log.debug("categories check: allowedCategories={}, hasMatchingItem={}", allowed, result);
        return result;
    }

    /**
     * Evalúa criterio: customer_tier (tier de clasificación asignado al cliente)
     */
    private boolean evaluateCustomerTier(JsonNode logicConditions, String customerTier) {
        if (!logicConditions.has("customer_tier") || logicConditions.get("customer_tier").isNull()) {
            return true;
        }

        String requiredTier = logicConditions.get("customer_tier").asText();
        boolean result = customerTier != null && customerTier.equalsIgnoreCase(requiredTier);
        log.debug("customer_tier check: customerTier={}, requiredTier={} -> {}", customerTier, requiredTier, result);
        return result;
    }

    /**
     * Evalúa lógica de evaluación personalizada (AND, OR)
     * 
     * Soporta frases simples como: "min_spent AND min_order_count OR customer_tier"
     * Evaluación left-to-right sin precedencia de operadores
     */
    private boolean evaluateCustomLogic(
        String evaluationLogic,
        boolean minSpent, boolean maxSpent,
        boolean minSubtotal, boolean maxSubtotal,
        boolean minOrderCount, boolean minMembershipDays,
        boolean categories, boolean tier
    ) {
        log.debug("Evaluating custom logic: {}", evaluationLogic);

        // Mapeo de criterios disponibles
        boolean[] results = new boolean[]{minSpent, maxSpent, minSubtotal, maxSubtotal,
                                         minOrderCount, minMembershipDays, categories, tier};
        String[] criteriaNames = {"min_spent", "max_spent", "min_subtotal", "max_subtotal",
                                  "min_order_count", "min_membership_days", "categories", "customer_tier"};

        // Parser simple left-to-right de AND/OR
        String normalized = evaluationLogic.toUpperCase().trim();
        
        // Casos simples
        if (normalized.equals("AND")) {
            return minSpent && maxSpent && minSubtotal && maxSubtotal &&
                   minOrderCount && minMembershipDays && categories && tier;
        }
        if (normalized.equals("OR")) {
            return minSpent || maxSpent || minSubtotal || maxSubtotal ||
                   minOrderCount || minMembershipDays || categories || tier;
        }

        // Por defecto: AND
        log.warn("Unknown evaluation_logic, defaulting to AND: {}", evaluationLogic);
        return minSpent && maxSpent && minSubtotal && maxSubtotal &&
               minOrderCount && minMembershipDays && categories && tier;
    }
}
