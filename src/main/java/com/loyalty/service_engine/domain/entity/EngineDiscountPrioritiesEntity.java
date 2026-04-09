package com.loyalty.service_engine.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Engine Service replica of discount priorities from Admin Service.
 * Maps discount types to their evaluation priority levels.
 *
 * SPEC-010: Unified rules design - all rule types (FIDELITY, SEASONAL, PRODUCT, CLASSIFICATION)
 * use priority levels defined in this table.
 *
 * Table: engine_discount_priorities
 * Source: Admin discount_priorities (synced via events)
 * Usage: Engine Service resolves priority_level for each rule type per ecommerce
 */
@Entity
@Table(name = "engine_discount_priorities", indexes = {
    @Index(name = "idx_discount_priorities_ecommerce", columnList = "ecommerce_id"),
    @Index(name = "idx_discount_priorities_type", columnList = "discount_type_code"),
    @Index(name = "idx_discount_priorities_level", columnList = "ecommerce_id, priority_level")
},
uniqueConstraints = {
    @UniqueConstraint(name = "uk_ecommerce_discount_type", columnNames = {"ecommerce_id", "discount_type_code"}),
    @UniqueConstraint(name = "uk_ecommerce_priority_level", columnNames = {"ecommerce_id", "priority_level"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EngineDiscountPrioritiesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ecommerce_id", nullable = false)
    private UUID ecommerceId;

    @Column(name = "discount_type_code", nullable = false, length = 50)
    private String discountTypeCode;  // FIDELITY, SEASONAL, PRODUCT, CLASSIFICATION

    @Column(name = "priority_level", nullable = false)
    private Integer priorityLevel;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (syncedAt == null) syncedAt = Instant.now();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
