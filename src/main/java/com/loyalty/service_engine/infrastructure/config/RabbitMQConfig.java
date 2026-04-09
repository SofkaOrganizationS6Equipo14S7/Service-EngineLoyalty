package com.loyalty.service_engine.infrastructure.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import java.util.Map;

@Configuration
public class RabbitMQConfig {
    
    @Value("${rabbitmq.exchange.config:loyalty.config.exchange}")
    private String configExchange;
    
    @Value("${rabbitmq.queue.api-keys:engine-api-keys-queue}")
    private String apiKeysQueue;

    @Value("${rabbitmq.routing.api-keys:}")
    private String apiKeysRoutingKey;

    @Value("${rabbitmq.exchange.config-dlx:loyalty.config.dlx}")
    private String configDeadLetterExchange;

    @Value("${rabbitmq.queue.api-keys-dlq:engine-api-keys-dlq}")
    private String apiKeysDlq;

    @Value("${rabbitmq.routing.api-keys-dlq:api.keys.dlq}")
    private String apiKeysDlqRoutingKey;

    @Value("${rabbitmq.retry.max-attempts:5}")
    private int maxAttempts;

    @Value("${rabbitmq.retry.initial-interval-ms:500}")
    private long initialIntervalMs;

    @Value("${rabbitmq.retry.multiplier:2.0}")
    private double retryMultiplier;

    @Value("${rabbitmq.retry.max-interval-ms:5000}")
    private long maxIntervalMs;

    // Seasonal Rules exchange and queue
    @Value("${rabbitmq.exchange.seasonal:loyalty.seasonal.exchange}")
    private String seasonalExchange;

    @Value("${rabbitmq.exchange.seasonal-dlx:loyalty.seasonal.dlx}")
    private String seasonalDeadLetterExchange;

    @Value("${rabbitmq.queue.seasonal-rules:loyalty.seasonal.rules.queue}")
    private String seasonalRulesQueue;

    @Value("${rabbitmq.queue.seasonal-rules-dlq:loyalty.seasonal.rules.dlq}")
    private String seasonalRulesDlq;

    @Value("${rabbitmq.routing.seasonal-created:seasonal.rule.created}")
    private String seasonalCreatedRoutingKey;

    @Value("${rabbitmq.routing.seasonal-updated:seasonal.rule.updated}")
    private String seasonalUpdatedRoutingKey;

    @Value("${rabbitmq.routing.seasonal-deleted:seasonal.rule.deleted}")
    private String seasonalDeletedRoutingKey;

    @Value("${rabbitmq.routing.seasonal-dlq:seasonal.rule.dlq}")
    private String seasonalDlqRoutingKey;
    
    /**
     * Queue para consumir eventos de API Key desde Admin Service.
     */
    @Bean
    public Queue apiKeysQueue() {
        return new Queue(apiKeysQueue, true, false, false, Map.of(
                "x-dead-letter-exchange", configDeadLetterExchange,
                "x-dead-letter-routing-key", apiKeysDlqRoutingKey
        ));
    }

    @Bean
    public Queue apiKeysDlqQueue() {
        return new Queue(apiKeysDlq, true);
    }
    
    /**
     * Fanout Exchange para recibir eventos de configuración desde Admin.
     */
    @Bean
    public DirectExchange configExchange() {
        return new DirectExchange(configExchange, true, false);
    }

    @Bean
    public DirectExchange configDeadLetterExchange() {
        return new DirectExchange(configDeadLetterExchange, true, false);
    }
    
    /**
     * Binding: API Keys Queue → Config Exchange
     */
    @Bean
    public Binding apiKeysBinding(Queue apiKeysQueue, DirectExchange configExchange) {
        return BindingBuilder.bind(apiKeysQueue)
            .to(configExchange)
            .with(apiKeysRoutingKey);
    }

    @Bean
    public Binding apiKeysDlqBinding(Queue apiKeysDlqQueue, DirectExchange configDeadLetterExchange) {
        return BindingBuilder.bind(apiKeysDlqQueue)
                .to(configDeadLetterExchange)
                .with(apiKeysDlqRoutingKey);
    }
    
    // ============================================================================
    // Seasonal Rules Exchange, Queues, and Bindings
    // ============================================================================
    
    /**
     * Fanout Exchange for seasonal rule events
     * Admin Service publishes events here, this service consumes
     */
    @Bean
    public FanoutExchange seasonalExchange() {
        return new FanoutExchange(seasonalExchange, true, false);
    }

    @Bean
    public DirectExchange seasonalDeadLetterExchange() {
        return new DirectExchange(seasonalDeadLetterExchange, true, false);
    }

    /**
     * Queue for seasonal rules events
     * Consumed by SeasonalRuleEventConsumer
     */
    @Bean
    public Queue seasonalRulesQueue() {
        return new Queue(seasonalRulesQueue, true, false, false, Map.of(
                "x-dead-letter-exchange", seasonalDeadLetterExchange,
                "x-dead-letter-routing-key", seasonalDlqRoutingKey
        ));
    }

    @Bean
    public Queue seasonalRulesDlq() {
        return new Queue(seasonalRulesDlq, true);
    }

    /**
     * Bindings for seasonal rule events (Fanout - no routing keys needed)
     */
    @Bean
    public Binding seasonalCreatedBinding(Queue seasonalRulesQueue, FanoutExchange seasonalExchange) {
        return BindingBuilder.bind(seasonalRulesQueue)
                .to(seasonalExchange);
    }

    @Bean
    public Binding seasonalUpdatedBinding(Queue seasonalRulesQueue, FanoutExchange seasonalExchange) {
        return BindingBuilder.bind(seasonalRulesQueue)
                .to(seasonalExchange);
    }

    @Bean
    public Binding seasonalDeletedBinding(Queue seasonalRulesQueue, FanoutExchange seasonalExchange) {
        return BindingBuilder.bind(seasonalRulesQueue)
                .to(seasonalExchange);
    }

    @Bean
    public Binding seasonalDlqBinding(Queue seasonalRulesDlq, DirectExchange seasonalDeadLetterExchange) {
        return BindingBuilder.bind(seasonalRulesDlq)
                .to(seasonalDeadLetterExchange)
                .with(seasonalDlqRoutingKey);
    }
    
    /**
     * Converter JSON para mensajes.
     */
    @Bean
    @Primary
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter();
    }
    
    /**
     * RabbitTemplate para enviar mensajes.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public RetryOperationsInterceptor apiKeyRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(maxAttempts)
                .backOffOptions(initialIntervalMs, retryMultiplier, maxIntervalMs)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory apiKeyEventListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            RetryOperationsInterceptor apiKeyRetryInterceptor
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(apiKeyRetryInterceptor);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
    
    /**
     * ObjectMapper para serialización/deserialización JSON.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
