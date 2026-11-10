package com.bts.personalbudget.core.domain.service.creditcardholder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.entity.CreditCardEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardHolderEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardPlasticEntity;
import com.bts.personalbudget.core.domain.entity.ThirdPartyEntity;
import com.bts.personalbudget.core.domain.enumerator.CreditCardHolderKind;
import com.bts.personalbudget.core.domain.enumerator.CreditCardHolderStatus;
import com.bts.personalbudget.core.domain.enumerator.CreditCardPlasticKind;
import com.bts.personalbudget.core.domain.enumerator.CreditCardPlasticStatus;
import com.bts.personalbudget.core.domain.enumerator.ThirdPartyStatus;
import com.bts.personalbudget.core.domain.factory.CreditCardFactory;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.CreditCardHolderMapper;
import com.bts.personalbudget.repository.CreditCardHolderRepository;
import com.bts.personalbudget.repository.CreditCardPlasticRepository;
import com.bts.personalbudget.repository.CreditCardRepository;
import com.bts.personalbudget.repository.ThirdPartyRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditCardHolderServiceTest {

    private static final UUID HOLDER_CODE = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000a1");
    private static final UUID PLASTIC_CODE = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000b1");
    private static final UUID THIRD_PARTY_CODE = UUID.fromString("cccccccc-0000-0000-0000-0000000000c1");

    @InjectMocks
    private CreditCardHolderService service;

    @Mock private CreditCardRepository cardRepository;
    @Mock private CreditCardHolderRepository holderRepository;
    @Mock private CreditCardPlasticRepository plasticRepository;
    @Mock private ThirdPartyRepository thirdPartyRepository;
    @Spy private CreditCardHolderMapper mapper = new CreditCardHolderMapper();

    @Test
    void shouldCreateAnAdditionalHolderWithTrimmedName() {
        final CreditCardEntity card = stubCard();
        when(holderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        final CreditCardHolder created = service.createAdditional(CreditCardFactory.CARD_CODE, "  Márcia  ");

        final ArgumentCaptor<CreditCardHolderEntity> captor = ArgumentCaptor.forClass(CreditCardHolderEntity.class);
        verify(holderRepository).save(captor.capture());
        assertThat(captor.getValue().getCard()).isSameAs(card);
        assertThat(captor.getValue().getName()).isEqualTo("Márcia");
        assertThat(captor.getValue().getKind()).isEqualTo(CreditCardHolderKind.ADICIONAL);
        assertThat(captor.getValue().getStatus()).isEqualTo(CreditCardHolderStatus.ACTIVE);
        assertThat(created.kind()).isEqualTo(CreditCardHolderKind.ADICIONAL);
        assertThat(created.cardCode()).isEqualTo(CreditCardFactory.CARD_CODE);
    }

    @Test
    void shouldRejectAHolderNameAlreadyUsedOnTheCard() {
        stubCard();
        when(holderRepository.existsByCardIdAndName(1L, "Márcia")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.createAdditional(CreditCardFactory.CARD_CODE, "Márcia"));

        verify(holderRepository, never()).save(any());
    }

    @Test
    void shouldRejectABlankHolderName() {
        stubCard();

        assertThrows(InvalidFieldsException.class, () -> service.createAdditional(CreditCardFactory.CARD_CODE, "   "));

        verify(holderRepository, never()).save(any());
    }

    @Test
    void shouldNotInactivateTheTitular() {
        final CreditCardEntity card = CreditCardFactory.buildEntity();
        final CreditCardHolderEntity titular = holder(card, CreditCardHolderKind.TITULAR, "Titular");
        when(holderRepository.findByCode(HOLDER_CODE)).thenReturn(Optional.of(titular));

        assertThrows(ConflictException.class, () -> service.changeHolderStatus(
                CreditCardFactory.CARD_CODE, HOLDER_CODE, CreditCardHolderStatus.INACTIVE));

        assertThat(titular.getStatus()).isEqualTo(CreditCardHolderStatus.ACTIVE);
        verify(holderRepository, never()).save(any());
    }

    @Test
    void shouldInactivateAnAdditionalHolder() {
        final CreditCardEntity card = CreditCardFactory.buildEntity();
        final CreditCardHolderEntity additional = holder(card, CreditCardHolderKind.ADICIONAL, "Márcia");
        when(holderRepository.findByCode(HOLDER_CODE)).thenReturn(Optional.of(additional));
        when(holderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        final CreditCardHolder result = service.changeHolderStatus(
                CreditCardFactory.CARD_CODE, HOLDER_CODE, CreditCardHolderStatus.INACTIVE);

        assertThat(result.status()).isEqualTo(CreditCardHolderStatus.INACTIVE);
    }

    @Test
    void shouldRejectAHolderThatBelongsToAnotherCard() {
        final CreditCardEntity otherCard = CreditCardFactory.buildEntity();
        otherCard.setCode(UUID.randomUUID());
        when(holderRepository.findByCode(HOLDER_CODE))
                .thenReturn(Optional.of(holder(otherCard, CreditCardHolderKind.ADICIONAL, "Márcia")));

        assertThrows(ConflictException.class, () -> service.changeHolderStatus(
                CreditCardFactory.CARD_CODE, HOLDER_CODE, CreditCardHolderStatus.ACTIVE));
    }

    @Test
    void shouldReportAMissingHolderAsNotFound() {
        when(holderRepository.findByCode(HOLDER_CODE)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.updateHolder(
                CreditCardFactory.CARD_CODE, HOLDER_CODE, "Novo nome"));
    }

    @Test
    void shouldCreateAPlasticWithUnknownKindWhenNoneIsGiven() {
        final CreditCardEntity card = stubCard();
        final CreditCardHolderEntity holder = holder(card, CreditCardHolderKind.ADICIONAL, "Márcia");
        when(holderRepository.findByCode(HOLDER_CODE)).thenReturn(Optional.of(holder));
        when(plasticRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        final CreditCardPlastic created = service.createPlastic(
                CreditCardFactory.CARD_CODE, HOLDER_CODE, "3202", null);

        assertThat(created.lastDigits()).isEqualTo("3202");
        assertThat(created.kind()).isEqualTo(CreditCardPlasticKind.UNKNOWN);
        assertThat(created.status()).isEqualTo(CreditCardPlasticStatus.ACTIVE);
        assertThat(created.holderName()).isEqualTo("Márcia");
    }

    @Test
    void shouldRejectARepeatedLastFourDigitsOnTheSameCard() {
        final CreditCardEntity card = stubCard();
        when(holderRepository.findByCode(HOLDER_CODE))
                .thenReturn(Optional.of(holder(card, CreditCardHolderKind.TITULAR, "Titular")));
        when(plasticRepository.findByCardIdAndLastDigits(1L, "3578"))
                .thenReturn(Optional.of(plastic(card, "3578")));

        assertThrows(ConflictException.class, () -> service.createPlastic(
                CreditCardFactory.CARD_CODE, HOLDER_CODE, "3578", CreditCardPlasticKind.PHYSICAL));

        verify(plasticRepository, never()).save(any());
    }

    @Test
    void shouldRejectLastDigitsThatAreNotExactlyFourDigits() {
        final CreditCardEntity card = stubCard();
        when(holderRepository.findByCode(HOLDER_CODE))
                .thenReturn(Optional.of(holder(card, CreditCardHolderKind.TITULAR, "Titular")));

        assertThrows(InvalidFieldsException.class, () -> service.createPlastic(
                CreditCardFactory.CARD_CODE, HOLDER_CODE, "35a8", null));
        assertThrows(InvalidFieldsException.class, () -> service.createPlastic(
                CreditCardFactory.CARD_CODE, HOLDER_CODE, "357", null));

        verify(plasticRepository, never()).save(any());
    }

    @Test
    void shouldSetAndClearThePlasticDefaultThirdParty() {
        final CreditCardEntity card = CreditCardFactory.buildEntity();
        final CreditCardPlasticEntity plastic = plastic(card, "6240");
        when(plasticRepository.findByCode(PLASTIC_CODE)).thenReturn(Optional.of(plastic));
        when(plasticRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(thirdPartyRepository.findByCode(THIRD_PARTY_CODE))
                .thenReturn(Optional.of(thirdParty(ThirdPartyStatus.ACTIVE)));

        final CreditCardPlastic withDefault = service.updatePlastic(
                CreditCardFactory.CARD_CODE, PLASTIC_CODE, null, null, THIRD_PARTY_CODE, false);
        assertThat(withDefault.defaultThirdPartyCode()).isEqualTo(THIRD_PARTY_CODE);

        final CreditCardPlastic cleared = service.updatePlastic(
                CreditCardFactory.CARD_CODE, PLASTIC_CODE, null, null, null, true);
        assertThat(cleared.defaultThirdPartyCode()).isNull();
    }

    @Test
    void shouldKeepWhatIsNotInformedWhenUpdatingAPlastic() {
        final CreditCardEntity card = CreditCardFactory.buildEntity();
        final CreditCardPlasticEntity plastic = plastic(card, "6240");
        plastic.setKind(CreditCardPlasticKind.VIRTUAL);
        when(plasticRepository.findByCode(PLASTIC_CODE)).thenReturn(Optional.of(plastic));
        when(plasticRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        final CreditCardPlastic result = service.updatePlastic(
                CreditCardFactory.CARD_CODE, PLASTIC_CODE, null, CreditCardPlasticStatus.REPLACED, null, false);

        assertThat(result.kind()).isEqualTo(CreditCardPlasticKind.VIRTUAL);
        assertThat(result.status()).isEqualTo(CreditCardPlasticStatus.REPLACED);
    }

    @Test
    void shouldRejectAnInactiveOrUnknownDefaultThirdPartyOnAPlastic() {
        final CreditCardEntity card = CreditCardFactory.buildEntity();
        when(plasticRepository.findByCode(PLASTIC_CODE)).thenReturn(Optional.of(plastic(card, "6240")));
        when(thirdPartyRepository.findByCode(THIRD_PARTY_CODE))
                .thenReturn(Optional.of(thirdParty(ThirdPartyStatus.INACTIVE)));

        assertThrows(InvalidFieldsException.class, () -> service.updatePlastic(
                CreditCardFactory.CARD_CODE, PLASTIC_CODE, null, null, THIRD_PARTY_CODE, false));

        final UUID unknown = UUID.randomUUID();
        when(thirdPartyRepository.findByCode(unknown)).thenReturn(Optional.empty());
        assertThrows(InvalidFieldsException.class, () -> service.updatePlastic(
                CreditCardFactory.CARD_CODE, PLASTIC_CODE, null, null, unknown, false));
        verify(plasticRepository, never()).save(any());
    }

    @Test
    void shouldRejectACodeTogetherWithTheClearFlagOnAPlastic() {
        assertThrows(InvalidFieldsException.class, () -> service.updatePlastic(
                CreditCardFactory.CARD_CODE, PLASTIC_CODE, null, null, THIRD_PARTY_CODE, true));

        verify(plasticRepository, never()).save(any());
    }

    @Test
    void shouldRejectAPlasticThatBelongsToAnotherCard() {
        final CreditCardEntity otherCard = CreditCardFactory.buildEntity();
        otherCard.setCode(UUID.randomUUID());
        when(plasticRepository.findByCode(PLASTIC_CODE)).thenReturn(Optional.of(plastic(otherCard, "6240")));

        assertThrows(ConflictException.class, () -> service.updatePlastic(
                CreditCardFactory.CARD_CODE, PLASTIC_CODE, null, null, null, false));
    }

    private CreditCardEntity stubCard() {
        final CreditCardEntity card = CreditCardFactory.buildEntity();
        when(cardRepository.findByCode(CreditCardFactory.CARD_CODE)).thenReturn(Optional.of(card));
        return card;
    }

    private static CreditCardHolderEntity holder(final CreditCardEntity card, final CreditCardHolderKind kind,
                                                 final String name) {
        return CreditCardHolderEntity.builder().id(10L).code(HOLDER_CODE).card(card).name(name).kind(kind)
                .status(CreditCardHolderStatus.ACTIVE).version(0L).build();
    }

    private static CreditCardPlasticEntity plastic(final CreditCardEntity card, final String lastDigits) {
        return CreditCardPlasticEntity.builder().id(20L).code(PLASTIC_CODE).card(card)
                .holder(holder(card, CreditCardHolderKind.TITULAR, "Titular")).lastDigits(lastDigits)
                .kind(CreditCardPlasticKind.UNKNOWN).status(CreditCardPlasticStatus.ACTIVE).version(0L).build();
    }

    private static ThirdPartyEntity thirdParty(final ThirdPartyStatus status) {
        return ThirdPartyEntity.builder().id(30L).code(THIRD_PARTY_CODE).name("João").status(status)
                .version(0L).build();
    }
}
