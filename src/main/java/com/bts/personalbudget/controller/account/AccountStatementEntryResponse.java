package com.bts.personalbudget.controller.account;

import com.bts.personalbudget.core.domain.enumerator.AccountStatementEntryType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "One line of an account statement.")
public record AccountStatementEntryResponse(

        @Schema(description = "INITIAL_BALANCE, SETTLEMENT, TRANSFER_IN or TRANSFER_OUT", example = "SETTLEMENT")
        AccountStatementEntryType type,

        @Schema(description = "Effective date", example = "2026-11-08", type = "string", format = "date")
        LocalDate date,

        @Schema(description = "Signed amount: credits and incoming transfers positive, debits and outgoing "
                + "transfers negative", example = "-412.35")
        BigDecimal amount,

        @Schema(description = "The account balance right after this line", example = "1087.65")
        BigDecimal balance,

        @Schema(description = "Description of the movement or of the transfer; \"Saldo inicial\" for the initial "
                + "balance. May be null for a transfer without description", example = "Mercado da semana")
        String description,

        @JsonProperty("movement_code")
        @Schema(description = "The movement settled; only for SETTLEMENT")
        UUID movementCode,

        @JsonProperty("settlement_code")
        @Schema(description = "Only for SETTLEMENT")
        UUID settlementCode,

        @JsonProperty("category_code")
        @Schema(description = "Category of the movement settled, when it has one")
        UUID categoryCode,

        @JsonProperty("invoice_payment_code")
        @Schema(description = "The card invoice payment that generated the settlement, when it did")
        UUID invoicePaymentCode,

        @JsonProperty("transfer_code")
        @Schema(description = "Only for TRANSFER_IN and TRANSFER_OUT")
        UUID transferCode,

        @JsonProperty("counterpart_account_code")
        @Schema(description = "The other account of the transfer")
        UUID counterpartAccountCode
) {
}
