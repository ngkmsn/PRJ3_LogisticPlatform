package com.logistics.order;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OrderDatabaseConnectionTest {

    @Inject
    DataSource dataSource;

    @Test
    void testOrderDatabaseConnectionIsValid() throws SQLException {
        assertNotNull(dataSource, "DataSource bean must be configured for order-service");
        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection, "Connection must be established");
            assertTrue(connection.isValid(2), "Database connection must be valid");
        }
    }
}
