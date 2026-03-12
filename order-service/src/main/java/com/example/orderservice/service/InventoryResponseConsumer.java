package com.example.orderservice.service;

import com.example.orderservice.event.InventoryResponseEvent;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryResponseConsumer {

    private final OrderService orderService;

    @RabbitListener(queues = "${rabbitmq.queue.inventory-response}")
    public void handleInventoryResponse(
            InventoryResponseEvent event,
            Message message,
            Channel channel) throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            log.info("Received InventoryResponseEvent for order: {}", event.getOrderNumber());
            orderService.handleInventoryResponse(event);
            channel.basicAck(deliveryTag, false);
            log.info("Acknowledged InventoryResponseEvent for order: {}", event.getOrderNumber());

        } catch (Exception ex) {
            log.error("Error processing InventoryResponseEvent: {}", ex.getMessage(), ex);
            // Determine requeue strategy based on exception type
            boolean requeue = !(ex instanceof IllegalArgumentException);
            channel.basicNack(deliveryTag, false, requeue);
        }
    }
}
