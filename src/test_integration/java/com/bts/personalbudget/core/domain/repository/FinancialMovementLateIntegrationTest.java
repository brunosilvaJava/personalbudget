package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlement;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlementService;
import com.bts.personalbudget.repository.FinancialMovementRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
@SpringBootTest
class FinancialMovementLateIntegrationTest {

    @Container
    public static MySQLContainer<MySQLContainerConfig> mySqlContainer = MySQLContainerConfig.getInstance();

    @Autowired
    private FinancialMovementService service;

    @Autowired
    private MovementSettlementService settlementService;

    @Autowired
    private FinancialMovementRepository repository;

    @Test
    void shouldMarkOnlyActiveOverduePendingMovementsLateAndPreserveProjectedBalance() {
        final LocalDate today = LocalDate.of(2088, 5, 10);
        final FinancialMovement overdue = createMovement(
                today.minusDays(1).atTime(12, 0), FinancialMovementStatus.PENDING);
        final FinancialMovement dueToday = createMovement(today.atStartOfDay(), FinancialMovementStatus.PENDING);
        final FinancialMovement alreadyPaid = createMovement(
                today.minusDays(2).atStartOfDay(), FinancialMovementStatus.PAID_OUT);
        final FinancialMovement deleted = createMovement(
                today.minusDays(3).atStartOfDay(), FinancialMovementStatus.PENDING);
        service.delete(deleted.code());

        final BigDecimal projectedBefore = service.findProjectedBalance(today);
        service.markOverdueAsLate(today);

        final FinancialMovementEntity storedOverdue = repository.findByCode(overdue.code()).orElseThrow();
        assertEquals(FinancialMovementStatus.LATE, storedOverdue.getStatus());
        assertNotNull(storedOverdue.getLastModifiedDate());
        assertEquals(FinancialMovementStatus.PENDING,
                repository.findByCode(dueToday.code()).orElseThrow().getStatus());
        assertEquals(FinancialMovementStatus.PAID_OUT,
                repository.findByCode(alreadyPaid.code()).orElseThrow().getStatus());
        assertFalse(repository.findByCode(deleted.code()).orElseThrow().getFlagActive());
        assertEquals(projectedBefore, service.findProjectedBalance(today));
    }

    private FinancialMovement createMovement(final LocalDateTime dueDate, final FinancialMovementStatus status) {
        final LocalDateTime movementDate = dueDate.minusDays(1);
        final FinancialMovement created = service.create(new FinancialMovement(
                UUID.randomUUID(), OperationType.DEBIT, "Lançamento de teste", new BigDecimal("20.00"),
                null, movementDate, dueDate, null, FinancialMovementStatus.PENDING, Boolean.TRUE, null, null, null,
                dueDate.toLocalDate(), false));
        if (status == FinancialMovementStatus.PAID_OUT) {
            settlementService.create(created.code(), new MovementSettlement(
                    null, null, new BigDecimal("20.00"), dueDate.toLocalDate(),
                    null, null, null, null, null));
        }
        return created;
    }
}
