package com.bts.personalbudget.core.domain.service.creditcard;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Limit of a card: {@code used} is the unpaid part of every active installment of the card, future
 * ones included. {@code creditLimit} and {@code available} are null when the card has no limit.
 * {@code available} can be negative when the limit was lowered under what is already used.
 */
public record CreditCardLimit(
        UUID cardCode,
        BigDecimal creditLimit,
        BigDecimal used,
        BigDecimal available
) {
}
