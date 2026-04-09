package com.loyalty.service_engine.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTO para request de cálculo de descuentos
 * Contiene: carrito (items), métricas del cliente para clasificación y datos de auditoría
 * 
 * @param ecommerceId Identificador del e-commerce (multi-tenant)
 * @param externalOrderId ID del pedido proporcionado por el e-commerce (para auditoría y deduplicación)
 * @param customerId ID del cliente en sistema externo (no se almacena en logs)
 * @param totalSpent Gasto histórico acumulado del cliente (para clasificación HU-10)
 * @param orderCount Cantidad histórica de pedidos del cliente (para clasificación)
 * @param membershipDays Días desde que el cliente es miembro (para clasificación)
 * @param items Array de items en el carrito (min 1)
 */
public record DiscountCalculateRequestV2(
    @NotNull(message = "ecommerce_id is required")
    UUID ecommerceId,

    @NotBlank(message = "external_order_id is required")
    @Size(max = 255, message = "external_order_id max 255 chars")
    String externalOrderId,

    @NotBlank(message = "customer_id is required")
    @Size(max = 100, message = "customer_id max 100 chars")
    String customerId,

    @NotNull(message = "total_spent is required")
    @DecimalMin(value = "0", inclusive = true, message = "total_spent must be >= 0")
    BigDecimal totalSpent,

    @NotNull(message = "order_count is required")
    @Min(value = 0, message = "order_count must be >= 0")
    Integer orderCount,

    @NotNull(message = "membership_days is required")
    @Min(value = 0, message = "membership_days must be >= 0")
    Integer membershipDays,

    @NotEmpty(message = "items array is required (min 1)")
    @Size(min = 1, message = "Carrito debe contener al menos 1 item")
    List<@Valid CartItemRequest> items
) {
}
