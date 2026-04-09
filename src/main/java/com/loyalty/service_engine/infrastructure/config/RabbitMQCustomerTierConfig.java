package com.loyalty.service_engine.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQCustomerTierConfig {

    @Value("${rabbitmq.exchange.customer-tiers:customer-tiers.exchange}")
    private String exchangeName;

    @Value("${rabbitmq.exchange.customer-tiers-dlx:customer-tiers.dlx}")
    private String dlxExchangeName;

    @Value("${rabbitmq.queue.customer-tiers:customer-tiers.sync.queue}")
    private String queueName;

    @Value("${rabbitmq.queue.customer-tiers-dlq:customer-tiers.dlq}")
    private String dlqName;

    @Bean(name = "customerTiersExchange")
    public Exchange customerTiersExchange() {
        return ExchangeBuilder
            .fanoutExchange(exchangeName)
            .durable(true)
            .build();
    }

    @Bean(name = "customerTiersDlxExchange")
    public Exchange customerTiersDlxExchange() {
        return ExchangeBuilder
            .directExchange(dlxExchangeName)
            .durable(true)
            .build();
    }

    @Bean(name = "customerTiersQueue")
    public Queue customerTiersQueue() {
        return QueueBuilder
            .durable(queueName)
            .deadLetterExchange(dlxExchangeName)
            .build();
    }

    @Bean(name = "customerTiersDlq")
    public Queue customerTiersDlq() {
        return QueueBuilder
            .durable(dlqName)
            .build();
    }

    @Bean
    public Binding customerTiersBinding(Queue customerTiersQueue, Exchange customerTiersExchange) {
        return BindingBuilder
            .bind(customerTiersQueue)
            .to((FanoutExchange) customerTiersExchange);
    }
}
