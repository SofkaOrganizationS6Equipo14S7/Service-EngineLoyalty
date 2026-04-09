package com.loyalty.service_engine.infrastructure.startup;

import com.loyalty.service_engine.application.dto.ClassificationRuleDTO;
import com.loyalty.service_engine.application.dto.CustomerTierDTO;
import com.loyalty.service_engine.application.service.ClassificationMatrixCaffeineCacheService;
import com.loyalty.service_engine.domain.entity.ClassificationRuleReplicaEntity;
import com.loyalty.service_engine.domain.entity.CustomerTierReplicaEntity;
import com.loyalty.service_engine.domain.repository.ClassificationRuleReplicaRepository;
import com.loyalty.service_engine.domain.repository.CustomerTierReplicaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cold Start Loader for Classification Matrix.
 *
 * Responsibility:
 * - Executes on application startup (ApplicationReadyEvent)
 * - Loads all active tiers and rules from replica database tables
 * - Populates Caffeine in-memory cache per ecommerce (1-hour TTL)
 * - Guarantees Engine autonomy even if RabbitMQ events haven't arrived yet
 *
 * Why this matters:
 * - Without this loader, Engine would fail on first /classify request after restart
 * - With this loader, classification works immediately after startup
 * - Replica tables are the fallback data source (populated by Admin via RabbitMQ events)
 *
 * Multi-tenant Strategy:
 * - Loads ALL ecommerces that have tiers/rules in the replica database
 * - Groups by ecommerce and loads each one separately
 * - Supports independent cache invalidation per ecommerce
 *
 * Execution Order:
 * - @EventListener(ApplicationReadyEvent.class) executes AFTER all @PostConstruct hooks
 * - Ensures all Spring beans are fully initialized before we populate the cache
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClassificationMatrixStartupLoader {

    private final CustomerTierReplicaRepository tierReplicaRepository;
    private final ClassificationRuleReplicaRepository ruleReplicaRepository;
    private final ClassificationMatrixCaffeineCacheService cacheService;

    @EventListener(ApplicationReadyEvent.class)
    public void loadClassificationMatrixOnStartup() {
        try {
            log.info("=== ClassificationMatrix Cold Start Loader ===");
            log.info("Loading customer tiers and rules for all ecommerces...");

            // Load all active tiers (regardless of ecommerce)
            List<CustomerTierReplicaEntity> allTiers = tierReplicaRepository.findAll()
                .stream()
                .filter(t -> t.getIsActive() != null && t.getIsActive())
                .collect(Collectors.toList());

            // Load all active rules (regardless of ecommerce)
            List<ClassificationRuleReplicaEntity> allRules = ruleReplicaRepository.findAll()
                .stream()
                .filter(r -> r.getIsActive() != null && r.getIsActive())
                .collect(Collectors.toList());

            log.info("Loaded {} total active tiers from database", allTiers.size());
            log.info("Loaded {} total active rules from database", allRules.size());

            if (allTiers.isEmpty() && allRules.isEmpty()) {
                log.warn("⚠ No active tiers or rules found in replica database - cache will be empty");
                return;
            }

            // Group tiers by ecommerce
            Map<UUID, List<CustomerTierReplicaEntity>> tiersByEcommerce = allTiers.stream()
                .collect(Collectors.groupingBy(CustomerTierReplicaEntity::getEcommerceId));

            // Group rules by ecommerce
            Map<UUID, List<ClassificationRuleReplicaEntity>> rulesByEcommerce = allRules.stream()
                .collect(Collectors.groupingBy(ClassificationRuleReplicaEntity::getEcommerceId));

            // Combine ecommerce IDs from both tiers and rules
            var allEcommerceIds = tiersByEcommerce.keySet().stream()
                .collect(Collectors.toSet());
            allEcommerceIds.addAll(rulesByEcommerce.keySet());

            // Load cache for each ecommerce
            int successCount = 0;
            for (UUID ecommerceId : allEcommerceIds) {
                try {
                    List<CustomerTierReplicaEntity> tiersList = tiersByEcommerce.getOrDefault(ecommerceId, List.of());
                    List<ClassificationRuleReplicaEntity> rulesList = rulesByEcommerce.getOrDefault(ecommerceId, List.of());

                    // Convert entities to DTOs
                    List<CustomerTierDTO> tierDTOs = tiersList.stream()
                        .map(this::toTierDTO)
                        .collect(Collectors.toList());

                    List<ClassificationRuleDTO> ruleDTOs = rulesList.stream()
                        .map(this::toRuleDTO)
                        .collect(Collectors.toList());

                    // Populate cache for this ecommerce
                    if (!tierDTOs.isEmpty()) {
                        cacheService.putTiers(ecommerceId, tierDTOs);
                        log.debug("  ✓ Ecommerce {}: {} tiers cached", ecommerceId, tierDTOs.size());
                    }

                    if (!ruleDTOs.isEmpty()) {
                        cacheService.putRules(ecommerceId, ruleDTOs);
                        log.debug("  ✓ Ecommerce {}: {} rules cached", ecommerceId, ruleDTOs.size());
                    }

                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to load classification matrix for ecommerce {}", ecommerceId, e);
                }
            }

            log.info("✓ ClassificationMatrix Cold Start complete: {} ecommerces loaded, {} tiers, {} rules ready",
                successCount, allTiers.size(), allRules.size());

        } catch (Exception e) {
            log.error("ClassificationMatrix Cold Start failed - classification may not work until cache is populated via events", e);
        }
    }

    private CustomerTierDTO toTierDTO(CustomerTierReplicaEntity entity) {
        return new CustomerTierDTO(
            entity.getId(),
            entity.getEcommerceId(),
            entity.getName(),
            entity.getDiscountPercentage(),
            entity.getHierarchyLevel(),
            entity.getIsActive() != null ? entity.getIsActive() : false,
            entity.getSyncedAt()
        );
    }

    private ClassificationRuleDTO toRuleDTO(ClassificationRuleReplicaEntity entity) {
        return new ClassificationRuleDTO(
            entity.getId(),
            entity.getEcommerceId(),
            entity.getName(),
            entity.getDiscountTypeCode(),
            entity.getDiscountType(),
            entity.getDiscountValue(),
            entity.getAppliedWith(),
            entity.getLogicConditions(),
            entity.getPriorityLevel(),
            entity.getIsActive() != null ? entity.getIsActive() : false,
            entity.getSyncedAt()
        );
    }
}
