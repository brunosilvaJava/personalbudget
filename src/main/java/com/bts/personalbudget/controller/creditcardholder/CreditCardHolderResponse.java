package com.bts.personalbudget.controller.creditcardholder;

import com.bts.personalbudget.core.domain.enumerator.CreditCardHolderKind;
import com.bts.personalbudget.core.domain.enumerator.CreditCardHolderStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record CreditCardHolderResponse(UUID code, @JsonProperty("card_code") UUID cardCode, String name,
                                       CreditCardHolderKind kind, CreditCardHolderStatus status) {
}
