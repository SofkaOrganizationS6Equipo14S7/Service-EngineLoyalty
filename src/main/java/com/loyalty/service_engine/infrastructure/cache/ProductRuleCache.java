package com.loyalty.service_engine.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.loyalty.service_engine.domain.entity.ProductRuleEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine-based in-memory cache for Product Rules
 * 
 * Key: productType (STRING)
 * Value: List<ProductRuleEntity> (all active rules for that product type)
 * TTL: 10 minutes
 * 
 * Used by:
 * - calculate() endpoint to skip database queries
 * - Cold start to populate rules after restart
 */
@Component
@Slf4j
public class ProductRuleCache {
    
    private final Cache<String, List<ProductRuleEntity>> cache;
    
    public ProductRuleCache() {
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)  // TTL: 10 minutes
            .maximumSize(1000)                       // Max 1000 product types in cache
            .recordStats()
            .build();
        
        log.info("ProductRuleCache initialized with 10-minute TTL and 1000 max entries");
    }
    
    /**
     * Get cached rules for a product type
     * 
     * @param productType the product type key
     * @return cached rules, or null if not found
     */
    public List<ProductRuleEntity> get(String productType) {
        return cache.getIfPresent(productType);
    }
    
    /**
     * Put rules in cache for a product type
     * 
     * @param productType the product type key
     * @param rules the rules to cache
     */
    public void put(String productType, List<ProductRuleEntity> rules) {
        cache.put(productType, rules);
        log.debug("cache=product_rule_put productType={} count={}", productType, rules.size());
    }
    
    /**
     * Invalidate cache entry for a product type
     * 
     * Forces a database reload on next access
     * 
     * @param productType the product type key
     */
    public void invalidate(String productType) {
        cache.invalidate(productType);
        log.debug("cache=product_rule_invalidated productType={}", productType);
    }
    
    /**
     * Invalidate entire cache
     * 
     * Used on startup failure or full sync
     */
    public void invalidateAll() {
        cache.invalidateAll();
        log.info("cache=product_rules_invalidated_all");
    }
    
    /**
     * Load all entries (populate cache)
     * 
     * @param allRules map of productType -> list of rules
     */
    public void loadAll(java.util.Map<String, List<ProductRuleEntity>> allRules) {
        cache.putAll(allRules);
        log.info("cache=product_rules_loaded_all count={}", allRules.size());
    }
    
    /**
     * Get cache statistics
     * 
     * @return cache stats
     */
    public com.github.benmanes.caffeine.cache.stats.CacheStats getStats() {
        return cache.stats();
    }
}
