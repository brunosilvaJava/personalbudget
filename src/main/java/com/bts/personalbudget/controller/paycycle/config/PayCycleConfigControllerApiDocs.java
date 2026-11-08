package com.bts.personalbudget.controller.paycycle.config;

import com.bts.personalbudget.controller.paycycle.PayCycleActivationPreviewRequest;
import com.bts.personalbudget.controller.paycycle.PayCycleActivationPreviewResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleActivationRequest;
import com.bts.personalbudget.controller.paycycle.PayCycleAnchorTransitionPreviewRequest;
import com.bts.personalbudget.controller.paycycle.PayCycleAnchorTransitionPreviewResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleAnchorTransitionRequest;
import com.bts.personalbudget.controller.paycycle.PayCycleAnchorTransitionResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleConfigResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleDeactivationRequest;
import com.bts.personalbudget.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Pay cycle configuration", description = "API for turning the configurable financial cycle on and off")
public interface PayCycleConfigControllerApiDocs {

    @Operation(
            summary = "Retrieve the pay cycle configuration",
            description = "Returns the mode (CALENDAR_MONTH, where reports follow the calendar month, or PAY_CYCLE, "
                    + "where the anchor income schedule opens the cycles), the anchor schedule, the activation date, "
                    + "the version and what is pending. The pendencies are calculated when read and go away by "
                    + "themselves when the calendar is reviewed or the rule is fixed.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The configuration",
                    content = @Content(schema = @Schema(implementation = PayCycleConfigResponse.class)))
    })
    @GetMapping
    ResponseEntity<PayCycleConfigResponse> find();

    @Operation(
            summary = "Preview an activation of the pay cycles",
            description = "Shows the first cycles an activation on initial_date would create, with dates, labels, "
                    + "references and pendencies, and creates nothing. When initial_date is the date of an anchor "
                    + "occurrence the first cycle is REGULAR; otherwise it is a TRANSITION from initial_date up to the "
                    + "next anchor date. The cycles are empty when the first one cannot be calculated; the "
                    + "pendencies say why (for example a calendar year that was not reviewed). The version in the "
                    + "answer is the one the activation expects.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The preview",
                    content = @Content(schema = @Schema(implementation = PayCycleActivationPreviewResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data, no active anchor schedule, or "
                    + "initial_date outside 31 days around today or before the end of a previous cycle"),
            @ApiResponse(responseCode = "409", description = "The pay cycles are already on",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/activation/preview")
    ResponseEntity<PayCycleActivationPreviewResponse> previewActivation(
            @Valid @RequestBody PayCycleActivationPreviewRequest request);

    @Operation(
            summary = "Activate the pay cycles",
            description = "Turns the pay cycles on: creates the first cycle on initial_date and the ones that keep "
                    + "the chain up to the current cycle plus two. Nothing before initial_date is reconstructed. "
                    + "expected_version is the version of the configuration the preview was made with.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pay cycles activated",
                    content = @Content(schema = @Schema(implementation = PayCycleConfigResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data, no active anchor schedule, "
                    + "initial_date not acceptable, or the first cycle cannot be calculated"),
            @ApiResponse(responseCode = "409", description = "The configuration changed since expected_version, or "
                    + "the pay cycles are already on",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/activation")
    ResponseEntity<PayCycleConfigResponse> activate(@Valid @RequestBody PayCycleActivationRequest request);

    @Operation(
            summary = "Deactivate the pay cycles",
            description = "Turns the pay cycles off: the cycles that have not started are cancelled and the ones "
                    + "that started stay as history. They can be activated again from the end of the last one. A "
                    + "justification is required and is recorded in the history of the last cycle that had started "
                    + "and of the first one that was cancelled (GET /pay_cycle/{code}/adjustment), in the same "
                    + "transaction.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pay cycles deactivated",
                    content = @Content(schema = @Schema(implementation = PayCycleConfigResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data: justification missing or "
                    + "longer than 500 characters"),
            @ApiResponse(responseCode = "409", description = "The configuration changed since expected_version, or "
                    + "the pay cycles are already off",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/deactivation")
    ResponseEntity<PayCycleConfigResponse> deactivate(@Valid @RequestBody PayCycleDeactivationRequest request);

    @Operation(
            summary = "Preview an anchor transition",
            description = "Previews changing the current anchor rule or switching to another active schedule. "
                    + "Started cycles and the last frozen boundary are preserved; future cycles that would be "
                    + "cancelled, created, or diverge are listed. Switching schedules requires transition_shape "
                    + "(SHORT or LONG) when the first new occurrence is after the frozen boundary. If the first "
                    + "occurrence is exactly on that boundary it continues regularly, without a TRANSITION. "
                    + "A same-schedule rule change derives SHORT or LONG from the direction of the date change. "
                    + "No dates are invented when a holiday year is not reviewed.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The preview, with no writes",
                    content = @Content(schema = @Schema(implementation = PayCycleAnchorTransitionPreviewResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid transition data or missing transition_shape"),
            @ApiResponse(responseCode = "409", description = "Cycles are off or the proposed transition cannot be calculated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/anchor_transition/preview")
    ResponseEntity<PayCycleAnchorTransitionPreviewResponse> previewAnchorTransition(
            @Valid @RequestBody PayCycleAnchorTransitionPreviewRequest request);

    @Operation(
            summary = "Apply an anchor transition",
            description = "Applies the rule change or anchor switch and rebuilds future cycles from the frozen "
                    + "boundary. expected_version must be the configuration version returned by the preview. "
                    + "A justification of up to 500 characters is recorded as ANCHOR_TRANSITION in the adjustment "
                    + "history in the same transaction. The configuration version advances after every transition.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transition applied",
                    content = @Content(schema = @Schema(implementation = PayCycleAnchorTransitionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid transition data, justification, or transition_shape"),
            @ApiResponse(responseCode = "409", description = "Stale configuration version, cycles are off, or "
                    + "transition dates cannot be calculated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/anchor_transition")
    ResponseEntity<PayCycleAnchorTransitionResponse> transitionAnchor(
            @Valid @RequestBody PayCycleAnchorTransitionRequest request);

}
