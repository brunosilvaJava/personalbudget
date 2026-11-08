package com.bts.personalbudget.controller.paycycle;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Schema(description = "Resumo financeiro do ciclo calculado por competência, sem alterar os relatórios de caixa")
public record PayCycleSummaryResponse(
        @Schema(description = "Ciclo resumido") PayCycleResponse cycle,
        @JsonProperty("recognized_revenue")
        @Schema(description = "Receitas pagas cuja competência pertence ao ciclo", example = "5000.00")
        BigDecimal recognizedRevenue,
        @JsonProperty("recognized_expense")
        @Schema(description = "Despesas pagas cuja competência pertence ao ciclo", example = "1800.00")
        BigDecimal recognizedExpense,
        @JsonProperty("projected_revenue")
        @Schema(description = "Receitas reconhecidas mais pendentes e ocorrências virtuais conhecidas", example = "6000.00")
        BigDecimal projectedRevenue,
        @JsonProperty("projected_expense")
        @Schema(description = "Despesas reconhecidas mais pendentes e ocorrências virtuais conhecidas", example = "2500.00")
        BigDecimal projectedExpense,
        @JsonProperty("projection_incomplete")
        @Schema(description = "Indica se há pendências que impedem calcular parte da projeção", example = "false")
        boolean projectionIncomplete,
        @Schema(description = "Detalhe por categoria; lançamentos sem categoria usam o nome 'Sem categoria'")
        List<PayCycleSummaryCategoryResponse> categories,
        @Schema(description = "Pendências de calendário ou de fronteira que afetam a projeção")
        List<PayCycleSummaryPendingResponse> pending
) {

    @Schema(description = "Valores reconhecidos e projetados de uma categoria")
    public record PayCycleSummaryCategoryResponse(
            @JsonProperty("category_code") UUID categoryCode,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("operation_type") OperationType operationType,
            @JsonProperty("recognized_amount")
            @Schema(description = "Valor pago na competência, positivo para receitas e despesas")
            BigDecimal recognizedAmount,
            @JsonProperty("projected_amount")
            @Schema(description = "Valor reconhecido mais obrigações projetadas, sempre positivo")
            BigDecimal projectedAmount
    ) {
    }

    @Schema(description = "Pendência que pode tornar incompleta a projeção do ciclo")
    public record PayCycleSummaryPendingResponse(
            @Schema(description = "PAY_CYCLE ou INCOME_SCHEDULE", example = "INCOME_SCHEDULE")
            String source,
            @Schema(description = "Motivo da projeção incompleta", example = "CALENDAR_NOT_REVIEWED")
            String type,
            @JsonProperty("source_code")
            @Schema(description = "Código do ciclo ou da agenda de receita, conforme source")
            UUID sourceCode,
            @JsonProperty("nominal_month") YearMonth nominalMonth,
            Integer year,
            @JsonProperty("boundary_date") LocalDate boundaryDate,
            @JsonProperty("anchor_date") LocalDate anchorDate
    ) {
    }
}
