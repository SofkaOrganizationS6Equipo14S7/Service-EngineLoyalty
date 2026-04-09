package com.loyalty.service_engine.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import java.util.Map;

@Configuration
public class ConfigEventsRabbitConfig {

    @Value("${rabbitmq.exchange.config:loyalty.config.exchange}")
    private String configExchange;

    @Value("${rabbitmq.exchange.config-dlx:loyalty.config.dlx}")
    private String configDlqExchange;

    @Value("${rabbitmq.queue.engine-config-updated:engine.config.updated.queue}")
    private String configUpdatedQueue;

    @Value("${rabbitmq.queue.engine-config-updated-dlq:engine.config.updated.dlq}")
    private String configUpdatedDlq;

    @Value("${rabbitmq.routing.config-updated:config.updated}")
    private String configUpdatedRoutingKey;

    @Value("${rabbitmq.routing.config-updated-dlq:config.updated.dlq}")
    private String configUpdatedDlqRoutingKey;

    @Value("${rabbitmq.retry.max-attempts:5}")
    private int maxAttempts;

    @Value("${rabbitmq.retry.initial-interval-ms:500}")
    private long initialIntervalMs;

    @Value("${rabbitmq.retry.multiplier:2.0}")
    private double retryMultiplier;

    @Value("${rabbitmq.retry.max-interval-ms:5000}")
    private long maxIntervalMs;

    @Bean
    public DirectExchange configExchangeV2() {
        return new DirectExchange(configExchange, true, false);
    }

    @Bean
    public DirectExchange configDlqExchangeV2() {
        return new DirectExchange(configDlqExchange, true, false);
    }

    @Bean
    public Queue engineConfigUpdatedQueue() {
        return new Queue(configUpdatedQueue, true, false, false, Map.of(
                "x-dead-letter-exchange", configDlqExchange,
                "x-dead-letter-routing-key", configUpdatedDlqRoutingKey
        ));
    }

    @Bean
    public Queue engineConfigUpdatedDlq() {
        return new Queue(configUpdatedDlq, true);
    }

    @Bean
    public Binding engineConfigUpdatedBinding(Queue engineConfigUpdatedQueue, DirectExchange configExchangeV2) {
        return BindingBuilder.bind(engineConfigUpdatedQueue).to(configExchangeV2).with(configUpdatedRoutingKey);
    }

    @Bean
    public Binding engineConfigUpdatedDlqBinding(Queue engineConfigUpdatedDlq, DirectExchange configDlqExchangeV2) {
        return BindingBuilder.bind(engineConfigUpdatedDlq).to(configDlqExchangeV2).with(configUpdatedDlqRoutingKey);
    }

    @Bean
    public MessageConverter configEventMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RetryOperationsInterceptor configRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(maxAttempts)
                .backOffOptions(initialIntervalMs, retryMultiplier, maxIntervalMs)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory configEventListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter configEventMessageConverter,
            RetryOperationsInterceptor configRetryInterceptor
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(configEventMessageConverter);
        factory.setAdviceChain(configRetryInterceptor);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
