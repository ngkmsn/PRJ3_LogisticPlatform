package com.logistics.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Notification Service.
 *
 * <p>Responsibilities (to be implemented in subsequent PB items):
 * <ul>
 *   <li>Receive notification events from other services (via REST or, later, a message broker)</li>
 *   <li>Send emails, SMS, and push notifications to users</li>
 *   <li>Manage notification templates and delivery preferences</li>
 *   <li>Track delivery status and retry failed notifications</li>
 * </ul>
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
