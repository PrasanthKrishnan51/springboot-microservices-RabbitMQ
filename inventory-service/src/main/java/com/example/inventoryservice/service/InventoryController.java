package com.example.inventoryservice.service;

import com.example.inventoryservice.entity.Inventory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public ResponseEntity<List<Inventory>> getAllInventory() {
        return ResponseEntity.ok(inventoryService.getAllInventory());
    }

    @PostMapping("/seed")
    public ResponseEntity<String> seedInventory() {
        inventoryService.addInventory("PROD-001", "MacBook Pro 14", 50);
        inventoryService.addInventory("PROD-002", "iPhone 15 Pro", 100);
        inventoryService.addInventory("PROD-003", "AirPods Pro", 200);
        inventoryService.addInventory("PROD-004", "iPad Air", 75);
        return ResponseEntity.ok("Inventory seeded successfully");
    }
}

@Component
@RequiredArgsConstructor
@Slf4j
class InventoryDataSeeder implements CommandLineRunner {

    private final InventoryService inventoryService;

    @Override
    public void run(String... args) {
        if (inventoryService.getAllInventory().isEmpty()) {
            log.info("Seeding inventory data...");
            inventoryService.addInventory("PROD-001", "MacBook Pro 14", 50);
            inventoryService.addInventory("PROD-002", "iPhone 15 Pro", 100);
            inventoryService.addInventory("PROD-003", "AirPods Pro", 200);
            inventoryService.addInventory("PROD-004", "iPad Air", 75);
            log.info("Inventory data seeded.");
        }
    }
}
