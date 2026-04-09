package com.loyalty.service_engine.infrastructure.rabbitmq;

import com.loyalty.service_engine.application.dto.CustomerTierDTO;
import com.loyalty.service_engine.application.service.ClassificationMatrixCaffeineCacheService;
import com.loyalty.service_engine.domain.entity.CustomerTierReplicaEntity;
import com.loyalty.service_engine.domain.repository.CustomerTierReplicaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumes customer tier synchronization events from Admin Service.
 * Updates the replica database and invalidates Caffeine cache.
 *
 * Event flow:
 * Admin Service → RabbitMQ (customer-tiers.updated) → Engine Service Listener → DB replica + Cache invalidation
 *
 * Operations:
 * 1. Created: Add new tier to replica and invalidate cache
 * 2. Updated: Update tier in replica and invalidate cache
 * 3. Deleted: Soft-delete tier in replica and invalidate cache
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerTierSyncConsumer {

    private final CustomerTierReplicaRepository tierReplicaRepo;
    private final ClassificationMatrixCaffeineCacheService cacheService;

    /**
     * Listen to customer tier synchronization events.
     * Event structure expected from Admin Service:
     * {
     *   "eventType": "CREATED" | "UPDATED" | "DELETED",
     *   "tier": { "id", "ecommerceId", "name", "discountPercentage", "hierarchyLevel", "isActive", "syncedAt" }
     * }
     */
    @RabbitListener(queues = "${rabbitmq.queue.customer-tiers:customer-tiers.sync.queue}")
    public void handleTierEvent(CustomerTierSyncEvent event) {
        if (event == null || event.tier() == null) {
            log.warn("Received null or incomplete tier sync event");
            return;
        }

        UUID ecommerceId = event.tier().ecommerceId();
        String eventType = event.eventType() != null ? event.eventType() : "UNKNOWN";

        try {
            switch (eventType.toUpperCase()) {
                case "CREATED":
                    handleTierCreated(event.tier());
                    break;
                case "UPDATED":
                    handleTierUpdated(event.tier());
                    break;
                case "DELETED":
                    handleTierDeleted(event.tier());
                    break;
                default:
                    log.warn("Unknown tier event type: {}", eventType);
                    return;
            }

            // Invalidate cache for this ecommerce
            cacheService.invalidateEcommerce(ecommerceId);
            log.info("Cache invalidated for ecommerce: {} after {} event", ecommerceId, eventType);

        } catch (Exception e) {
            log.error("Error processing tier sync event for ecommerce={}, type={}", ecommerceId, eventType, e);
        }
    }

    private void handleTierCreated(CustomerTierDTO tierDto) {
        log.info("Creating tier: uid={}, ecommerce={}, name={}", 
            tierDto.uid(), tierDto.ecommerceId(), tierDto.name());

        CustomerTierReplicaEntity replica = new CustomerTierReplicaEntity(
            tierDto.uid(),
            tierDto.ecommerceId(),
            tierDto.name(),
            tierDto.discountPercentage(),
            tierDto.hierarchyLevel(),
            tierDto.isActive(),
            Instant.now(),
            Instant.now(),
            Instant.now()
        );

        tierReplicaRepo.save(replica);
        log.info("Tier created in replica: uid={}", tierDto.uid());
    }

    private void handleTierUpdated(CustomerTierDTO tierDto) {
        log.info("Updating tier: uid={}, ecommerce={}", tierDto.uid(), tierDto.ecommerceId());

        Optional<CustomerTierReplicaEntity> existing = tierReplicaRepo.findById(tierDto.uid());
        
        if (existing.isPresent()) {
            CustomerTierReplicaEntity entity = existing.get();
            entity.setName(tierDto.name());
            entity.setDiscountPercentage(tierDto.discountPercentage());
            entity.setHierarchyLevel(tierDto.hierarchyLevel());
            entity.setIsActive(tierDto.isActive());
            entity.setSyncedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());

            tierReplicaRepo.save(entity);
            log.info("Tier updated in replica: uid={}", tierDto.uid());
        } else {
            log.warn("Tier not found for update: uid={}", tierDto.uid());
            // Treat as create if not exists
            handleTierCreated(tierDto);
        }
    }

    private void handleTierDeleted(CustomerTierDTO tierDto) {
        log.info("Soft-deleting tier: uid={}", tierDto.uid());

        Optional<CustomerTierReplicaEntity> existing = tierReplicaRepo.findById(tierDto.uid());
        
        if (existing.isPresent()) {
            CustomerTierReplicaEntity entity = existing.get();
            entity.setIsActive(false);
            entity.setSyncedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());

            tierReplicaRepo.save(entity);
            log.info("Tier soft-deleted in replica: uid={}", tierDto.uid());
        } else {
            log.warn("Tier not found for deletion: uid={}", tierDto.uid());
        }
    }

    /**
     * Event payload for tier synchronization from Admin Service.
     */
    public record CustomerTierSyncEvent(
        String eventType, // CREATED | UPDATED | DELETED
        CustomerTierDTO tier
    ) {}
}
