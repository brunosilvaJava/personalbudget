package com.bts.personalbudget.core.domain.service.thirdparty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bts.personalbudget.exception.InvalidFieldsException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ThirdPartyResolverTest {

    private static final UUID EXPLICIT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLASTIC = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CARD = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void shouldResolveDefaultThirdPartyFromPlasticBeforeCard() {
        assertThat(ThirdPartyResolver.resolve(null, null, PLASTIC, CARD)).isEqualTo(PLASTIC);
    }

    @Test
    void shouldFallBackToTheCardDefaultWhenThePlasticHasNone() {
        assertThat(ThirdPartyResolver.resolve(null, null, null, CARD)).isEqualTo(CARD);
    }

    @Test
    void shouldPreferTheExplicitThirdPartyOverEveryDefault() {
        assertThat(ThirdPartyResolver.resolve(EXPLICIT, null, PLASTIC, CARD)).isEqualTo(EXPLICIT);
        assertThat(ThirdPartyResolver.resolve(EXPLICIT, false, PLASTIC, CARD)).isEqualTo(EXPLICIT);
    }

    @Test
    void shouldForceOwnExpenseOverDefault() {
        assertThat(ThirdPartyResolver.resolve(null, true, PLASTIC, CARD)).isNull();
    }

    @Test
    void shouldResolveToNobodyWhenThereIsNoThirdPartyAndNoDefault() {
        assertThat(ThirdPartyResolver.resolve(null, null, null, null)).isNull();
        assertThat(ThirdPartyResolver.resolve(null, false, null, null)).isNull();
    }

    @Test
    void shouldRejectAnExplicitThirdPartyTogetherWithOwnExpense() {
        assertThrows(InvalidFieldsException.class, () -> ThirdPartyResolver.resolve(EXPLICIT, true, PLASTIC, CARD));
    }
}
