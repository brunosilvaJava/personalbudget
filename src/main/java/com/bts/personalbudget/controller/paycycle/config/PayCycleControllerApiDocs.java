package com.bts.personalbudget.controller.paycycle.config;

import com.bts.personalbudget.controller.paycycle.PayCycleAdjustmentResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleCurrentNotFoundResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleClosurePreviewResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleClosureRequest;
import com.bts.personalbudget.controller.paycycle.PayCycleClosureResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleEndAdjustmentPreviewRequest;
import com.bts.personalbudget.controller.paycycle.PayCycleEndAdjustmentPreviewResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleEndAdjustmentRequest;
import com.bts.personalbudget.controller.paycycle.PayCycleEndAdjustmentResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleSummaryResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleReopenRequest;
import com.bts.personalbudget.controller.paycycle.PayCycleSnapshotResponse;
import com.bts.personalbudget.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Pay cycle", description = "API for reading and adjusting the financial cycles")
public interface PayCycleControllerApiDocs {

    @Operation(
            summary = "List the pay cycles",
            description = "Returns the cycles ordered by start date. A cycle is the interval [start_date, "
                    + "end_date_exclusive): the day the next one opens belongs to the next one. The list is not "
                    + "paginated. With date, only the cycle that contains it; with from and/or to, the cycles that "
                    + "share at least one day with that range (both inclusive). date cannot be combined with "
                    + "from or to. Cancelled cycles are returned only with include_cancelled.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of cycles"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    ResponseEntity<List<PayCycleResponse>> find(
            @Parameter(description = "Only the cycle that contains this date (yyyy-MM-dd)") LocalDate date,
            @Parameter(description = "Cycles that end after this day (yyyy-MM-dd)") LocalDate from,
            @Parameter(description = "Cycles that start on or before this day (yyyy-MM-dd)") LocalDate to,
            @Parameter(description = "Also return cancelled cycles. Defaults to false") boolean includeCancelled);

    @Operation(
            summary = "Retrieve the current pay cycle",
            description = "Returns the cycle that contains today. The answer is 404 with the pendencies that explain "
                    + "it when there is none: the pay cycles are off, the first cycle has not started, or the chain "
                    + "could not reach today (for example a calendar year that was not reviewed).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The current cycle",
                    content = @Content(schema = @Schema(implementation = PayCycleResponse.class))),
            @ApiResponse(responseCode = "404", description = "No cycle contains today",
                    content = @Content(schema = @Schema(implementation = PayCycleCurrentNotFoundResponse.class)))
    })
    ResponseEntity<?> findCurrent();

    @Operation(
            summary = "Retrieve a pay cycle by code",
            description = "Fetches a cycle by its unique identifier (UUID), which never changes when its dates are "
                    + "adjusted. Cancelled cycles are returned too.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cycle found and returned",
                    content = @Content(schema = @Schema(implementation = PayCycleResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cycle not found")
    })
    ResponseEntity<PayCycleResponse> findByCode(@PathVariable UUID code);

    @Operation(
            summary = "Summarize a pay cycle by competence",
            description = "Returns amounts recognized by competence_date and a projection of active "
                    + "financial movements plus known virtual occurrences of fixed bills, installments and "
                    + "income schedules. The interval is [start_date, end_date_exclusive). Recognized amounts "
                    + "use PAID_OUT movements; projected amounts include those recognized amounts, PENDING/LATE "
                    + "movements and virtual occurrences that are not already materialized. Debits are positive "
                    + "in this response. Category details include inactive categories used by existing movements "
                    + "and the 'Sem categoria' line. A pending item marks the projection incomplete; a clean "
                    + "response does not represent balances, transfers, cards or envelopes, which are outside "
                    + "this endpoint's supported sources.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cycle summary",
                    content = @Content(schema = @Schema(implementation = PayCycleSummaryResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cycle not found")
    })
    ResponseEntity<PayCycleSummaryResponse> summary(@PathVariable UUID code);

    @Operation(
            summary = "Preview closing a pay cycle",
            description = "Returns the competence summary and expected_version. Closure is refused when the cycle "
                    + "has projection pendencies, any LATE movement in its competence interval, or has not ended. "
                    + "Known PENDING movements alone do not prevent closure.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Closure preview",
                    content = @Content(schema = @Schema(implementation = PayCycleClosurePreviewResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cycle not found"),
            @ApiResponse(responseCode = "409", description = "The cycle is not active")
    })
    ResponseEntity<PayCycleClosurePreviewResponse> previewClosure(@PathVariable UUID code);

    @Operation(
            summary = "Close a pay cycle",
            description = "Uses the preview's expected_version. In one transaction it locks configuration, anchor "
                    + "and cycle, marks the cycle CLOSED, calculates its summary and records the immutable snapshot. "
                    + "The operation is rolled back if a projection pendency or LATE movement prevents closure.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cycle closed and snapshot created",
                    content = @Content(schema = @Schema(implementation = PayCycleClosureResponse.class))),
            @ApiResponse(responseCode = "400", description = "expected_version is required"),
            @ApiResponse(responseCode = "404", description = "Cycle not found"),
            @ApiResponse(responseCode = "409", description = "Stale version or closure blocker",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PayCycleClosureResponse> close(
            @PathVariable UUID code,
            @Valid @RequestBody PayCycleClosureRequest request);

    @Operation(
            summary = "Reopen a closed pay cycle",
            description = "Reopens the cycle using its current expected_version. Existing snapshots remain immutable "
                    + "and are retained as history; changes that affect the closed budget are permitted only after "
                    + "this operation.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cycle reopened",
                    content = @Content(schema = @Schema(implementation = PayCycleResponse.class))),
            @ApiResponse(responseCode = "400", description = "expected_version is required"),
            @ApiResponse(responseCode = "404", description = "Cycle not found"),
            @ApiResponse(responseCode = "409", description = "Cycle is not closed or version is stale",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PayCycleResponse> reopen(
            @PathVariable UUID code,
            @Valid @RequestBody PayCycleReopenRequest request);

    @Operation(
            summary = "List immutable snapshots of a pay cycle",
            description = "Returns the closure snapshots newest first, preserving the category names as they were "
                    + "when each snapshot was created.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Snapshots, possibly empty",
                    content = @Content(schema = @Schema(implementation = PayCycleSnapshotResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cycle not found")
    })
    ResponseEntity<List<PayCycleSnapshotResponse>> snapshots(@PathVariable UUID code);

    @Operation(
            summary = "Preview moving the end of a pay cycle",
            description = "Shows what moving the end of the cycle to new_end_date would do, and changes nothing. The "
                    + "end of a cycle is the day the next one opens, so the start of the next cycle moves with it and "
                    + "the two stay contiguous, with no gap and no overlap. The answer has the two cycles before and "
                    + "after, the financial movements whose competence date would change from one cycle to the other "
                    + "(the competence date itself does not change) and the income occurrences planned for the moved "
                    + "days. The versions in the answer are the ones the adjustment expects. The same rules as the "
                    + "adjustment apply, so a date the preview accepts is a date the adjustment accepts.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The preview",
                    content = @Content(schema = @Schema(implementation = PayCycleEndAdjustmentPreviewResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data: new_end_date missing, equal to the "
                    + "current end, or not after the start of the cycle and before the end of the next one"),
            @ApiResponse(responseCode = "404", description = "Cycle not found"),
            @ApiResponse(responseCode = "409", description = "The pay cycles are off, or the cycle cannot be adjusted: "
                    + "it is cancelled, belongs to an earlier activation or has no next cycle",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PayCycleEndAdjustmentPreviewResponse> previewEndAdjustment(
            @PathVariable UUID code,
            @Valid @RequestBody PayCycleEndAdjustmentPreviewRequest request);

    @Operation(
            summary = "Move the end of a pay cycle",
            description = "Moves the end of the cycle, and with it the start of the next one, to new_end_date. Both "
                    + "cycles are marked as adjusted (end_adjusted and start_adjusted), so the automatic "
                    + "recalculation never moves that boundary again, and the change is recorded in the history of "
                    + "both cycles in the same transaction. A justification is required. expected_version and "
                    + "expected_next_version are the versions of the two cycles the preview was made with: if either "
                    + "changed, the answer is 409 and nothing is written. The cycle codes and their reference months "
                    + "do not change. There is no undo: going back is another adjustment, with its own preview.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Boundary moved",
                    content = @Content(schema = @Schema(implementation = PayCycleEndAdjustmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data: justification missing or longer "
                    + "than 500 characters, or new_end_date missing, equal to the current end, or not after the start "
                    + "of the cycle and before the end of the next one"),
            @ApiResponse(responseCode = "404", description = "Cycle not found"),
            @ApiResponse(responseCode = "409", description = "One of the two versions changed, the pay cycles are off, "
                    + "or the cycle cannot be adjusted: it is cancelled, belongs to an earlier activation or has no "
                    + "next cycle",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PayCycleEndAdjustmentResponse> adjustEnd(
            @PathVariable UUID code,
            @Valid @RequestBody PayCycleEndAdjustmentRequest request);

    @Operation(
            summary = "Retrieve the adjustment history of a pay cycle",
            description = "Returns the entries of the history that touched the cycle, as the previous or as the next "
                    + "one of a boundary, newest first. A manual adjustment is in the history of both cycles around "
                    + "the boundary; a deactivation is in the history of the last cycle that had started and of the "
                    + "first one it cancelled. The list is empty when nothing touched the cycle. Cancelled cycles can "
                    + "be asked for too.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The history, possibly empty"),
            @ApiResponse(responseCode = "404", description = "Cycle not found")
    })
    ResponseEntity<List<PayCycleAdjustmentResponse>> findAdjustments(@PathVariable UUID code);

}
