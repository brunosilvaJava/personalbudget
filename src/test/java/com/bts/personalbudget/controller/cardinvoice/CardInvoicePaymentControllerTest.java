package com.bts.personalbudget.controller.cardinvoice;

import com.bts.personalbudget.core.domain.factory.AccountFactory;
import com.bts.personalbudget.core.domain.factory.CardPurchaseFactory;
import com.bts.personalbudget.core.domain.service.cardinvoice.CardInvoicePaymentService;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.ControllerExceptionHandler;
import com.bts.personalbudget.mapper.CardInvoicePaymentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CardInvoicePaymentControllerTest {

    private static final UUID INVOICE = CardPurchaseFactory.INVOICE_CODE;
    private static final LocalDate DATE = LocalDate.of(2026, 12, 5);

    @InjectMocks private CardInvoicePaymentController controller;
    @Mock private CardInvoicePaymentService service;
    @Spy private CardInvoicePaymentMapper mapper = Mappers.getMapper(CardInvoicePaymentMapper.class);
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
    void shouldPayTheInvoiceAndReturn201WithTheSnakeCasePayment() throws Exception {
        when(service.pay(INVOICE, AccountFactory.ACCOUNT_CODE, DATE))
                .thenReturn(CardPurchaseFactory.buildPaymentModel(null));

        mvc.perform(post("/card_invoice/{code}/payment", INVOICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account_code\":\"" + AccountFactory.ACCOUNT_CODE + "\",\"payment_date\":\"2026-12-05\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(CardPurchaseFactory.PAYMENT_CODE.toString()))
                .andExpect(jsonPath("$.invoice_code").value(INVOICE.toString()))
                .andExpect(jsonPath("$.account_code").value(AccountFactory.ACCOUNT_CODE.toString()))
                .andExpect(jsonPath("$.payment_date").value("2026-12-05"))
                .andExpect(jsonPath("$.amount").value(140.00))
                .andExpect(jsonPath("$.reversed_at").doesNotExist());
    }

    @Test
    void shouldAcceptAPaymentWithoutAnAccount() throws Exception {
        when(service.pay(INVOICE, null, DATE)).thenReturn(CardPurchaseFactory.buildPaymentModel(null));

        mvc.perform(post("/card_invoice/{code}/payment", INVOICE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"payment_date\":\"2026-12-05\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldRejectAPaymentWithoutADateWith400() throws Exception {
        mvc.perform(post("/card_invoice/{code}/payment", INVOICE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).pay(INVOICE, null, null);
    }

    @Test
    void shouldMapAConflictTo409AndAnUnknownInvoiceTo404() throws Exception {
        when(service.pay(INVOICE, null, DATE)).thenThrow(new ConflictException("A fatura já foi paga"));
        final UUID other = UUID.randomUUID();
        when(service.pay(other, null, DATE)).thenThrow(new EntityNotFoundException("not found"));

        mvc.perform(post("/card_invoice/{code}/payment", INVOICE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"payment_date\":\"2026-12-05\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/card_invoice/{code}/payment", other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"payment_date\":\"2026-12-05\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldListThePaymentsIncludingReversedOnes() throws Exception {
        when(service.findAll(INVOICE)).thenReturn(List.of(
                CardPurchaseFactory.buildPaymentModel(LocalDateTime.of(2026, 12, 6, 9, 0))));

        mvc.perform(get("/card_invoice/{code}/payment", INVOICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reversed_at").value("2026-12-06T09:00:00"));
    }

    @Test
    void shouldReverseThePaymentAndReturn200() throws Exception {
        when(service.reverse(INVOICE, CardPurchaseFactory.PAYMENT_CODE)).thenReturn(
                CardPurchaseFactory.buildPaymentModel(LocalDateTime.of(2026, 12, 6, 9, 0)));

        mvc.perform(post("/card_invoice/{code}/payment/{paymentCode}/reversal", INVOICE,
                        CardPurchaseFactory.PAYMENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reversed_at").value("2026-12-06T09:00:00"));
    }
}
