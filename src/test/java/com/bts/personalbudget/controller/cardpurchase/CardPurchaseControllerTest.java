package com.bts.personalbudget.controller.cardpurchase;

import com.bts.personalbudget.core.domain.factory.CardPurchaseFactory;
import com.bts.personalbudget.core.domain.factory.CreditCardFactory;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardPurchaseService;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.ControllerExceptionHandler;
import com.bts.personalbudget.mapper.CardPurchaseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jakarta.persistence.EntityNotFoundException;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CardPurchaseControllerTest {

    @InjectMocks private CardPurchaseController controller;
    @Mock private CardPurchaseService service;
    @Spy private CardPurchaseMapper mapper = Mappers.getMapper(CardPurchaseMapper.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ControllerExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    private String body() {
        return "{\"card_code\":\"" + CreditCardFactory.CARD_CODE + "\",\"description\":\"Geladeira\","
                + "\"purchase_date\":\"2026-11-09\",\"installments\":3,\"installment_amount\":100.00}";
    }

    @Test
    void shouldCreatePurchaseWithCreatedStatusAndSnakeCaseBody() throws Exception {
        when(service.create(any())).thenReturn(CardPurchaseFactory.buildModel());

        mvc.perform(post("/card_purchase").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(CardPurchaseFactory.PURCHASE_CODE.toString()))
                .andExpect(jsonPath("$.card_code").value(CreditCardFactory.CARD_CODE.toString()))
                .andExpect(jsonPath("$.purchase_date").value("2026-11-09"))
                .andExpect(jsonPath("$.installment_amount").value(100.00))
                .andExpect(jsonPath("$.total_amount").value(300.00))
                .andExpect(jsonPath("$.installment_list[0].installment_number").value(1))
                .andExpect(jsonPath("$.installment_list[0].invoice_reference_month").value("2026-11"))
                .andExpect(jsonPath("$.installment_list[0].competence_date").value("2026-12-05"))
                .andExpect(jsonPath("$.installment_list[0].status").value("PENDING"));
    }

    @Test
    void shouldRejectInvalidBodiesBeforeCallingService() throws Exception {
        mvc.perform(post("/card_purchase").contentType(MediaType.APPLICATION_JSON).content("{\"installments\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/card_purchase").contentType(MediaType.APPLICATION_JSON)
                        .content(body().replace("\"installments\":3", "\"installments\":61")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/card_purchase").contentType(MediaType.APPLICATION_JSON)
                        .content(body().replace("100.00", "0")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void shouldAnswerConflictWhenThePurchasePassesTheLimit() throws Exception {
        when(service.create(any())).thenThrow(new ConflictException("Limite do cartão insuficiente"));

        mvc.perform(post("/card_purchase").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldListByPeriodAndFindByCode() throws Exception {
        when(service.find(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null))
                .thenReturn(List.of(CardPurchaseFactory.buildModel()));
        when(service.findByCode(CardPurchaseFactory.PURCHASE_CODE)).thenReturn(CardPurchaseFactory.buildModel());

        mvc.perform(get("/card_purchase").param("start_date", "2026-11-01").param("end_date", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value(CardPurchaseFactory.PURCHASE_CODE.toString()));
        mvc.perform(get("/card_purchase/{code}", CardPurchaseFactory.PURCHASE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installments").value(3));
    }

    @Test
    void shouldPassOnlyTheSentFieldsOfThePatchBodyToTheService() throws Exception {
        when(service.update(eq(CardPurchaseFactory.PURCHASE_CODE), isNull(), isNull(), eq(6), isNull(), isNull()))
                .thenReturn(CardPurchaseFactory.buildModel());

        mvc.perform(patch("/card_purchase/{code}", CardPurchaseFactory.PURCHASE_CODE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"installments\":6}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDeleteWithNoContentAndAnswerNotFoundForAnUnknownPurchase() throws Exception {
        doNothing().when(service).delete(CardPurchaseFactory.PURCHASE_CODE);
        doThrow(new EntityNotFoundException("not found")).when(service).delete(eq(CreditCardFactory.CARD_CODE));

        mvc.perform(delete("/card_purchase/{code}", CardPurchaseFactory.PURCHASE_CODE))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/card_purchase/{code}", CreditCardFactory.CARD_CODE))
                .andExpect(status().isNotFound());
    }
}
