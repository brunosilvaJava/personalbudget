package com.bts.personalbudget.core.domain.service.creditcardholder;

import com.bts.personalbudget.core.domain.enumerator.CreditCardPlasticKind;
import com.bts.personalbudget.core.domain.enumerator.CreditCardPlasticStatus;
import java.util.UUID;

public record CreditCardPlastic(UUID code, UUID cardCode, UUID holderCode, String holderName,
                                String lastDigits, CreditCardPlasticKind kind,
                                CreditCardPlasticStatus status, UUID defaultThirdPartyCode) {
}
