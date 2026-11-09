package com.bts.personalbudget.controller.cardpurchase.config;

import com.bts.personalbudget.controller.cardpurchase.CardPurchaseRequest;
import com.bts.personalbudget.controller.cardpurchase.CardPurchaseResponse;
import com.bts.personalbudget.controller.cardpurchase.CardPurchaseUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "Card purchase", description = "API for credit card purchases and their installments")
public interface CardPurchaseControllerApiDocs {

    @Operation(summary = "Register a card purchase",
            description = "Splits the purchase into installments of installment_amount, one per invoice starting "
                    + "at the invoice of the purchase date. Each installment is a DEBIT financial movement whose "
                    + "due date and competence are the due date of its invoice; the purchase date stays as the "
                    + "movement date. The invoices are created when needed. Nothing is paid: paying the invoice is "
                    + "what moves cash, so the purchase is never counted twice.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Purchase registered"),
            @ApiResponse(responseCode = "400", description = "Invalid fields, unknown or inactive card, or invalid "
                    + "category"),
            @ApiResponse(responseCode = "409", description = "The total passes the available limit, an installment "
                    + "falls in a paid invoice, or its competence is in a closed pay cycle")
    })
    ResponseEntity<CardPurchaseResponse> create(CardPurchaseRequest request);

    @Operation(summary = "List card purchases",
            description = "Active purchases with their installments whose purchase date is in the period (both "
                    + "dates inclusive, at most 366 days), newest first. card_code keeps one card. Not paginated.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of purchases"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid period")
    })
    ResponseEntity<List<CardPurchaseResponse>> find(
            @Parameter(description = "First day, yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "Last day (inclusive), yyyy-MM-dd") LocalDate endDate,
            @Parameter(description = "Only purchases of this card") UUID cardCode);

    @Operation(summary = "Retrieve a card purchase by code", description = "With its installments.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Purchase found"),
            @ApiResponse(responseCode = "404", description = "Purchase not found or deleted")
    })
    ResponseEntity<CardPurchaseResponse> findByCode(UUID code);

    @Operation(summary = "Update a card purchase",
            description = "Fields left out are kept; the card cannot change. Date, installments and amount are only "
                    + "accepted while no installment has a payment: the installments are recalculated, extra ones "
                    + "are deleted and missing ones created. A category change needs every competence of the "
                    + "purchase in an open pay cycle. The new total must fit the available limit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Purchase updated"),
            @ApiResponse(responseCode = "400", description = "Invalid fields or category"),
            @ApiResponse(responseCode = "404", description = "Purchase not found or deleted"),
            @ApiResponse(responseCode = "409", description = "An installment has a payment, the total passes the "
                    + "limit, an installment falls in a paid invoice, or a competence is in a closed pay cycle")
    })
    ResponseEntity<CardPurchaseResponse> update(UUID code, CardPurchaseUpdateRequest request);

    @Operation(summary = "Delete a card purchase",
            description = "Soft delete: the installments leave the reports and the limit is released. Refused when "
                    + "an installment has a payment or a competence is in a closed pay cycle.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Purchase deleted"),
            @ApiResponse(responseCode = "404", description = "Purchase not found or already deleted"),
            @ApiResponse(responseCode = "409", description = "An installment has a payment or a competence is in a "
                    + "closed pay cycle")
    })
    ResponseEntity<Void> delete(UUID code);
}
