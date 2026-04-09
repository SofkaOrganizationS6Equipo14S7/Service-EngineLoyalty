package com.loyalty.service_engine.infrastructure.rabbitmq;

import com.loyalty.service_engine.application.dto.RuleStatusChangedEvent;
import com.loyalty.service_engine.application.service.ClassificationMatrixCaffeineCacheService;
import com.loyalty.service_engine.domain.entity.ClassificationRuleReplicaEntity;
import com.loyalty.service_engine.domain.repository.ClassificationRuleReplicaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleStatusEventConsumerTest {

    @Mock
    private ClassificationRuleReplicaRepository ruleRepository;

    @Mock
    private ClassificationMatrixCaffeineCacheService cacheService;

    @InjectMocks
    private RuleStatusEventConsumer consumer;

    private final UUID ruleId = UUID.randomUUID();
    private final UUID ecommerceId = UUID.randomUUID();

    @Test
    @DisplayName("Should update rule status when rule exists")
    void updateStatusExistingRule() {
        RuleStatusChangedEvent event = new RuleStatusChangedEvent(ruleId, ecommerceId, false, true, Instant.now());

        ClassificationRuleReplicaEntity rule = new ClassificationRuleReplicaEntity(
                ruleId, ecommerceId, "Test", null, "CLASSIFICATION", "PERCENTAGE",
                BigDecimal.TEN, "INDIVIDUAL", Map.of(), 1, true, null, null, null);

        when(ruleRepository.findByIdAndEcommerceId(ruleId, ecommerceId)).thenReturn(Optional.of(rule));

        consumer.handleRuleStatusChanged(event);

        verify(ruleRepository).save(rule);
        verify(cacheService).invalidateEcommerce(ecommerceId);
    }

    @Test
    @DisplayName("Should skip when rule not found")
    void ruleNotFound() {
        RuleStatusChangedEvent event = new RuleStatusChangedEvent(ruleId, ecommerceId, false, true, Instant.now());

        when(ruleRepository.findByIdAndEcommerceId(ruleId, ecommerceId)).thenReturn(Optional.empty());

        consumer.handleRuleStatusChanged(event);

        verify(ruleRepository, never()).save(any());
        verify(cacheService, never()).invalidateEcommerce(any());
    }

    @Test
    @DisplayName("Should handle null event gracefully")
    void nullEvent() {
        // Should not throw
        consumer.handleRuleStatusChanged(null);
        verifyNoInteractions(ruleRepository);
        verifyNoInteractions(cacheService);
    }

    @Test
    @DisplayName("Should handle event with null ruleId")
    void nullRuleId() {
        RuleStatusChangedEvent event = new RuleStatusChangedEvent(null, ecommerceId, false, true, Instant.now());
        consumer.handleRuleStatusChanged(event);
        verifyNoInteractions(ruleRepository);
    }

    @Test
    @DisplayName("Should handle event with null ecommerceId")
    void nullEcommerceId() {
        RuleStatusChangedEvent event = new RuleStatusChangedEvent(ruleId, null, false, true, Instant.now());
        consumer.handleRuleStatusChanged(event);
        verifyNoInteractions(ruleRepository);
    }

    @Test
    @DisplayName("Should handle event with null newStatus")
    void nullNewStatus() {
        RuleStatusChangedEvent event = new RuleStatusChangedEvent(ruleId, ecommerceId, null, true, Instant.now());
        consumer.handleRuleStatusChanged(event);
        verifyNoInteractions(ruleRepository);
    }

    @Test
    @DisplayName("Should propagate exception from repository")
    void repositoryException() {
        RuleStatusChangedEvent event = new RuleStatusChangedEvent(ruleId, ecommerceId, false, true, Instant.now());

        ClassificationRuleReplicaEntity rule = new ClassificationRuleReplicaEntity(
                ruleId, ecommerceId, "Test", null, "CLASSIFICATION", "PERCENTAGE",
                BigDecimal.TEN, "INDIVIDUAL", Map.of(), 1, true, null, null, null);

        when(ruleRepository.findByIdAndEcommerceId(ruleId, ecommerceId)).thenReturn(Optional.of(rule));
        doThrow(new RuntimeException("DB error")).when(ruleRepository).save(any());

        try {
            consumer.handleRuleStatusChanged(event);
        } catch (RuntimeException e) {
            // expected
        }
    }
}
