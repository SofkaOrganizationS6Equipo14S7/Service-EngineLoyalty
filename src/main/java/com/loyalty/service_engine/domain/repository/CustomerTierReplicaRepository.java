package com.loyalty.service_engine.domain.repository;

import com.loyalty.service_engine.domain.entity.CustomerTierReplicaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for CustomerTierReplicaEntity.
 * Read-only access to replicated tier data from service-admin (engine_customer_tiers).
 * Used for Cold Start and classification cache population.
 */
@Repository
public interface CustomerTierReplicaRepository extends JpaRepository<CustomerTierReplicaEntity, UUID> {

    /**
     * Find all active tiers for an ecommerce, ordered by hierarchy level ascending.
     */
    List<CustomerTierReplicaEntity> findByEcommerceIdAndIsActiveTrueOrderByHierarchyLevelAsc(UUID ecommerceId);

    /**
     * Find all tiers for an ecommerce, ordered by hierarchy level.
     */
    List<CustomerTierReplicaEntity> findByEcommerceIdOrderByHierarchyLevelAsc(UUID ecommerceId);

    /**
     * Find a tier by ecommerce, name, and active status.
     */
    Optional<CustomerTierReplicaEntity> findByEcommerceIdAndNameAndIsActiveTrue(UUID ecommerceId, String name);

    /**
     * Find tier by ecommerce and name (regardless of active status).
     */
    Optional<CustomerTierReplicaEntity> findByEcommerceIdAndName(UUID ecommerceId, String name);

    /**
     * Check if a tier with given name exists for ecommerce and is active.
     */
    boolean existsByEcommerceIdAndNameAndIsActiveTrue(UUID ecommerceId, String name);

    /**
     * Get the minimum hierarchy level for an ecommerce (lowest tier).
     */
    @Query("SELECT ct FROM CustomerTierReplicaEntity ct WHERE ct.ecommerceId = :ecommerceId " +
           "AND ct.isActive = true ORDER BY ct.hierarchyLevel ASC LIMIT 1")
    Optional<CustomerTierReplicaEntity> findMinimumTier(@Param("ecommerceId") UUID ecommerceId);

    /**
     * Get the maximum hierarchy level for an ecommerce (highest tier).
     */
    @Query("SELECT ct FROM CustomerTierReplicaEntity ct WHERE ct.ecommerceId = :ecommerceId " +
           "AND ct.isActive = true ORDER BY ct.hierarchyLevel DESC LIMIT 1")
    Optional<CustomerTierReplicaEntity> findMaximumTier(@Param("ecommerceId") UUID ecommerceId);
}
