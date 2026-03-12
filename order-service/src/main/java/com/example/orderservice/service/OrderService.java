package com.example.orderservice.service;

import com.example.orderservice.dto.OrderDtos.*;
import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.Order.OrderStatus;
import com.example.orderservice.entity.OrderItem;
import com.example.orderservice.event.InventoryResponseEvent;
import com.example.orderservice.event.OrderCreatedEvent;
import com.example.orderservice.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.order}")
    private String orderExchange;

    @Value("${rabbitmq.routing-key.order-created}")
    private String orderCreatedRoutingKey;

    // ─────────────────────────────────────────
    // Create Order
    // ─────────────────────────────────────────

    @Transactional
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "createOrderFallback")
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for customer: {}", request.getCustomerId());

        // 1. Build order
        Order order = buildOrder(request);
        Order savedOrder = orderRepository.save(order);

        // 2. Publish OrderCreatedEvent to RabbitMQ
        OrderCreatedEvent event = buildOrderCreatedEvent(savedOrder);
        publishOrderCreatedEvent(event);

        log.info("Order created: {} with status PENDING", savedOrder.getOrderNumber());
        return mapToResponse(savedOrder);
    }

    public OrderResponse createOrderFallback(CreateOrderRequest request, Exception ex) {
        log.error("Circuit breaker open for order creation: {}", ex.getMessage());
        throw new RuntimeException("Order service temporarily unavailable. Please try again later.");
    }

    // ─────────────────────────────────────────
    // Handle Inventory Response (consumer callback)
    // ─────────────────────────────────────────

    @Transactional
    public void handleInventoryResponse(InventoryResponseEvent event) {
        log.info("Handling inventory response for order: {}", event.getOrderNumber());

        Order order = orderRepository.findByOrderNumber(event.getOrderNumber())
                .orElseThrow(() -> new RuntimeException("Order not found: " + event.getOrderNumber()));

        if (event.isAllItemsAvailable()) {
            order.setStatus(OrderStatus.INVENTORY_RESERVED);
            log.info("Inventory reserved for order: {}", order.getOrderNumber());
        } else {
            order.setStatus(OrderStatus.FAILED);
            order.setFailureReason(event.getMessage());
            log.warn("Inventory NOT available for order: {}. Reason: {}",
                    order.getOrderNumber(), event.getMessage());
        }

        orderRepository.save(order);
    }

    // ─────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumberWithItems(orderNumber)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomer(String customerId) {
        return orderRepository.findByCustomerId(customerId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────

    private Order buildOrder(CreateOrderRequest request) {
        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .customerId(request.getCustomerId())
                .customerEmail(request.getCustomerEmail())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        List<OrderItem> items = request.getItems().stream().map(itemReq -> {
            BigDecimal totalPrice = itemReq.getUnitPrice()
                    .multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            return OrderItem.builder()
                    .order(order)
                    .productId(itemReq.getProductId())
                    .productName(itemReq.getProductName())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .totalPrice(totalPrice)
                    .build();
        }).collect(Collectors.toList());

        order.setItems(items);
        order.setTotalAmount(
                items.stream().map(OrderItem::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        return order;
    }

    private void publishOrderCreatedEvent(OrderCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(orderExchange, orderCreatedRoutingKey, event);
            log.info("Published OrderCreatedEvent for order: {}", event.getOrderNumber());
        } catch (Exception ex) {
            log.error("Failed to publish OrderCreatedEvent: {}", ex.getMessage());
            // Could persist to outbox table here for guaranteed delivery
            throw new RuntimeException("Failed to publish order event", ex);
        }
    }

    private OrderCreatedEvent buildOrderCreatedEvent(Order order) {
        List<OrderCreatedEvent.OrderItemEvent> itemEvents = order.getItems().stream()
                .map(item -> OrderCreatedEvent.OrderItemEvent.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .build())
                .collect(Collectors.toList());

        return OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderNumber(order.getOrderNumber())
                .customerId(order.getCustomerId())
                .customerEmail(order.getCustomerEmail())
                .totalAmount(order.getTotalAmount())
                .items(itemEvents)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerId(order.getCustomerId())
                .customerEmail(order.getCustomerEmail())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
