package com.loyalty.service_engine.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for Engine Service.
 *
 * Role: Declares the Fanout Exchange (for connection), Queue, and Binding.
 * This is the "consumer" side — Engine listens to classification rule events.
 *
 * Topology:
 * - Exchange: classification.exchange (Fanout)
 * - Queue: classification.matrix.queue
 * - Binding: Queue bound to Exchange (fanout style, no routing keys needed)
 *
 * When Admin publishes, the message is delivered to Engine's queue automatically.
 */
@Configuration
public class RabbitMQClassificationEngineConfig {

    @Value("${rabbitmq.exchange.classification:classification.exchange}")
    private String exchangeName;

    @Value("${rabbitmq.queue.classification-rules:classification.matrix.queue}")
    private String queueName;

    @Bean(name = "classificationExchange")
    public Exchange classificationExchange() {
        return ExchangeBuilder
            .fanoutExchange(exchangeName)
            .durable(true)
            .build();
    }

    @Bean(name = "classificationQueue")
    public Queue classificationQueue() {
        return QueueBuilder
            .durable(queueName)
            .build();
    }

    @Bean
    public Binding classificationBinding(
            Queue classificationQueue,
            Exchange classificationExchange) {
        return BindingBuilder
            .bind(classificationQueue)
            .to((FanoutExchange) classificationExchange);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
