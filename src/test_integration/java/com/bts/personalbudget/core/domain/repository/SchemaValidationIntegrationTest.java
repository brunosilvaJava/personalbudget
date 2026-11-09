package com.bts.personalbudget.core.domain.repository;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The local profile (used by docker compose) runs Hibernate with ddl-auto=validate. The context only
 * starts when every mapped column has the type the entity expects, so a migration that declares
 * TINYINT for a field mapped as int breaks the application at startup.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class SchemaValidationIntegrationTest {

    @Container
    static final MySQLContainer<MySQLContainerConfig> MYSQL = MySQLContainerConfig.getInstance();

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void shouldStartWithHibernateSchemaValidation() {
        assertThat(entityManagerFactory.isOpen()).isTrue();
    }
}
