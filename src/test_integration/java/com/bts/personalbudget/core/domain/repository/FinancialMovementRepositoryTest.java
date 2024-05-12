package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.model.FinancialMovementModel;
import com.bts.personalbudget.core.domain.model.FinancialMovementEntityFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
@SpringBootTest
public class FinancialMovementRepositoryTest {

    @Container
    public static MySQLContainer<MySQLContainerConfig> mySqlContainer = MySQLContainerConfig.getInstance();

    @Autowired
    private FinancialMovementRepository financialMovementRepository;

    @Test
    void shouldSaveFinancialMovement() {
        final FinancialMovementModel financialMovementModelMock = FinancialMovementEntityFactory.build();
        financialMovementRepository.save(financialMovementModelMock);
        final FinancialMovementModel financialMovementModel = financialMovementRepository.findByCode(financialMovementModelMock.getCode());
        assertNotNull(financialMovementModel);
    }

}
