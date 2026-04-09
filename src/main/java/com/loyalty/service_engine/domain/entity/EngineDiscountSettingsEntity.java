package com.loyalty.service_engine.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "engine_discount_settings")
public class EngineDiscountSettingsEntity {

    @Id
    @Column(name = "ecommerce_id")
    private UUID ecommerceId;

    @Column(name = "max_discount_cap", nullable = false, precision = 12, scale = 4)
    private BigDecimal maxDiscountCap;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "allow_stacking", nullable = false)
    private Boolean allowStacking = true;

    @Column(name = "rounding_rule", nullable = false, length = 20)
    private String roundingRule = "ROUND_HALF_UP";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public EngineDiscountSettingsEntity() {}

    public UUID getEcommerceId() { return ecommerceId; }
    public void setEcommerceId(UUID ecommerceId) { this.ecommerceId = ecommerceId; }
    public BigDecimal getMaxDiscountCap() { return maxDiscountCap; }
    public void setMaxDiscountCap(BigDecimal maxDiscountCap) { this.maxDiscountCap = maxDiscountCap; }
    public BigDecimal getMaxDiscountLimit() { return maxDiscountCap; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public Boolean getAllowStacking() { return allowStacking; }
    public void setAllowStacking(Boolean allowStacking) { this.allowStacking = allowStacking; }
    public String getRoundingRule() { return roundingRule; }
    public void setRoundingRule(String roundingRule) { this.roundingRule = roundingRule; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public Instant getSyncedAt() { return syncedAt; }
    public void setSyncedAt(Instant syncedAt) { this.syncedAt = syncedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}