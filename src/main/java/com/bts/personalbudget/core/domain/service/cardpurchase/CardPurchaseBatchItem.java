package com.bts.personalbudget.core.domain.service.cardpurchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CardPurchaseBatchItem(String description, LocalDate purchaseDate, Integer installments,
                                    Integer firstInstallment, BigDecimal installmentAmount, UUID categoryCode,
                                    String cardLastDigits, UUID cardHolderCode, UUID thirdPartyCode,
                                    Boolean ownExpense, Boolean acceptDuplicate) { }
