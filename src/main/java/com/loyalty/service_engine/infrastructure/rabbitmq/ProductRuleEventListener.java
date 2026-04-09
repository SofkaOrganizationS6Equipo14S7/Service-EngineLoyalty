package com.loyalty.service_engine.infrastructure.rabbitmq;

import com.loyalty.service_engine.application.dto.ProductRuleEvent;
import com.loyalty.service_engine.application.service.ProductRuleSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ listener for Product Rule events from Admin Service
 * 
 * Listens to:
 * - Queue: engine-service.product-rules
 * - Exchange: product-rules.exchange (topic)
 * - Routing keys: product.rules.*
 */
@Component
@Slf4j
public class ProductRuleEventListener {
    
    private final ProductRuleSyncService productRuleSyncService;
    
    public ProductRuleEventListener(ProductRuleSyncService productRuleSyncService) {
        this.productRuleSyncService = productRuleSyncService;
    }
    
    /**
     * Listen for product rule events from Admin Service
     * 
     * @param event the product rule event
     * @param eventType the event type from message header
     */
    @RabbitListener(queues = "${rabbitmq.queue.product-rules:engine-service.product-rules}")
    public void onProductRuleEvent(
        ProductRuleEvent event,
        @Header(value = "x-event-type", required = false) String eventType
    ) {
        try {
            log.info("event=product_rule_received eventType={} ruleUid={} ecommerceId={}", 
                event.eventType(), event.uid(), event.ecommerceId());
            
            // Route to appropriate handler based on event type
            switch (event.eventType()) {
                case "PRODUCT_RULE_CREATED":
                    productRuleSyncService.handleProductRuleCreated(event);
                    log.info("event=product_rule_created_processed ruleUid={}", event.uid());
                    break;
                    
                case "PRODUCT_RULE_UPDATED":
                    productRuleSyncService.handleProductRuleUpdated(event);
                    log.info("event=product_rule_updated_processed ruleUid={}", event.uid());
                    break;
                    
                case "PRODUCT_RULE_DELETED":
                    productRuleSyncService.handleProductRuleDeleted(event);
                    log.info("event=product_rule_deleted_processed ruleUid={}", event.uid());
                    break;
                    
                default:
                    log.warn("event=unknown_product_rule_event eventType={} ruleUid={}", 
                        event.eventType(), event.uid());
            }
        } catch (Exception e) {
            log.error("event=product_rule_event_processing_failed eventType={} ruleUid={}", 
                event.eventType(), event.uid(), e);
            // Intentionally not throwing exception - let RabbitMQ handle requeue if needed
            // Or implement a DLX handler for failed messages
        }
    }
}
