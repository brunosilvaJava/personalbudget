package com.bts.personalbudget.controller.creditcard;

import com.bts.personalbudget.core.domain.enumerator.CreditCardStatus;
import com.bts.personalbudget.core.domain.factory.CreditCardFactory;
import com.bts.personalbudget.core.domain.service.creditcard.CreditCardLimit;
import com.bts.personalbudget.core.domain.service.creditcard.CreditCardService;
import com.bts.personalbudget.exception.ControllerExceptionHandler;
import com.bts.personalbudget.mapper.CreditCardMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.util.List;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CreditCardControllerTest {

    @InjectMocks private CreditCardController controller;
    @Mock private CreditCardService service;
    @Spy private CreditCardMapper mapper = Mappers.getMapper(CreditCardMapper.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ControllerExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    @Test
    void shouldCreateCardWithCreatedStatusAndSnakeCaseBody() throws Exception {
        when(service.create(any())).thenReturn(CreditCardFactory.buildModel());

        mvc.perform(post("/credit_card").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nubank Roxinho\",\"closing_day\":25,\"due_day\":5,"
                                + "\"credit_limit\":5000.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(CreditCardFactory.CARD_CODE.toString()))
                .andExpect(jsonPath("$.closing_day").value(25))
                .andExpect(jsonPath("$.due_day").value(5))
                .andExpect(jsonPath("$.credit_limit").value(5000.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldRejectInvalidBodiesBeforeCallingService() throws Exception {
        mvc.perform(post("/credit_card").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ab\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/credit_card").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cartão\",\"closing_day\":32,\"due_day\":5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/credit_card").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cartão\",\"closing_day\":25,\"due_day\":5,\"credit_limit\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void shouldListAndFindByCode() throws Exception {
        when(service.find(isNull())).thenReturn(List.of(CreditCardFactory.buildModel()));
        when(service.findByCode(CreditCardFactory.CARD_CODE)).thenReturn(CreditCardFactory.buildModel());

        mvc.perform(get("/credit_card")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value(CreditCardFactory.CARD_NAME));
        mvc.perform(get("/credit_card/{code}", CreditCardFactory.CARD_CODE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.due_day").value(5));
    }

    @Test
    void shouldPassTheStatusFilterToTheService() throws Exception {
        when(service.find(List.of(CreditCardStatus.INACTIVE))).thenReturn(List.of());

        mvc.perform(get("/credit_card").param("status", "INACTIVE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldPassOnlyTheSentFieldsOfThePatchBodyToTheService() throws Exception {
        when(service.update(eq(CreditCardFactory.CARD_CODE), isNull(), eq(20), isNull(),
                eq(new BigDecimal("8000.00")), isNull())).thenReturn(CreditCardFactory.buildModel());

        mvc.perform(patch("/credit_card/{code}", CreditCardFactory.CARD_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closing_day\":20,\"credit_limit\":8000.00}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldInactivateAndActivateWithOkStatus() throws Exception {
        when(service.inactivate(CreditCardFactory.CARD_CODE)).thenReturn(CreditCardFactory.buildModel());
        when(service.activate(CreditCardFactory.CARD_CODE)).thenReturn(CreditCardFactory.buildModel());

        mvc.perform(patch("/credit_card/{code}/inactivate", CreditCardFactory.CARD_CODE))
                .andExpect(status().isOk());
        mvc.perform(patch("/credit_card/{code}/activate", CreditCardFactory.CARD_CODE))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnTheLimitOfTheCard() throws Exception {
        when(service.limit(CreditCardFactory.CARD_CODE)).thenReturn(new CreditCardLimit(
                CreditCardFactory.CARD_CODE, new BigDecimal("5000.00"), new BigDecimal("1250.00"),
                new BigDecimal("3750.00")));

        mvc.perform(get("/credit_card/{code}/limit", CreditCardFactory.CARD_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card_code").value(CreditCardFactory.CARD_CODE.toString()))
                .andExpect(jsonPath("$.credit_limit").value(5000.00))
                .andExpect(jsonPath("$.used").value(1250.00))
                .andExpect(jsonPath("$.available").value(3750.00));
    }
}
