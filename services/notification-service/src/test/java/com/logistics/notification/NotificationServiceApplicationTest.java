package com.logistics.notification;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class NotificationServiceApplicationTest {

    @Test
    void contextLoads() {
        assertTrue(true, "Quarkus NotificationService application context loaded successfully");
    }
}
