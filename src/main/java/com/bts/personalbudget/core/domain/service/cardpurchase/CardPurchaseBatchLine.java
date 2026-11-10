package com.bts.personalbudget.core.domain.service.cardpurchase;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CardPurchaseBatchLine(CardPurchaseBatchStatus status, Map<String, String> errors, String importKey,
                                    YearMonth invoiceReferenceMonth, LocalDate dueDate, UUID holderCode,
                                    UUID thirdPartyCode, List<UUID> matches) { }
