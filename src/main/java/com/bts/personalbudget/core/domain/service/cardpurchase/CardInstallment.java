package com.bts.personalbudget.core.domain.service.cardpurchase;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * One installment of a card purchase, seen as the financial movement it is. Amounts are positive.
 * The competence and the due date are the due date of the invoice the installment falls in.
 */
public record CardInstallment(
        UUID movementCode,
        UUID purchaseCode,
        int installmentNumber,
        int installmentCount,
        UUID invoiceCode,
        YearMonth invoiceReferenceMonth,
        String description,
        BigDecimal amount,
        BigDecimal amountPaid,
        LocalDate dueDate,
        LocalDate competenceDate,
        FinancialMovementStatus status
) {
}
