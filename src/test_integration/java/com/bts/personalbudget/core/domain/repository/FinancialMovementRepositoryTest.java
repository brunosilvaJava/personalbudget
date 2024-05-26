package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.model.FinancialMovementFactory;
import com.bts.personalbudget.core.domain.model.FinancialMovementModel;
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
        final FinancialMovementModel financialMovementModelMock = FinancialMovementFactory.build();
        financialMovementRepository.save(financialMovementModelMock);
        final Optional<FinancialMovementModel> financialMovementModel =
                financialMovementRepository.findByCode(financialMovementModelMock.getCode());
        assertTrue(financialMovementModel.isPresent());
    }

}
