package com.bts.personalbudget.core.domain.service.account;

import com.bts.personalbudget.core.domain.enumerator.AccountStatementEntryType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One line of an account statement.
 *
 * @param amount                 signed: credits and incoming transfers positive, debits and outgoing
 *                               transfers negative
 * @param balance                the account balance right after this line
 * @param movementCode           the movement settled; only for SETTLEMENT
 * @param settlementCode         only for SETTLEMENT
 * @param categoryCode           the category of the movement settled, when it has one
 * @param invoicePaymentCode     the card invoice payment that generated the settlement, when it did
 * @param transferCode           only for TRANSFER_IN and TRANSFER_OUT
 * @param counterpartAccountCode the other account of the transfer
 */
public record AccountStatementEntry(
        AccountStatementEntryType type,
        LocalDate date,
        BigDecimal amount,
        BigDecimal balance,
        String description,
        UUID movementCode,
        UUID settlementCode,
        UUID categoryCode,
        UUID invoicePaymentCode,
        UUID transferCode,
        UUID counterpartAccountCode
) {
}
