package com.bts.personalbudget.controller.thirdparty.config;

import com.bts.personalbudget.controller.thirdparty.ReceiptRequest;
import com.bts.personalbudget.controller.thirdparty.ReceivableResponse;
import com.bts.personalbudget.controller.thirdparty.ReceivableUpdateRequest;
import com.bts.personalbudget.controller.thirdparty.ReceivableWriteOffRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "Receivable", description = "Amounts a third party owes for installments paid on the card")
public interface ReceivableControllerApiDocs {

    @Operation(summary = "List the receivables of a third party",
            description = "One receivable per card installment of a purchase marked as theirs, oldest due date "
                    + "first. status is PENDING, LATE, PAID_OUT or WRITTEN_OFF.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Receivables"),
            @ApiResponse(responseCode = "404", description = "Third party not found")
    })
    ResponseEntity<List<ReceivableResponse>> find(UUID thirdPartyCode);

    @Operation(summary = "Change the due date of a receivable",
            description = "The only change allowed on a receivable. Amount, category and competence follow the "
                    + "installment; the generic movement endpoints refuse a receivable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Receivable updated"),
            @ApiResponse(responseCode = "400", description = "Invalid date"),
            @ApiResponse(responseCode = "404", description = "Third party or receivable not found")
    })
    ResponseEntity<ReceivableResponse> updateDueDate(UUID thirdPartyCode, UUID receivableCode,
                                                     ReceivableUpdateRequest request);

    @Operation(summary = "Receive an amount from a third party",
            description = "Allocates the amount from the oldest receivable to the newest, one settlement per "
                    + "receivable, all in one transaction; the last one may be partial. Receiving is not income "
                    + "and is allowed even in a closed cycle, like paying. Returns the receivables touched.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Amount received"),
            @ApiResponse(responseCode = "400", description = "Invalid amount, date or account"),
            @ApiResponse(responseCode = "404", description = "Third party not found"),
            @ApiResponse(responseCode = "409", description = "The amount is greater than what is open")
    })
    ResponseEntity<List<ReceivableResponse>> receive(UUID thirdPartyCode, ReceiptRequest request);

    @Operation(summary = "Write off a receivable",
            description = "No cash moves. Records the amount written off on the given date; without an amount, "
                    + "writes off what is still open. The date must belong to a cycle that is not closed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Receivable written off"),
            @ApiResponse(responseCode = "400", description = "Invalid date, or amount not between zero and the "
                    + "open balance"),
            @ApiResponse(responseCode = "404", description = "Third party or receivable not found"),
            @ApiResponse(responseCode = "409", description = "The write-off date belongs to a closed cycle")
    })
    ResponseEntity<ReceivableResponse> writeOff(UUID thirdPartyCode, UUID receivableCode,
                                                ReceivableWriteOffRequest request);
}
