package com.logistics.order;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OrderServiceApplicationTest {

    @Test
    void contextLoads() {
        assertTrue(true, "Quarkus OrderService application context loaded successfully");
    }
}
