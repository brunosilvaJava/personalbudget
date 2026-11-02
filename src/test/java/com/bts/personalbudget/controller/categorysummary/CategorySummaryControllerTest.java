package com.bts.personalbudget.controller.categorysummary;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.CategorySummary;
import com.bts.personalbudget.core.domain.service.category.CategorySummaryService;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.CategorySummaryMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategorySummaryController.class)
class CategorySummaryControllerTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 30);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategorySummaryService categorySummaryService;

    @MockBean
    private CategorySummaryMapper mapper;

    // PersonalBudgetApplication has @EnableJpaAuditing, which needs a JPA mapping context the
    // web slice does not create.
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void shouldAcceptSnakeCaseParamsAndPassThemToTheService() throws Exception {
        final CategorySummary summary = emptySummary(OperationType.CREDIT);
        when(categorySummaryService.summarize(START, END, OperationType.CREDIT)).thenReturn(summary);
        when(mapper.toResponse(summary)).thenReturn(new CategorySummaryResponse(
                START, END, OperationType.CREDIT, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));

        mockMvc.perform(get("/category_summary")
                        .param("start_date", "2026-11-01")
                        .param("end_date", "2026-11-30")
                        .param("operation_type", "CREDIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start_date").value("2026-11-01"))
                .andExpect(jsonPath("$.end_date").value("2026-11-30"))
                .andExpect(jsonPath("$.operation_type").value("CREDIT"))
                .andExpect(jsonPath("$.projected_total").value(0));

        verify(categorySummaryService).summarize(START, END, OperationType.CREDIT);
    }

    @Test
    void shouldSummarizeDebitsWhenOperationTypeIsOmitted() throws Exception {
        final CategorySummary summary = emptySummary(OperationType.DEBIT);
        when(categorySummaryService.summarize(START, END, OperationType.DEBIT)).thenReturn(summary);

        mockMvc.perform(get("/category_summary")
                        .param("start_date", "2026-11-01")
                        .param("end_date", "2026-11-30"))
                .andExpect(status().isOk());

        verify(categorySummaryService).summarize(START, END, OperationType.DEBIT);
    }

    @Test
    void shouldReturnBadRequestWhenParamsAreCamelCase() throws Exception {
        mockMvc.perform(get("/category_summary")
                        .param("startDate", "2026-11-01")
                        .param("endDate", "2026-11-30"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenOperationTypeIsUnknown() throws Exception {
        mockMvc.perform(get("/category_summary")
                        .param("start_date", "2026-11-01")
                        .param("end_date", "2026-11-30")
                        .param("operation_type", "TRANSFER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWithSnakeCaseFieldWhenPeriodIsInvalid() throws Exception {
        when(categorySummaryService.summarize(any(), any(), any())).thenThrow(new InvalidFieldsException(
                "Parâmetros inválidos", CategorySummaryService.class, Map.of("endDate", "período inválido")));

        mockMvc.perform(get("/category_summary")
                        .param("start_date", "2026-11-30")
                        .param("end_date", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validations.end_date").value("período inválido"));
    }

    private CategorySummary emptySummary(final OperationType operationType) {
        return new CategorySummary(START, END, operationType, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }
}
