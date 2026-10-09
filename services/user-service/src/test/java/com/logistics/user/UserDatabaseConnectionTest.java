package com.logistics.user;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class UserDatabaseConnectionTest {

    @Inject
    DataSource dataSource;

    @Test
    void testDatabaseConnectionEstablished() throws SQLException {
        assertNotNull(dataSource, "DataSource bean must be injected");
        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection, "Database connection must not be null");
            assertFalse(connection.isClosed(), "Database connection must be open");
        }
    }
}
