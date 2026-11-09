package com.bts.personalbudget.controller.cardinvoice;

import com.bts.personalbudget.core.domain.factory.CardPurchaseFactory;
import com.bts.personalbudget.core.domain.factory.CreditCardFactory;
import com.bts.personalbudget.core.domain.service.cardinvoice.CardInvoiceService;
import com.bts.personalbudget.exception.ControllerExceptionHandler;
import com.bts.personalbudget.mapper.CardInvoiceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.EntityNotFoundException;
import java.time.YearMonth;
import java.util.List;
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

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CardInvoiceControllerTest {

    @InjectMocks private CardInvoiceController controller;
    @Mock private CardInvoiceService service;
    @Spy private CardInvoiceMapper mapper = Mappers.getMapper(CardInvoiceMapper.class);
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
    void shouldListTheInvoicesOfACardWithoutInstallments() throws Exception {
        when(service.find(CreditCardFactory.CARD_CODE, null))
                .thenReturn(List.of(CardPurchaseFactory.buildInvoiceModel(List.of())));

        mvc.perform(get("/credit_card/{code}/invoice", CreditCardFactory.CARD_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value(CardPurchaseFactory.INVOICE_CODE.toString()))
                .andExpect(jsonPath("$[0].reference_month").value("2026-11"))
                .andExpect(jsonPath("$[0].closing_date").value("2026-11-25"))
                .andExpect(jsonPath("$[0].due_date").value("2026-12-05"))
                .andExpect(jsonPath("$[0].state").value("OPEN"))
                .andExpect(jsonPath("$[0].total_amount").value(100.00))
                .andExpect(jsonPath("$[0].installments").doesNotExist());
    }

    @Test
    void shouldPassTheReferenceMonthToTheService() throws Exception {
        when(service.find(CreditCardFactory.CARD_CODE, YearMonth.of(2026, 11))).thenReturn(List.of());

        mvc.perform(get("/credit_card/{code}/invoice", CreditCardFactory.CARD_CODE)
                        .param("reference_month", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturnTheInvoiceDetailWithInstallmentsOrNotFound() throws Exception {
        when(service.findByCode(CardPurchaseFactory.INVOICE_CODE)).thenReturn(
                CardPurchaseFactory.buildInvoiceModel(List.of(CardPurchaseFactory.buildInstallmentModel())));
        when(service.findByCode(CreditCardFactory.CARD_CODE)).thenThrow(new EntityNotFoundException("not found"));

        mvc.perform(get("/card_invoice/{code}", CardPurchaseFactory.INVOICE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installments[0].description").value("Geladeira (1/3)"))
                .andExpect(jsonPath("$.installments[0].invoice_reference_month").value("2026-11"));
        mvc.perform(get("/card_invoice/{code}", CreditCardFactory.CARD_CODE)).andExpect(status().isNotFound());
    }
}
