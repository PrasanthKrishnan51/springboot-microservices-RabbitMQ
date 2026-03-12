package com.example.orderservice.dto;

import com.example.orderservice.entity.Order;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderDtos {

    // ─── Request ──────────────────────────────
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrderRequest {

        @NotBlank(message = "Customer ID is required")
        private String customerId;

        @NotBlank(message = "Customer email is required")
        @Email(message = "Invalid email format")
        private String customerEmail;

        @NotEmpty(message = "Order must have at least one item")
        @Valid
        private List<OrderItemRequest> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {

        @NotBlank(message = "Product ID is required")
        private String productId;

        @NotBlank(message = "Product name is required")
        private String productName;

        @NotNull @Min(1)
        private Integer quantity;

        @NotNull @DecimalMin("0.01")
        private BigDecimal unitPrice;
    }

    // ─── Response ─────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderResponse {
        private Long id;
        private String orderNumber;
        private String customerId;
        private String customerEmail;
        private Order.OrderStatus status;
        private BigDecimal totalAmount;
        private List<OrderItemResponse> items;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private String productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
    }

    // ─── API Wrapper ──────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data, String message) {
            return ApiResponse.<T>builder()
                    .success(true).message(message).data(data).build();
        }

        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder()
                    .success(false).message(message).build();
        }
    }
}
