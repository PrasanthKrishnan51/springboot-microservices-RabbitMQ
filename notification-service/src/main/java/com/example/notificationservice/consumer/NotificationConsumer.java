package com.example.notificationservice.consumer;

import com.example.notificationservice.dto.OrderCreatedEvent;
import com.example.notificationservice.dto.InventoryResponseEvent;
import com.example.notificationservice.service.NotificationService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;

    @Value("${notification.idempotency.ttl-seconds:86400}")
    private long idempotencyTtlSeconds;

    // ─── Listen to OrderCreatedEvent ──────────────────────────────

    @RabbitListener(queues = "${rabbitmq.queue.order-created}")
    public void handleOrderCreated(
            OrderCreatedEvent event,
            Message message,
            Channel channel) throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            // Idempotency check — skip if already processed
            if (isAlreadyProcessed("order-created:" + event.getEventId())) {
                log.warn("Duplicate OrderCreatedEvent ignored: {}", event.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            log.info("🔔 Processing order created notification for: {}", event.getOrderNumber());
            notificationService.sendOrderConfirmationEmail(event);
            markAsProcessed("order-created:" + event.getEventId());

            channel.basicAck(deliveryTag, false);

        } catch (Exception ex) {
            log.error("Failed to process OrderCreatedEvent: {}", ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false); // → DLQ
        }
    }

    // ─── Listen to InventoryResponseEvent ─────────────────────────

    @RabbitListener(queues = "${rabbitmq.queue.inventory-response}")
    public void handleInventoryResponse(
            InventoryResponseEvent event,
            Message message,
            Channel channel) throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            if (isAlreadyProcessed("inventory-response:" + event.getEventId())) {
                log.warn("Duplicate InventoryResponseEvent ignored: {}", event.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            log.info("🔔 Processing inventory response notification for: {}", event.getOrderNumber());

            if (event.isAllItemsAvailable()) {
                notificationService.sendInventoryReservedEmail(event);
            } else {
                notificationService.sendOrderFailedEmail(event);
            }

            markAsProcessed("inventory-response:" + event.getEventId());
            channel.basicAck(deliveryTag, false);

        } catch (Exception ex) {
            log.error("Failed to process InventoryResponseEvent: {}", ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    // ─── Idempotency Helpers ──────────────────────────────────────

    private boolean isAlreadyProcessed(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("notif:processed:" + key));
    }

    private void markAsProcessed(String key) {
        redisTemplate.opsForValue().set(
                "notif:processed:" + key,
                "1",
                Duration.ofSeconds(idempotencyTtlSeconds)
        );
    }
}
