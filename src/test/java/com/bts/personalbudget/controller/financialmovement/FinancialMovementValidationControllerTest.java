package com.bts.personalbudget.controller.financialmovement;

import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.exception.ControllerExceptionHandler;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinancialMovementController.class)
@Import(ControllerExceptionHandler.class)
class FinancialMovementValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FinancialMovementService service;

    @MockBean
    private FinancialMovementMapper mapper;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void shouldReturnSnakeCaseValidationFieldsWhenPostIncludesLegacyPaymentFields() throws Exception {
        mockMvc.perform(post("/financial_movement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operation_type":"DEBIT","description":"Padaria","amount":12.50,
                                 "movement_date":"2026-11-05T10:00:00","due_date":"2026-11-05T10:00:00",
                                 "amount_paid":5.00,"pay_date":"2026-11-05T10:00:00","status":"PAID_OUT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validations.amount_paid").exists())
                .andExpect(jsonPath("$.validations.pay_date").exists())
                .andExpect(jsonPath("$.validations.status").exists())
                .andExpect(jsonPath("$.validations.payment_fields_are_not_provided").doesNotExist());

        verifyNoInteractions(service, mapper);
    }

    @Test
    void shouldReturnSnakeCaseValidationFieldsWhenPatchIncludesLegacyPaymentFields() throws Exception {
        mockMvc.perform(patch("/financial_movement/123e4567-e89b-12d3-a456-426614174000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount_paid":5.00,"pay_date":"2026-11-05T10:00:00","status":"PAID_OUT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validations.amount_paid").exists())
                .andExpect(jsonPath("$.validations.pay_date").exists())
                .andExpect(jsonPath("$.validations.status").exists())
                .andExpect(jsonPath("$.validations.payment_fields_are_not_provided").doesNotExist());

        verifyNoInteractions(service, mapper);
    }
}
