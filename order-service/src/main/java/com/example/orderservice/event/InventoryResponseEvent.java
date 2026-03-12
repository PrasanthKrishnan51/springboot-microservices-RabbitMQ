package com.example.orderservice.event;

import lombok.*;

/**
 * Published by Inventory Service → consumed by Order Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponseEvent {

    private String eventId;
    private String orderNumber;
    private boolean allItemsAvailable;
    private String message;
}
