package com.bts.personalbudget.controller.cardinvoice;

import com.bts.personalbudget.core.domain.enumerator.CreditCardHolderKind;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record CardInvoiceHolderTotalResponse(@JsonProperty("holder_code") UUID holderCode,
                                             @JsonProperty("holder_name") String holderName,
                                             CreditCardHolderKind kind,
                                             @JsonProperty("total_amount") BigDecimal totalAmount) { }
