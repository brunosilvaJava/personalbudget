package com.bts.personalbudget.core.domain.service.movementsettlement;

import com.bts.personalbudget.core.domain.entity.AccountEntity;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.AccountStatus;
import com.bts.personalbudget.core.domain.factory.AccountFactory;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.repository.AccountRepository;
import com.bts.personalbudget.core.domain.entity.MovementSettlementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleMovementGuard;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.mapper.MovementSettlementMapper;
import com.bts.personalbudget.repository.FinancialMovementRepository;
import com.bts.personalbudget.repository.MovementSettlementRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovementSettlementServiceTest {

    @InjectMocks
    private MovementSettlementService service;

    @Mock
    private FinancialMovementRepository movementRepository;

    @Mock
    private MovementSettlementRepository settlementRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PayCycleMovementGuard payCycleMovementGuard;

    @Spy
    private MovementSettlementMapper mapper = Mappers.getMapper(MovementSettlementMapper.class);

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"),
            ZoneId.of("America/Sao_Paulo"));

    @Test
    void shouldRecordPartialDebitAsPositiveSettlementAndKeepResidualPending() {
        final FinancialMovementEntity movement = movement(OperationType.DEBIT, "-100.00", "0.00");
        when(movementRepository.findForUpdateByCodeAndFlagActiveTrue(movement.getCode()))
                .thenReturn(Optional.of(movement));
        when(accountRepository.findByDefaultAccountTrue()).thenReturn(Optional.of(AccountFactory.buildEntity()));
        when(settlementRepository.save(any(MovementSettlementEntity.class))).thenAnswer(invocation -> {
            final MovementSettlementEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            entity.prePersist();
            return entity;
        });

        final MovementSettlement recorded = service.create(movement.getCode(), settlement("60.00"));

        assertThat(recorded.amount()).isEqualByComparingTo("60.00");
        assertThat(recorded.getBalanceCalcValue()).isEqualByComparingTo("-60.00");
        assertThat(movement.getAmountPaid()).isEqualByComparingTo("-60.00");
        assertThat(movement.getStatus()).isEqualTo(FinancialMovementStatus.PENDING);
        verify(payCycleMovementGuard).lock();
    }

    @Test
    void shouldRejectConcurrentOrExcessSettlementBeforeSaving() {
        final FinancialMovementEntity movement = movement(OperationType.CREDIT, "100.00", "75.00");
        when(movementRepository.findForUpdateByCodeAndFlagActiveTrue(movement.getCode()))
                .thenReturn(Optional.of(movement));
        when(accountRepository.findByDefaultAccountTrue()).thenReturn(Optional.of(AccountFactory.buildEntity()));

        assertThrows(ConflictException.class,
                () -> service.create(movement.getCode(), settlement("30.00")));

        verify(settlementRepository, never()).save(any());
    }

    @Test
    void shouldMarkMovementPaidOutOnlyWhenTheSettlementCompletesTheTotal() {
        final FinancialMovementEntity movement = movement(OperationType.CREDIT, "100.00", "60.00");
        when(movementRepository.findForUpdateByCodeAndFlagActiveTrue(movement.getCode()))
                .thenReturn(Optional.of(movement));
        when(accountRepository.findByDefaultAccountTrue()).thenReturn(Optional.of(AccountFactory.buildEntity()));
        when(settlementRepository.save(any(MovementSettlementEntity.class))).thenAnswer(invocation -> {
            final MovementSettlementEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            entity.prePersist();
            return entity;
        });

        service.create(movement.getCode(), settlement("40.00"));

        assertThat(movement.getAmountPaid()).isEqualByComparingTo("100.00");
        assertThat(movement.getStatus()).isEqualTo(FinancialMovementStatus.PAID_OUT);
    }

    @Test
    void shouldReverseSettlementWithoutDeletingItAndRestoreResidualStatus() {
        final FinancialMovementEntity movement = movement(OperationType.DEBIT, "-100.00", "-60.00");
        final MovementSettlementEntity settlement = MovementSettlementEntity.builder()
                .id(2L)
                .code(UUID.randomUUID())
                .financialMovement(movement)
                .amount(new BigDecimal("60.00"))
                .effectiveDate(LocalDate.of(2026, 11, 5))
                .version(0L)
                .build();
        when(movementRepository.findForUpdateByCodeAndFlagActiveTrue(movement.getCode()))
                .thenReturn(Optional.of(movement));
        when(settlementRepository.findForUpdateByCodeAndMovementId(settlement.getCode(), movement.getId()))
                .thenReturn(Optional.of(settlement));

        final MovementSettlement reversed = service.reverse(movement.getCode(), settlement.getCode());

        assertThat(reversed.reversedAt()).isEqualTo(LocalDateTime.of(2026, 11, 8, 9, 0));
        assertThat(movement.getAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(movement.getStatus()).isEqualTo(FinancialMovementStatus.PENDING);
        assertThat(settlement.getId()).isEqualTo(2L);
        verify(settlementRepository).flush();
    }

    @Test
    void shouldRejectReversingTheSameSettlementTwice() {
        final FinancialMovementEntity movement = movement(OperationType.CREDIT, "100.00", "40.00");
        final MovementSettlementEntity settlement = MovementSettlementEntity.builder()
                .id(2L)
                .code(UUID.randomUUID())
                .financialMovement(movement)
                .amount(new BigDecimal("40.00"))
                .effectiveDate(LocalDate.of(2026, 11, 5))
                .reversedAt(LocalDateTime.of(2026, 11, 7, 9, 0))
                .version(1L)
                .build();
        when(movementRepository.findForUpdateByCodeAndFlagActiveTrue(movement.getCode()))
                .thenReturn(Optional.of(movement));
        when(settlementRepository.findForUpdateByCodeAndMovementId(settlement.getCode(), movement.getId()))
                .thenReturn(Optional.of(settlement));

        assertThrows(ConflictException.class,
                () -> service.reverse(movement.getCode(), settlement.getCode()));
    }

    @Test
    void shouldRestoreLateStatusWhenReversingPaymentOfAnOverdueMovement() {
        final FinancialMovementEntity movement = movement(OperationType.DEBIT, "-100.00", "-100.00");
        movement.setDueDate(LocalDateTime.of(2026, 11, 7, 0, 0));
        movement.setStatus(FinancialMovementStatus.PAID_OUT);
        final MovementSettlementEntity settlement = MovementSettlementEntity.builder()
                .code(UUID.randomUUID()).financialMovement(movement)
                .amount(new BigDecimal("100.00")).effectiveDate(LocalDate.of(2026, 11, 5)).build();
        when(movementRepository.findForUpdateByCodeAndFlagActiveTrue(movement.getCode()))
                .thenReturn(Optional.of(movement));
        when(settlementRepository.findForUpdateByCodeAndMovementId(settlement.getCode(), movement.getId()))
                .thenReturn(Optional.of(settlement));

        service.reverse(movement.getCode(), settlement.getCode());

        assertThat(movement.getStatus()).isEqualTo(FinancialMovementStatus.LATE);
        assertThat(movement.getAmountPaid()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldUseTheDefaultAccountWhenNoAccountIsInformed() {
        final FinancialMovementEntity movement = movement(OperationType.DEBIT, "-100.00", "0.00");
        final AccountEntity defaultAccount = AccountFactory.buildEntity();
        when(movementRepository.findForUpdateByCodeAndFlagActiveTrue(movement.getCode()))
                .thenReturn(Optional.of(movement));
        when(accountRepository.findByDefaultAccountTrue()).thenReturn(Optional.of(defaultAccount));
        when(settlementRepository.save(any(MovementSettlementEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final MovementSettlement recorded = service.create(movement.getCode(), settlement("10.00"));

        assertThat(recorded.accountCode()).isEqualTo(defaultAccount.getCode());
    }

    @Test
    void shouldUseTheInformedAccountInsteadOfTheDefault() {
        final FinancialMovementEntity movement = movement(OperationType.DEBIT, "-100.00", "0.00");
        final AccountEntity wallet = AccountFactory.buildEntity();
        wallet.setCode(AccountFactory.OTHER_ACCOUNT_CODE);
        when(movementRepository.findForUpdateByCodeAndFlagActiveTrue(movement.getCode()))
                .thenReturn(Optional.of(movement));
        when(accountRepository.findByCode(wallet.getCode())).thenReturn(Optional.of(wallet));
        when(settlementRepository.save(any(MovementSettlementEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final MovementSettlement recorded = service.create(movement.getCode(),
                settlementWithAccount("10.00", wallet.getCode(), LocalDate.of(2026, 11, 8)));

        assertThat(recorded.accountCode()).isEqualTo(wallet.getCode());
        verify(accountRepository, never()).findByDefaultAccountTrue();
    }

    @Test
    void shouldRejectUnknownAccountBeforeSaving() {
        final FinancialMovementEntity movement = movement(OperationType.DEBIT, "-100.00", "0.00");
        final UUID unknown = UUID.randomUUID();
        when(movementRepository.findForUpdateByCodeAndFlagActiveTrue(movement.getCode()))
                .thenReturn(Optional.of(movement));
        when(accountRepository.findByCode(unknown)).thenReturn(Optional.empty());

        assertThrows(InvalidFieldsException.class, () -> service.create(movement.getCode(),
                settlementWithAccount("10.00", unknown, LocalDate.of(2026, 11, 8))));

        verify(settlementRepository, never()).save(any());
    }

    @Test
    void shouldRejectInactiveAccountBeforeSaving() {
        final FinancialMovementEntity movement = movement(OperationType.DEBIT, "-100.00", "0.00");
        final AccountEntity inactive = AccountFactory.buildEntity();
        inactive.setStatus(AccountStatus.INACTIVE);
        when(movementRepository.findForUpdateByCodeAndFlagActiveTrue(movement.getCode()))
                .thenReturn(Optional.of(movement));
        when(accountRepository.findByCode(inactive.getCode())).thenReturn(Optional.of(inactive));

        assertThrows(InvalidFieldsException.class, () -> service.create(movement.getCode(),
                settlementWithAccount("10.00", inactive.getCode(), LocalDate.of(2026, 11, 8))));

        verify(settlementRepository, never()).save(any());
    }

    @Test
    void shouldRejectSettlementDatedBeforeTheAccountInitialBalanceDate() {
        final FinancialMovementEntity movement = movement(OperationType.DEBIT, "-100.00", "0.00");
        final AccountEntity account = AccountFactory.buildEntity();
        account.setInitialBalanceDate(LocalDate.of(2026, 11, 1));
        when(movementRepository.findForUpdateByCodeAndFlagActiveTrue(movement.getCode()))
                .thenReturn(Optional.of(movement));
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));

        assertThrows(InvalidFieldsException.class, () -> service.create(movement.getCode(),
                settlementWithAccount("10.00", account.getCode(), LocalDate.of(2026, 10, 31))));

        verify(settlementRepository, never()).save(any());
    }

    private static MovementSettlement settlementWithAccount(final String amount, final UUID accountCode,
                                                            final LocalDate effectiveDate) {
        return new MovementSettlement(null, null, new BigDecimal(amount), effectiveDate,
                null, null, null, null, null, accountCode);
    }

    private static MovementSettlement settlement(final String amount) {
        return new MovementSettlement(null, null, new BigDecimal(amount), LocalDate.of(2026, 11, 8),
                null, null, null, null, null);
    }

    private static FinancialMovementEntity movement(final OperationType type,
                                                    final String amount,
                                                    final String amountPaid) {
        final FinancialMovementEntity entity = FinancialMovementEntity.builder()
                .id(1L)
                .code(UUID.randomUUID())
                .operationType(type)
                .description("Teste")
                .amount(new BigDecimal(amount))
                .amountPaid(new BigDecimal(amountPaid))
                .movementDate(LocalDateTime.of(2026, 11, 1, 0, 0))
                .dueDate(LocalDateTime.of(2026, 11, 20, 0, 0))
                .status(FinancialMovementStatus.PENDING)
                .flagActive(true)
                .build();
        return entity;
    }
}
