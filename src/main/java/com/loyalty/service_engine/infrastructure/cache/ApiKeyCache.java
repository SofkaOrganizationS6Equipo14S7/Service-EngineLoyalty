package com.loyalty.service_engine.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.loyalty.service_engine.domain.entity.ApiKeyEntity;
import com.loyalty.service_engine.domain.repository.ApiKeyRepository;
import com.loyalty.service_engine.infrastructure.util.HashingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Caché Caffeine para API Keys hasheadas (SHA-256).
 * 
 * Estructura: {hashedKey → ecommerceId}
 * 
 * Flujo de seguridad:
 * 1. Admin Service publica evento con hashedKey (SHA-256)
 * 2. Event Listener recibe el hash y lo agrega a caché + BD
 * 3. En validación, Engine recibe plaintext en Authorization header
 * 4. Engine hashea el plaintext con SHA-256 y busca en caché
 * 5. Si coincide, la solicitud es válida
 * 
 * Se sincroniza con BD y RabbitMQ.
 */
@Component
@Slf4j
public class ApiKeyCache {
    
    private final Cache<String, String> cache;
    private final ApiKeyRepository apiKeyRepository;
    
    public ApiKeyCache(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
        
        // Configurar caché con expiración de 1 día
        this.cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(1, TimeUnit.DAYS)
            .recordStats()
            .build();
        
        // Cargar keys al inicializar
        loadFromDatabase();
    }
    
    /**
     * Carga todas las API Keys desde BD y las inserta en caché.
     * Ejecutado al startup de Engine (cold start).
     */
    public void loadFromDatabase() {
        try {
            log.info("Loading API Keys from database into cache...");
            List<ApiKeyEntity> keys = apiKeyRepository.findAll();
            
            for (ApiKeyEntity key : keys) {
                cache.put(key.getHashedKey(), key.getEcommerceId().toString());
            }
            
            log.info("Loaded {} API Keys into cache", keys.size());
        } catch (Exception e) {
            log.error("Error loading API Keys from database", e);
            // No lanzar excepción, permitir que Engine arranque con caché vacía
        }
    }
    
    /**
     * Valida si una API Key plaintext es válida.
     * 
     * Flujo:
     * 1. Recibe el plaintext de Authorization: Bearer <plaintext>
     * 2. Hashea con SHA-256
     * 3. Busca el hash en caché
     * 4. Retorna true si existe
     * 
     * Búsqueda en caché (< 1ms esperado).
     * 
     * @param plainKeyValue el plaintext de la API Key
     * @return true si la clave existe en caché, false si no
     */
    public boolean validateKey(String plainKeyValue) {
        if (plainKeyValue == null || plainKeyValue.isEmpty()) {
            return false;
        }
        
        // 1. Hashear el plaintext recibido
        String hashedKey = HashingUtil.sha256(plainKeyValue);
        
        // 2. Buscar en caché
        String ecommerceId = cache.getIfPresent(hashedKey);
        boolean isValid = ecommerceId != null;
        
        log.debug("API Key validation: hash={} - {}", 
            hashedKey.substring(0, Math.min(8, hashedKey.length())) + "...", 
            isValid ? "VALID" : "INVALID");
        
        return isValid;
    }
    
    /**
     * Obtiene el ecommerce asociado a una API Key plaintext.
     * 
     * @param plainKeyValue el plaintext de la API Key
     * @return ecommerceId si existe, null si no
     */
    public String getEcommerceId(String plainKeyValue) {
        if (plainKeyValue == null || plainKeyValue.isEmpty()) {
            return null;
        }
        
        // Hashear el plaintext
        String hashedKey = HashingUtil.sha256(plainKeyValue);
        return cache.getIfPresent(hashedKey);
    }
    
    /**
     * Agrega una nueva API Key hasheada a caché y BD.
     * Llamado cuando se recibe evento API_KEY_CREATED desde RabbitMQ.
     * 
     * @param hashedKeyValue el hash SHA-256 (ya hasheado por Admin Service)
     * @param ecommerceId el ecommerce propietario
     */
    public void addKey(String hashedKeyValue, String ecommerceId) {
        try {
            // Insertar en BD
            ApiKeyEntity entity = new ApiKeyEntity();
            entity.setHashedKey(hashedKeyValue);
            entity.setEcommerceId(UUID.fromString(ecommerceId));
            entity.setExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofDays(365)));
            apiKeyRepository.save(entity);
            
            // Insertar en caché
            cache.put(hashedKeyValue, ecommerceId);
            
            log.info("API Key added to cache: hash={}, ecommerceId={}",
                hashedKeyValue.substring(0, Math.min(8, hashedKeyValue.length())) + "...",
                ecommerceId);
        } catch (Exception e) {
            log.error("Error adding API Key to cache", e);
            throw new RuntimeException("Error adding API Key", e);
        }
    }
    
    /**
     * Elimina una API Key hasheada de caché y BD.
     * Llamado cuando se recibe evento API_KEY_DELETED desde RabbitMQ.
     * 
     * @param hashedKeyValue el hash SHA-256 a eliminar
     */
    public void removeKey(String hashedKeyValue) {
        try {
            // Eliminar de BD
            apiKeyRepository.findByHashedKey(hashedKeyValue)
                .ifPresent(apiKeyRepository::delete);
            
            // Eliminar de caché
            cache.invalidate(hashedKeyValue);
            
            log.info("API Key removed from cache: hash={}",
                hashedKeyValue.substring(0, Math.min(8, hashedKeyValue.length())) + "...");
        } catch (Exception e) {
            log.error("Error removing API Key from cache", e);
            throw new RuntimeException("Error removing API Key", e);
        }
    }
    
    /**
     * Obtiene estadísticas de caché.
     */
    public CacheStats getCacheStats() {
        return cache.stats();
    }
    
    /**
     * Limpia toda la caché y la recarga de BD.
     * Útil para resincronización.
     */
    public void refresh() {
        cache.invalidateAll();
        loadFromDatabase();
        log.info("Cache refreshed");
    }
}
