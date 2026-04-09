package com.loyalty.service_engine.domain.repository;

import com.loyalty.service_engine.domain.entity.ProductRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ProductRuleEntity in Service-Engine
 * 
 * Used for:
 * - Cold start: loading all active rules into Caffeine cache
 * - Fallback: if cache miss, query database
 * - Event sync: updating/deleting replicated rules
 */
@Repository
public interface ProductRuleRepository extends JpaRepository<ProductRuleEntity, UUID> {

    /**
     * Find all active product rules for an ecommerce
     * Used during cold start and cache fallback
     */
    List<ProductRuleEntity> findByEcommerceIdAndIsActiveTrue(UUID ecommerceId);

    /**
     * Find a specific rule by product type and ecommerce (active only)
     * Used during cache lookups
     */
    Optional<ProductRuleEntity> findByEcommerceIdAndProductTypeAndIsActiveTrue(UUID ecommerceId, String productType);

    /**
     * Find a specific rule by UID
     * Used during updates
     */
    Optional<ProductRuleEntity> findByUid(UUID uid);

    /**
     * Find all active rules (across all ecommerces)
     * Used during cold start to populate entire cache
     */
    List<ProductRuleEntity> findByIsActiveTrue();
}
