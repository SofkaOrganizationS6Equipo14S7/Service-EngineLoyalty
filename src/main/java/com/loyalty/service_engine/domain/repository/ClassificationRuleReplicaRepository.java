package com.loyalty.service_engine.domain.repository;

import com.loyalty.service_engine.domain.entity.ClassificationRuleReplicaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for ClassificationRuleReplicaEntity (engine_rules table).
 * Read-only access to replicated classification rules from service-admin.
 * Used for Cold Start and classification cache population.
 * Filters by discount_type_code = "CLASSIFICATION".
 */
@Repository
public interface ClassificationRuleReplicaRepository extends JpaRepository<ClassificationRuleReplicaEntity, UUID> {

    /**
     * Find all active classification rules for an ecommerce, ordered by priority.
     */
    List<ClassificationRuleReplicaEntity> findByEcommerceIdAndDiscountTypeCodeAndIsActiveTrueOrderByPriorityLevelAsc(
        UUID ecommerceId, String discountTypeCode
    );

    /**
     * Find all classification rules for an ecommerce (active and inactive), ordered by priority.
     */
    List<ClassificationRuleReplicaEntity> findByEcommerceIdAndDiscountTypeCodeOrderByPriorityLevelAsc(
        UUID ecommerceId, String discountTypeCode
    );

    /**
     * Find all active classification rules for bulk loading into cache.
     * Assumes you call with discountTypeCode = "CLASSIFICATION".
     */
    @Query("SELECT r FROM ClassificationRuleReplicaEntity r " +
           "WHERE r.ecommerceId = :ecommerceId " +
           "AND r.discountTypeCode = :discountTypeCode " +
           "AND r.isActive = true " +
           "ORDER BY r.priorityLevel ASC")
    List<ClassificationRuleReplicaEntity> findActiveClassificationRulesForCache(
        @Param("ecommerceId") UUID ecommerceId,
        @Param("discountTypeCode") String discountTypeCode
    );

    /**
     * Find all rules by ecommerce and discount type code for initialization.
     */
    List<ClassificationRuleReplicaEntity> findByEcommerceIdAndDiscountTypeCode(
        UUID ecommerceId, String discountTypeCode
    );

    /**
     * Find a specific rule by ID and ecommerce for status updates.
     * Used by RuleStatusEventConsumer to sync status changes from Admin.
     * SPEC-009: RuleStatusEventConsumer uses this to find rule before updating is_active.
     */
    java.util.Optional<ClassificationRuleReplicaEntity> findByIdAndEcommerceId(UUID id, UUID ecommerceId);
}
