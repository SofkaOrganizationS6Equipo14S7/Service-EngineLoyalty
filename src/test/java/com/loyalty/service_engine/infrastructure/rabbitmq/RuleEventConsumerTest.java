package com.loyalty.service_engine.infrastructure.rabbitmq;

import com.loyalty.service_engine.application.dto.events.RuleEvent;
import com.loyalty.service_engine.application.service.ClassificationMatrixCaffeineCacheService;
import com.loyalty.service_engine.application.service.EngineRuleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleEventConsumerTest {

    @Mock
    private EngineRuleService engineRuleService;

    @Mock
    private ClassificationMatrixCaffeineCacheService cacheService;

    @InjectMocks
    private RuleEventConsumer consumer;

    private final UUID ruleId = UUID.randomUUID();
    private final UUID ecommerceId = UUID.randomUUID();

    private RuleEvent createEvent(String eventType) {
        return new RuleEvent(
                eventType, ruleId, ecommerceId,
                "Test Rule", "Description",
                "FIDELITY", BigDecimal.TEN, 1,
                Map.of("min_spent", 100),
                true, "INDIVIDUAL", Instant.now()
        );
    }

    @Test
    @DisplayName("Should handle RULE_CREATED event")
    void handleRuleCreated() {
        RuleEvent event = createEvent("RULE_CREATED");

        consumer.consume(event);

        verify(engineRuleService).createOrUpdateEngineRule(
                eq(ruleId), eq(ecommerceId),
                eq("Test Rule"), eq("Description"),
                eq("FIDELITY"), eq(BigDecimal.TEN), eq(1),
                any(), eq(true), eq("INDIVIDUAL")
        );
        verify(cacheService).invalidateEcommerce(ecommerceId);
    }

    @Test
    @DisplayName("Should handle RULE_UPDATED event (same as created)")
    void handleRuleUpdated() {
        RuleEvent event = createEvent("RULE_UPDATED");

        consumer.consume(event);

        verify(engineRuleService).createOrUpdateEngineRule(
                eq(ruleId), eq(ecommerceId),
                any(), any(), any(), any(), any(), any(), any(), any()
        );
        verify(cacheService).invalidateEcommerce(ecommerceId);
    }

    @Test
    @DisplayName("Should handle RULE_DELETED event")
    void handleRuleDeleted() {
        RuleEvent event = createEvent("RULE_DELETED");

        consumer.consume(event);

        verify(engineRuleService).markRuleAsDeleted(ruleId, ecommerceId);
        verify(cacheService).invalidateEcommerce(ecommerceId);
    }

    @Test
    @DisplayName("Should throw on unknown event type")
    void handleUnknownType() {
        RuleEvent event = createEvent("RULE_UNKNOWN");

        assertThrows(RuntimeException.class, () -> consumer.consume(event));
    }

    @Test
    @DisplayName("Should throw on null ruleId")
    void nullRuleId() {
        RuleEvent event = new RuleEvent("RULE_CREATED", null, ecommerceId,
                "name", "desc", "FIDELITY", BigDecimal.TEN, 1, Map.of(), true, "INDIVIDUAL", Instant.now());

        assertThrows(RuntimeException.class, () -> consumer.consume(event));
    }

    @Test
    @DisplayName("Should throw on null ecommerceId")
    void nullEcommerceId() {
        RuleEvent event = new RuleEvent("RULE_CREATED", ruleId, null,
                "name", "desc", "FIDELITY", BigDecimal.TEN, 1, Map.of(), true, "INDIVIDUAL", Instant.now());

        assertThrows(RuntimeException.class, () -> consumer.consume(event));
    }

    @Test
    @DisplayName("Should throw on null eventType")
    void nullEventType() {
        RuleEvent event = new RuleEvent(null, ruleId, ecommerceId,
                "name", "desc", "FIDELITY", BigDecimal.TEN, 1, Map.of(), true, "INDIVIDUAL", Instant.now());

        assertThrows(RuntimeException.class, () -> consumer.consume(event));
    }

    @Test
    @DisplayName("Should throw on null discountTypeCode")
    void nullDiscountTypeCode() {
        RuleEvent event = new RuleEvent("RULE_CREATED", ruleId, ecommerceId,
                "name", "desc", null, BigDecimal.TEN, 1, Map.of(), true, "INDIVIDUAL", Instant.now());

        assertThrows(RuntimeException.class, () -> consumer.consume(event));
    }

    @Test
    @DisplayName("Should throw on null isActive")
    void nullIsActive() {
        RuleEvent event = new RuleEvent("RULE_CREATED", ruleId, ecommerceId,
                "name", "desc", "FIDELITY", BigDecimal.TEN, 1, Map.of(), null, "INDIVIDUAL", Instant.now());

        assertThrows(RuntimeException.class, () -> consumer.consume(event));
    }
}
