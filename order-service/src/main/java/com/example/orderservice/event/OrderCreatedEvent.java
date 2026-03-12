package com.example.orderservice.event;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Published by Order Service → consumed by Inventory & Notification services
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {

    private String eventId;           // UUID for idempotency
    private String orderNumber;
    private String customerId;
    private String customerEmail;
    private BigDecimal totalAmount;
    private List<OrderItemEvent> items;
    private LocalDateTime createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemEvent {
        private String productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
