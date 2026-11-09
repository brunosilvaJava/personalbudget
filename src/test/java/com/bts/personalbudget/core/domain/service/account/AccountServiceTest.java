package com.bts.personalbudget.core.domain.service.account;

import com.bts.personalbudget.core.domain.entity.AccountEntity;
import com.bts.personalbudget.core.domain.entity.AccountTransferEntity;
import com.bts.personalbudget.core.domain.entity.CardInvoicePaymentEntity;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.entity.MovementSettlementEntity;
import com.bts.personalbudget.core.domain.enumerator.AccountStatementEntryType;
import com.bts.personalbudget.core.domain.enumerator.AccountStatus;
import com.bts.personalbudget.core.domain.enumerator.AccountType;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.factory.AccountFactory;
import com.bts.personalbudget.core.domain.model.Account;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.AccountMapper;
import com.bts.personalbudget.repository.AccountRepository;
import com.bts.personalbudget.repository.AccountTransferRepository;
import com.bts.personalbudget.repository.MovementSettlementRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    @InjectMocks
    private AccountService service;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private MovementSettlementRepository settlementRepository;

    @Mock
    private AccountTransferRepository transferRepository;

    @Spy
    private AccountMapper accountMapper = Mappers.getMapper(AccountMapper.class);

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    @Test
    void shouldCreateActiveNonDefaultAccountWithZeroBalanceAndTodayWhenOmitted() {
        when(accountRepository.existsByName("Carteira")).thenReturn(false);
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final Account created = service.create(AccountFactory.buildNewModel("  Carteira "));

        assertThat(created.name()).isEqualTo("Carteira");
        assertThat(created.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(created.defaultAccount()).isFalse();
        assertThat(created.initialBalance()).isEqualByComparingTo("0");
        assertThat(created.initialBalanceDate()).isEqualTo(TODAY);
    }

    @Test
    void shouldRejectCreationWhenNameIsAlreadyUsed() {
        when(accountRepository.existsByName("Carteira")).thenReturn(true);

        assertThrows(InvalidFieldsException.class, () -> service.create(AccountFactory.buildNewModel("Carteira")));

        verify(accountRepository, never()).save(any());
    }

    @Test
    void shouldRejectCreationWhenNameIsTooShortAfterTrim() {
        assertThrows(InvalidFieldsException.class, () -> service.create(AccountFactory.buildNewModel("ab ")));
    }

    @Test
    void shouldListOnlyActiveAccountsWhenNoStatusIsGiven() {
        when(accountRepository.findByStatuses(List.of(AccountStatus.ACTIVE)))
                .thenReturn(List.of(AccountFactory.buildEntity()));

        assertThat(service.find(null)).hasSize(1);
        assertThat(service.find(List.of())).hasSize(1);
    }

    @Test
    void shouldReturnNotFoundWhenAccountCodeDoesNotExist() {
        final UUID code = UUID.randomUUID();
        when(accountRepository.findByCode(code)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.findByCode(code));
    }

    @Test
    void shouldReleaseCurrentDefaultBeforeMarkingAnotherAccountAsDefault() {
        final AccountEntity current = AccountFactory.buildEntity();
        final AccountEntity other = AccountFactory.buildEntity(2L, AccountFactory.OTHER_ACCOUNT_CODE, "Carteira", false);
        when(accountRepository.findByCode(other.getCode())).thenReturn(Optional.of(other));
        when(accountRepository.findByDefaultAccountTrue()).thenReturn(Optional.of(current));
        when(accountRepository.save(other)).thenReturn(other);

        final Account updated = service.update(other.getCode(), null, null, null, null, true);

        assertThat(updated.defaultAccount()).isTrue();
        assertThat(current.isDefaultAccount()).isFalse();
        // The unique slot allows a single default: the release must be flushed before the new one is saved.
        final var order = inOrder(accountRepository);
        order.verify(accountRepository).saveAndFlush(current);
        order.verify(accountRepository).save(other);
    }

    @Test
    void shouldRejectMakingAnInactiveAccountTheDefault() {
        final AccountEntity inactive = AccountFactory.buildEntity(2L, AccountFactory.OTHER_ACCOUNT_CODE, "Carteira", false);
        inactive.setStatus(AccountStatus.INACTIVE);
        when(accountRepository.findByCode(inactive.getCode())).thenReturn(Optional.of(inactive));

        assertThrows(ConflictException.class,
                () -> service.update(inactive.getCode(), null, null, null, null, true));

        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectUnmarkingTheDefaultFlagDirectly() {
        assertThrows(InvalidFieldsException.class,
                () -> service.update(AccountFactory.ACCOUNT_CODE, null, null, null, null, false));

        verify(accountRepository, never()).save(any());
    }

    @Test
    void shouldRejectRenameToANameUsedByAnotherAccount() {
        final AccountEntity account = AccountFactory.buildEntity();
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));
        when(accountRepository.existsByNameAndCodeNot("Carteira", account.getCode())).thenReturn(true);

        assertThrows(InvalidFieldsException.class,
                () -> service.update(account.getCode(), "Carteira", null, null, null, null));
    }

    @Test
    void shouldUpdateNameTypeAndInitialBalance() {
        final AccountEntity account = AccountFactory.buildEntity();
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));
        when(accountRepository.existsByNameAndCodeNot("Nubank", account.getCode())).thenReturn(false);
        when(accountRepository.save(account)).thenReturn(account);

        final Account updated = service.update(account.getCode(), "Nubank", AccountType.SAVINGS,
                new BigDecimal("250.00"), null, null);

        assertThat(updated.name()).isEqualTo("Nubank");
        assertThat(updated.type()).isEqualTo(AccountType.SAVINGS);
        assertThat(updated.initialBalance()).isEqualByComparingTo("250.00");
    }

    @Test
    void shouldNotSaveWhenNothingChanges() {
        final AccountEntity account = AccountFactory.buildEntity();
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));

        service.update(account.getCode(), account.getName(), account.getType(), account.getInitialBalance(),
                account.getInitialBalanceDate(), null);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void shouldRejectInitialBalanceDateLaterThanTheFirstActiveSettlement() {
        final AccountEntity account = AccountFactory.buildEntity();
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));
        when(settlementRepository.findEarliestActiveEffectiveDateByAccount(account.getId()))
                .thenReturn(Optional.of(LocalDate.of(2026, 3, 10)));

        assertThrows(InvalidFieldsException.class, () -> service.update(account.getCode(), null, null, null,
                LocalDate.of(2026, 3, 11), null));

        verify(accountRepository, never()).save(any());
    }

    @Test
    void shouldAllowInitialBalanceDateOnTheFirstSettlementDay() {
        final AccountEntity account = AccountFactory.buildEntity();
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));
        when(settlementRepository.findEarliestActiveEffectiveDateByAccount(account.getId()))
                .thenReturn(Optional.of(LocalDate.of(2026, 3, 10)));
        when(accountRepository.save(account)).thenReturn(account);

        final Account updated = service.update(account.getCode(), null, null, null, LocalDate.of(2026, 3, 10), null);

        assertThat(updated.initialBalanceDate()).isEqualTo(LocalDate.of(2026, 3, 10));
    }

    @Test
    void shouldRefuseToInactivateTheDefaultAccount() {
        final AccountEntity account = AccountFactory.buildEntity();
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));

        assertThrows(ConflictException.class, () -> service.inactivate(account.getCode()));

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void shouldInactivateAndActivateANonDefaultAccountIdempotently() {
        final AccountEntity account = AccountFactory.buildEntity(2L, AccountFactory.OTHER_ACCOUNT_CODE, "Carteira", false);
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        assertThat(service.inactivate(account.getCode()).status()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(service.inactivate(account.getCode()).status()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(service.activate(account.getCode()).status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(service.activate(account.getCode()).status()).isEqualTo(AccountStatus.ACTIVE);
        verify(accountRepository, org.mockito.Mockito.times(2)).save(account);
    }

    @Test
    void shouldAddInitialBalanceAndSettlementsOnTheBalance() {
        final AccountEntity account = AccountFactory.buildEntity();
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));
        when(settlementRepository.sumActiveSignedAmountsByAccountBetween(
                eq(account.getId()), eq(account.getInitialBalanceDate()), eq(LocalDate.of(2026, 11, 5)),
                eq(OperationType.DEBIT))).thenReturn(new BigDecimal("-30.00"));
        stubTransfers(account, LocalDate.of(2026, 11, 5), BigDecimal.ZERO, BigDecimal.ZERO);

        final AccountBalance balance = service.balance(account.getCode(), LocalDate.of(2026, 11, 5));

        assertThat(balance.initialBalance()).isEqualByComparingTo("100.00");
        assertThat(balance.settlementsTotal()).isEqualByComparingTo("-30.00");
        assertThat(balance.transfersTotal()).isEqualByComparingTo("0");
        assertThat(balance.balance()).isEqualByComparingTo("70.00");
    }

    @Test
    void shouldAddIncomingAndSubtractOutgoingTransfersOnTheBalance() {
        final AccountEntity account = AccountFactory.buildEntity();
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));
        when(settlementRepository.sumActiveSignedAmountsByAccountBetween(
                eq(account.getId()), eq(account.getInitialBalanceDate()), eq(LocalDate.of(2026, 11, 5)),
                eq(OperationType.DEBIT))).thenReturn(new BigDecimal("-30.00"));
        stubTransfers(account, LocalDate.of(2026, 11, 5), new BigDecimal("500.00"), new BigDecimal("120.00"));

        final AccountBalance balance = service.balance(account.getCode(), LocalDate.of(2026, 11, 5));

        assertThat(balance.transfersTotal()).isEqualByComparingTo("380.00");
        assertThat(balance.balance()).isEqualByComparingTo("450.00");
    }

    @Test
    void shouldSumEveryAccountOnTheTotalAndLetTransfersCancelOut() {
        final AccountEntity main = AccountFactory.buildEntity();
        final AccountEntity wallet = AccountFactory.buildEntity(2L, AccountFactory.OTHER_ACCOUNT_CODE, "Carteira", false);
        wallet.setInitialBalance(new BigDecimal("50.00"));
        final LocalDate date = LocalDate.of(2026, 11, 5);
        when(accountRepository.findByStatuses(List.of(AccountStatus.ACTIVE, AccountStatus.INACTIVE)))
                .thenReturn(List.of(main, wallet));
        when(settlementRepository.sumActiveSignedAmountsByAccountBetween(
                any(), eq(main.getInitialBalanceDate()), eq(date), eq(OperationType.DEBIT)))
                .thenReturn(new BigDecimal("-30.00"));
        // 200.00 leaves the main account and enters the wallet
        stubTransfers(main, date, BigDecimal.ZERO, new BigDecimal("200.00"));
        stubTransfers(wallet, date, new BigDecimal("200.00"), BigDecimal.ZERO);

        final AccountsBalance result = service.balances(date);

        assertThat(result.accounts()).hasSize(2);
        assertThat(result.accounts().get(0).balance()).isEqualByComparingTo("-130.00");
        assertThat(result.accounts().get(1).balance()).isEqualByComparingTo("220.00");
        assertThat(result.total()).isEqualByComparingTo("90.00");
    }

    @Test
    void shouldRejectInitialBalanceDateLaterThanTheFirstActiveTransfer() {
        final AccountEntity account = AccountFactory.buildEntity();
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));
        when(settlementRepository.findEarliestActiveEffectiveDateByAccount(account.getId()))
                .thenReturn(Optional.empty());
        when(transferRepository.findEarliestActiveEffectiveDateByAccount(account.getId()))
                .thenReturn(Optional.of(LocalDate.of(2026, 3, 10)));

        assertThrows(InvalidFieldsException.class, () -> service.update(account.getCode(), null, null, null,
                LocalDate.of(2026, 3, 11), null));

        verify(accountRepository, never()).save(any());
    }

    private void stubTransfers(final AccountEntity account, final LocalDate date,
                               final BigDecimal incoming, final BigDecimal outgoing) {
        when(transferRepository.sumActiveIncomingBetween(account.getId(), account.getInitialBalanceDate(), date))
                .thenReturn(incoming);
        when(transferRepository.sumActiveOutgoingBetween(account.getId(), account.getInitialBalanceDate(), date))
                .thenReturn(outgoing);
    }

    @Test
    void shouldUseTodayOnTheBalanceWhenNoDateIsGiven() {
        final AccountEntity account = AccountFactory.buildEntity();
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));
        when(settlementRepository.sumActiveSignedAmountsByAccountBetween(
                eq(account.getId()), eq(account.getInitialBalanceDate()), eq(TODAY), eq(OperationType.DEBIT)))
                .thenReturn(BigDecimal.ZERO);
        stubTransfers(account, TODAY, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(service.balance(account.getCode(), null).date()).isEqualTo(TODAY);
    }

    @Test
    void shouldReturnZeroBalanceBeforeTheInitialBalanceDateWithoutQueryingSettlements() {
        final AccountEntity account = AccountFactory.buildEntity();
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));

        final AccountBalance balance = service.balance(account.getCode(), LocalDate.of(2025, 12, 31));

        assertThat(balance.balance()).isEqualByComparingTo("0");
        assertThat(balance.initialBalance()).isEqualByComparingTo("0");
        verify(settlementRepository, never()).sumActiveSignedAmountsByAccountBetween(
                any(), any(), any(), any());
    }

    @Test
    void shouldListInitialBalanceSettlementsAndTransfersWithTheRunningBalance() {
        final AccountEntity account = AccountFactory.buildEntity();
        final AccountEntity other = AccountFactory.buildEntity(2L, AccountFactory.OTHER_ACCOUNT_CODE, "Carteira", false);
        final LocalDate start = LocalDate.of(2026, 1, 1);
        final LocalDate end = LocalDate.of(2026, 1, 31);
        final UUID categoryCode = UUID.randomUUID();
        final MovementSettlementEntity expense = settlementEntity(OperationType.DEBIT, "Mercado", "30.00",
                LocalDate.of(2026, 1, 5), categoryCode, null);
        final MovementSettlementEntity income = settlementEntity(OperationType.CREDIT, "Freelance", "50.00",
                LocalDate.of(2026, 1, 10), null, null);
        final AccountTransferEntity incoming = transferEntity(20L, other, account, "200.00", LocalDate.of(2026, 1, 3));
        final AccountTransferEntity outgoing = transferEntity(21L, account, other, "20.00", LocalDate.of(2026, 1, 5));
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));
        when(settlementRepository.findActiveByAccountBetween(account.getId(), start, end))
                .thenReturn(List.of(expense, income));
        when(transferRepository.findActiveByAccountBetween(account.getId(), start, end))
                .thenReturn(List.of(incoming, outgoing));

        final AccountStatement statement = service.statement(account.getCode(), start, end);

        assertThat(statement.openingBalance()).isEqualByComparingTo("0");
        assertThat(statement.entries()).extracting(AccountStatementEntry::type).containsExactly(
                AccountStatementEntryType.INITIAL_BALANCE, AccountStatementEntryType.TRANSFER_IN,
                AccountStatementEntryType.SETTLEMENT, AccountStatementEntryType.TRANSFER_OUT,
                AccountStatementEntryType.SETTLEMENT);
        assertThat(statement.entries()).extracting(AccountStatementEntry::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("-30"),
                        new BigDecimal("-20"), new BigDecimal("50"));
        assertThat(statement.entries()).extracting(AccountStatementEntry::balance)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("100"), new BigDecimal("300"), new BigDecimal("270"),
                        new BigDecimal("250"), new BigDecimal("300"));
        assertThat(statement.closingBalance()).isEqualByComparingTo("300");
        final AccountStatementEntry settlementLine = statement.entries().get(2);
        assertThat(settlementLine.description()).isEqualTo("Mercado");
        assertThat(settlementLine.movementCode()).isEqualTo(expense.getFinancialMovement().getCode());
        assertThat(settlementLine.settlementCode()).isEqualTo(expense.getCode());
        assertThat(settlementLine.categoryCode()).isEqualTo(categoryCode);
        assertThat(statement.entries().get(1).counterpartAccountCode()).isEqualTo(AccountFactory.OTHER_ACCOUNT_CODE);
        assertThat(statement.entries().get(3).counterpartAccountCode()).isEqualTo(AccountFactory.OTHER_ACCOUNT_CODE);
    }

    @Test
    void shouldOpenTheStatementWithTheBalanceOfTheDayBeforeThePeriod() {
        final AccountEntity account = AccountFactory.buildEntity();
        final LocalDate start = LocalDate.of(2026, 11, 1);
        final LocalDate end = LocalDate.of(2026, 11, 30);
        final UUID paymentCode = UUID.randomUUID();
        final MovementSettlementEntity installment = settlementEntity(OperationType.DEBIT, "Geladeira (2/10)",
                "389.90", LocalDate.of(2026, 11, 10), null, paymentCode);
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));
        when(settlementRepository.sumActiveSignedAmountsByAccountBetween(
                eq(account.getId()), eq(account.getInitialBalanceDate()), eq(LocalDate.of(2026, 10, 31)),
                eq(OperationType.DEBIT))).thenReturn(new BigDecimal("900.00"));
        stubTransfers(account, LocalDate.of(2026, 10, 31), BigDecimal.ZERO, BigDecimal.ZERO);
        when(settlementRepository.findActiveByAccountBetween(account.getId(), start, end))
                .thenReturn(List.of(installment));
        when(transferRepository.findActiveByAccountBetween(account.getId(), start, end)).thenReturn(List.of());

        final AccountStatement statement = service.statement(account.getCode(), start, end);

        assertThat(statement.openingBalance()).isEqualByComparingTo("1000.00");
        assertThat(statement.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.type()).isEqualTo(AccountStatementEntryType.SETTLEMENT);
            assertThat(entry.amount()).isEqualByComparingTo("-389.90");
            assertThat(entry.balance()).isEqualByComparingTo("610.10");
            assertThat(entry.invoicePaymentCode()).isEqualTo(paymentCode);
        });
        assertThat(statement.closingBalance()).isEqualByComparingTo("610.10");
    }

    @Test
    void shouldReturnAnEmptyStatementForAPeriodBeforeTheInitialBalanceDate() {
        final AccountEntity account = AccountFactory.buildEntity();
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));

        final AccountStatement statement =
                service.statement(account.getCode(), LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31));

        assertThat(statement.entries()).isEmpty();
        assertThat(statement.openingBalance()).isEqualByComparingTo("0");
        assertThat(statement.closingBalance()).isEqualByComparingTo("0");
        verify(settlementRepository, never()).findActiveByAccountBetween(any(), any(), any());
        verify(transferRepository, never()).findActiveByAccountBetween(any(), any(), any());
    }

    @Test
    void shouldRejectAStatementWithoutDatesInvertedOrLongerThan366Days() {
        final UUID code = AccountFactory.ACCOUNT_CODE;

        assertThrows(InvalidFieldsException.class,
                () -> service.statement(code, null, LocalDate.of(2026, 11, 30)));
        assertThrows(InvalidFieldsException.class,
                () -> service.statement(code, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1)));
        assertThrows(InvalidFieldsException.class,
                () -> service.statement(code, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 3)));
        verify(accountRepository, never()).findByCode(any());
    }

    private static MovementSettlementEntity settlementEntity(final OperationType type, final String description,
                                                             final String amount, final LocalDate date,
                                                             final UUID categoryCode, final UUID paymentCode) {
        final FinancialMovementEntity movement = FinancialMovementEntity.builder()
                .id(100L)
                .code(UUID.randomUUID())
                .operationType(type)
                .description(description)
                .amount(type == OperationType.DEBIT ? new BigDecimal(amount).negate() : new BigDecimal(amount))
                .categoryCode(categoryCode)
                .build();
        return MovementSettlementEntity.builder()
                .id(200L)
                .code(UUID.randomUUID())
                .financialMovement(movement)
                .invoicePayment(paymentCode == null ? null : CardInvoicePaymentEntity.builder().code(paymentCode).build())
                .amount(new BigDecimal(amount))
                .effectiveDate(date)
                .version(0L)
                .build();
    }

    private static AccountTransferEntity transferEntity(final Long id, final AccountEntity from, final AccountEntity to,
                                                        final String amount, final LocalDate date) {
        return AccountTransferEntity.builder()
                .id(id)
                .code(UUID.randomUUID())
                .fromAccount(from)
                .toAccount(to)
                .amount(new BigDecimal(amount))
                .effectiveDate(date)
                .description("Reserva")
                .version(0L)
                .build();
    }
}
