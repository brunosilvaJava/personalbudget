package com.bts.personalbudget.controller.budgetenvelope;

import com.bts.personalbudget.core.domain.factory.BudgetEnvelopeFactory;
import com.bts.personalbudget.core.domain.factory.CategoryFactory;
import com.bts.personalbudget.core.domain.service.budgetenvelope.BudgetEnvelopeService;
import com.bts.personalbudget.core.domain.service.budgetenvelope.CycleEnvelopes;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.ControllerExceptionHandler;
import com.bts.personalbudget.mapper.BudgetEnvelopeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BudgetEnvelopeControllerTest {

    private static final UUID CYCLE = BudgetEnvelopeFactory.CYCLE_CODE;
    private static final UUID CODE = BudgetEnvelopeFactory.ENVELOPE_CODE;

    @InjectMocks private BudgetEnvelopeController controller;
    @Mock private BudgetEnvelopeService service;
    @Spy private BudgetEnvelopeMapper mapper = Mappers.getMapper(BudgetEnvelopeMapper.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ControllerExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    private static String body(final String amount) {
        return "{\"category_code\":\"" + CategoryFactory.CATEGORY_CODE + "\",\"planned_amount\":" + amount + "}";
    }

    @Test
    void shouldCreateAnEnvelopeAndReturn201WithSnakeCaseFields() throws Exception {
        when(service.create(CYCLE, CategoryFactory.CATEGORY_CODE, new BigDecimal("500.00")))
                .thenReturn(BudgetEnvelopeFactory.buildModel());

        mvc.perform(post("/pay_cycle/{cycleCode}/envelope", CYCLE)
                        .contentType(MediaType.APPLICATION_JSON).content(body("500.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(CODE.toString()))
                .andExpect(jsonPath("$.cycle_code").value(CYCLE.toString()))
                .andExpect(jsonPath("$.category_name").value(CategoryFactory.CATEGORY_NAME))
                .andExpect(jsonPath("$.planned_amount").value(500.00))
                .andExpect(jsonPath("$.recognized_amount").value(100.00))
                .andExpect(jsonPath("$.committed_amount").value(200.00))
                .andExpect(jsonPath("$.consumed_amount").value(300.00))
                .andExpect(jsonPath("$.remaining_amount").value(200.00))
                .andExpect(jsonPath("$.reserved_amount").value(200.00))
                .andExpect(jsonPath("$.overrun_amount").value(0));
    }

    @Test
    void shouldRejectAMissingOrNonPositiveAmountOrCategoryWith400() throws Exception {
        mvc.perform(post("/pay_cycle/{cycleCode}/envelope", CYCLE)
                        .contentType(MediaType.APPLICATION_JSON).content(body("0")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/pay_cycle/{cycleCode}/envelope", CYCLE)
                        .contentType(MediaType.APPLICATION_JSON).content(body("1.234")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/pay_cycle/{cycleCode}/envelope", CYCLE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"planned_amount\":10.00}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/envelope/{code}", CODE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldMapAConflictTo409AndAnUnknownCycleTo404() throws Exception {
        when(service.create(CYCLE, CategoryFactory.CATEGORY_CODE, new BigDecimal("500.00")))
                .thenThrow(new ConflictException("A categoria já tem um envelope neste ciclo"));
        final UUID other = UUID.randomUUID();
        when(service.create(other, CategoryFactory.CATEGORY_CODE, new BigDecimal("500.00")))
                .thenThrow(new EntityNotFoundException("not found"));

        mvc.perform(post("/pay_cycle/{cycleCode}/envelope", CYCLE)
                        .contentType(MediaType.APPLICATION_JSON).content(body("500.00")))
                .andExpect(status().isConflict());
        mvc.perform(post("/pay_cycle/{cycleCode}/envelope", other)
                        .contentType(MediaType.APPLICATION_JSON).content(body("500.00")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldListTheEnvelopesOfTheCycleWithTotals() throws Exception {
        when(service.find(CYCLE)).thenReturn(new CycleEnvelopes(CYCLE, List.of(BudgetEnvelopeFactory.buildModel()),
                new BigDecimal("500.00"), new BigDecimal("300.00"), new BigDecimal("200.00"), BigDecimal.ZERO, true));

        mvc.perform(get("/pay_cycle/{cycleCode}/envelope", CYCLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycle_code").value(CYCLE.toString()))
                .andExpect(jsonPath("$.envelopes[0].code").value(CODE.toString()))
                .andExpect(jsonPath("$.total_planned").value(500.00))
                .andExpect(jsonPath("$.total_consumed").value(300.00))
                .andExpect(jsonPath("$.total_reserved").value(200.00))
                .andExpect(jsonPath("$.total_overrun").value(0))
                .andExpect(jsonPath("$.projection_incomplete").value(true));
    }

    @Test
    void shouldUpdateTheEnvelopeAndDeleteItWith204() throws Exception {
        when(service.update(CODE, new BigDecimal("900.00"))).thenReturn(BudgetEnvelopeFactory.buildModel());

        mvc.perform(patch("/envelope/{code}", CODE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"planned_amount\":900.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CODE.toString()));
        mvc.perform(delete("/envelope/{code}", CODE)).andExpect(status().isNoContent());

        verify(service).delete(CODE);
    }
}
