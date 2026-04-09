package com.loyalty.service_engine.infrastructure.rabbitmq;

import com.loyalty.service_engine.application.dto.CustomerTierDTO;
import com.loyalty.service_engine.application.service.ClassificationMatrixCaffeineCacheService;
import com.loyalty.service_engine.domain.entity.CustomerTierReplicaEntity;
import com.loyalty.service_engine.domain.repository.CustomerTierReplicaRepository;
import com.loyalty.service_engine.infrastructure.rabbitmq.CustomerTierSyncConsumer.CustomerTierSyncEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerTierSyncConsumerTest {

    @Mock
    private CustomerTierReplicaRepository tierReplicaRepo;

    @Mock
    private ClassificationMatrixCaffeineCacheService cacheService;

    @InjectMocks
    private CustomerTierSyncConsumer consumer;

    private final UUID tierId = UUID.randomUUID();
    private final UUID ecommerceId = UUID.randomUUID();

    private CustomerTierDTO createTierDTO() {
        return new CustomerTierDTO(tierId, ecommerceId, "Gold", BigDecimal.TEN, 2, true, Instant.now());
    }

    @Test
    @DisplayName("Should handle CREATED event")
    void handleCreated() {
        CustomerTierSyncEvent event = new CustomerTierSyncEvent("CREATED", createTierDTO());

        consumer.handleTierEvent(event);

        verify(tierReplicaRepo).save(any(CustomerTierReplicaEntity.class));
        verify(cacheService).invalidateEcommerce(ecommerceId);
    }

    @Test
    @DisplayName("Should handle UPDATED event when tier exists")
    void handleUpdatedExisting() {
        CustomerTierSyncEvent event = new CustomerTierSyncEvent("UPDATED", createTierDTO());
        CustomerTierReplicaEntity existing = new CustomerTierReplicaEntity(
                tierId, ecommerceId, "Silver", BigDecimal.valueOf(5), 1, true, null, null, null);

        when(tierReplicaRepo.findById(tierId)).thenReturn(Optional.of(existing));

        consumer.handleTierEvent(event);

        verify(tierReplicaRepo).save(existing);
        verify(cacheService).invalidateEcommerce(ecommerceId);
    }

    @Test
    @DisplayName("Should treat UPDATED as CREATED when tier not found")
    void handleUpdatedNotFound() {
        CustomerTierSyncEvent event = new CustomerTierSyncEvent("UPDATED", createTierDTO());
        when(tierReplicaRepo.findById(tierId)).thenReturn(Optional.empty());

        consumer.handleTierEvent(event);

        verify(tierReplicaRepo).save(any(CustomerTierReplicaEntity.class));
        verify(cacheService).invalidateEcommerce(ecommerceId);
    }

    @Test
    @DisplayName("Should handle DELETED event when tier exists")
    void handleDeletedExisting() {
        CustomerTierSyncEvent event = new CustomerTierSyncEvent("DELETED", createTierDTO());
        CustomerTierReplicaEntity existing = new CustomerTierReplicaEntity(
                tierId, ecommerceId, "Gold", BigDecimal.TEN, 2, true, null, null, null);

        when(tierReplicaRepo.findById(tierId)).thenReturn(Optional.of(existing));

        consumer.handleTierEvent(event);

        verify(tierReplicaRepo).save(existing);
        verify(cacheService).invalidateEcommerce(ecommerceId);
    }

    @Test
    @DisplayName("Should handle DELETED event when tier not found")
    void handleDeletedNotFound() {
        CustomerTierSyncEvent event = new CustomerTierSyncEvent("DELETED", createTierDTO());
        when(tierReplicaRepo.findById(tierId)).thenReturn(Optional.empty());

        consumer.handleTierEvent(event);

        verify(tierReplicaRepo, never()).save(any());
        verify(cacheService).invalidateEcommerce(ecommerceId);
    }

    @Test
    @DisplayName("Should handle null event")
    void handleNull() {
        consumer.handleTierEvent(null);
        verifyNoInteractions(tierReplicaRepo);
        verifyNoInteractions(cacheService);
    }

    @Test
    @DisplayName("Should handle event with null tier")
    void handleNullTier() {
        CustomerTierSyncEvent event = new CustomerTierSyncEvent("CREATED", null);
        consumer.handleTierEvent(event);
        verifyNoInteractions(tierReplicaRepo);
        verifyNoInteractions(cacheService);
    }

    @Test
    @DisplayName("Should handle unknown event type")
    void handleUnknownType() {
        CustomerTierSyncEvent event = new CustomerTierSyncEvent("UNKNOWN", createTierDTO());
        consumer.handleTierEvent(event);
        verifyNoInteractions(tierReplicaRepo);
    }
}
