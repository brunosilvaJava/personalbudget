package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.enumerator.AccountStatementEntryType;
import com.bts.personalbudget.core.domain.enumerator.AccountType;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.Account;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.account.AccountBalance;
import com.bts.personalbudget.core.domain.service.account.AccountService;
import com.bts.personalbudget.core.domain.service.account.AccountStatement;
import com.bts.personalbudget.core.domain.service.account.AccountStatementEntry;
import com.bts.personalbudget.core.domain.service.accounttransfer.AccountTransfer;
import com.bts.personalbudget.core.domain.service.accounttransfer.AccountTransferService;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlement;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlementService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class AccountIntegrationTest {

    @Container
    static final MySQLContainer<MySQLContainerConfig> MYSQL = MySQLContainerConfig.getInstance();

    @Autowired
    private AccountService accountService;

    @Autowired
    private FinancialMovementService movementService;

    @Autowired
    private MovementSettlementService settlementService;

    @Autowired
    private AccountTransferService transferService;

    @Autowired
    private JdbcTemplate jdbc;

    private final ConcurrentLinkedQueue<UUID> movementCodes = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<UUID> transferCodes = new ConcurrentLinkedQueue<>();

    @AfterEach
    void cleanUp() {
        UUID transferCode;
        while ((transferCode = transferCodes.poll()) != null) {
            jdbc.update("DELETE FROM account_transfer WHERE code = UUID_TO_BIN(?)", transferCode.toString());
        }
        UUID movementCode;
        while ((movementCode = movementCodes.poll()) != null) {
            jdbc.update("DELETE FROM movement_settlement WHERE financial_movement_id IN "
                            + "(SELECT id FROM financial_movement WHERE code = UUID_TO_BIN(?))",
                    movementCode.toString());
            jdbc.update("DELETE FROM financial_movement WHERE code = UUID_TO_BIN(?)", movementCode.toString());
        }
    }

    @Test
    void shouldComputeBalancePerAccountFromInitialBalanceAndSettlementsAndSplitByAccount() {
        final Account wallet = accountService.create(new Account(null, "Carteira " + UUID.randomUUID(),
                AccountType.CASH, new BigDecimal("500.00"), LocalDate.of(2090, 1, 1), null, false));
        final Account defaultAccount = accountService.find(null).stream()
                .filter(Account::defaultAccount).findFirst().orElseThrow();
        final BigDecimal defaultBefore = accountService
                .balance(defaultAccount.code(), LocalDate.of(2090, 1, 31)).balance();
        final FinancialMovement expense = createMovement(OperationType.DEBIT, "100.00");
        final FinancialMovement income = createMovement(OperationType.CREDIT, "40.00");

        settlementService.create(expense.code(), settlement("30.00", LocalDate.of(2090, 1, 5), wallet.code()));
        settlementService.create(income.code(), settlement("40.00", LocalDate.of(2090, 1, 10), wallet.code()));
        final MovementSettlement onDefault =
                settlementService.create(expense.code(), settlement("20.00", LocalDate.of(2090, 1, 6), null));

        assertThat(onDefault.accountCode()).isEqualTo(defaultAccount.code());
        assertThat(accountService.balance(wallet.code(), LocalDate.of(2090, 1, 4)).balance())
                .isEqualByComparingTo("500.00");
        assertThat(accountService.balance(wallet.code(), LocalDate.of(2090, 1, 5)).balance())
                .isEqualByComparingTo("470.00");
        final AccountBalance walletEnd = accountService.balance(wallet.code(), LocalDate.of(2090, 1, 31));
        assertThat(walletEnd.settlementsTotal()).isEqualByComparingTo("10.00");
        assertThat(walletEnd.balance()).isEqualByComparingTo("510.00");
        assertThat(accountService.balance(defaultAccount.code(), LocalDate.of(2090, 1, 31)).balance()
                .subtract(defaultBefore)).isEqualByComparingTo("-20.00");
        assertThat(accountService.balance(wallet.code(), LocalDate.of(2089, 12, 31)).balance())
                .isEqualByComparingTo("0");
    }

    @Test
    void shouldExcludeReversedSettlementsFromTheAccountBalance() {
        final Account wallet = accountService.create(new Account(null, "Reserva " + UUID.randomUUID(),
                AccountType.SAVINGS, new BigDecimal("100.00"), LocalDate.of(2091, 1, 1), null, false));
        final FinancialMovement expense = createMovement(OperationType.DEBIT, "50.00");
        final MovementSettlement created =
                settlementService.create(expense.code(), settlement("50.00", LocalDate.of(2091, 1, 2), wallet.code()));
        assertThat(accountService.balance(wallet.code(), LocalDate.of(2091, 1, 31)).balance())
                .isEqualByComparingTo("50.00");

        settlementService.reverse(expense.code(), created.code());

        assertThat(accountService.balance(wallet.code(), LocalDate.of(2091, 1, 31)).balance())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void shouldMoveTheDefaultFlagWithoutBreakingTheSingleDefaultConstraint() {
        final Account original = accountService.find(null).stream()
                .filter(Account::defaultAccount).findFirst().orElseThrow();
        final Account first = accountService.create(new Account(null, "Padrão A " + UUID.randomUUID(),
                AccountType.CHECKING, null, LocalDate.of(2000, 1, 1), null, false));
        final Account second = accountService.create(new Account(null, "Padrão B " + UUID.randomUUID(),
                AccountType.CHECKING, null, LocalDate.of(2000, 1, 1), null, false));
        try {
            accountService.update(first.code(), null, null, null, null, true);
            accountService.update(second.code(), null, null, null, null, true);

            assertThat(accountService.find(null).stream().filter(Account::defaultAccount))
                    .singleElement().extracting(Account::code).isEqualTo(second.code());
            assertThat(accountService.findByCode(first.code()).defaultAccount()).isFalse();
        } finally {
            // The default account is shared with the other integration tests: give it back.
            accountService.update(original.code(), null, null, null, null, true);
        }
    }

    @Test
    void shouldMoveMoneyBetweenAccountsKeepTheTotalAndRestoreBothBalancesOnReversal() {
        final LocalDate date = LocalDate.of(2092, 1, 31);
        final Account savings = accountService.create(new Account(null, "Poupança " + UUID.randomUUID(),
                AccountType.SAVINGS, new BigDecimal("1000.00"), LocalDate.of(2092, 1, 1), null, false));
        final Account wallet = accountService.create(new Account(null, "Carteira " + UUID.randomUUID(),
                AccountType.CASH, new BigDecimal("50.00"), LocalDate.of(2092, 1, 1), null, false));
        final BigDecimal totalBefore = accountService.balances(date).total();

        final AccountTransfer transfer = transferService.create(new AccountTransfer(null, savings.code(),
                wallet.code(), new BigDecimal("300.00"), LocalDate.of(2092, 1, 10), "Dinheiro vivo", null, null));
        transferCodes.add(transfer.code());

        final AccountBalance savingsBalance = accountService.balance(savings.code(), date);
        assertThat(savingsBalance.transfersTotal()).isEqualByComparingTo("-300.00");
        assertThat(savingsBalance.balance()).isEqualByComparingTo("700.00");
        assertThat(accountService.balance(wallet.code(), date).balance()).isEqualByComparingTo("350.00");
        assertThat(accountService.balance(wallet.code(), LocalDate.of(2092, 1, 9)).balance())
                .isEqualByComparingTo("50.00");
        assertThat(accountService.balances(date).total()).isEqualByComparingTo(totalBefore);

        final AccountTransfer reversed = transferService.reverse(transfer.code());

        assertThat(reversed.reversedAt()).isNotNull();
        assertThat(accountService.balance(savings.code(), date).balance()).isEqualByComparingTo("1000.00");
        assertThat(accountService.balance(wallet.code(), date).balance()).isEqualByComparingTo("50.00");
        assertThat(transferService.find(LocalDate.of(2092, 1, 1), date, savings.code()))
                .extracting(AccountTransfer::code).containsExactly(transfer.code());
    }

    @Test
    void shouldListTheStatementWithTheRunningBalanceLeavingReversedSettlementsOut() {
        final Account wallet = accountService.create(new Account(null, "Extrato " + UUID.randomUUID(),
                AccountType.CASH, new BigDecimal("100.00"), LocalDate.of(2093, 1, 1), null, false));
        final Account savings = accountService.create(new Account(null, "Cofre " + UUID.randomUUID(),
                AccountType.SAVINGS, BigDecimal.ZERO, LocalDate.of(2093, 1, 1), null, false));
        final FinancialMovement expense = createMovement(OperationType.DEBIT, "80.00");
        final FinancialMovement income = createMovement(OperationType.CREDIT, "40.00");
        settlementService.create(expense.code(), settlement("30.00", LocalDate.of(2093, 1, 5), wallet.code()));
        final MovementSettlement reversed =
                settlementService.create(expense.code(), settlement("50.00", LocalDate.of(2093, 1, 6), wallet.code()));
        settlementService.reverse(expense.code(), reversed.code());
        settlementService.create(income.code(), settlement("40.00", LocalDate.of(2093, 1, 10), wallet.code()));
        final AccountTransfer transfer = transferService.create(new AccountTransfer(null, wallet.code(),
                savings.code(), new BigDecimal("20.00"), LocalDate.of(2093, 1, 7), "Reserva", null, null));
        transferCodes.add(transfer.code());

        final AccountStatement january =
                accountService.statement(wallet.code(), LocalDate.of(2093, 1, 1), LocalDate.of(2093, 1, 31));

        assertThat(january.openingBalance()).isEqualByComparingTo("0");
        assertThat(january.entries()).extracting(AccountStatementEntry::type).containsExactly(
                AccountStatementEntryType.INITIAL_BALANCE, AccountStatementEntryType.SETTLEMENT,
                AccountStatementEntryType.TRANSFER_OUT, AccountStatementEntryType.SETTLEMENT);
        assertThat(january.entries()).extracting(AccountStatementEntry::balance)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("100"), new BigDecimal("70"), new BigDecimal("50"),
                        new BigDecimal("90"));
        assertThat(january.entries().get(1).movementCode()).isEqualTo(expense.code());
        assertThat(january.entries().get(2).counterpartAccountCode()).isEqualTo(savings.code());
        assertThat(january.closingBalance()).isEqualByComparingTo(
                accountService.balance(wallet.code(), LocalDate.of(2093, 1, 31)).balance());

        final AccountStatement fromTheSixth =
                accountService.statement(wallet.code(), LocalDate.of(2093, 1, 6), LocalDate.of(2093, 1, 31));

        assertThat(fromTheSixth.openingBalance()).isEqualByComparingTo("70.00");
        assertThat(fromTheSixth.entries()).hasSize(2);
        assertThat(fromTheSixth.closingBalance()).isEqualByComparingTo(january.closingBalance());
    }

    private FinancialMovement createMovement(final OperationType type, final String amount) {
        final LocalDateTime dueDate = LocalDateTime.of(2091, 6, 1, 0, 0);
        final FinancialMovement movement = movementService.create(new FinancialMovement(
                UUID.randomUUID(), type, "Lançamento de conta", new BigDecimal(amount),
                null, dueDate.minusDays(1), dueDate, null, FinancialMovementStatus.PENDING, Boolean.TRUE, null,
                null, null, dueDate.toLocalDate(), false));
        movementCodes.add(movement.code());
        return movement;
    }

    private static MovementSettlement settlement(final String amount, final LocalDate date, final UUID accountCode) {
        return new MovementSettlement(null, null, new BigDecimal(amount), date, null, null, null, null, null,
                accountCode);
    }
}
