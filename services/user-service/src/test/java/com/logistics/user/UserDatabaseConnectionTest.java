package com.logistics.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class UserDatabaseConnectionTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void testUserDatabaseConnectionIsValid() throws SQLException {
        assertNotNull(dataSource, "DataSource bean must be configured for user-service");
        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection, "Connection must be established");
            assertTrue(connection.isValid(2), "Database connection must be valid");
        }
    }
}
