package com.loyalty.service_engine.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad para auditoría de cálculos de descuentos en transacciones.
 * Registra cada cálculo de carrito incluyendo descuentos aplicados, caps y métricas.
 * 
 * Almacenamiento:
 * - Subtotal, descuentos (calculado vs aplicado), monto final
 * - Flag de capping si se limitó el descuento
 * - Reglas aplicadas en JSONB (applied_rules_json)
 * - Métricas del cliente sin PII (client_metrics_json)
 * - Tier de clasificación del cliente
 * - Timestamps de cálculo y expiración (7 días para limpieza)
 * 
 * Propósito: Auditoría, debugging, cumplimiento normativo, análisis de descuentos
 */
@Entity
@Table(
    name = "transaction_logs",
    indexes = {
        @Index(name = "idx_transaction_logs_ecommerce_created", columnList = "ecommerce_id, created_at DESC"),
        @Index(name = "idx_transaction_logs_expires_at", columnList = "created_at, expires_at"),
        @Index(name = "idx_transaction_logs_external_order", columnList = "ecommerce_id, external_order_id", unique = true)
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionLogEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    
    /**
     * Identificador del e-commerce (multi-tenant)
     */
    @Column(name = "ecommerce_id", nullable = false)
    private UUID ecommerceId;
    
    /**
     * ID externo del pedido/orden (proporcionado por e-commerce)
     * Único por ecommerce para evitar duplicados
     */
    @Column(name = "external_order_id", nullable = false, length = 255)
    private String externalOrderId;
    
    /**
     * Monto subtotal del carrito antes de descuentos
     */
    @Column(name = "subtotal_amount", nullable = false, precision = 12, scale = 4)
    private BigDecimal subtotalAmount;
    
    /**
     * Descuento calculado (antes de aplicar cap)
     */
    @Column(name = "discount_calculated", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountCalculated;
    
    /**
     * Descuento finalmente aplicado (respeta cap)
     */
    @Column(name = "discount_applied", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountApplied;
    
    /**
     * Monto final después de descuentos (subtotal - discount_applied)
     */
    @Column(name = "final_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalAmount;
    
    /**
     * Flag: true si se aplicó max_discount_cap
     */
    @Column(name = "was_capped", nullable = false)
    private Boolean wasCapped;
    
    /**
     * Razón del capping (ej. "max_discount_cap", null si no fue capeado)
     */
    @Column(name = "cap_reason", length = 100)
    private String capReason;
    
    /**
     * Reglas aplicadas serializadas en JSONB
     * Array de { rule_id, rule_name, discount_type, discount_percentage, discount_amount, priority_level }
     */
    @Column(name = "applied_rules_json", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode appliedRulesJson;
    
    /**
     * Tier de clasificación del cliente (ej. "Gold", "Silver", "Bronze")
     */
    @Column(name = "customer_tier", length = 50)
    private String customerTier;
    
    /**
     * Métricas del cliente sin datos personales (total_spent, order_count, membership_days)
     * En JSONB para flexibilidad
     */
    @Column(name = "client_metrics_json", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode clientMetricsJson;
    
    /**
     * Estado del cálculo: SUCCESS, PARTIALLY_APPLIED, REJECTED
     */
    @Column(name = "status", nullable = false, length = 50)
    private String status;
    
    /**
     * Timestamp cuando se realizó el cálculo
     */
    @Column(name = "calculated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant calculatedAt;
    
    /**
     * Timestamp de creación del registro
     */
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;
    
    /**
     * Timestamp de expiración (created_at + 7 días) para limpieza automática
     */
    @Column(name = "expires_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant expiresAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (calculatedAt == null) {
            calculatedAt = Instant.now();
        }
        if (expiresAt == null) {
            // 7 días de retención
            expiresAt = createdAt.plusSeconds(7 * 86400);
        }
        if (status == null) {
            status = "SUCCESS";
        }
    }
}
