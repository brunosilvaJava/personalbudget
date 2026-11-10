package com.bts.personalbudget.core.domain.service.creditcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.entity.CreditCardEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardHolderEntity;
import com.bts.personalbudget.core.domain.entity.ThirdPartyEntity;
import com.bts.personalbudget.core.domain.enumerator.CreditCardHolderKind;
import com.bts.personalbudget.core.domain.enumerator.CreditCardHolderStatus;
import com.bts.personalbudget.core.domain.enumerator.CreditCardStatus;
import com.bts.personalbudget.core.domain.enumerator.ThirdPartyStatus;
import com.bts.personalbudget.core.domain.factory.CreditCardFactory;
import com.bts.personalbudget.core.domain.model.CreditCard;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.CreditCardMapper;
import com.bts.personalbudget.repository.AccountRepository;
import com.bts.personalbudget.repository.CardInstallmentRepository;
import com.bts.personalbudget.repository.CreditCardHolderRepository;
import com.bts.personalbudget.repository.CreditCardRepository;
import com.bts.personalbudget.repository.ThirdPartyRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/** The titular created with every card and the default third party of a card. */
@ExtendWith(MockitoExtension.class)
class CreditCardServiceHoldersAndThirdPartyTest {

    private static final UUID THIRD_PARTY_CODE = UUID.fromString("cccccccc-0000-0000-0000-0000000000c1");

    @InjectMocks
    private CreditCardService service;

    @Mock private CreditCardRepository cardRepository;
    @Mock private CreditCardHolderRepository holderRepository;
    @Mock private ThirdPartyRepository thirdPartyRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CardInstallmentRepository installmentRepository;
    @Spy private CreditCardMapper mapper = Mappers.getMapper(CreditCardMapper.class);

    @Test
    void shouldCreateTheTitularHolderTogetherWithTheCard() {
        when(cardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(CreditCardFactory.buildNewModel("Inter Gold"));

        final ArgumentCaptor<CreditCardEntity> card = ArgumentCaptor.forClass(CreditCardEntity.class);
        final ArgumentCaptor<CreditCardHolderEntity> holder = ArgumentCaptor.forClass(CreditCardHolderEntity.class);
        verify(cardRepository).save(card.capture());
        verify(holderRepository).save(holder.capture());
        assertThat(holder.getValue().getCard()).isSameAs(card.getValue());
        assertThat(holder.getValue().getName()).isEqualTo("Titular");
        assertThat(holder.getValue().getKind()).isEqualTo(CreditCardHolderKind.TITULAR);
        assertThat(holder.getValue().getStatus()).isEqualTo(CreditCardHolderStatus.ACTIVE);
    }

    @Test
    void shouldCreateACardWithAnActiveDefaultThirdParty() {
        final ThirdPartyEntity thirdParty = thirdParty(ThirdPartyStatus.ACTIVE);
        when(thirdPartyRepository.findByCode(THIRD_PARTY_CODE)).thenReturn(Optional.of(thirdParty));
        when(cardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        final CreditCard request = new CreditCard(null, "Cartão emprestado", 25, 5, new BigDecimal("5000.00"),
                null, THIRD_PARTY_CODE, null);

        final CreditCard created = service.create(request);

        final ArgumentCaptor<CreditCardEntity> card = ArgumentCaptor.forClass(CreditCardEntity.class);
        verify(cardRepository).save(card.capture());
        assertThat(card.getValue().getDefaultThirdParty()).isSameAs(thirdParty);
        assertThat(created.defaultThirdPartyCode()).isEqualTo(THIRD_PARTY_CODE);
    }

    @Test
    void shouldRefuseAnInactiveDefaultThirdPartyOnANewCard() {
        when(thirdPartyRepository.findByCode(THIRD_PARTY_CODE))
                .thenReturn(Optional.of(thirdParty(ThirdPartyStatus.INACTIVE)));
        final CreditCard request = new CreditCard(null, "Cartão emprestado", 25, 5, new BigDecimal("5000.00"),
                null, THIRD_PARTY_CODE, null);

        assertThrows(InvalidFieldsException.class, () -> service.create(request));

        verify(cardRepository, never()).save(any());
        verify(holderRepository, never()).save(any());
    }

    @Test
    void shouldSetAndThenClearTheDefaultThirdPartyOfAnExistingCard() {
        final CreditCardEntity card = CreditCardFactory.buildEntity();
        when(cardRepository.findByCode(CreditCardFactory.CARD_CODE)).thenReturn(Optional.of(card));
        when(cardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(thirdPartyRepository.findByCode(THIRD_PARTY_CODE))
                .thenReturn(Optional.of(thirdParty(ThirdPartyStatus.ACTIVE)));

        final CreditCard withDefault = service.update(CreditCardFactory.CARD_CODE, null, null, null, null, null,
                THIRD_PARTY_CODE, false);
        assertThat(withDefault.defaultThirdPartyCode()).isEqualTo(THIRD_PARTY_CODE);

        final CreditCard cleared = service.update(CreditCardFactory.CARD_CODE, null, null, null, null, null,
                null, true);
        assertThat(cleared.defaultThirdPartyCode()).isNull();
    }

    @Test
    void shouldRefuseACodeTogetherWithTheClearFlagOnACard() {
        assertThrows(InvalidFieldsException.class, () -> service.update(CreditCardFactory.CARD_CODE, null, null, null,
                null, null, THIRD_PARTY_CODE, true));

        verify(cardRepository, never()).save(any());
    }

    @Test
    void shouldKeepTheStatusOfACardWhenOnlyTheDefaultThirdPartyChanges() {
        final CreditCardEntity card = CreditCardFactory.buildEntity();
        when(cardRepository.findByCode(CreditCardFactory.CARD_CODE)).thenReturn(Optional.of(card));
        when(cardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(thirdPartyRepository.findByCode(THIRD_PARTY_CODE))
                .thenReturn(Optional.of(thirdParty(ThirdPartyStatus.ACTIVE)));

        service.update(CreditCardFactory.CARD_CODE, null, null, null, null, null, THIRD_PARTY_CODE, false);

        assertThat(card.getStatus()).isEqualTo(CreditCardStatus.ACTIVE);
        assertThat(card.getClosingDay()).isEqualTo(25);
    }

    private static ThirdPartyEntity thirdParty(final ThirdPartyStatus status) {
        return ThirdPartyEntity.builder().id(30L).code(THIRD_PARTY_CODE).name("João").status(status)
                .version(0L).build();
    }
}
