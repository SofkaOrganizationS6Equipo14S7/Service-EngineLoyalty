package com.loyalty.service_engine.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only replica of customer tiers from service-admin (engine_customer_tiers table).
 * Used for Cold Start autonomy and classification in-memory cache.
 * Synchronized via RabbitMQ events "customer-tiers.updated".
 */
@Entity
@Table(name = "engine_customer_tiers", indexes = {
    @Index(name = "idx_customer_tiers_ecommerce", columnList = "ecommerce_id"),
    @Index(name = "idx_customer_tiers_active", columnList = "is_active"),
    @Index(name = "idx_customer_tiers_hierarchy", columnList = "ecommerce_id, hierarchy_level")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerTierReplicaEntity {

    @Id
    private UUID id;

    @Column(name = "ecommerce_id", nullable = false)
    private UUID ecommerceId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "discount_percentage", nullable = false)
    private BigDecimal discountPercentage;

    @Column(name = "hierarchy_level", nullable = false)
    private Integer hierarchyLevel;

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
