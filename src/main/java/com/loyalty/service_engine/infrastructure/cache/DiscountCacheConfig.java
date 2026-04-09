package com.loyalty.service_engine.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Configuración de caché en memoria Caffeine para optimizar acceso a configuración de descuentos.
 * 
 * Estrategia:
 * - discount_config: se cachea por 10 minutos (cambios via RabbitMQ)
 * - discount_priority: se cachea por 10 minutos (cambios via RabbitMQ)
 * 
 * Cuando service-engine emite evento "DiscountConfigUpdated", el consumidor lo recibe
 * e invalida el caché para forzar reload de BD.
 */
@Configuration
@EnableCaching
@Slf4j
public class DiscountCacheConfig {
    
    public static final String DISCOUNT_CONFIG_CACHE = "discount_config";
    public static final String DISCOUNT_PRIORITY_CACHE = "discount_priority";
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Configurar políticas de caché para cada tipo de dato
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .recordStats());
        
        cacheManager.setCacheNames(Arrays.asList(
            DISCOUNT_CONFIG_CACHE,
            DISCOUNT_PRIORITY_CACHE
        ));
        
        log.info("Caffeine cache configured with 10-minute TTL for discount configuration");
        return cacheManager;
    }
}
