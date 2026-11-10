package com.bts.personalbudget.controller.creditcardholder;

import com.bts.personalbudget.core.domain.enumerator.CreditCardPlasticKind;
import com.bts.personalbudget.core.domain.enumerator.CreditCardPlasticStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record CreditCardPlasticResponse(UUID code, @JsonProperty("card_code") UUID cardCode,
                                        @JsonProperty("holder_code") UUID holderCode,
                                        @JsonProperty("holder_name") String holderName,
                                        @JsonProperty("last_digits") String lastDigits,
                                        CreditCardPlasticKind kind, CreditCardPlasticStatus status,
                                        @JsonProperty("default_third_party_code") UUID defaultThirdPartyCode) {
}
