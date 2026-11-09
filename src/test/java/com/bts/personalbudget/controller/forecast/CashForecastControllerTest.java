package com.bts.personalbudget.controller.forecast;

import com.bts.personalbudget.core.domain.service.forecast.CashForecast;
import com.bts.personalbudget.core.domain.service.forecast.CashForecastService;
import com.bts.personalbudget.exception.ControllerExceptionHandler;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.CashForecastMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CashForecastControllerTest {

    @InjectMocks private CashForecastController controller;
    @Mock private CashForecastService service;
    @Spy private CashForecastMapper mapper = Mappers.getMapper(CashForecastMapper.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ControllerExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    private static CashForecast forecast() {
        return new CashForecast(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 30), false,
                new BigDecimal("1000.00"), new BigDecimal("360.00"), new BigDecimal("200.00"),
                new BigDecimal("240.00"), new BigDecimal("100.00"), new BigDecimal("1020.00"), UUID.randomUUID(),
                new BigDecimal("120.00"), new BigDecimal("200.00"), new BigDecimal("700.00"),
                new BigDecimal("600.00"), LocalDate.of(2026, 11, 12), null, false, false, true);
    }

    @Test
    void shouldReturnTheForecastWithSnakeCaseComponentsAndTheTwoAlerts() throws Exception {
        when(service.forecast(LocalDate.of(2026, 11, 30), false)).thenReturn(forecast());

        mvc.perform(get("/forecast").param("until", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.until").value("2026-11-30"))
                .andExpect(jsonPath("$.cash_balance").value(1000.00))
                .andExpect(jsonPath("$.expected_income").value(360.00))
                .andExpect(jsonPath("$.late_income").value(200.00))
                .andExpect(jsonPath("$.obligations").value(240.00))
                .andExpect(jsonPath("$.card_invoices").value(100.00))
                .andExpect(jsonPath("$.projected_cash").value(1020.00))
                .andExpect(jsonPath("$.envelope_reserve").value(120.00))
                .andExpect(jsonPath("$.card_beyond_horizon").value(200.00))
                .andExpect(jsonPath("$.free_after_reserves").value(700.00))
                .andExpect(jsonPath("$.lowest_projected_cash_date").value("2026-11-12"))
                .andExpect(jsonPath("$.first_negative_date").doesNotExist())
                .andExpect(jsonPath("$.negative_cash_alert").value(false))
                .andExpect(jsonPath("$.insufficient_budget_alert").value(false))
                .andExpect(jsonPath("$.projection_incomplete").value(true));
    }

    @Test
    void shouldPassTheLateIncomeAssumptionAndDefaultTheHorizon() throws Exception {
        when(service.forecast(null, true)).thenReturn(forecast());

        mvc.perform(get("/forecast").param("include_late_income", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldMapAnInvalidHorizonTo400() throws Exception {
        when(service.forecast(LocalDate.of(2020, 1, 1), false))
                .thenThrow(new InvalidFieldsException("Campos inválidos", CashForecast.class,
                        Map.of("until", "não pode ser anterior a hoje")));

        mvc.perform(get("/forecast").param("until", "2020-01-01")).andExpect(status().isBadRequest());
    }
}
