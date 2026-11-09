package com.bts.personalbudget.controller.accounttransfer;

import com.bts.personalbudget.core.domain.factory.AccountFactory;
import com.bts.personalbudget.core.domain.factory.AccountTransferFactory;
import com.bts.personalbudget.core.domain.service.accounttransfer.AccountTransferService;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.ControllerExceptionHandler;
import com.bts.personalbudget.mapper.AccountTransferMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.LocalDate;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountTransferControllerTest {

    @InjectMocks private AccountTransferController controller;
    @Mock private AccountTransferService service;
    @Spy private AccountTransferMapper mapper = Mappers.getMapper(AccountTransferMapper.class);
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
    void shouldCreateTransferWithCreatedStatusAndSnakeCaseBody() throws Exception {
        when(service.create(any())).thenReturn(AccountTransferFactory.buildModel());

        mvc.perform(post("/account_transfer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from_account_code\":\"" + AccountFactory.ACCOUNT_CODE
                                + "\",\"to_account_code\":\"" + AccountFactory.OTHER_ACCOUNT_CODE
                                + "\",\"amount\":200.00,\"effective_date\":\"2026-11-05\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(AccountTransferFactory.TRANSFER_CODE.toString()))
                .andExpect(jsonPath("$.from_account_code").value(AccountFactory.ACCOUNT_CODE.toString()))
                .andExpect(jsonPath("$.to_account_code").value(AccountFactory.OTHER_ACCOUNT_CODE.toString()))
                .andExpect(jsonPath("$.amount").value(200.00))
                .andExpect(jsonPath("$.effective_date").value("2026-11-05"));
    }

    @Test
    void shouldRejectMissingFieldsBeforeCallingService() throws Exception {
        mvc.perform(post("/account_transfer").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void shouldListByPeriodAndFindByCode() throws Exception {
        when(service.find(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null))
                .thenReturn(List.of(AccountTransferFactory.buildModel()));
        when(service.findByCode(AccountTransferFactory.TRANSFER_CODE)).thenReturn(AccountTransferFactory.buildModel());

        mvc.perform(get("/account_transfer").param("start_date", "2026-11-01").param("end_date", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value(AccountTransferFactory.TRANSFER_CODE.toString()));
        mvc.perform(get("/account_transfer/{code}", AccountTransferFactory.TRANSFER_CODE))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReverseWithOkAndConflictWhenAlreadyReversed() throws Exception {
        when(service.reverse(AccountTransferFactory.TRANSFER_CODE))
                .thenReturn(AccountTransferFactory.buildModel())
                .thenThrow(new ConflictException("A transferência já foi estornada"));

        mvc.perform(post("/account_transfer/{code}/reversal", AccountTransferFactory.TRANSFER_CODE))
                .andExpect(status().isOk());
        mvc.perform(post("/account_transfer/{code}/reversal", AccountTransferFactory.TRANSFER_CODE))
                .andExpect(status().isConflict());
    }
}
