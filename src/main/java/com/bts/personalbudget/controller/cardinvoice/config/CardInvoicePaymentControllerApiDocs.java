package com.bts.personalbudget.controller.cardinvoice.config;

import com.bts.personalbudget.controller.cardinvoice.CardInvoicePaymentRequest;
import com.bts.personalbudget.controller.cardinvoice.CardInvoicePaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "Card invoice payment", description = "API for paying and reversing credit card invoices")
public interface CardInvoicePaymentControllerApiDocs {

    @Operation(summary = "Pay a card invoice in full",
            description = "Settles what is still owed on every installment of the invoice, in one account and "
                    + "on one date. The cash moves once, on the payment date; no consumption is created. Partial "
                    + "payment of an invoice is not supported.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Invoice paid"),
            @ApiResponse(responseCode = "400", description = "Missing date, unknown or inactive account, or a "
                    + "date before the account's initial balance date"),
            @ApiResponse(responseCode = "404", description = "Invoice not found"),
            @ApiResponse(responseCode = "409", description = "The invoice is already paid or has nothing to pay")
    })
    ResponseEntity<CardInvoicePaymentResponse> pay(UUID code, CardInvoicePaymentRequest request);

    @Operation(summary = "List the payments of an invoice",
            description = "Newest first. Reversed payments remain in the history with reversed_at filled.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of payments"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    ResponseEntity<List<CardInvoicePaymentResponse>> list(UUID code);

    @Operation(summary = "Reverse the payment of an invoice",
            description = "Reverses every settlement the payment generated: the installments are owed again "
                    + "and the card limit is released back. The record is kept, never deleted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment reversed"),
            @ApiResponse(responseCode = "404", description = "Invoice or payment not found"),
            @ApiResponse(responseCode = "409", description = "The payment was already reversed")
    })
    ResponseEntity<CardInvoicePaymentResponse> reverse(UUID code, UUID paymentCode);
}
