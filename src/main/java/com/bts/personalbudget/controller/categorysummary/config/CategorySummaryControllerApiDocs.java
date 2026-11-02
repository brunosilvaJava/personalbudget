package com.bts.personalbudget.controller.categorysummary.config;

import com.bts.personalbudget.controller.categorysummary.CategorySummaryResponse;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;

@Tag(name = "Category summary", description = "API for the totals of a period by category group and category")
public interface CategorySummaryControllerApiDocs {

    @Operation(
            summary = "Summarize a period by category",
            description = "Returns the totals of one operation type between start_date and end_date (inclusive), "
                    + "grouped by category group. The totals come from the same data as the daily balance, so they "
                    + "match the dashboard: total is what was paid in the period and projected_total also counts "
                    + "what is still pending, including occurrences of fixed and installment bills not posted yet. "
                    + "Debits are negative; percentages are positive. Movements without a category appear in a "
                    + "last group named \"Sem categoria\". The period can have at most 366 days.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Totals of the period by group and category"),
            @ApiResponse(responseCode = "400",
                    description = "Missing or invalid start_date, end_date or operation_type; end_date before "
                            + "start_date; period longer than 366 days")
    })
    ResponseEntity<CategorySummaryResponse> summarize(
            @Parameter(description = "First day of the period (yyyy-MM-dd)", example = "2026-11-01")
            LocalDate startDate,
            @Parameter(description = "Last day of the period (yyyy-MM-dd)", example = "2026-11-30")
            LocalDate endDate,
            @Parameter(description = "Operation type to summarize; DEBIT when omitted", example = "DEBIT")
            OperationType operationType);
}
