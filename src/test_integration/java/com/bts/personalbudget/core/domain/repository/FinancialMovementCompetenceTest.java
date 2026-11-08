package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.model.FinancialMovementUpdate;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlement;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlementService;
import com.bts.personalbudget.repository.FinancialMovementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
class FinancialMovementCompetenceTest {

    @Container
    public static MySQLContainer<MySQLContainerConfig> mySqlContainer = MySQLContainerConfig.getInstance();

    @Autowired
    private FinancialMovementService service;

    @Autowired
    private MovementSettlementService settlementService;

    @Autowired
    private FinancialMovementRepository repository;

    @Test
    void shouldPersistAutomaticCompetenceFromMovementDateWhenStandaloneMovementIsCreated() {
        final FinancialMovement created = service.create(
                movement(LocalDateTime.of(2051, 3, 5, 18, 30), LocalDateTime.of(2051, 3, 20, 0, 0), null, null));

        final FinancialMovementEntity stored = repository.findByCode(created.code()).orElseThrow();
        assertEquals(LocalDate.of(2051, 3, 5), stored.getCompetenceDate());
        assertFalse(stored.getCompetenceOverridden());
    }

    @Test
    void shouldPersistOccurrenceDateAsCompetenceWhenMovementComesFromRecurrenceBill() {
        final LocalDate occurrence = LocalDate.of(2052, 4, 10);
        final FinancialMovement created = service.create(new FinancialMovement(
                OperationType.DEBIT, "Aluguel", new BigDecimal("100.00"), occurrence.atStartOfDay().minusDays(3),
                occurrence.atStartOfDay(), FinancialMovementStatus.PENDING, UUID.randomUUID(), occurrence));

        final FinancialMovementEntity stored = repository.findByCode(created.code()).orElseThrow();
        assertEquals(occurrence, stored.getCompetenceDate());
        assertEquals(occurrence, stored.getOccurrenceDate());
        assertFalse(stored.getCompetenceOverridden());
    }

    @Test
    void shouldKeepManualCompetenceAcrossUpdatesAndRestoreAutomaticOnRequest() {
        final LocalDateTime movementDate = LocalDateTime.of(2053, 6, 5, 10, 0);
        final LocalDate manual = LocalDate.of(2053, 5, 1);
        final FinancialMovement created = service.create(movement(movementDate, movementDate, manual, null));
        assertTrue(repository.findByCode(created.code()).orElseThrow().getCompetenceOverridden());

        service.update(update(created.code(), LocalDateTime.of(2053, 7, 9, 10, 0), null, false));
        final FinancialMovementEntity afterMovementDateChange = repository.findByCode(created.code()).orElseThrow();
        assertEquals(manual, afterMovementDateChange.getCompetenceDate());
        assertTrue(afterMovementDateChange.getCompetenceOverridden());

        service.update(update(created.code(), null, null, true));
        final FinancialMovementEntity restored = repository.findByCode(created.code()).orElseThrow();
        assertEquals(LocalDate.of(2053, 7, 9), restored.getCompetenceDate());
        assertFalse(restored.getCompetenceOverridden());

        service.update(update(created.code(), LocalDateTime.of(2053, 8, 2, 10, 0), null, false));
        assertEquals(LocalDate.of(2053, 8, 2), repository.findByCode(created.code()).orElseThrow().getCompetenceDate());
    }

