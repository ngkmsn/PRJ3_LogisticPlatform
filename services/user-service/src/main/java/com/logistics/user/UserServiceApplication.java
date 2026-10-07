package com.logistics.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the User Service.
 *
 * <p>Responsibilities (to be implemented in subsequent PB items):
 * <ul>
 *   <li>User registration and profile management</li>
 *   <li>Role and permission management (admin, dispatcher, driver, customer)</li>
 *   <li>Authentication identity (credentials stored here, JWT issued here)</li>
 * </ul>
 */
@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
