package com.loyalty.service_engine.application.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * DTO para un item de carrito
 * 
 * @param productId ID del producto
 * @param quantity Cantidad de unidades
 * @param unitPrice Precio unitario
 * @param category Categoría del producto (opcional, para criterios de descuento)
 */
public record CartItemRequest(
    @NotBlank(message = "product_id is required")
    @Size(max = 100, message = "product_id max 100 chars")
    String productId,

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be >= 1")
    Integer quantity,

    @NotNull(message = "unit_price is required")
    @DecimalMin(value = "0", inclusive = true, message = "unit_price must be >= 0")
    BigDecimal unitPrice,

    @Size(max = 100, message = "category max 100 chars")
    String category  // opcional
) {
}
