package com.logistics.user;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class UserLiquibaseMigrationTest {

    @Inject
    DataSource dataSource;

    @Test
    void testLiquibaseTablesAndMetadataCreated() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            // Verify Liquibase metadata tables exist
            assertTrue(tableExists(metaData, "DATABASECHANGELOG"), "DATABASECHANGELOG metadata table must exist");
            assertTrue(tableExists(metaData, "DATABASECHANGELOGLOCK"), "DATABASECHANGELOGLOCK metadata table must exist");

            // Verify domain schema table created by Liquibase migration
            assertTrue(tableExists(metaData, "USERS"), "USERS table must be created by Liquibase");
            assertTrue(columnExists(metaData, "USERS", "PASSWORD_HASH"), "PASSWORD_HASH column must exist in USERS");
            assertTrue(columnExists(metaData, "USERS", "ROLE"), "ROLE column must exist in USERS");
            assertTrue(columnExists(metaData, "USERS", "UPDATED_AT"), "UPDATED_AT column must exist in USERS");

            // Verify seeded sample users
            try (var statement = connection.createStatement();
                 var rs = statement.executeQuery("SELECT COUNT(*) FROM users")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 4, "Users table must contain seeded sample accounts");
            }
        }
    }

    private boolean columnExists(DatabaseMetaData metaData, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                if (columnName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
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
