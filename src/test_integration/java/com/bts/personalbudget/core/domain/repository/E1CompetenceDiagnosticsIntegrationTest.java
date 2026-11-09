package com.bts.personalbudget.core.domain.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs every query of the E1 diagnostic script against the migrated schema. It guards the script
 * against column or table names that drift from the migrations, and against any statement that is
 * not a read.
 */
class E1CompetenceDiagnosticsIntegrationTest {

    private static final Path SCRIPT = Path.of("local", "database", "diagnostics", "e1-competence.sql");
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @BeforeAll
    static void migrate() {
        MYSQL.start();
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .load()
                .migrate();
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @Test
    void shouldRunEveryDiagnosticQueryAgainstTheMigratedSchema() throws SQLException, IOException {
        final List<String> statements = statements();
        assertTrue(statements.size() == 4, "expected four diagnostic queries, found " + statements.size());

        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                assertDoesNotThrow(() -> {
                    try (ResultSet resultSet = statement.executeQuery(sql)) {
                        while (resultSet.next()) {
                            // Only checks that the query runs against the real column names.
                        }
                    }
                }, sql);
            }
        }
    }

    @Test
    void shouldContainOnlyReadStatements() throws IOException {
        for (String sql : statements()) {
            assertTrue(sql.toUpperCase().startsWith("SELECT"), "not a read: " + sql);
        }
    }

    /** Statements of the script, without the comment lines (which may contain ';'). */
    private static List<String> statements() throws IOException {
        final String withoutComments = Files.readAllLines(SCRIPT).stream()
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));
        return Arrays.stream(withoutComments.split(";"))
                .map(String::trim)
                .filter(sql -> !sql.isEmpty())
                .toList();
    }
}
