package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.balance.BalanceService;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlement;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlementService;
import com.bts.personalbudget.exception.ConflictException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringBootTest
class MovementSettlementIntegrationTest {

    @Container
    static final MySQLContainer<MySQLContainerConfig> MYSQL = MySQLContainerConfig.getInstance();

    @Autowired
    private FinancialMovementService movementService;

    @Autowired
    private MovementSettlementService settlementService;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private JdbcTemplate jdbc;

    private final ConcurrentLinkedQueue<UUID> movementCodes = new ConcurrentLinkedQueue<>();

    @AfterEach
    void cleanUp() {
        UUID movementCode;
        while ((movementCode = movementCodes.poll()) != null) {
            jdbc.update("DELETE FROM movement_settlement WHERE financial_movement_id IN "
                            + "(SELECT id FROM financial_movement WHERE code = UUID_TO_BIN(?))",
                    movementCode.toString());
            jdbc.update("DELETE FROM financial_movement WHERE code = UUID_TO_BIN(?)", movementCode.toString());
        }
    }

    @Test
    void shouldUseIndividualEffectiveDatesForCashAndProjectOnlyTheResidual() {
        final LocalDate firstPaymentDate = LocalDate.of(2088, 1, 5);
        final LocalDate beforeSecondPaymentDate = LocalDate.of(2088, 1, 19);
        final LocalDate secondPaymentDate = LocalDate.of(2088, 1, 20);
        final LocalDate dueDate = LocalDate.of(2088, 12, 31);
        final BigDecimal firstCashBefore = settlementService.sumActiveSignedAmountsThrough(firstPaymentDate);
        final BigDecimal secondCashBefore = settlementService.sumActiveSignedAmountsThrough(beforeSecondPaymentDate);
        final BigDecimal firstProjectionBefore = movementService.findProjectedBalance(beforeSecondPaymentDate);
        final BigDecimal secondProjectionBefore = movementService.findProjectedBalance(secondPaymentDate);
        final BigDecimal dueProjectionBefore = movementService.findProjectedBalance(dueDate);
        final long firstDateEntriesBefore = balanceService.findBalanceCalcDataBetween(
                        LocalDate.of(2088, 1, 1), LocalDate.of(2088, 1, 31)).stream()
                .filter(data -> data.findBalanceCalcDate().equals(firstPaymentDate)).count();
        final FinancialMovement movement = createMovement(OperationType.DEBIT, "100.00");
        settlementService.create(movement.code(), settlement("30.00", firstPaymentDate));
        settlementService.create(movement.code(), settlement("20.00", secondPaymentDate));

        assertThat(settlementService.sumActiveSignedAmountsThrough(firstPaymentDate).subtract(firstCashBefore))
                .isEqualByComparingTo("-30.00");
        assertThat(settlementService.sumActiveSignedAmountsThrough(beforeSecondPaymentDate).subtract(secondCashBefore))
                .isEqualByComparingTo("-30.00");
        assertThat(movementService.findProjectedBalance(beforeSecondPaymentDate).subtract(firstProjectionBefore))
                .isEqualByComparingTo("-30.00");
        assertThat(movementService.findProjectedBalance(secondPaymentDate).subtract(secondProjectionBefore))
                .isEqualByComparingTo("-50.00");
        assertThat(movementService.findProjectedBalance(dueDate).subtract(dueProjectionBefore))
                .isEqualByComparingTo("-100.00");
        assertThat(balanceService.findBalanceCalcDataBetween(
                        LocalDate.of(2088, 1, 1), LocalDate.of(2088, 1, 31)))
                .filteredOn(data -> data.findBalanceCalcDate().equals(LocalDate.of(2088, 1, 5)))
                .hasSize(Math.toIntExact(firstDateEntriesBefore + 1));
    }

    @Test
    void shouldKeepReversedSettlementInHistoryAndExcludeItFromCash() {
        final LocalDate effectiveDate = LocalDate.of(2088, 2, 1);
        final BigDecimal cashBefore = settlementService.sumActiveSignedAmountsThrough(effectiveDate);
        final FinancialMovement movement = createMovement(OperationType.CREDIT, "75.00");
        final MovementSettlement created =
                settlementService.create(movement.code(), settlement("25.00", effectiveDate));
        assertThat(settlementService.sumActiveSignedAmountsThrough(effectiveDate).subtract(cashBefore))
                .isEqualByComparingTo("25.00");

        final MovementSettlement reversed = settlementService.reverse(movement.code(), created.code());
        final List<MovementSettlement> history = settlementService.findAll(movement.code());

        assertThat(reversed.reversedAt()).isNotNull();
        assertThat(reversed.version()).isEqualTo(created.version() + 1);
        assertThat(reversed.version()).isEqualTo(history.getFirst().version());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().reversedAt()).isNotNull();
        assertThat(settlementService.sumActiveSignedAmountsThrough(effectiveDate))
                .isEqualByComparingTo(cashBefore);
        assertThat(movementService.find(movement.code()).amountPaid()).isEqualByComparingTo("0.00");
        assertThat(movementService.find(movement.code()).status()).isEqualTo(FinancialMovementStatus.PENDING);
    }

    @Test
    void shouldSerializeConcurrentSettlementsAndPreventOverpayment() throws Exception {
        final FinancialMovement movement = createMovement(OperationType.CREDIT, "100.00");
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<Boolean> first = executor.submit(() -> tryConcurrentSettlement(movement.code(), ready, start));
            final Future<Boolean> second = executor.submit(() -> tryConcurrentSettlement(movement.code(), ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            final int accepted = (first.get(30, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(30, TimeUnit.SECONDS) ? 1 : 0);

            assertThat(accepted).isEqualTo(1);
            assertThat(settlementService.findAll(movement.code()))
                    .filteredOn(settlement -> settlement.reversedAt() == null)
                    .hasSize(1);
            assertThat(movementService.find(movement.code()).amountPaid()).isEqualByComparingTo("60.00");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectAmountLargerThanRemainingBalance() {
        final FinancialMovement movement = createMovement(OperationType.DEBIT, "100.00");
        settlementService.create(movement.code(), settlement("75.00", LocalDate.of(2088, 3, 1)));

        assertThrows(ConflictException.class, () -> settlementService.create(
                movement.code(), settlement("30.00", LocalDate.of(2088, 3, 2))));
        assertThat(movementService.find(movement.code()).amountPaid()).isEqualByComparingTo("-75.00");
    }

    private boolean tryConcurrentSettlement(final UUID movementCode,
                                            final CountDownLatch ready,
                                            final CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            settlementService.create(movementCode, settlement("60.00", LocalDate.of(2088, 4, 1)));
            return true;
        } catch (ConflictException exception) {
            return false;
        }
    }

    private FinancialMovement createMovement(final OperationType type, final String amount) {
        final LocalDateTime dueDate = LocalDateTime.of(2088, 12, 31, 0, 0);
        final FinancialMovement movement = movementService.create(new FinancialMovement(
                UUID.randomUUID(), type, "Teste E7", new BigDecimal(amount), null,
                dueDate.minusDays(1), dueDate, null, FinancialMovementStatus.PENDING,
                true, null, null, null));
        movementCodes.add(movement.code());
        return movement;
    }

    private static MovementSettlement settlement(final String amount, final LocalDate effectiveDate) {
        return new MovementSettlement(null, null, new BigDecimal(amount), effectiveDate,
                null, null, null, null, null);
    }
}
