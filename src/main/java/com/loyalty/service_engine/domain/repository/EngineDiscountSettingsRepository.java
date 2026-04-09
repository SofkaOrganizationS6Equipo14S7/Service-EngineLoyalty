package com.loyalty.service_engine.domain.repository;

import com.loyalty.service_engine.domain.entity.EngineDiscountSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EngineDiscountSettingsRepository extends JpaRepository<EngineDiscountSettingsEntity, UUID> {
    Optional<EngineDiscountSettingsEntity> findByEcommerceIdAndIsActiveTrue(UUID ecommerceId);
}