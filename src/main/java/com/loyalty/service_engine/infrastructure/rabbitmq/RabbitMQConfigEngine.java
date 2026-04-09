package com.loyalty.service_engine.infrastructure.rabbitmq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración RabbitMQ para service-engine.
 * 
 * service-engine CONSUME eventos publicados por service-admin cuando
 * cambian reglas de descuentos y API Keys.
 * 
 * Eventos:
 * - discount.config.updated → configuración de límite máximo
 */
@Configuration
public class RabbitMQConfigEngine {
    
    /**
     * Cola para recibir eventos de actualización de configuración de descuentos.
     */
    @Bean
    public Queue discountConfigQueue() {
        return new Queue("discount.config.queue", true);
    }

    /**
     * Cola para recibir eventos de actualización de prioridades.
     */
    @Bean
    public Queue discountPriorityQueue() {
        return new Queue("discount.priority.queue", true);
    }
    
    /**
     * Exchange al cual se vincula la cola.
     * Nota: Could be declared here or rely on auto-declaration, but being explicit is safer.
     */
    @Bean
    public DirectExchange discountExchange() {
        return new DirectExchange("discount-exchange", true, false);
    }
    
    /**
     * Binding: discount.config.queue escucha mensajes con routing key
     * "discount.config.updated" del exchange "discount-exchange".
     */
    @Bean
    public Binding discountConfigBinding(Queue discountConfigQueue, DirectExchange discountExchange) {
        return BindingBuilder
            .bind(discountConfigQueue)
            .to(discountExchange)
            .with("discount.config.updated");
    }

    /**
     * Binding: discount.priority.queue escucha mensajes con routing key
     * "discount.priority.updated".
     */
    @Bean
    public Binding discountPriorityBinding(Queue discountPriorityQueue, DirectExchange discountExchange) {
        return BindingBuilder
            .bind(discountPriorityQueue)
            .to(discountExchange)
            .with("discount.priority.updated");
    }
    
    /**
     * Dead Letter Queue para mensajes que no pudieron ser procesados.
     */
    @Bean
    public Queue discountConfigDLQ() {
        return new Queue("discount.config.dlq", true);
    }
    
    /**
     * DLX para reenviar mensajes fallidos.
     */
    @Bean
    public DirectExchange discountConfigDLX() {
        return new DirectExchange("discount-dlx", true, false);
    }
    
    /**
     * Binding de DLQ al DLX.
     */
    @Bean
    public Binding discountConfigDLQBinding(Queue discountConfigDLQ, DirectExchange discountConfigDLX) {
        return BindingBuilder
            .bind(discountConfigDLQ)
            .to(discountConfigDLX)
            .with("discount.config.dlq");
    }
}
