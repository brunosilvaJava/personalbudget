package com.bts.personalbudget.core.domain.service.creditcard;

import com.bts.personalbudget.core.domain.entity.AccountEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardEntity;
import com.bts.personalbudget.core.domain.enumerator.AccountStatus;
import com.bts.personalbudget.core.domain.enumerator.CreditCardStatus;
import com.bts.personalbudget.core.domain.factory.AccountFactory;
import com.bts.personalbudget.core.domain.factory.CreditCardFactory;
import com.bts.personalbudget.core.domain.model.CreditCard;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.CreditCardMapper;
import com.bts.personalbudget.repository.AccountRepository;
import com.bts.personalbudget.repository.CreditCardRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditCardServiceTest {

    @InjectMocks
    private CreditCardService service;

    @Mock
    private CreditCardRepository cardRepository;

    @Mock
    private AccountRepository accountRepository;

    @Spy
    private CreditCardMapper mapper = Mappers.getMapper(CreditCardMapper.class);

    @Test
    void shouldCreateAnActiveCardWithTrimmedNameAndDefaultAccount() {
        final AccountEntity account = AccountFactory.buildEntity();
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));
        when(cardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        final CreditCard request = new CreditCard(null, "  Inter Gold  ", 10, 17, null, account.getCode(), null);

        final CreditCard created = service.create(request);

        final ArgumentCaptor<CreditCardEntity> captor = ArgumentCaptor.forClass(CreditCardEntity.class);
        verify(cardRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Inter Gold");
        assertThat(captor.getValue().getStatus()).isEqualTo(CreditCardStatus.ACTIVE);
        assertThat(captor.getValue().getDefaultAccount()).isSameAs(account);
        assertThat(created.defaultAccountCode()).isEqualTo(account.getCode());
        assertThat(created.creditLimit()).isNull();
    }

    @Test
    void shouldRejectDuplicateNameAndShortName() {
        when(cardRepository.existsByName("Inter Gold")).thenReturn(true);

        assertThrows(InvalidFieldsException.class,
                () -> service.create(CreditCardFactory.buildNewModel("Inter Gold")));
        assertThrows(InvalidFieldsException.class, () -> service.create(CreditCardFactory.buildNewModel("ab")));

        verify(cardRepository, never()).save(any());
    }

    @Test
    void shouldRejectDaysOutsideTheMonthAndNonPositiveLimit() {
        assertThrows(InvalidFieldsException.class, () -> service.create(
                new CreditCard(null, "Cartão A", 0, 5, null, null, null)));
        assertThrows(InvalidFieldsException.class, () -> service.create(
                new CreditCard(null, "Cartão A", 25, 32, null, null, null)));
        assertThrows(InvalidFieldsException.class, () -> service.create(
                new CreditCard(null, "Cartão A", 25, 5, BigDecimal.ZERO, null, null)));

        verify(cardRepository, never()).save(any());
    }

    @Test
    void shouldRejectUnknownOrInactiveDefaultAccount() {
        final AccountEntity inactive = AccountFactory.buildEntity();
        inactive.setStatus(AccountStatus.INACTIVE);
        when(accountRepository.findByCode(inactive.getCode())).thenReturn(Optional.of(inactive));
        when(accountRepository.findByCode(AccountFactory.OTHER_ACCOUNT_CODE)).thenReturn(Optional.empty());

        assertThrows(InvalidFieldsException.class, () -> service.create(
                new CreditCard(null, "Cartão A", 25, 5, null, inactive.getCode(), null)));
        assertThrows(InvalidFieldsException.class, () -> service.create(
                new CreditCard(null, "Cartão A", 25, 5, null, AccountFactory.OTHER_ACCOUNT_CODE, null)));

        verify(cardRepository, never()).save(any());
    }

    @Test
    void shouldListOnlyActiveCardsWhenNoStatusIsGiven() {
        when(cardRepository.findByStatuses(List.of(CreditCardStatus.ACTIVE)))
                .thenReturn(List.of(CreditCardFactory.buildEntity()));

        assertThat(service.find(null)).hasSize(1);
        assertThat(service.find(List.of())).hasSize(1);
    }

    @Test
    void shouldAnswerNotFoundForUnknownCard() {
        when(cardRepository.findByCode(CreditCardFactory.CARD_CODE)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.findByCode(CreditCardFactory.CARD_CODE));
        assertThrows(EntityNotFoundException.class, () -> service.inactivate(CreditCardFactory.CARD_CODE));
    }

    @Test
    void shouldUpdateOnlyTheFieldsThatWereSent() {
        final CreditCardEntity card = CreditCardFactory.buildEntity();
        when(cardRepository.findByCode(card.getCode())).thenReturn(Optional.of(card));
        when(cardRepository.save(card)).thenReturn(card);

        final CreditCard updated = service.update(card.getCode(), null, 20, null, new BigDecimal("8000.00"), null);

        assertThat(updated.closingDay()).isEqualTo(20);
        assertThat(updated.dueDay()).isEqualTo(5);
        assertThat(updated.creditLimit()).isEqualByComparingTo("8000.00");
        assertThat(updated.name()).isEqualTo(CreditCardFactory.CARD_NAME);
    }

    @Test
    void shouldNotSaveWhenNothingChanged() {
        final CreditCardEntity card = CreditCardFactory.buildEntity();
        when(cardRepository.findByCode(card.getCode())).thenReturn(Optional.of(card));

        service.update(card.getCode(), CreditCardFactory.CARD_NAME, 25, 5, new BigDecimal("5000.0"), null);

        verify(cardRepository, never()).save(any());
    }

    @Test
    void shouldRejectRenamingToANameUsedByAnotherCard() {
        final CreditCardEntity card = CreditCardFactory.buildEntity();
        when(cardRepository.findByCode(card.getCode())).thenReturn(Optional.of(card));
        when(cardRepository.existsByNameAndCodeNot("Inter Gold", card.getCode())).thenReturn(true);

        assertThrows(InvalidFieldsException.class,
                () -> service.update(card.getCode(), "Inter Gold", null, null, null, null));

        verify(cardRepository, never()).save(any());
    }

    @Test
    void shouldReplaceTheDefaultAccountWithAnActiveOne() {
        final CreditCardEntity card = CreditCardFactory.buildEntity();
        final AccountEntity account = AccountFactory.buildEntity();
        when(cardRepository.findByCode(card.getCode())).thenReturn(Optional.of(card));
        when(accountRepository.findByCode(account.getCode())).thenReturn(Optional.of(account));
        when(cardRepository.save(card)).thenReturn(card);

        final CreditCard updated = service.update(card.getCode(), null, null, null, null, account.getCode());

        assertThat(updated.defaultAccountCode()).isEqualTo(account.getCode());
    }

    @Test
    void shouldInactivateAndActivateIdempotently() {
        final CreditCardEntity card = CreditCardFactory.buildEntity();
        when(cardRepository.findByCode(card.getCode())).thenReturn(Optional.of(card));
        when(cardRepository.save(card)).thenReturn(card);

        assertThat(service.inactivate(card.getCode()).status()).isEqualTo(CreditCardStatus.INACTIVE);
        assertThat(service.inactivate(card.getCode()).status()).isEqualTo(CreditCardStatus.INACTIVE);
        assertThat(service.activate(card.getCode()).status()).isEqualTo(CreditCardStatus.ACTIVE);
        assertThat(service.activate(card.getCode()).status()).isEqualTo(CreditCardStatus.ACTIVE);
        verify(cardRepository, times(2)).save(card);
    }
}
