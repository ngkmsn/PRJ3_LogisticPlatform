package com.logistics.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class NotificationLiquibaseMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void testLiquibaseTablesAndMetadataCreated() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            // Verify Liquibase metadata tables exist
            assertTrue(tableExists(metaData, "DATABASECHANGELOG"), "DATABASECHANGELOG metadata table must exist");
            assertTrue(tableExists(metaData, "DATABASECHANGELOGLOCK"), "DATABASECHANGELOGLOCK metadata table must exist");

            // Verify domain schema table created by Liquibase migration
            assertTrue(tableExists(metaData, "NOTIFICATIONS"), "NOTIFICATIONS table must be created by Liquibase");
        }
    }

    private boolean tableExists(DatabaseMetaData metaData, String tableName) throws SQLException {
        try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (tableName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
