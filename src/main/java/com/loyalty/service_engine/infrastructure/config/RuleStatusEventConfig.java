package com.loyalty.service_engine.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * RabbitMQ configuration for rule status change events (SPEC-009).
 * Consumes rule status changes from Admin Service via loyalty.events exchange.
 * Routes to engine-service-rule-status-changes queue with routing key: rule.status.changed
 *
 * Admin → [RuleStatusChangedEvent] → loyalty.events (TopicExchange)
 *   → engine-service-rule-status-changes (Queue, @RabbitListener in RuleStatusEventConsumer)
 *   → RuleStatusEventConsumer.handleRuleStatusChanged()
 *   → Update engine_rules.is_active + invalidate cache
 *
 * On permanent failure (3 retries):
 *   → engine-service-rule-status-changes.dlq (Dead Letter Queue for manual inspection)
 *
 * Properties (from application.properties):
 * - rabbitmq.exchange.events=loyalty.events (TopicExchange, shared with other events)
 * - rabbitmq.exchange.events-dlx=loyalty.events.dlx (DLX)
 * - rabbitmq.queue.rule-status-changes=engine-service-rule-status-changes
 * - rabbitmq.queue.rule-status-changes-dlq=engine-service-rule-status-changes.dlq
 * - rabbitmq.routing.rule-status-changed=rule.status.changed
 */
@Configuration
public class RuleStatusEventConfig {

    @Value("${rabbitmq.exchange.events:loyalty.events}")
    private String eventsExchange;

    @Value("${rabbitmq.exchange.events-dlx:loyalty.events.dlx}")
    private String eventsDlxExchange;

    @Value("${rabbitmq.queue.rule-status-changes:engine-service-rule-status-changes}")
    private String ruleStatusQueue;

    @Value("${rabbitmq.queue.rule-status-changes-dlq:engine-service-rule-status-changes.dlq}")
    private String ruleStatusDlq;

    @Value("${rabbitmq.routing.rule-status-changed:rule.status.changed}")
    private String ruleStatusRoutingKey;

    @Value("${rabbitmq.routing.rule-status-changed-dlq:rule.status.changed.dlq}")
    private String ruleStatusDlqRoutingKey;

    /**
     * Main Topic Exchange for all event types (shared).
     * Used by multiple consumers: ClassificationRuleEventConsumer, CustomerTierSyncConsumer, RuleStatusEventConsumer, etc.
     */
    @Bean(name = "eventsExchange")
    public TopicExchange eventsExchange() {
        return new TopicExchange(eventsExchange, true, false);
    }

    /**
     * Dead Letter Exchange for rule status events.
     */
    @Bean(name = "eventsDlxExchange")
    public DirectExchange eventsDlxExchange() {
        return new DirectExchange(eventsDlxExchange, true, false);
    }

    /**
     * Main queue for rule status changes.
     * Maps to RuleStatusEventConsumer @RabbitListener.
     * DLX: If message fails 3 times, goes to ruleStatusDlq for inspection.
     */
    @Bean(name = "ruleStatusQueue")
    public Queue ruleStatusQueue() {
        return QueueBuilder
            .durable(ruleStatusQueue)
            .withArguments(Map.of(
                "x-dead-letter-exchange", eventsDlxExchange,
                "x-dead-letter-routing-key", ruleStatusDlqRoutingKey
            ))
            .build();
    }

    /**
     * Dead Letter Queue for permanently failed rule status events.
     * Messages here need manual investigation and reprocessing.
     */
    @Bean(name = "ruleStatusDlq")
    public Queue ruleStatusDlq() {
        return QueueBuilder.durable(ruleStatusDlq).build();
    }

    /**
     * Binding between ruleStatusQueue and eventsExchange.
     * Routing key: rule.status.changed (published by Admin RuleService.updateRuleStatus())
     */
    @Bean
    public Binding ruleStatusBinding(
            Queue ruleStatusQueue,
            TopicExchange eventsExchange) {
        return BindingBuilder
            .bind(ruleStatusQueue)
            .to(eventsExchange)
            .with(ruleStatusRoutingKey);
    }

    /**
     * Binding between ruleStatusDlq and eventsDlxExchange.
     * Routes permanently failed messages for inspection.
     */
    @Bean
    public Binding ruleStatusDlqBinding(
            Queue ruleStatusDlq,
            DirectExchange eventsDlxExchange) {
        return BindingBuilder
            .bind(ruleStatusDlq)
            .to(eventsDlxExchange)
            .with(ruleStatusDlqRoutingKey);
    }
}
