package com.bts.personalbudget.controller.forecast.config;

import com.bts.personalbudget.controller.forecast.CashForecastResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;

@Tag(name = "Cash forecast", description = "API for the cash forecast")
public interface CashForecastControllerApiDocs {

    @Operation(summary = "Cash forecast from today through a horizon",
            description = "Starts from the balance of all accounts and adds what is still to arrive and takes "
                    + "out what is still to pay (obligations and card invoices), keeping every component apart. "
                    + "Overdue income is not counted as sure money unless include_late_income is true; overdue "
                    + "obligations and invoices stay in the debt. The envelope reserve and the card still "
                    + "committed after the horizon are taken out separately (free_after_reserves). Two alerts, "
                    + "independent: negative cash on some day, and not enough cash for the reserves.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cash forecast"),
            @ApiResponse(responseCode = "400", description = "until before today or more than 366 days ahead")
    })
    ResponseEntity<CashForecastResponse> forecast(
            @Parameter(description = "Last day included (yyyy-MM-dd). Default: the last day of the current "
                    + "cycle, or of the current month when there is none") LocalDate until,
            @Parameter(description = "Count overdue income as money that will arrive. Default false")
            boolean includeLateIncome);
}
