package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.entity.FinancialMovementFactory;
import com.bts.personalbudget.repository.FinancialMovementRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
public class FinancialMovementRepositoryTest {

    @Container
    public static MySQLContainer<MySQLContainerConfig> mySqlContainer = MySQLContainerConfig.getInstance();

    @Autowired
    private FinancialMovementRepository financialMovementRepository;

    @Test
    void shouldSaveFinancialMovement() {
        final FinancialMovementEntity financialMovementEntityMock = FinancialMovementFactory.buildModel();
        financialMovementRepository.save(financialMovementEntityMock);
        final Optional<FinancialMovementEntity> financialMovementModel =
                financialMovementRepository.findByCode(financialMovementEntityMock.getCode());
        assertTrue(financialMovementModel.isPresent());
    }

}
