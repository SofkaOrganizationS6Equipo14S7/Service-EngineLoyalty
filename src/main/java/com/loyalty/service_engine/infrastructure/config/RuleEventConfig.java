package com.loyalty.service_engine.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * RabbitMQ configuration for rule event consumer (SPEC-010).
 * Consumes generic RuleEvent for all discount types: FIDELITY, SEASONAL, PRODUCT, CLASSIFICATION.
 *
 * Consumer Side (Engine Service):
 * - Receives on queue: loyalty.rules.queue
 * - From exchange: loyalty.events (shared TopicExchange)
 * - Routing key pattern: rule.* (listeners for all rule event types)
 * - Dead Letter Queue: loyalty.dlq for error handling
 *
 * Related to RuleEventProducerConfig in Admin Service.
 */
@Configuration
public class RuleEventConfig {

    @Value("${rabbitmq.exchange.events:loyalty.events}")
    private String eventsExchange;

    @Value("${rabbitmq.exchange.events-dlx:loyalty.events.dlx}")
    private String eventsDlxExchange;

    @Value("${rabbitmq.queue.rules:loyalty.rules.queue}")
    private String rulesQueue;

    @Value("${rabbitmq.queue.rules-dlq:loyalty.dlq}")
    private String rulesDlq;

    @Value("${rabbitmq.routing.rules:rule.*}")
    private String rulesRoutingKey;

    /**
     * Main Topic Exchange for all event types (shared with other consumers).
     * Shared with ClassificationRuleEventConsumer, CustomerTierSyncConsumer, etc.
     */
    @Bean(name = "eventsExchangeEngine")
    public TopicExchange eventsExchange() {
        return new TopicExchange(eventsExchange, true, false);
    }

    /**
     * Dead Letter Exchange for permanently failed events.
     */
    @Bean(name = "eventsDlxExchangeEngine")
    public DirectExchange eventsDlxExchange() {
        return new DirectExchange(eventsDlxExchange, true, false);
    }

    /**
     * Main queue for unified rule events (all rule types).
     * Routes to RuleEventConsumer @RabbitListener.
     * DLX/DLQ: If message fails 3 times, sent to DLQ for manual inspection.
     */
    @Bean(name = "rulesQueue")
    public Queue rulesQueue() {
        return QueueBuilder
                .durable(rulesQueue)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", eventsDlxExchange,
                        "x-dead-letter-routing-key", "rule.dlq"
                ))
                .build();
    }

    /**
     * Dead Letter Queue for permanently failed rule events.
     * Messages go here after exhaustive retries for manual inspection + reprocessing.
     */
    @Bean(name = "rulesDlq")
    public Queue rulesDlq() {
        return QueueBuilder.durable(rulesDlq).build();
    }

    /**
     * Binding: rulesQueue ← eventsExchange with routing key rule.*
     * Matches all rule events: rule.created, rule.updated, rule.deleted, etc.
     */
    @Bean
    public Binding rulesBinding(Queue rulesQueue, TopicExchange eventsExchange) {
        return BindingBuilder
                .bind(rulesQueue)
                .to(eventsExchange)
                .with(rulesRoutingKey);
    }

    /**
     * Binding: rulesDlq ← eventsDlxExchange
     * Routes permanently failed messages for auditing.
     * Uses @Qualifier to explicitly select the correct DirectExchange bean
     * when multiple DirectExchange beans exist in the context.
     */
    @Bean
    public Binding rulesDlqBinding(
            Queue rulesDlq,
            @Qualifier("eventsDlxExchangeEngine") DirectExchange eventsDlxExchange) {
        return BindingBuilder
                .bind(rulesDlq)
                .to(eventsDlxExchange)
                .with("rule.dlq");
    }
}
