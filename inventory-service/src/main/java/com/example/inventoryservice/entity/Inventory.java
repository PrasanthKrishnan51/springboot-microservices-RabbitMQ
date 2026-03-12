package com.example.inventoryservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "inventory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    private String id;

    @Indexed(unique = true)
    private String productId;

    private String productName;

    private Integer availableQuantity;

    private Integer reservedQuantity;

    private Integer reorderPoint;

    private LocalDateTime lastUpdated;

    public boolean hasSufficientStock(int requestedQuantity) {
        return availableQuantity >= requestedQuantity;
    }

    public void reserve(int quantity) {
        this.availableQuantity -= quantity;
        this.reservedQuantity += quantity;
        this.lastUpdated = LocalDateTime.now();
    }
}
