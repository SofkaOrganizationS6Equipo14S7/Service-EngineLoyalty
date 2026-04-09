package com.loyalty.service_engine.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only replica of classification rules from service-admin (engine_rules table).
 * Stores dynamic evaluation criteria in JSONB logic_conditions for classification.
 * Used for Cold Start autonomy and in-memory cache.
 * Synchronized via RabbitMQ events "classification-rules.updated".
 */
@Entity
@Table(name = "engine_rules", indexes = {
    @Index(name = "idx_engine_rules_ecommerce", columnList = "ecommerce_id"),
    @Index(name = "idx_engine_rules_type", columnList = "discount_type_code"),
    @Index(name = "idx_engine_rules_priority", columnList = "ecommerce_id, priority_level"),
    @Index(name = "idx_engine_rules_active", columnList = "is_active"),
    @Index(name = "idx_engine_rules_ecommerce_active", columnList = "ecommerce_id, is_active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassificationRuleReplicaEntity {

    @Id
    private UUID id;

    @Column(name = "ecommerce_id", nullable = false)
    private UUID ecommerceId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "discount_type_code", nullable = false, length = 50)
    private String discountTypeCode; // e.g., "CLASSIFICATION"

    @Column(name = "discount_type", nullable = false, length = 50)
    private String discountType; // PERCENTAGE | FIXED_AMOUNT

    @Column(name = "discount_value", nullable = false)
    private BigDecimal discountValue;

    @Column(name = "applied_with", nullable = false, length = 50)
    private String appliedWith; // INDIVIDUAL | CUMULATIVE | EXCLUSIVE

    @Column(name = "logic_conditions", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> logicConditions; // JSONB: {min_spent: {type: "NUMERIC", value: 2000}, ...}

    @Column(name = "priority_level", nullable = false)
    private Integer priorityLevel;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (syncedAt == null) {
            syncedAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        syncedAt = Instant.now();
    }
}