    @Test
    void shouldKeepCashQueriesIndependentOfCompetence() {
        final LocalDateTime movementDate = LocalDateTime.of(2054, 1, 5, 10, 0);
        final LocalDateTime dueDate = LocalDateTime.of(2054, 1, 20, 0, 0);
        final FinancialMovement pending = service.create(movement(movementDate, dueDate, LocalDate.of(2040, 1, 1), null));
        final FinancialMovement paid = service.create(new FinancialMovement(
                UUID.randomUUID(), OperationType.DEBIT, "Pago", new BigDecimal("100.00"), null,
                movementDate, dueDate, null, FinancialMovementStatus.PENDING, Boolean.TRUE, null, null, null,
                LocalDate.of(2040, 1, 1), false));
        settlementService.create(paid.code(), new MovementSettlement(
                null, null, new BigDecimal("80.00"), LocalDate.of(2054, 2, 3),
                null, null, null, null, null));

        // The outstanding residual follows due date; each settled amount follows its effective date.
        final List<UUID> janWindow = codesIn(LocalDate.of(2054, 1, 1), LocalDate.of(2054, 1, 31));
        final List<MovementSettlement> febSettlements = settlementService.findForCashDateBetween(
                LocalDate.of(2054, 2, 1), LocalDate.of(2054, 2, 28));
        final List<UUID> competenceWindow = codesIn(LocalDate.of(2040, 1, 1), LocalDate.of(2040, 1, 31));

        assertTrue(janWindow.contains(pending.code()));
        assertTrue(janWindow.contains(paid.code()));
        assertEquals(List.of(paid.code()), febSettlements.stream().map(MovementSettlement::movementCode).toList());
        assertTrue(competenceWindow.isEmpty());
        assertEquals(new BigDecimal("-80.00"), service.findBalance(LocalDate.of(2054, 2, 28))
                .subtract(service.findBalance(LocalDate.of(2054, 1, 31))));
    }

    @Test
    void shouldFindTheActiveMovementsWhoseCompetenceIsInAnIntervalWithTheEndExcluded() {
        final LocalDate from = LocalDate.of(2061, 1, 5);
        final LocalDate toExclusive = LocalDate.of(2061, 1, 10);
        final FinancialMovement before = createWithCompetence(LocalDate.of(2061, 1, 4));
        final FinancialMovement onTheFirstDay = createWithCompetence(from);
        final FinancialMovement inside = createWithCompetence(LocalDate.of(2061, 1, 9));
        final FinancialMovement onTheEndDay = createWithCompetence(toExclusive);
        final FinancialMovement deleted = createWithCompetence(LocalDate.of(2061, 1, 7));
        service.delete(deleted.code());

        final List<UUID> found = service.findByCompetenceInterval(from, toExclusive).stream()
                .map(FinancialMovement::code)
                .toList();

        assertEquals(List.of(onTheFirstDay.code(), inside.code()), found);
        assertFalse(found.contains(before.code()));
        assertFalse(found.contains(onTheEndDay.code()));
        assertFalse(found.contains(deleted.code()));
    }

    @Test
    void shouldFindTheMovementsOfAnIntervalOldestCompetenceFirst() {
        final FinancialMovement later = createWithCompetence(LocalDate.of(2062, 3, 9));
        final FinancialMovement earlier = createWithCompetence(LocalDate.of(2062, 3, 6));

        final List<UUID> found = service.findByCompetenceInterval(LocalDate.of(2062, 3, 5), LocalDate.of(2062, 3, 10))
                .stream()
                .map(FinancialMovement::code)
                .toList();

        assertEquals(List.of(earlier.code(), later.code()), found);
    }

    @Test
    void shouldFindNothingInAnIntervalWithNoMovements() {
        assertTrue(service.findByCompetenceInterval(LocalDate.of(2063, 1, 1), LocalDate.of(2063, 2, 1)).isEmpty());
    }

    private FinancialMovement createWithCompetence(final LocalDate competenceDate) {
        final LocalDateTime date = competenceDate.atTime(10, 0);
        return service.create(movement(date, date, competenceDate, null));
    }

    private List<UUID> codesIn(final LocalDate start, final LocalDate end) {
        return service.findMovementsForBalanceCalculation(start, end).stream().map(FinancialMovement::code).toList();
    }

    private static FinancialMovement movement(final LocalDateTime movementDate, final LocalDateTime dueDate,
                                              final LocalDate competenceDate, final UUID recurrenceBillCode) {
        return new FinancialMovement(UUID.randomUUID(), OperationType.DEBIT, "Padaria", new BigDecimal("12.50"),
                null, movementDate, dueDate, null, FinancialMovementStatus.PENDING, Boolean.TRUE,
                recurrenceBillCode, null, null, competenceDate, false);
    }

    private static FinancialMovementUpdate update(final UUID code, final LocalDateTime movementDate,
                                                  final LocalDate competenceDate, final boolean restore) {
        return new FinancialMovementUpdate(code, null, null, null, null, movementDate, null, null, null, null,
                competenceDate, restore);
    }
}
