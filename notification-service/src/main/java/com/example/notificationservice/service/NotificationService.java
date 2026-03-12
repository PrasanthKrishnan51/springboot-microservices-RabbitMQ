package com.example.notificationservice.service;

import com.example.notificationservice.dto.InventoryResponseEvent;
import com.example.notificationservice.dto.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;

    public void sendOrderConfirmationEmail(OrderCreatedEvent event) {
        String subject = "Order Confirmed - " + event.getOrderNumber();
        String body = String.format("""
                Dear Customer %s,
                
                Your order %s has been received and is being processed.
                
                Order Total: $%.2f
                Items: %d
                
                We will notify you once inventory is confirmed.
                
                Thank you for your order!
                """,
                event.getCustomerId(),
                event.getOrderNumber(),
                event.getTotalAmount(),
                event.getItems() != null ? event.getItems().size() : 0
        );

        sendEmail(event.getCustomerEmail(), subject, body);
        log.info("✅ Order confirmation email sent for: {}", event.getOrderNumber());
    }

    public void sendInventoryReservedEmail(InventoryResponseEvent event) {
        String subject = "Order Processing - " + event.getOrderNumber();
        String body = String.format("""
                Great news!
                
                Your order %s has been confirmed and inventory has been reserved.
                Your order is now being processed for shipment.
                
                Thank you!
                """, event.getOrderNumber());

        // Note: In real scenario you'd look up customer email from order service
        log.info("✅ Inventory reserved email would be sent for: {}", event.getOrderNumber());
        // sendEmail(customerEmail, subject, body);
    }

    public void sendOrderFailedEmail(InventoryResponseEvent event) {
        String subject = "Order Failed - " + event.getOrderNumber();
        String body = String.format("""
                We're sorry.
                
                Your order %s could not be processed due to insufficient inventory.
                Reason: %s
                
                Please contact our support team or try again.
                
                We apologize for the inconvenience.
                """, event.getOrderNumber(), event.getMessage());

        log.warn("❌ Order failed email would be sent for: {}", event.getOrderNumber());
        // sendEmail(customerEmail, subject, body);
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom("noreply@example.com");
            mailSender.send(message);
        } catch (Exception ex) {
            // Log but don't propagate — email failure shouldn't requeue the message
            log.error("Failed to send email to {}: {}", to, ex.getMessage());
        }
    }
}
