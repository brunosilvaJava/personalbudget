package com.bts.personalbudget.core.domain.service.accounttransfer;

import com.bts.personalbudget.core.domain.entity.AccountEntity;
import com.bts.personalbudget.core.domain.entity.AccountTransferEntity;
import com.bts.personalbudget.core.domain.enumerator.AccountStatus;
import com.bts.personalbudget.core.domain.factory.AccountFactory;
import com.bts.personalbudget.core.domain.factory.AccountTransferFactory;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.AccountTransferMapper;
import com.bts.personalbudget.repository.AccountRepository;
import com.bts.personalbudget.repository.AccountTransferRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountTransferServiceTest {

    @InjectMocks
    private AccountTransferService service;

    @Mock
    private AccountTransferRepository transferRepository;

    @Mock
    private AccountRepository accountRepository;

    @Spy
    private AccountTransferMapper mapper = Mappers.getMapper(AccountTransferMapper.class);

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private final AccountEntity from = AccountFactory.buildEntity();
    private final AccountEntity to =
            AccountFactory.buildEntity(2L, AccountFactory.OTHER_ACCOUNT_CODE, "Carteira", false);

    private void stubAccounts() {
        when(accountRepository.findByCode(from.getCode())).thenReturn(Optional.of(from));
        when(accountRepository.findByCode(to.getCode())).thenReturn(Optional.of(to));
    }

    @Test
    void shouldCreateTransferBetweenTwoActiveAccounts() {
        stubAccounts();
        when(transferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        final AccountTransfer created = service.create(AccountTransferFactory.buildNewModel());

        final ArgumentCaptor<AccountTransferEntity> captor = ArgumentCaptor.forClass(AccountTransferEntity.class);
        verify(transferRepository).save(captor.capture());
        assertThat(captor.getValue().getFromAccount()).isSameAs(from);
        assertThat(captor.getValue().getToAccount()).isSameAs(to);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("200.00");
        assertThat(created.fromAccountCode()).isEqualTo(from.getCode());
        assertThat(created.toAccountCode()).isEqualTo(to.getCode());
        assertThat(created.reversedAt()).isNull();
    }

    @Test
    void shouldRejectTransferToTheSameAccount() {
        final AccountTransfer transfer = new AccountTransfer(null, from.getCode(), from.getCode(),
                new BigDecimal("10.00"), AccountTransferFactory.EFFECTIVE_DATE, null, null, null);

        assertThrows(InvalidFieldsException.class, () -> service.create(transfer));

        verify(transferRepository, never()).save(any());
    }

    @Test
    void shouldRejectZeroOrNegativeAmount() {
        for (final String amount : List.of("0.00", "-5.00")) {
            final AccountTransfer transfer = new AccountTransfer(null, from.getCode(), to.getCode(),
                    new BigDecimal(amount), AccountTransferFactory.EFFECTIVE_DATE, null, null, null);
            assertThrows(InvalidFieldsException.class, () -> service.create(transfer));
        }
        verify(transferRepository, never()).save(any());
    }

    @Test
    void shouldRejectInactiveOrUnknownAccount() {
        to.setStatus(AccountStatus.INACTIVE);
        stubAccounts();
        assertThrows(InvalidFieldsException.class, () -> service.create(AccountTransferFactory.buildNewModel()));

        when(accountRepository.findByCode(from.getCode())).thenReturn(Optional.empty());
        assertThrows(InvalidFieldsException.class, () -> service.create(AccountTransferFactory.buildNewModel()));

        verify(transferRepository, never()).save(any());
    }

    @Test
    void shouldRejectEffectiveDateBeforeTheInitialBalanceDateOfEitherAccount() {
        to.setInitialBalanceDate(LocalDate.of(2026, 11, 6));
        stubAccounts();

        assertThrows(InvalidFieldsException.class, () -> service.create(AccountTransferFactory.buildNewModel()));

        verify(transferRepository, never()).save(any());
    }

    @Test
    void shouldReverseWithoutDeletingAndRefuseToReverseTwice() {
        final AccountTransferEntity entity = AccountTransferFactory.buildEntity(from, to);
        when(transferRepository.findForUpdateByCode(entity.getCode())).thenReturn(Optional.of(entity));

        final AccountTransfer reversed = service.reverse(entity.getCode());

        assertThat(reversed.reversedAt()).isNotNull();
        verify(transferRepository, never()).delete(any());
        assertThrows(ConflictException.class, () -> service.reverse(entity.getCode()));
    }

    @Test
    void shouldAnswerNotFoundForUnknownTransfer() {
        when(transferRepository.findByCode(AccountTransferFactory.TRANSFER_CODE)).thenReturn(Optional.empty());
        when(transferRepository.findForUpdateByCode(AccountTransferFactory.TRANSFER_CODE))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.findByCode(AccountTransferFactory.TRANSFER_CODE));
        assertThrows(EntityNotFoundException.class, () -> service.reverse(AccountTransferFactory.TRANSFER_CODE));
    }

    @Test
    void shouldRejectInvalidOrTooLongPeriods() {
        final LocalDate start = LocalDate.of(2026, 1, 1);

        assertThrows(InvalidFieldsException.class, () -> service.find(null, start, null));
        assertThrows(InvalidFieldsException.class, () -> service.find(start, start.minusDays(1), null));
        assertThrows(InvalidFieldsException.class, () -> service.find(start, start.plusDays(367), null));
        verify(transferRepository, never()).findByFilters(any(), any(), any());
    }

    @Test
    void shouldListTransfersOfThePeriodFilteredByAccount() {
        final LocalDate start = LocalDate.of(2026, 11, 1);
        final LocalDate end = LocalDate.of(2026, 11, 30);
        when(transferRepository.findByFilters(start, end, from.getCode()))
                .thenReturn(List.of(AccountTransferFactory.buildEntity(from, to)));

        final List<AccountTransfer> result = service.find(start, end, from.getCode());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo(AccountTransferFactory.TRANSFER_CODE);
    }
}
