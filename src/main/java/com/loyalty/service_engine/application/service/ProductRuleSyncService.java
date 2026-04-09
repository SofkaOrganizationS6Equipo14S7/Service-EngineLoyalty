package com.loyalty.service_engine.application.service;

import com.loyalty.service_engine.application.dto.ProductRuleEvent;
import com.loyalty.service_engine.domain.entity.ProductRuleEntity;
import com.loyalty.service_engine.domain.repository.ProductRuleRepository;
import com.loyalty.service_engine.infrastructure.cache.ProductRuleCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for synchronizing Product Rules from Admin Service events
 * 
 * Responsibilities:
 * - Consume RabbitMQ events (CREATE, UPDATE, DELETE)
 * - Update local database replica
 * - Invalidate/update Caffeine cache
 */
@Service
@Slf4j
public class ProductRuleSyncService {
    
    private final ProductRuleRepository productRuleRepository;
    private final ProductRuleCache productRuleCache;
    
    public ProductRuleSyncService(
        ProductRuleRepository productRuleRepository,
        ProductRuleCache productRuleCache
    ) {
        this.productRuleRepository = productRuleRepository;
        this.productRuleCache = productRuleCache;
    }
    
    /**
     * Handle creation of a new product rule
     * 
     * @param event the RabbitMQ event from Admin Service
     */
    @Transactional
    public void handleProductRuleCreated(ProductRuleEvent event) {
        log.info("Syncing product rule creation: uid={} ecommerce={} productType={}", 
            event.uid(), event.ecommerceId(), event.productType());
        
        // Create entity
        ProductRuleEntity entity = new ProductRuleEntity();
        entity.setUid(event.uid());
        entity.setEcommerceId(event.ecommerceId());
        entity.setName(event.name());
        entity.setProductType(event.productType());
        entity.setDiscountPercentage(event.discountPercentage());
        entity.setBenefit(event.benefit());
        entity.setIsActive(event.isActive());
        entity.setCreatedAt(event.timestamp());
        entity.setUpdatedAt(event.timestamp());
        
        // Persist to local replica
        productRuleRepository.save(entity);
        
        // Invalidate cache for this product type
        productRuleCache.invalidate(event.productType());
        
        log.info("Product rule creation synced: uid={}", event.uid());
    }
    
    /**
     * Handle update of an existing product rule
     * 
     * @param event the RabbitMQ event from Admin Service
     */
    @Transactional
    public void handleProductRuleUpdated(ProductRuleEvent event) {
        log.info("Syncing product rule update: uid={} ecommerce={} productType={}", 
            event.uid(), event.ecommerceId(), event.productType());
        
        // Find existing rule
        ProductRuleEntity entity = productRuleRepository.findByUid(event.uid())
            .orElseThrow(() -> new RuntimeException("Product rule not found: " + event.uid()));
        
        // Update fields
        entity.setName(event.name());
        entity.setDiscountPercentage(event.discountPercentage());
        entity.setBenefit(event.benefit());
        entity.setIsActive(event.isActive());
        entity.setUpdatedAt(event.timestamp());
        
        // Persist
        productRuleRepository.save(entity);
        
        // Invalidate cache for this product type
        productRuleCache.invalidate(event.productType());
        
        log.info("Product rule update synced: uid={}", event.uid());
    }
    
    /**
     * Handle deletion (soft delete) of a product rule
     * 
     * @param event the RabbitMQ event from Admin Service
     */
    @Transactional
    public void handleProductRuleDeleted(ProductRuleEvent event) {
        log.info("Syncing product rule deletion: uid={} ecommerce={} productType={}", 
            event.uid(), event.ecommerceId(), event.productType());
        
        // Find existing rule
        ProductRuleEntity entity = productRuleRepository.findByUid(event.uid())
            .orElseThrow(() -> new RuntimeException("Product rule not found: " + event.uid()));
        
        // Soft delete
        entity.setIsActive(false);
        entity.setUpdatedAt(event.timestamp());
        
        // Persist
        productRuleRepository.save(entity);
        
        // Invalidate cache for this product type
        productRuleCache.invalidate(event.productType());
        
        log.info("Product rule deletion synced: uid={}", event.uid());
    }
}
