package com.example.inventoryservice.dto;

import lombok.*;

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
