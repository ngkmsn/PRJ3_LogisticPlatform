package com.logistics.gateway;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ApiGatewayApplicationTest {

    @Test
    void contextLoads() {
        assertTrue(true, "Quarkus API Gateway context loaded successfully");
    }
}
