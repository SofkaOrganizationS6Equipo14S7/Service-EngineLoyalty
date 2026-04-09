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
public class RabbitMQProductRuleConfig {

    @Value("${rabbitmq.exchange.product-rules:product-rules.exchange}")
    private String exchangeName;

    @Value("${rabbitmq.queue.product-rules:product-rules.queue}")
    private String queueName;

    @Bean(name = "productRulesExchange")
    public Exchange productRulesExchange() {
        return ExchangeBuilder
            .fanoutExchange(exchangeName)
            .durable(true)
            .build();
    }

    @Bean(name = "productRulesQueue")
    public Queue productRulesQueue() {
        return QueueBuilder
            .durable(queueName)
            .build();
    }

    @Bean
    public Binding productRulesBinding(Queue productRulesQueue, Exchange productRulesExchange) {
        return BindingBuilder
            .bind(productRulesQueue)
            .to((FanoutExchange) productRulesExchange);
    }
}
