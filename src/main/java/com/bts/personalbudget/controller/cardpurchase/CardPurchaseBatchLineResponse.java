package com.bts.personalbudget.controller.cardpurchase;

import com.bts.personalbudget.core.domain.service.cardpurchase.CardPurchaseBatchStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CardPurchaseBatchLineResponse(CardPurchaseBatchStatus status, Map<String, String> errors,
                                            @JsonProperty("import_key") String importKey,
                                            @JsonProperty("invoice_reference_month") YearMonth invoiceReferenceMonth,
                                            @JsonProperty("due_date") LocalDate dueDate,
                                            @JsonProperty("holder_code") UUID holderCode,
                                            @JsonProperty("third_party_code") UUID thirdPartyCode,
                                            List<UUID> matches) { }
