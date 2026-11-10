package com.bts.personalbudget.controller.creditcardholder;

import com.bts.personalbudget.core.domain.enumerator.CreditCardPlasticKind;
import com.bts.personalbudget.core.domain.enumerator.CreditCardPlasticStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record CreditCardPlasticUpdateRequest(
        CreditCardPlasticKind kind,
        CreditCardPlasticStatus status,
        @JsonProperty("default_third_party_code") UUID defaultThirdPartyCode,
        @JsonProperty("clear_default_third_party") Boolean clearDefaultThirdParty) {
}
