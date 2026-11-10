package com.bts.personalbudget.core.domain.service.creditcardholder;

import com.bts.personalbudget.core.domain.enumerator.CreditCardHolderKind;
import com.bts.personalbudget.core.domain.enumerator.CreditCardHolderStatus;
import java.util.UUID;

public record CreditCardHolder(UUID code, UUID cardCode, String name, CreditCardHolderKind kind,
                               CreditCardHolderStatus status) {
}
