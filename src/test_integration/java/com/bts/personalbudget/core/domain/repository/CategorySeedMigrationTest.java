package com.bts.personalbudget.core.domain.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategorySeedMigrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");
    private static final UUID INACTIVE_EXISTING_CODE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTIVE_EXISTING_CODE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_OPERATION_CODE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final List<SeededCategory> SEEDED_CATEGORIES = List.of(
            new SeededCategory("Residência", "Aluguel/Financiamento", "DEBIT"),
            new SeededCategory("Residência", "Água", "DEBIT"),
            new SeededCategory("Residência", "Energia elétrica", "DEBIT"),
            new SeededCategory("Residência", "Gás", "DEBIT"),
            new SeededCategory("Residência", "Internet", "DEBIT"),
            new SeededCategory("Residência", "Manutenção da casa", "DEBIT"),
            new SeededCategory("Pessoal", "Alimentação", "DEBIT"),
            new SeededCategory("Pessoal", "Saúde", "DEBIT"),
            new SeededCategory("Pessoal", "Vestuário", "DEBIT"),
            new SeededCategory("Pessoal", "Lazer", "DEBIT"),
            new SeededCategory("Pessoal", "Cuidados pessoais", "DEBIT"),
            new SeededCategory("Instrução", "Educação", "DEBIT"),
            new SeededCategory("Instrução", "Cursos", "DEBIT"),
            new SeededCategory("Instrução", "Livros e materiais", "DEBIT"),
            new SeededCategory("Moto", "Combustível", "DEBIT"),
            new SeededCategory("Moto", "Manutenção", "DEBIT"),
            new SeededCategory("Moto", "Seguro", "DEBIT"),
            new SeededCategory("Moto", "Documentação", "DEBIT"),
            new SeededCategory("Investimento", "Reserva de emergência", "DEBIT"),
            new SeededCategory("Investimento", "Investimentos", "DEBIT"),
            new SeededCategory("Outros", "Impostos e taxas", "DEBIT"),
            new SeededCategory("Outros", "Doações", "DEBIT"),
            new SeededCategory("Outros", "Outros", "DEBIT"),
            new SeededCategory("Receitas", "Salário", "CREDIT"),
            new SeededCategory("Receitas", "Renda extra", "CREDIT"),
            new SeededCategory("Receitas", "Rendimentos", "CREDIT"),
            new SeededCategory("Receitas", "Reembolso", "CREDIT"),
            new SeededCategory("Receitas", "Outros", "CREDIT")
    );

    @BeforeAll
    static void prepareDatabaseWithExistingCategories() throws SQLException {
        MYSQL.start();
        flyway("14").migrate();
        insertCategory(INACTIVE_EXISTING_CODE, "Água", "DEBIT", "INACTIVE", "Pessoal",
                "2020-01-02 03:04:05", "2020-06-07 08:09:10");
        insertCategory(ACTIVE_EXISTING_CODE, "Salário", "CREDIT", "ACTIVE", "Receitas",
                "2021-02-03 04:05:06", "2021-07-08 09:10:11");
        insertCategory(OTHER_OPERATION_CODE, "Manutenção", "CREDIT", "ACTIVE", "Pessoal",
                "2022-03-04 05:06:07", "2022-08-09 10:11:12");
        flyway("15").migrate();
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @Test
    void shouldPreserveExistingCategoriesAndInsertMissingDefaultsWhenMigratingFromV14() throws SQLException {
        for (final SeededCategory category : SEEDED_CATEGORIES) {
            assertEquals(1, count(category.name(), category.operationType()),
                    () -> "Expected exactly one " + category.operationType() + " category named " + category.name());
            if (!category.name().equals("Água")) {
                assertEquals(category.groupName(), groupName(category.name(), category.operationType()));
            }
        }

        assertPreserved(INACTIVE_EXISTING_CODE, "Água", "DEBIT", "INACTIVE", "Pessoal",
                "2020-01-02 03:04:05", "2020-06-07 08:09:10");
        assertPreserved(ACTIVE_EXISTING_CODE, "Salário", "CREDIT", "ACTIVE", "Receitas",
                "2021-02-03 04:05:06", "2021-07-08 09:10:11");
        assertPreserved(OTHER_OPERATION_CODE, "Manutenção", "CREDIT", "ACTIVE", "Pessoal",
                "2022-03-04 05:06:07", "2022-08-09 10:11:12");
        assertEquals("Moto", groupName("Manutenção", "DEBIT"));
    }

    private static Flyway flyway(final String target) {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target(target)
                .load();
    }

    private static void insertCategory(final UUID code, final String name, final String operationType,
                                       final String status, final String groupName,
                                       final String createdDate, final String lastModifiedDate) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO category (code, name, operation_type, status, category_group_id,
                                           created_date, last_modified_date)
                     SELECT UUID_TO_BIN(?), ?, ?, ?, id, ?, ?
                     FROM category_group WHERE name = ?""")) {
            statement.setString(1, code.toString());
            statement.setString(2, name);
            statement.setString(3, operationType);
            statement.setString(4, status);
            statement.setString(5, createdDate);
            statement.setString(6, lastModifiedDate);
            statement.setString(7, groupName);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void assertPreserved(final UUID code, final String name, final String operationType,
                                        final String status, final String groupName,
                                        final String createdDate, final String lastModifiedDate) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT BIN_TO_UUID(category.code), category.status, category_group.name,
                            DATE_FORMAT(category.created_date, '%Y-%m-%d %H:%i:%s'),
                            DATE_FORMAT(category.last_modified_date, '%Y-%m-%d %H:%i:%s')
                     FROM category
                     JOIN category_group ON category_group.id = category.category_group_id
                     WHERE category.name = ? AND category.operation_type = ?""")) {
            statement.setString(1, name);
            statement.setString(2, operationType);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(code.toString(), result.getString(1));
                assertEquals(status, result.getString(2));
                assertEquals(groupName, result.getString(3));
                assertEquals(createdDate, result.getString(4));
                assertEquals(lastModifiedDate, result.getString(5));
                assertFalse(result.next());
            }
        }
    }

    private static int count(final String name, final String operationType) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM category WHERE name = ? AND operation_type = ?")) {
            statement.setString(1, name);
            statement.setString(2, operationType);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static String groupName(final String name, final String operationType) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT category_group.name FROM category
                     JOIN category_group ON category_group.id = category.category_group_id
                     WHERE category.name = ? AND category.operation_type = ?""")) {
            statement.setString(1, name);
            statement.setString(2, operationType);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private record SeededCategory(String groupName, String name, String operationType) {
    }
}
