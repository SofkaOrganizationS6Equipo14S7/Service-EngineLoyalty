package com.loyalty.service_engine.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.loyalty.service_engine.application.dto.ClassificationRuleDTO;
import com.loyalty.service_engine.application.dto.CustomerTierDTO;
import com.loyalty.service_engine.infrastructure.exception.CacheUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * In-memory Caffeine cache for Classification Matrix.
 * Stores tiers and rules per ecommerce for fast classification lookups.
 * TTL: 1 hour. Invalidated via RabbitMQ events.
 * Multi-tenant: separate cache keys per ecommerceId.
 */
@Service
@Slf4j
public class ClassificationMatrixCaffeineCacheService {

    private static final long TTL_MINUTES = 60; // 1 hour
    private static final String TIERS_PREFIX = "TIERS:";
    private static final String RULES_PREFIX = "RULES:";

    private final Cache<String, Object> cache;

    public ClassificationMatrixCaffeineCacheService() {
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(TTL_MINUTES, TimeUnit.MINUTES)
            .maximumSize(100_000) // Support many ecommerces with their tiers/rules
            .recordStats()
            .build();
        log.info("ClassificationMatrixCaffeineCacheService initialized with TTL={}min", TTL_MINUTES);
    }

    /**
     * Store tiers in cache for a specific ecommerce.
     */
    public void putTiers(UUID ecommerceId, List<CustomerTierDTO> tiers) {
        if (ecommerceId == null) {
            throw new CacheUnavailableException("ecommerceId cannot be null");
        }
        String cacheKey = TIERS_PREFIX + ecommerceId;
        cache.put(cacheKey, tiers);
        log.debug("Cached {} customer tiers for ecommerce: {}", tiers.size(), ecommerceId);
    }

    /**
     * Retrieve tiers from cache for a specific ecommerce.
     */
    @SuppressWarnings("unchecked")
    public Optional<List<CustomerTierDTO>> getTiers(UUID ecommerceId) {
        if (ecommerceId == null) {
            return Optional.empty();
        }
        String cacheKey = TIERS_PREFIX + ecommerceId;
        Object cached = cache.getIfPresent(cacheKey);
        return Optional.ofNullable((List<CustomerTierDTO>) cached);
    }

    /**
     * Store rules in cache for a specific ecommerce.
     */
    public void putRules(UUID ecommerceId, List<ClassificationRuleDTO> rules) {
        if (ecommerceId == null) {
            throw new CacheUnavailableException("ecommerceId cannot be null");
        }
        String cacheKey = RULES_PREFIX + ecommerceId;
        cache.put(cacheKey, rules);
        log.debug("Cached {} classification rules for ecommerce: {}", rules.size(), ecommerceId);
    }

    /**
     * Retrieve rules from cache for a specific ecommerce.
     */
    @SuppressWarnings("unchecked")
    public Optional<List<ClassificationRuleDTO>> getRules(UUID ecommerceId) {
        if (ecommerceId == null) {
            return Optional.empty();
        }
        String cacheKey = RULES_PREFIX + ecommerceId;
        Object cached = cache.getIfPresent(cacheKey);
        return Optional.ofNullable((List<ClassificationRuleDTO>) cached);
    }

    /**
     * Invalidate cache for a specific ecommerce (call when tiers/rules updated via RabbitMQ).
     */
    public void invalidateEcommerce(UUID ecommerceId) {
        if (ecommerceId == null) {
            return;
        }
        cache.invalidate(TIERS_PREFIX + ecommerceId);
        cache.invalidate(RULES_PREFIX + ecommerceId);
        log.debug("Cache invalidated for ecommerce: {}", ecommerceId);
    }

    /**
     * Invalidate entire cache (use with caution).
     */
    public void invalidateAll() {
        cache.invalidateAll();
        log.info("Classification matrix cache invalidated (all ecommerces)");
    }

    /**
     * Check if cache is populated for a specific ecommerce.
     */
    public boolean isPopulated(UUID ecommerceId) {
        return getTiers(ecommerceId).isPresent() && getRules(ecommerceId).isPresent();
    }

    /**
     * Get cache stats for monitoring.
     */
    public com.github.benmanes.caffeine.cache.stats.CacheStats getStats() {
        return cache.stats();
    }
}
