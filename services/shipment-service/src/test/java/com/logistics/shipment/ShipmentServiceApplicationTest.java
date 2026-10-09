package com.logistics.shipment;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ShipmentServiceApplicationTest {

    @Test
    void contextLoads() {
        assertTrue(true, "Quarkus ShipmentService application context loaded successfully");
    }
}
