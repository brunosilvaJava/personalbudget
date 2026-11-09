package com.bts.personalbudget.controller.cardinvoice.config;

import com.bts.personalbudget.controller.cardinvoice.CardInvoiceResponse;
import com.bts.personalbudget.controller.cardinvoice.CardInvoiceSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "Card invoice", description = "API for querying credit card invoices")
public interface CardInvoiceControllerApiDocs {

    @Operation(summary = "List the invoices of a card",
            description = "Newest first, without the installments. An invoice exists once a purchase has an "
                    + "installment in it. With reference_month (yyyy-MM) only that month's invoice, or an empty "
                    + "list when there is none. The state is derived: OPEN before the closing date, CLOSED from "
                    + "then on, PAID when every installment is paid.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of invoices"),
            @ApiResponse(responseCode = "404", description = "Card not found")
    })
    ResponseEntity<List<CardInvoiceSummaryResponse>> find(
            UUID code,
            @Parameter(description = "Month the invoice closes in, yyyy-MM") YearMonth referenceMonth);

    @Operation(summary = "Retrieve an invoice by code",
            description = "The invoice with its installments, in purchase and installment order, and its totals.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoice found"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    ResponseEntity<CardInvoiceResponse> findByCode(UUID code);
}
