package com.loyalty.service_engine.infrastructure.rabbitmq;

import com.loyalty.service_engine.application.service.ClassificationMatrixCaffeineCacheService;
import com.loyalty.service_engine.domain.entity.ClassificationRuleReplicaEntity;
import com.loyalty.service_engine.domain.repository.ClassificationRuleReplicaRepository;
import com.loyalty.service_engine.infrastructure.rabbitmq.ClassificationRuleEventConsumer.ClassificationRuleEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassificationRuleEventConsumerTest {

    @Mock
    private ClassificationRuleReplicaRepository ruleReplicaRepo;

    @Mock
    private ClassificationMatrixCaffeineCacheService cacheService;

    @InjectMocks
    private ClassificationRuleEventConsumer consumer;

    private final UUID ruleId = UUID.randomUUID();
    private final UUID ecommerceId = UUID.randomUUID();

    private ClassificationRuleEvent createEvent(String eventType) {
        return new ClassificationRuleEvent(
                eventType, ruleId, ecommerceId,
                "Gold Rule", "Gold Tier Discount",
                "CLASSIFICATION", "PERCENTAGE",
                BigDecimal.TEN, "INDIVIDUAL",
                Map.of("min_spent", Map.of("operator", ">=", "value", 1000)),
                1, true
        );
    }

    @Test
    @DisplayName("Should handle CREATED event")
    void handleCreated() {
        ClassificationRuleEvent event = createEvent("CREATED");
        consumer.handleRuleEvent(event);

        verify(ruleReplicaRepo).save(any(ClassificationRuleReplicaEntity.class));
        verify(cacheService).invalidateEcommerce(ecommerceId);
    }

    @Test
    @DisplayName("Should handle UPDATED event when rule exists")
    void handleUpdatedExisting() {
        ClassificationRuleEvent event = createEvent("UPDATED");
        ClassificationRuleReplicaEntity existing = new ClassificationRuleReplicaEntity(
                ruleId, ecommerceId, "Old Name", null, "CLASSIFICATION", "PERCENTAGE",
                BigDecimal.valueOf(5), "INDIVIDUAL", Map.of(), 2, true, null, null, null);

        when(ruleReplicaRepo.findById(ruleId)).thenReturn(Optional.of(existing));

        consumer.handleRuleEvent(event);

        verify(ruleReplicaRepo).save(existing);
        verify(cacheService).invalidateEcommerce(ecommerceId);
    }

    @Test
    @DisplayName("Should treat UPDATED as CREATED when rule not found")
    void handleUpdatedNotFound() {
        ClassificationRuleEvent event = createEvent("UPDATED");
        when(ruleReplicaRepo.findById(ruleId)).thenReturn(Optional.empty());

        consumer.handleRuleEvent(event);

        verify(ruleReplicaRepo).save(any(ClassificationRuleReplicaEntity.class));
        verify(cacheService).invalidateEcommerce(ecommerceId);
    }

    @Test
    @DisplayName("Should handle DELETED event when rule exists")
    void handleDeletedExisting() {
        ClassificationRuleEvent event = createEvent("DELETED");
        ClassificationRuleReplicaEntity existing = new ClassificationRuleReplicaEntity(
                ruleId, ecommerceId, "Name", null, "CLASSIFICATION", "PERCENTAGE",
                BigDecimal.TEN, "INDIVIDUAL", Map.of(), 1, true, null, null, null);

        when(ruleReplicaRepo.findById(ruleId)).thenReturn(Optional.of(existing));

        consumer.handleRuleEvent(event);

        verify(ruleReplicaRepo).save(existing);
        verify(cacheService).invalidateEcommerce(ecommerceId);
    }

    @Test
    @DisplayName("Should handle DELETED event when rule not found")
    void handleDeletedNotFound() {
        ClassificationRuleEvent event = createEvent("DELETED");
        when(ruleReplicaRepo.findById(ruleId)).thenReturn(Optional.empty());

        consumer.handleRuleEvent(event);

        verify(ruleReplicaRepo, never()).save(any());
        verify(cacheService).invalidateEcommerce(ecommerceId);
    }

    @Test
    @DisplayName("Should handle null event")
    void handleNull() {
        consumer.handleRuleEvent(null);
        verifyNoInteractions(ruleReplicaRepo);
        verifyNoInteractions(cacheService);
    }

    @Test
    @DisplayName("Should handle unknown event type")
    void handleUnknownType() {
        ClassificationRuleEvent event = createEvent("UNKNOWN");
        consumer.handleRuleEvent(event);
        verifyNoInteractions(ruleReplicaRepo);
    }
}
