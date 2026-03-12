package com.example.orderservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange.order}")
    private String orderExchange;

    @Value("${rabbitmq.exchange.dlx}")
    private String deadLetterExchange;

    @Value("${rabbitmq.queue.order-created}")
    private String orderCreatedQueue;

    @Value("${rabbitmq.queue.order-created-dlq}")
    private String orderCreatedDLQ;

    @Value("${rabbitmq.queue.inventory-response}")
    private String inventoryResponseQueue;

    @Value("${rabbitmq.routing-key.order-created}")
    private String orderCreatedRoutingKey;

    @Value("${rabbitmq.routing-key.order-created-dlq}")
    private String orderCreatedDLQRoutingKey;

    @Value("${rabbitmq.routing-key.inventory-response}")
    private String inventoryResponseRoutingKey;

    // ─────────────────────────────────────────
    // Exchanges
    // ─────────────────────────────────────────

    @Bean
    public TopicExchange orderExchange() {
        return ExchangeBuilder.topicExchange(orderExchange)
                .durable(true)
                .build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(deadLetterExchange)
                .durable(true)
                .build();
    }

    // ─────────────────────────────────────────
    // Queues
    // ─────────────────────────────────────────

    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(orderCreatedQueue)
                .withArgument("x-dead-letter-exchange", deadLetterExchange)
                .withArgument("x-dead-letter-routing-key", orderCreatedDLQRoutingKey)
                .withArgument("x-message-ttl", 300_000)   // 5 min TTL
                .withArgument("x-max-length", 10_000)      // max 10k messages
                .build();
    }

    @Bean
    public Queue orderCreatedDLQ() {
        return QueueBuilder.durable(orderCreatedDLQ).build();
    }

    @Bean
    public Queue inventoryResponseQueue() {
        return QueueBuilder.durable(inventoryResponseQueue).build();
    }

    // ─────────────────────────────────────────
    // Bindings
    // ─────────────────────────────────────────

    @Bean
    public Binding orderCreatedBinding() {
        return BindingBuilder
                .bind(orderCreatedQueue())
                .to(orderExchange())
                .with(orderCreatedRoutingKey);
    }

    @Bean
    public Binding orderCreatedDLQBinding() {
        return BindingBuilder
                .bind(orderCreatedDLQ())
                .to(deadLetterExchange())
                .with(orderCreatedDLQRoutingKey);
    }

    @Bean
    public Binding inventoryResponseBinding() {
        return BindingBuilder
                .bind(inventoryResponseQueue())
                .to(orderExchange())
                .with(inventoryResponseRoutingKey);
    }

    // ─────────────────────────────────────────
    // Message Converter & Template
    // ─────────────────────────────────────────

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        // Publisher confirms
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                // log and handle nack
                System.err.println("Message not confirmed: " + cause);
            }
        });
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        return factory;
    }
}
