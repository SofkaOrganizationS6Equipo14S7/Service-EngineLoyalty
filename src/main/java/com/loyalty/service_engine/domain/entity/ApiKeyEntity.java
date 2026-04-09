package com.loyalty.service_engine.domain.entity;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "engine_api_keys")
public class ApiKeyEntity implements Persistable<String> {
    
    @Id
    @Column(name = "hashed_key")
    private String hashedKey;
    
    @Column(name = "ecommerce_id", nullable = false)
    private UUID ecommerceId;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    
    @Column(name = "synced_at")
    private Instant syncedAt;
    
    @Transient
    private boolean isNew = true;

    public ApiKeyEntity() {}

    @Override
    public String getId() { return hashedKey; }

    @Override
    public boolean isNew() { return isNew; }

    @PostLoad
    @PostPersist
    void markNotNew() { this.isNew = false; }
    
    public String getHashedKey() { return hashedKey; }
    public void setHashedKey(String hashedKey) { this.hashedKey = hashedKey; }
    public UUID getEcommerceId() { return ecommerceId; }
    public void setEcommerceId(UUID ecommerceId) { this.ecommerceId = ecommerceId; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getSyncedAt() { return syncedAt; }
    public void setSyncedAt(Instant syncedAt) { this.syncedAt = syncedAt; }
    
    @PrePersist
    protected void onCreate() {
        if (syncedAt == null) {
            syncedAt = Instant.now();
        }
    }
}