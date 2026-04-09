package com.loyalty.service_engine.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_rules", indexes = {
    @Index(name = "idx_product_rules_ecommerce_id", columnList = "ecommerce_id"),
    @Index(name = "idx_product_rules_product_type", columnList = "product_type"),
    @Index(name = "idx_product_rules_active", columnList = "is_active"),
    @Index(name = "idx_product_rules_ecommerce_active", columnList = "ecommerce_id, is_active")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uq_product_rules_ecommerce_product_active",
            columnNames = {"ecommerce_id", "product_type", "is_active"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductRuleEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uid;
    
    @Column(name = "ecommerce_id", nullable = false)
    private UUID ecommerceId;
    
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    
    @Column(name = "product_type", nullable = false, length = 100)
    private String productType;
    
    @Column(name = "discount_percentage", nullable = false)
    private BigDecimal discountPercentage;
    
    @Column(name = "benefit", length = 255)
    private String benefit;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
