package com.example.inventoryservice.consumer;

import com.example.inventoryservice.dto.OrderCreatedEvent;
import com.example.inventoryservice.service.InventoryService;
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
public class OrderCreatedConsumer {

    private final InventoryService inventoryService;

    @RabbitListener(queues = "${rabbitmq.queue.order-created}")
    public void handleOrderCreated(
            OrderCreatedEvent event,
            Message message,
            Channel channel) throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            log.info("📦 Received OrderCreatedEvent: order={}, items={}",
                    event.getOrderNumber(),
                    event.getItems() != null ? event.getItems().size() : 0);

            // Idempotency check via eventId could be added here (Redis SET NX)
            inventoryService.processOrderCreatedEvent(event);

            channel.basicAck(deliveryTag, false);

        } catch (Exception ex) {
            log.error("Error processing OrderCreatedEvent for order {}: {}",
                    event.getOrderNumber(), ex.getMessage(), ex);

            // Don't requeue — let DLQ handle it
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
