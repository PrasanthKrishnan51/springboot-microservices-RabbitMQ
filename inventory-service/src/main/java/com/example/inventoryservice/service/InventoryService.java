package com.example.inventoryservice.service;

import com.example.inventoryservice.dto.InventoryResponseEvent;
import com.example.inventoryservice.dto.OrderCreatedEvent;
import com.example.inventoryservice.entity.Inventory;
import com.example.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.order}")
    private String orderExchange;

    @Value("${rabbitmq.routing-key.inventory-response}")
    private String inventoryResponseRoutingKey;

    /**
     * Core logic: check stock → reserve → reply to Order Service
     */
    public void processOrderCreatedEvent(OrderCreatedEvent event) {
        log.info("Processing inventory check for order: {}", event.getOrderNumber());

        List<String> insufficientItems = new ArrayList<>();

        // Phase 1: Check all items first (no reservation yet)
        for (OrderCreatedEvent.OrderItemEvent item : event.getItems()) {
            Optional<Inventory> inventoryOpt = inventoryRepository.findByProductId(item.getProductId());

            if (inventoryOpt.isEmpty()) {
                insufficientItems.add(item.getProductId() + " (not found)");
                continue;
            }

            Inventory inventory = inventoryOpt.get();
            if (!inventory.hasSufficientStock(item.getQuantity())) {
                insufficientItems.add(String.format("%s (requested: %d, available: %d)",
                        item.getProductId(), item.getQuantity(), inventory.getAvailableQuantity()));
            }
        }

        // Phase 2: If all available → reserve stock
        if (insufficientItems.isEmpty()) {
            reserveStock(event);
            publishInventoryResponse(event.getOrderNumber(), true, "All items reserved successfully");
        } else {
            String reason = "Insufficient stock: " + String.join(", ", insufficientItems);
            log.warn("Inventory check failed for order {}: {}", event.getOrderNumber(), reason);
            publishInventoryResponse(event.getOrderNumber(), false, reason);
        }
    }

    private void reserveStock(OrderCreatedEvent event) {
        event.getItems().forEach(item -> {
            inventoryRepository.findByProductId(item.getProductId()).ifPresent(inventory -> {
                inventory.reserve(item.getQuantity());
                inventoryRepository.save(inventory);
                log.info("Reserved {} units of {} for order {}",
                        item.getQuantity(), item.getProductId(), event.getOrderNumber());
            });
        });
    }

    private void publishInventoryResponse(String orderNumber, boolean available, String message) {
        InventoryResponseEvent response = InventoryResponseEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderNumber(orderNumber)
                .allItemsAvailable(available)
                .message(message)
                .build();

        rabbitTemplate.convertAndSend(orderExchange, inventoryResponseRoutingKey, response);
        log.info("Published InventoryResponseEvent for order: {} | available: {}", orderNumber, available);
    }

    // ─── Seed data helper ─────────────────────────────
    public Inventory addInventory(String productId, String productName, int quantity) {
        return inventoryRepository.save(Inventory.builder()
                .productId(productId)
                .productName(productName)
                .availableQuantity(quantity)
                .reservedQuantity(0)
                .reorderPoint(10)
                .lastUpdated(java.time.LocalDateTime.now())
                .build());
    }

    public List<Inventory> getAllInventory() {
        return inventoryRepository.findAll();
    }
}
