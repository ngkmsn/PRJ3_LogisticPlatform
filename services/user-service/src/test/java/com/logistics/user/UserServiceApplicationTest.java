package com.logistics.user;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Smoke test: verifies that the Quarkus application starts without errors.
 */
@QuarkusTest
class UserServiceApplicationTest {

    @Test
    void testContextLoads() {
        // If the Quarkus context fails to start, this test will fail automatically.
    }
}
