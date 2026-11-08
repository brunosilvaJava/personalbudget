package com.bts.personalbudget.controller.movementsettlement;

import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlement;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlementService;
import com.bts.personalbudget.mapper.MovementSettlementMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MovementSettlementControllerTest {

    @InjectMocks private MovementSettlementController controller;
    @Mock private MovementSettlementService service;
    @Spy private MovementSettlementMapper mapper = Mappers.getMapper(MovementSettlementMapper.class);
    private MockMvc mvc;
    private final UUID movementCode = UUID.randomUUID();
    private final UUID settlementCode = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).setMessageConverters(
                new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules())).build();
    }

    @Test
    void shouldCreateSettlementAndReturnSeparateResponseWithCreatedStatus() throws Exception {
        when(service.create(eq(movementCode), any())).thenReturn(settlement());

        mvc.perform(post("/financial_movement/{code}/settlements", movementCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":30.00,\"effective_date\":\"2026-11-08\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movement_code").value(movementCode.toString()))
                .andExpect(jsonPath("$.amount").value(30.00));
    }

    @Test
    void shouldListAndReverseSettlementsThroughTheirOwnEndpoints() throws Exception {
        when(service.findAll(movementCode)).thenReturn(List.of(settlement()));
        when(service.reverse(movementCode, settlementCode)).thenReturn(settlement());

        mvc.perform(get("/financial_movement/{code}/settlements", movementCode))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value(settlementCode.toString()));
        mvc.perform(post("/financial_movement/{code}/settlements/{settlement}/reversal",
                        movementCode, settlementCode))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(settlementCode.toString()));
    }

    @Test
    void shouldRejectNonPositiveAmountAndMissingEffectiveDateBeforeCallingService() throws Exception {
        mvc.perform(post("/financial_movement/{code}/settlements", movementCode)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private MovementSettlement settlement() {
        return new MovementSettlement(settlementCode, movementCode, new BigDecimal("30.00"),
                LocalDate.of(2026, 11, 8), null, 0L, null, null, null);
    }
}
