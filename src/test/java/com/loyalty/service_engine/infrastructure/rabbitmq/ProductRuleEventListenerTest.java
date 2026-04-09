package com.loyalty.service_engine.infrastructure.rabbitmq;

import com.loyalty.service_engine.application.dto.ProductRuleEvent;
import com.loyalty.service_engine.application.service.ProductRuleSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductRuleEventListenerTest {

    @Mock
    private ProductRuleSyncService productRuleSyncService;

    @InjectMocks
    private ProductRuleEventListener listener;

    private ProductRuleEvent createEvent(String eventType) {
        return new ProductRuleEvent(
                eventType,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Test Rule",
                "ELECTRONICS",
                BigDecimal.TEN,
                "10% off",
                true,
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should route PRODUCT_RULE_CREATED to sync service")
    void handleCreated() {
        ProductRuleEvent event = createEvent("PRODUCT_RULE_CREATED");
        listener.onProductRuleEvent(event, "PRODUCT_RULE_CREATED");
        verify(productRuleSyncService).handleProductRuleCreated(event);
    }

    @Test
    @DisplayName("Should route PRODUCT_RULE_UPDATED to sync service")
    void handleUpdated() {
        ProductRuleEvent event = createEvent("PRODUCT_RULE_UPDATED");
        listener.onProductRuleEvent(event, "PRODUCT_RULE_UPDATED");
        verify(productRuleSyncService).handleProductRuleUpdated(event);
    }

    @Test
    @DisplayName("Should route PRODUCT_RULE_DELETED to sync service")
    void handleDeleted() {
        ProductRuleEvent event = createEvent("PRODUCT_RULE_DELETED");
        listener.onProductRuleEvent(event, "PRODUCT_RULE_DELETED");
        verify(productRuleSyncService).handleProductRuleDeleted(event);
    }

    @Test
    @DisplayName("Should handle unknown event type gracefully")
    void handleUnknown() {
        ProductRuleEvent event = createEvent("PRODUCT_RULE_UNKNOWN");
        // Should not throw, just log warning
        listener.onProductRuleEvent(event, "PRODUCT_RULE_UNKNOWN");
        verifyNoInteractions(productRuleSyncService);
    }

    @Test
    @DisplayName("Should catch and log processing exceptions")
    void handleProcessingException() {
        ProductRuleEvent event = createEvent("PRODUCT_RULE_CREATED");
        doThrow(new RuntimeException("DB error")).when(productRuleSyncService).handleProductRuleCreated(event);

        // Should not propagate exception
        listener.onProductRuleEvent(event, "PRODUCT_RULE_CREATED");
    }
}
