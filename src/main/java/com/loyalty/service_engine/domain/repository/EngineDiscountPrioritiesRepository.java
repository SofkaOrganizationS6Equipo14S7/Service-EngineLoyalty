package com.loyalty.service_engine.domain.repository;

import com.loyalty.service_engine.domain.entity.EngineDiscountPrioritiesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for EngineDiscountPrioritiesEntity (engine_discount_priorities table).
 * 
 * Provides access to discount type priorities replicated from Admin Service.
 * Used to resolve priority levels for each rule type during discount evaluation.
 *
 * SPEC-010: Unified rules design - all rule types query priorities from this table.
 */
@Repository
public interface EngineDiscountPrioritiesRepository extends JpaRepository<EngineDiscountPrioritiesEntity, UUID> {

    /**
     * Find priority by ecommerce and discount type code (unique pair).
     */
    Optional<EngineDiscountPrioritiesEntity> findByEcommerceIdAndDiscountTypeCode(
            UUID ecommerceId, String discountTypeCode
    );

    /**
     * Find all active priorities for an ecommerce, ordered by priority level.
     */
    @Query("SELECT p FROM EngineDiscountPrioritiesEntity p " +
           "WHERE p.ecommerceId = :ecommerceId AND p.isActive = true " +
           "ORDER BY p.priorityLevel ASC")
    List<EngineDiscountPrioritiesEntity> findActiveByEcommerceId(@Param("ecommerceId") UUID ecommerceId);

    /**
     * Find all priorities for an ecommerce (active and inactive).
     */
    List<EngineDiscountPrioritiesEntity> findByEcommerceId(UUID ecommerceId);

    /**
     * Find priority by ecommerce and priority level.
     */
    Optional<EngineDiscountPrioritiesEntity> findByEcommerceIdAndPriorityLevel(
            UUID ecommerceId, Integer priorityLevel
    );
}
