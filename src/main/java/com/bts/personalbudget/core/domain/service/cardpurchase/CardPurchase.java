package com.bts.personalbudget.core.domain.service.cardpurchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A purchase on a credit card. {@code installmentAmount} is the amount of each installment and
 * {@code totalAmount} is derived (installment amount times installments). On input only the
 * client's fields are set; code, totalAmount and installmentList come from the service.
 */
public record CardPurchase(
        UUID code,
        UUID cardCode,
        String description,
        LocalDate purchaseDate,
        Integer installments,
        BigDecimal installmentAmount,
        BigDecimal totalAmount,
        UUID categoryCode,
        List<CardInstallment> installmentList
) {
}
