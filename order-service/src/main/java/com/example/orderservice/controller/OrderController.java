package com.example.orderservice.controller;

import com.example.orderservice.dto.OrderDtos.*;
import com.example.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /api/v1/orders - customerId: {}", request.getCustomerId());
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Order created successfully"));
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @PathVariable String orderNumber) {
        OrderResponse response = orderService.getOrderByNumber(orderNumber);
        return ResponseEntity.ok(ApiResponse.success(response, "Order fetched"));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersByCustomer(
            @PathVariable String customerId) {
        List<OrderResponse> orders = orderService.getOrdersByCustomer(customerId);
        return ResponseEntity.ok(ApiResponse.success(orders, "Orders fetched"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders() {
        List<OrderResponse> orders = orderService.getAllOrders();
        return ResponseEntity.ok(ApiResponse.success(orders, "All orders fetched"));
    }
}
