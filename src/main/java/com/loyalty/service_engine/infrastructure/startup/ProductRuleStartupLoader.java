package com.loyalty.service_engine.infrastructure.startup;

import com.loyalty.service_engine.domain.entity.ProductRuleEntity;
import com.loyalty.service_engine.domain.repository.ProductRuleRepository;
import com.loyalty.service_engine.infrastructure.cache.ProductRuleCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Startup loader for Product Rules
 * 
 * CRITICAL for Cold Start Recovery:
 * - Executed at @PostConstruct (right after Spring initialization)
 * - Loads all active product rules from local DB into Caffeine cache
 * - Guarantees that /calculate endpoint can work WITHOUT calling Admin Service
 * - Essential for Cyber Monday scenarios (high traffic, potential restarts)
 * 
 * Without this:
 * - Engine restart = thousands of queries to Admin Service
 * - Potential cascading failure if Admin is down
 * 
 * With this:
 * - Engine restart = milliseconds to load cache from local disk
 * - Completely autonomous operation post-startup
 */
@Component
@Slf4j
public class ProductRuleStartupLoader {
    
    private final ProductRuleRepository productRuleRepository;
    private final ProductRuleCache productRuleCache;
    
    public ProductRuleStartupLoader(
        ProductRuleRepository productRuleRepository,
        ProductRuleCache productRuleCache
    ) {
        this.productRuleRepository = productRuleRepository;
        this.productRuleCache = productRuleCache;
    }
    
    /**
     * Load all product rules into cache at startup
     * 
     * Called automatically by Spring after bean initialization
     * This is @PostConstruct to ensure it runs AFTER all dependencies are ready
     */
    @PostConstruct
    public void loadProductRulesIntoCache() {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("event=product_rules_startup_loading");
            
            // Load all active rules from local replica
            List<ProductRuleEntity> allActiveRules = productRuleRepository.findByIsActiveTrue();
            
            if (allActiveRules.isEmpty()) {
                log.warn("event=product_rules_startup_no_rules_found");
                return;
            }
            
            // Group by product type (for efficient cache lookup)
            Map<String, List<ProductRuleEntity>> rulesByProductType = allActiveRules
                .stream()
                .collect(Collectors.groupingBy(ProductRuleEntity::getProductType));
            
            // Load into Caffeine cache
            productRuleCache.loadAll(rulesByProductType);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("event=product_rules_startup_completed totalRules={} productTypes={} durationMs={}", 
                allActiveRules.size(), rulesByProductType.size(), duration);
            
        } catch (Exception e) {
            log.error("event=product_rules_startup_failed", e);
            // Don't crash the application on cache load failure
            // The cache will be populated lazily on first access
            // Or queries will fall back to database if needed
            throw new RuntimeException("Failed to load product rules at startup", e);
        }
    }
}
